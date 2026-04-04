package com.example.batterymonitor.data.repository

import android.app.Application
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkCapabilities
import android.os.BatteryManager
import com.example.batterymonitor.data.local.AppDatabase
import com.example.batterymonitor.data.local.AppUsageLog
import com.example.batterymonitor.data.local.ChargeSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.Calendar

data class BatteryInfo(
    val level: Int,
    val isCharging: Boolean,
    val temperature: Float,
    val voltage: Float,
    val chargingWattage: Float = 0f
)

data class BatteryHealthData(
    val score: Float,
    val penaltyReasons: List<String> = emptyList()
)

data class AppUsageSummary(
    val packageName: String,
    val label: String,
    val estimatedDrainPct: Float,
    val foregroundTimeMs: Long,
    val estimatedDrainMah: Float = 0f
)

data class DetailedAppStats(
    val foregroundTimeMs: Long = 0,
    val backgroundTimeMs: Long = 0,
    val foregroundDrainPct: Float = 0f,
    val backgroundDrainPct: Float = 0f,
    val networkUsageBytes: Long = 0,
    val cpuIntensityScore: Float = 0f,
    val wakeLockEstimate: Int = 0,
    val sevenDayAverageDrain: Float = 0f,
    val dailyTrend: List<Pair<String, Float>> = emptyList(),
    val daysOfData: Int = 0,
    val anomalyType: String? = null,
    val sotImpactPct: Float = 0f
)

class BatteryRepository(private val application: Application) {

    private val database = AppDatabase.getDatabase(application)
    private val usageStatsManager = application.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val networkStatsManager = application.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
    private val packageManager = application.packageManager

    private val labelCache = mutableMapOf<String, String>()

    fun getBatteryInfo(): BatteryInfo {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val intent = application.registerReceiver(null, filter)
        
        return intent?.let {
            val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val temp = it.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
            val volt = it.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
            val status = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)

            val currentPct = if (level != -1 && scale != -1) (level * 100) / scale else 0
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            val voltageV = if (volt > 1000) volt / 1000f else volt.toFloat()

            var wattage = 0f
            if (isCharging) {
                try {
                    val manager = application.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                    val currentNow = manager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) // in microamperes
                    if (currentNow != Long.MIN_VALUE) {
                        val currentAmps = currentNow / 1_000_000f
                        wattage = kotlin.math.abs(currentAmps * voltageV)
                    }
                } catch (e: Exception) {}
            }

