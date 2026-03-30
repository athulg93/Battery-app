package com.example.batterymonitor.ui.history
 
import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.batterymonitor.data.local.ChargeSession
import com.example.batterymonitor.data.repository.BatteryRepository
import com.example.batterymonitor.utils.BackupUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import android.util.Log

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = BatteryRepository(application)
    private val context = application.applicationContext

    val sessions: StateFlow<List<ChargeSession>> = repository.getSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _restoreStatus = MutableStateFlow<RestoreStatus>(RestoreStatus.Idle)
    val restoreStatus: StateFlow<RestoreStatus> = _restoreStatus.asStateFlow()

    fun restoreHistory(uri: Uri) {
        viewModelScope.launch {
            _restoreStatus.value = RestoreStatus.Loading
            try {
                val json = BackupUtils.readBackupFromUri(context, uri)
                if (json == null) {
                    _restoreStatus.value = RestoreStatus.Error("Could not read backup file")
                    return@launch
                }

                val data = BackupUtils.parseBackupJson(json)
                if (data == null) {
                    _restoreStatus.value = RestoreStatus.Error("Invalid backup format")
                    return@launch
                }

                repository.insertSessions(data.first)
                repository.insertUsageLogs(data.second)
                
                _restoreStatus.value = RestoreStatus.Success
            } catch (e: Exception) {
                Log.e("HistoryViewModel", "Restore failed", e)
                _restoreStatus.value = RestoreStatus.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun resetRestoreStatus() {
        _restoreStatus.value = RestoreStatus.Idle
    }
}

sealed class RestoreStatus {
    object Idle : RestoreStatus()
    object Loading : RestoreStatus()
    object Success : RestoreStatus()
    data class Error(val message: String) : RestoreStatus()
}
