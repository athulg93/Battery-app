package com.example.batterymonitor.ui.dashboard

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.batterymonitor.data.local.AppDatabase
import com.example.batterymonitor.data.local.AppUsageLog
import com.example.batterymonitor.data.local.ChargeSession
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.roundToInt

data class BatteryStateUi(
    val level: Int = 0,
    val isCharging: Boolean = false,
    val temperature: Float = 0f,
    val voltage: Float = 0f,
    val healthEstimate: Float = 100f
)

data class AppUsageSummary(
    val packageName: String,
    val estimatedDrainPct: Float,
    val foregroundTimeMs: Long
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
    val dailyTrend: List<Pair<String, Float>> = emptyList(), // Day Label to Drain%
    val hasAnomaly: Boolean = false,
    val sotImpactPct: Float = 0f
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(BatteryStateUi())
    val uiState: StateFlow<BatteryStateUi> = _uiState.asStateFlow()

    private val database = AppDatabase.getDatabase(application)
    
    val sessions = database.chargeSessionDao().getAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _appUsage = MutableStateFlow<List<AppUsageSummary>>(emptyList())
    val appUsage: StateFlow<List<AppUsageSummary>> = _appUsage.asStateFlow()

    init {
        viewModelScope.launch {
            while (isActive) {
                updateBatteryState()
                delay(5000) // Update every 5 seconds
            }
        }
        
        viewModelScope.launch {
            sessions.collect { sessionList ->
                _uiState.value = _uiState.value.copy(
                    healthEstimate = calculateBatteryHealth(sessionList)
                )
            }
        }

        viewModelScope.launch {
            // Load usage from the last 24 hours
            val last24h = System.currentTimeMillis() - (1000 * 60 * 60 * 24)
            database.appUsageDao().getUsageSince(last24h).collect { logs ->
                _appUsage.value = calculateUsagePercentages(logs)
            }
        }
    }

    private fun calculateUsagePercentages(logs: List<AppUsageLog>): List<AppUsageSummary> {
        if (logs.isEmpty()) return emptyList()

        // Group by package across multiple worker polls
        val grouped = logs.groupBy { it.packageName }
        val aggregated = grouped.mapValues { entry -> 
            entry.value.maxOf { it.foregroundTimeMs } // The UsageStatsManager returns total time, so we take the max
        }

        val totalForegroundTimeMs = aggregated.values.sum().coerceAtLeast(1) // prevent div by zero

        return aggregated.map { (pkg, timeMs) ->
            AppUsageSummary(
                packageName = pkg,
                estimatedDrainPct = (timeMs.toFloat() / totalForegroundTimeMs) * 100f,
                foregroundTimeMs = timeMs
            )
        }.sortedByDescending { it.estimatedDrainPct }
    }

    private fun updateBatteryState() {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val intent = getApplication<Application>().registerReceiver(null, filter)
        
        intent?.let {
            val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val temp = it.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
            val volt = it.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
            val status = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)

            val currentPct = if (level != -1 && scale != -1) (level * 100) / scale else 0
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

            // Voltage handling: Android returns mV, but some environments/emulators might return V.
            // Standard Li-ion is ~3.7V - 4.4V. 
            // If volt is e.g. 4000, it's mV. If it's e.g. 4, it's V.
            val voltageV = when {
                volt > 1000 -> volt / 1000f
                volt > 0 -> volt.toFloat()
                else -> 0f
            }

            _uiState.value = _uiState.value.copy(
                level = currentPct,
                isCharging = isCharging,
                temperature = temp / 10f, // Temperature is in tenths of a degree C
                voltage = voltageV
            )
        }
    }

    private fun calculateBatteryHealth(sessions: List<ChargeSession>): Float {
        if (sessions.isEmpty()) return 100f
        
        // A more realistic health estimation based on cumulative charge (cycles).
        // 1 cycle = 100% total charge amount.
        // Li-ion usually starts degrading after ~300-500 cycles.
        
        var totalChargePercent = 0f
        var penaltyFactor = 1.0f
        
        val completedSessions = sessions.filter { it.isComplete }
        if (completedSessions.isEmpty()) return 100f

        for (session in completedSessions) {
            val chargeDelta = (session.endLevel - session.startLevel).coerceAtLeast(0)
            totalChargePercent += chargeDelta
            
            // Penalize high heat (>45C)
            if (session.endTemperature > 450) {
                penaltyFactor += 0.05f
            }
            // Penalize deep discharge (<15%)
            if (session.startLevel < 15) {
                penaltyFactor += 0.02f
            }
        }
        
        val cycleCount = totalChargePercent / 100f
        
        // Simplified degradation: 0.05% loss per cycle * penalty factor
        val degradation = cycleCount * 0.05f * penaltyFactor
        
        val health = 100f - degradation
        return health.coerceIn(50f, 100f)
    }

    suspend fun getLastChargeTime(): Long {
        return database.chargeSessionDao().getLastSession()?.startTime ?: 0L
    }

    suspend fun getLastFullChargeTime(): Long {
        return database.chargeSessionDao().getLastFullCharge()?.startTime ?: 0L
    }

    suspend fun getAppUsageSince(packageName: String, sinceTime: Long): Float {
        val logs = database.appUsageDao().getUsageSinceTime(sinceTime)
        if (logs.isEmpty()) return 0f

        val grouped = logs.groupBy { it.packageName }
        val aggregated = grouped.mapValues { it.value.maxOf { log -> log.foregroundTimeMs } }
        val totalTime = aggregated.values.sum().coerceAtLeast(1)
        val appTime = aggregated[packageName] ?: 0L

        return (appTime.toFloat() / totalTime) * 100f
    }

    suspend fun getDetailedStatsForApp(packageName: String): DetailedAppStats {
        val appContext = getApplication<Application>()
        val usageStatsManager = appContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val networkStatsManager = appContext.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
        
        val endTime = System.currentTimeMillis()
        val startTime24h = endTime - (24 * 60 * 60 * 1000)
        val startTime7d = endTime - (7 * 24 * 60 * 60 * 1000)

        // 1. Time Analysis (Foreground vs Background)
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
                    // PARTIAL_WAKE_LOCK or generic background activity event check
                    // On modern Android, background services/jobs are mapped to certain event types
                    UsageEvents.Event.FOREGROUND_SERVICE_START -> wakeLockCount++
                }
            }
        }
        
        // Total Device Screen-On Time (SOT)
        val stats24hTotal = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime24h, endTime)
        val totalDeviceSOT = stats24hTotal.sumOf { it.totalTimeInForeground }
        
        // Total time app was "active"
        val stats24h = usageStatsManager.queryAndAggregateUsageStats(startTime24h, endTime)
        val totalActiveTime = stats24h[packageName]?.totalTimeInForeground ?: 0L
        val backgroundTime = (totalActiveTime - foregroundTime).coerceAtLeast(0L)
        
        // Refined Wake Lock Heuristic: Events + Background Active Blocks
        val estWakeLocks = wakeLockCount + (backgroundTime / (1000 * 60 * 15)).toInt() // Base on events + 1 per 15 mins background

        // 2. Network Usage
        var networkBytes = 0L
        try {
            val uid = appContext.packageManager.getPackageUid(packageName, 0)
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
        } catch (e: Exception) { /* Silently fail */ }

        // 3. Historical Trends (7 days)
        val logs7d = database.appUsageDao().getUsageSinceTime(startTime7d)
        val weekdayFormatter = java.text.SimpleDateFormat("EEE", java.util.Locale.getDefault())
        
        // Group logs by day and calculate total drain % per day for this app
        val dailyTrend = mutableListOf<Pair<String, Float>>()
        val calendar = Calendar.getInstance()
        
        for (i in 6 downTo 0) {
            calendar.timeInMillis = endTime
            calendar.add(Calendar.DAY_OF_YEAR, -i)
            val dayStart = calendar.clone() as Calendar
            dayStart.set(Calendar.HOUR_OF_DAY, 0)
            dayStart.set(Calendar.MINUTE, 0)
            dayStart.set(Calendar.SECOND, 0)
            dayStart.set(Calendar.MILLISECOND, 0)
            
            val dayEnd = dayStart.clone() as Calendar
            dayEnd.add(Calendar.DAY_OF_YEAR, 1)
            
            val dayLogs = logs7d.filter { 
                it.packageName == packageName && it.timestamp >= dayStart.timeInMillis && it.timestamp < dayEnd.timeInMillis 
            }
            
            // Heuristic: Max consumption log for that day (since my logs are cumulative/snapshots)
            val dayDrain = if (dayLogs.isNotEmpty()) {
                // In a real scenario, we'd sum diffs, but here max() represents the "end of day" total for that app's sessions
                // To be accurate with %: we use getAppUsageSince for that specific 24h window
                getAppUsageSince(packageName, dayStart.timeInMillis, dayEnd.timeInMillis)
            } else 0f
            
            dailyTrend.add(weekdayFormatter.format(dayStart.time) to dayDrain)
        }
        
        val averageDrain = if (dailyTrend.isNotEmpty()) dailyTrend.map { it.second }.average().toFloat() else 0f
        val current24hDrain = getAppUsageSince(packageName, startTime24h)
        val backgroundDrain = (backgroundTime.toFloat() / totalActiveTime.coerceAtLeast(1)) * current24hDrain
        val totalImpactSinceCharge = getAppUsageSince(packageName, viewModelScope.run { getLastChargeTime() })

        // Intelligent Anomaly Detection
        val hasAnomaly = (backgroundDrain > 3f) || // Condition 1: High Background Drain
                         (averageDrain > 0 && current24hDrain > (averageDrain * 1.5f)) || // Condition 2: Abnormal Drain Rate
                         (totalImpactSinceCharge > 15f) // Condition 3: Massive Hog

        // SOT Impact: Share of total device screen time
        val sotImpact = if (totalDeviceSOT > 0) (foregroundTime.toFloat() / totalDeviceSOT) * 100 else 0f

        return DetailedAppStats(
            foregroundTimeMs = foregroundTime,
            backgroundTimeMs = backgroundTime,
            foregroundDrainPct = (foregroundTime.toFloat() / totalActiveTime.coerceAtLeast(1)) * current24hDrain,
            backgroundDrainPct = backgroundDrain,
            networkUsageBytes = networkBytes,
            cpuIntensityScore = (totalActiveTime.toFloat() / (24 * 60 * 60 * 1000)) * 1000,
            wakeLockEstimate = estWakeLocks,
            sevenDayAverageDrain = averageDrain,
            dailyTrend = dailyTrend,
            hasAnomaly = hasAnomaly,
            sotImpactPct = sotImpact
        )
    }

    // Overloaded helper for specific time window
    private suspend fun getAppUsageSince(packageName: String, start: Long, end: Long): Float {
        val logs = database.appUsageDao().getUsageSinceTime(start)
        val windowLogs = logs.filter { it.timestamp < end }
        if (windowLogs.isEmpty()) return 0f

        val grouped = windowLogs.groupBy { it.packageName }
        val aggregated = grouped.mapValues { it.value.maxOf { log -> log.foregroundTimeMs } }
        val totalTime = aggregated.values.sum().coerceAtLeast(1)
        val appTime = aggregated[packageName] ?: 0L

        return (appTime.toFloat() / totalTime) * 100f
    }
}
