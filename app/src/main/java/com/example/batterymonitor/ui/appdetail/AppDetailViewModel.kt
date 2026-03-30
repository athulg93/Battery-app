package com.example.batterymonitor.ui.appdetail

import android.app.Application
import android.graphics.drawable.Drawable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.batterymonitor.data.repository.BatteryRepository
import com.example.batterymonitor.data.repository.DetailedAppStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AppDetailState {
    object Loading : AppDetailState()
    data class Success(
        val label: String,
        val icon: Drawable?,
        val stats: DetailedAppStats
    ) : AppDetailState()
    data class Error(val message: String) : AppDetailState()
}

class AppDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = BatteryRepository(application)
    private val packageManager = application.packageManager

    private val _uiState = MutableStateFlow<AppDetailState>(AppDetailState.Loading)
    val uiState: StateFlow<AppDetailState> = _uiState.asStateFlow()

    fun loadAppDetails(packageName: String) {
        viewModelScope.launch {
            _uiState.value = AppDetailState.Loading
            try {
                val info = packageManager.getApplicationInfo(packageName, 0)
                val label = packageManager.getApplicationLabel(info).toString()
                val icon = packageManager.getApplicationIcon(info)
                val stats = repository.getDetailedStatsForApp(packageName)
                
                _uiState.value = AppDetailState.Success(label, icon, stats)
            } catch (e: Exception) {
                _uiState.value = AppDetailState.Error("Could not load details for $packageName")
            }
        }
    }
}
