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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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

            _uiState.value = _uiState.value.copy(
                level = currentPct,
                isCharging = isCharging,
                temperature = temp / 10f, // Temperature is in tenths of a degree C
                voltage = volt / 1000f // Voltage is usually in mV
            )
        }
    }

    private fun calculateBatteryHealth(sessions: List<ChargeSession>): Float {
        if (sessions.isEmpty()) return 100f
        
        // A very simplified health estimation model based on completed charging cycles.
        // It penalizes deep discharges (charging from < 20%) and high heat.
        var healthPoints = 100f
        val completedCycles = sessions.filter { it.isComplete }
        
        if (completedCycles.isEmpty()) return 100f
        
        for (session in completedCycles) {
            // Charging from very low (0-20%) hurts health more than 40-80% cycles
            if (session.startLevel in 0..20) {
                healthPoints -= 0.1f
            }
            
            // Charging to 100% and high heat also penalizes slightly over time
            if (session.endLevel > 95) {
                healthPoints -= 0.05f
            }
            if (session.endTemperature > 400) { // > 40C
                healthPoints -= 0.2f
            }
        }
        
        return healthPoints.coerceIn(50f, 100f)
    }
}
