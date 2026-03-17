package com.example.batterymonitor.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.batterymonitor.service.BatteryMonitorService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Device booted, starting BatteryMonitorService")
            val serviceIntent = Intent(context, BatteryMonitorService::class.java)
            context.startForegroundService(serviceIntent)
        }
    }
}
