package com.example.batterymonitor.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.batterymonitor.R
import com.example.batterymonitor.data.local.AppDatabase
import com.example.batterymonitor.data.local.ChargeSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BatteryMonitorService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private lateinit var database: AppDatabase
    
    private var currentSessionId: Long? = null
    private var lastLevel: Int = -1
    private var isCharging: Boolean = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_BATTERY_CHANGED -> handleBatteryChanged(intent)
                Intent.ACTION_POWER_CONNECTED -> handlePowerConnected()
                Intent.ACTION_POWER_DISCONNECTED -> handlePowerDisconnected(intent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getDatabase(this)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("Monitoring Battery..."))

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        registerReceiver(batteryReceiver, filter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(batteryReceiver)
        job.cancel()
    }

    private fun handleBatteryChanged(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
        val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
        
        if (level == -1 || scale == -1) return
        val currentPct = (level * 100) / scale.toFloat()
        
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val currentlyCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || 
                                status == BatteryManager.BATTERY_STATUS_FULL

        // Check for over-temperature warnings (e.g., > 40C / 400 tenths of a degree)
        if (temp > 400 && currentlyCharging) {
            sendAlertNotification("High Battery Temperature!", "Battery is at ${temp / 10f}°C while charging. Consider unplugging.")
        }

        // Trickle charge warning if full and still plugged in
        if (status == BatteryManager.BATTERY_STATUS_FULL && currentlyCharging) {
             // In a real app we might debounce this so it doesn't spam
             sendAlertNotification("Battery Full", "Device is at 100%. Unplug to avoid battery wear.")
        }
    }

    private fun handlePowerConnected() {
        isCharging = true
        Log.d("BatteryMonitor", "Power Connected - Starting Session")
        
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val temp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val voltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
        
        val startPct = if (level != -1 && scale != -1) (level * 100) / scale else -1

        scope.launch {
            val session = ChargeSession(
                startTime = System.currentTimeMillis(),
                endTime = 0,
                startLevel = startPct,
                endLevel = -1,
                isComplete = false,
                startVoltage = voltage,
                endVoltage = -1,
                startTemperature = temp,
                endTemperature = -1
            )
            currentSessionId = database.chargeSessionDao().insertSession(session)
        }
    }

    private fun handlePowerDisconnected(lastIntent: Intent) {
        isCharging = false
        Log.d("BatteryMonitor", "Power Disconnected - Ending Session")
        
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val temp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val voltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
        
        val endPct = if (level != -1 && scale != -1) (level * 100) / scale else -1
        
        if (endPct < 80) {
            sendAlertNotification("Incomplete Charge", "You unplugged at $endPct%. Charging to 80-90% is optimal for lithium batteries.")
        }

        currentSessionId?.let { id ->
            scope.launch {
                val session = database.chargeSessionDao().getLastSession()
                if (session != null && session.id == id.toInt()) {
                    val updated = session.copy(
                        endTime = System.currentTimeMillis(),
                        endLevel = endPct,
                        isComplete = true,
                        endVoltage = voltage,
                        endTemperature = temp
                    )
                    database.chargeSessionDao().updateSession(updated)
                }
                currentSessionId = null
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Battery Monitor Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
            
            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "Battery Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )
            manager?.createNotificationChannel(alertChannel)
        }
    }

    private fun createNotification(text: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Battery Monitor")
        .setContentText(text)
        .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()
        
    private fun sendAlertNotification(title: String, text: String) {
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
            
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(System.currentTimeMillis().toInt(), notification)
    }

    companion object {
        const val CHANNEL_ID = "BatteryMonitorServiceChannel"
        const val ALERT_CHANNEL_ID = "BatteryAlertChannel"
        const val NOTIFICATION_ID = 1
    }
}
