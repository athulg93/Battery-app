package com.example.batterymonitor.ui.appusage

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.batterymonitor.data.repository.AppUsageSummary
import com.example.batterymonitor.data.repository.BatteryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AppSortOrder {
    DRAIN, NAME, TIME
}

class AppUsageViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = BatteryRepository(application)

    private val _appUsage = MutableStateFlow<List<AppUsageSummary>>(emptyList())
    val appUsage: StateFlow<List<AppUsageSummary>> = _appUsage.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _sortOrder = MutableStateFlow(AppSortOrder.DRAIN)
    val sortOrder: StateFlow<AppSortOrder> = _sortOrder.asStateFlow()

    init {
        loadUsage()
    }

    fun setSortOrder(order: AppSortOrder) {
        _sortOrder.value = order
        applySort()
    }

    fun loadUsage() {
        viewModelScope.launch {
            _isLoading.value = true
            val last24h = System.currentTimeMillis() - (1000 * 60 * 60 * 24)
            _appUsage.value = repository.getAppUsageSummary(last24h)
            applySort()
            _isLoading.value = false
        }
    }

    private fun applySort() {
        val currentList = _appUsage.value
        _appUsage.value = when (_sortOrder.value) {
            AppSortOrder.DRAIN -> currentList.sortedByDescending { it.estimatedDrainPct }
            AppSortOrder.NAME -> currentList.sortedBy { it.label }
            AppSortOrder.TIME -> currentList.sortedByDescending { it.foregroundTimeMs }
        }
    }
}