            BatteryInfo(currentPct, isCharging, temp / 10f, voltageV, wattage)
        } ?: BatteryInfo(0, false, 0f, 0f, 0f)
    }

    fun getSessions(): Flow<List<ChargeSession>> = database.chargeSessionDao().getAllSessions()

    fun getOverchargeEventsFlow(): Flow<List<com.example.batterymonitor.data.local.OverchargeEvent>> = database.overchargeDao().getAllEventsFlow()

    fun calculateBatteryHealth(
        sessions: List<ChargeSession>, 
        overcharges: List<com.example.batterymonitor.data.local.OverchargeEvent> = emptyList()
    ): BatteryHealthData {
        if (sessions.isEmpty()) return BatteryHealthData(100f)
        
        var totalChargePercent = 0f
        var penaltyFactor = 1.0f
        val penalties = mutableSetOf<String>()
        
        val completedSessions = sessions.filter { it.isComplete }
        if (completedSessions.isEmpty()) return BatteryHealthData(100f)

        for (session in completedSessions) {
            val chargeDelta = (session.endLevel - session.startLevel).coerceAtLeast(0)
            totalChargePercent += chargeDelta
            if (session.endTemperature > 450) {
                penaltyFactor += 0.05f
                penalties.add("Thermal Stress (Charging above 45°C)")
            }
            if (session.startLevel < 15) {
                penaltyFactor += 0.02f
                penalties.add("Deep Discharge (Starting below 15%)")
            }
        }
        
        var totalOverchargeMs = 0L
        for (event in overcharges) {
            totalOverchargeMs += event.durationMs
        }
        val hoursOvercharged = totalOverchargeMs / (1000f * 60 * 60)
        if (hoursOvercharged > 0.5f) { // More than 30 mins cumulative triggers penalty
            penaltyFactor += (hoursOvercharged * 0.02f) // 2% compounded per hour overcharged
            penalties.add("Overcharging Degradation (Excessive time at 100%)")
        }
        
        val cycleCount = totalChargePercent / 100f
        val degradation = cycleCount * 0.05f * penaltyFactor
        return BatteryHealthData(
            score = (100f - degradation).coerceIn(50f, 100f),
            penaltyReasons = penalties.toList()
        )
    }

    suspend fun getAppUsageSummary(last24h: Long): List<AppUsageSummary> = withContext(Dispatchers.IO) {
        val logs = database.appUsageDao().getUsageSinceTime(last24h)
        if (logs.isEmpty()) return@withContext emptyList()

        val grouped = logs.groupBy { it.packageName }
        val aggregated = grouped.mapValues { it.value.maxOf { log -> log.foregroundTimeMs } }
        val totalTime = aggregated.values.sum().coerceAtLeast(1)

        val batteryCapacity = 4500f // Typical mAh
        val totalDrainPct = 30f // Assume 30% drain in 24h for calculation base
        
        aggregated.map { (pkg, timeMs) ->
            val label = labelCache[pkg] ?: try {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString().also {
                    labelCache[pkg] = it
                }
            } catch (e: Exception) { pkg.substringAfterLast(".") }

            val drainPct = (timeMs.toFloat() / totalTime) * totalDrainPct
            val drainMah = (drainPct / 100f) * batteryCapacity

            AppUsageSummary(pkg, label, drainPct, timeMs, drainMah)
        }.sortedByDescending { it.estimatedDrainPct }
    }

    suspend fun getDetailedStatsForApp(packageName: String): DetailedAppStats = withContext(Dispatchers.IO) {
        val endTime = System.currentTimeMillis()
        val startTime24h = endTime - (24 * 60 * 60 * 1000)
        val startTime7d = endTime - (7 * 24 * 60 * 60 * 1000)

        // Time Analysis
        val events = usageStatsManager.queryEvents(startTime24h, endTime)
        var foregroundTime = 0L
        var lastForegroundStart = 0L
        var wakeLockCount = 0
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.packageName == packageName) {
                when (event.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> lastForegroundStart = event.timeStamp
                    UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                        if (lastForegroundStart > 0) {
                            foregroundTime += (event.timeStamp - lastForegroundStart)
                            lastForegroundStart = 0
                        }
                    }
                    UsageEvents.Event.FOREGROUND_SERVICE_START -> wakeLockCount++
                }
            }
        }
        
        val stats24hTotal = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime24h, endTime)
        val totalDeviceSOT = stats24hTotal.sumOf { it.totalTimeInForeground }
        
        val stats24h = usageStatsManager.queryAndAggregateUsageStats(startTime24h, endTime)
        val totalActiveTime = stats24h[packageName]?.totalTimeInForeground ?: 0L
        val backgroundTime = (totalActiveTime - foregroundTime).coerceAtLeast(0L)
        val estWakeLocks = wakeLockCount + (backgroundTime / (1000 * 60 * 15)).toInt()

        // Network Usage
        var networkBytes = 0L
        try {
            val uid = packageManager.getPackageUid(packageName, 0)
            val wifiStats = networkStatsManager.querySummary(NetworkCapabilities.TRANSPORT_WIFI, null, startTime24h, endTime)
            val mobileStats = networkStatsManager.querySummary(NetworkCapabilities.TRANSPORT_CELLULAR, null, startTime24h, endTime)
            val netBucket = NetworkStats.Bucket()
            while (wifiStats.hasNextBucket()) {
                wifiStats.getNextBucket(netBucket)
                if (netBucket.uid == uid) networkBytes += (netBucket.rxBytes + netBucket.txBytes)
            }
            while (mobileStats.hasNextBucket()) {
                mobileStats.getNextBucket(netBucket)
                if (netBucket.uid == uid) networkBytes += (netBucket.rxBytes + netBucket.txBytes)
            }
        } catch (e: Exception) {}

        // Historical Trends
        val logs7d = database.appUsageDao().getUsageSinceTime(startTime7d)
        val weekdayFormatter = java.text.SimpleDateFormat("EEE", java.util.Locale.getDefault())
        val dailyTrend = mutableListOf<Pair<String, Float>>()
        val calendar = Calendar.getInstance()
        
        for (i in 6 downTo 0) {
            calendar.timeInMillis = endTime
            calendar.add(Calendar.DAY_OF_YEAR, -i)
            val dayStart = (calendar.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val dayEnd = (dayStart.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
            
            val dayDrain = getAppUsageInRange(packageName, dayStart.timeInMillis, dayEnd.timeInMillis)
            dailyTrend.add(weekdayFormatter.format(dayStart.time) to dayDrain)
        }
        
        val averageDrain = if (dailyTrend.isNotEmpty()) dailyTrend.map { it.second }.average().toFloat() else 0f
        val current24hDrain = getAppUsageInRange(packageName, startTime24h, endTime)
        val backgroundDrain = (backgroundTime.toFloat() / totalActiveTime.coerceAtLeast(1)) * current24hDrain
        val daysOfData = dailyTrend.count { it.second > 0 }
        
        val anomalyType: String? = when {
            current24hDrain < 1.5f -> null
            backgroundDrain > 4f -> "High background activity"
            daysOfData < 3 && current24hDrain > 10f -> "Heavy battery usage detected"
            daysOfData >= 3 && current24hDrain > (averageDrain * 1.5f) -> "Using significantly more battery than usual"
            else -> null
        }

        val sotImpact = if (totalDeviceSOT > 0) (foregroundTime.toFloat() / totalDeviceSOT) * 100 else 0f

        DetailedAppStats(
            foregroundTimeMs = foregroundTime,
            backgroundTimeMs = backgroundTime,
            foregroundDrainPct = (foregroundTime.toFloat() / totalActiveTime.coerceAtLeast(1)) * current24hDrain,
            backgroundDrainPct = backgroundDrain,
            networkUsageBytes = networkBytes,
            cpuIntensityScore = (totalActiveTime.toFloat() / (24 * 60 * 60 * 1000)) * 1000,
            wakeLockEstimate = estWakeLocks,
            sevenDayAverageDrain = averageDrain,
            dailyTrend = dailyTrend,
            daysOfData = daysOfData,
            anomalyType = anomalyType,
            sotImpactPct = sotImpact
        )
    }

    private suspend fun getAppUsageInRange(packageName: String, start: Long, end: Long): Float {
        val logs = database.appUsageDao().getUsageSinceTime(start)
        val windowLogs = logs.filter { it.timestamp < end }
        if (windowLogs.isEmpty()) return 0f

        val grouped = windowLogs.groupBy { it.packageName }
        val aggregated = grouped.mapValues { it.value.maxOf { log -> log.foregroundTimeMs } }
        val totalTime = aggregated.values.sum().coerceAtLeast(1)
        val appTime = aggregated[packageName] ?: 0L

        return (appTime.toFloat() / totalTime) * 100f
    }
    
    suspend fun getHourlyUsageForApp(packageName: String, since: Long): List<Float> = withContext(Dispatchers.IO) {
        val logs = database.appUsageDao().getUsageSinceTime(since)
        val hourlyBuckets = FloatArray(24) { 0f }
        
        val calendar = Calendar.getInstance()
        val now = System.currentTimeMillis()
        
        logs.filter { it.packageName == packageName }.forEach { log ->
            calendar.timeInMillis = log.timestamp
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            // Add normalized value (we'll just use raw ms and then normalize in UI or here)
            hourlyBuckets[hour] += log.foregroundTimeMs.toFloat()
        }
        
        val maxVal = hourlyBuckets.maxOrNull()?.coerceAtLeast(1f) ?: 1f
        hourlyBuckets.map { it / maxVal }.toList()
    }

    suspend fun getLastChargeTime(): Long = database.chargeSessionDao().getLastSession()?.startTime ?: 0L
    suspend fun getLastFullChargeTime(): Long = database.chargeSessionDao().getLastFullCharge()?.startTime ?: 0L
    
    suspend fun getLatestActivityTimestamp(): Long = withContext(Dispatchers.IO) {
        val lastSession = database.chargeSessionDao().getLastSession()?.endTime ?: 0L
        val lastUsage = database.appUsageDao().getUsageSinceTime(System.currentTimeMillis() - 365L * 24 * 60 * 60 * 1000).maxOfOrNull { it.timestamp } ?: 0L
        maxOf(lastSession, lastUsage)
    }

    suspend fun insertSessions(sessions: List<ChargeSession>) = withContext(Dispatchers.IO) {
        database.chargeSessionDao().insertSessions(sessions)
    }

    suspend fun insertUsageLogs(logs: List<AppUsageLog>) = withContext(Dispatchers.IO) {
        database.appUsageDao().insertUsageLogs(logs)
    }
}
