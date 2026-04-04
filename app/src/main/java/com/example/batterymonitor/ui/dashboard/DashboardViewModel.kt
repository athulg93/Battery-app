package com.example.batterymonitor.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.batterymonitor.data.local.ChargeSession
import com.example.batterymonitor.data.repository.BatteryRepository
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
    val healthEstimate: Float = 100f,
    val healthReasons: List<String> = emptyList(),
    val chargingWattage: Float = 0f,
    val overchargeOccurrences: Int = 0,
    val totalOverchargeDurationMs: Long = 0L
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BatteryRepository(application)
    
    private val _uiState = MutableStateFlow(BatteryStateUi())
    val uiState: StateFlow<BatteryStateUi> = _uiState.asStateFlow()

    val sessions: StateFlow<List<ChargeSession>> = repository.getSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val overchargeEvents: StateFlow<List<com.example.batterymonitor.data.local.OverchargeEvent>> = repository.getOverchargeEventsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            while (isActive) {
                val info = repository.getBatteryInfo()
                _uiState.value = _uiState.value.copy(
                    level = info.level,
                    isCharging = info.isCharging,
                    temperature = info.temperature,
                    voltage = info.voltage,
                    chargingWattage = info.chargingWattage
                )
                delay(5000)
            }
        }
        
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(sessions, overchargeEvents) { s, o ->
                Pair(s, o)
            }.collect { (sessionList, overchargeList) ->
                val healthData = repository.calculateBatteryHealth(sessionList, overchargeList)
                val totalMs = overchargeList.sumOf { it.durationMs }
                _uiState.value = _uiState.value.copy(
                    healthEstimate = healthData.score,
                    healthReasons = healthData.penaltyReasons,
                    overchargeOccurrences = overchargeList.size,
                    totalOverchargeDurationMs = totalMs
                )
            }
        }
    }

    suspend fun getLastChargeTime(): Long = repository.getLastChargeTime()
    suspend fun getLastFullChargeTime(): Long = repository.getLastFullChargeTime()
}
