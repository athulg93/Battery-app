package com.example.batterymonitor.ui.settings

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.batterymonitor.data.pref.AppTheme
import com.example.batterymonitor.data.pref.ThemeManager
import com.example.batterymonitor.data.repository.BatteryRepository
import com.example.batterymonitor.utils.BackupUtils
import com.example.batterymonitor.utils.UpdateManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class RestoreStatus {
    object Idle : RestoreStatus()
    object Loading : RestoreStatus()
    object Success : RestoreStatus()
    data class Warning(val message: String, val uri: Uri) : RestoreStatus()
    data class Error(val message: String) : RestoreStatus()
}

data class UpdateInfo(val version: String, val downloadUrl: String, val body: String)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = BatteryRepository(application)
    private val context = application.applicationContext
    private val themeManager = ThemeManager(context)

    val appTheme: StateFlow<AppTheme> = themeManager.themeFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppTheme.SYSTEM)

    private val _restoreStatus = MutableStateFlow<RestoreStatus>(RestoreStatus.Idle)
    val restoreStatus: StateFlow<RestoreStatus> = _restoreStatus.asStateFlow()

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo.asStateFlow()
    
    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    fun clearToast() {
        _toastMessage.value = null
    }

    fun setTheme(theme: AppTheme) {
        viewModelScope.launch {
            themeManager.setTheme(theme)
        }
    }

    fun checkForUpdates() {
        if (_isCheckingUpdate.value) return
        _isCheckingUpdate.value = true
        
        UpdateManager.checkForUpdates(context, object : UpdateManager.UpdateCallback {
            override fun onUpdateAvailable(newVersion: String, downloadUrl: String, body: String) {
                _isCheckingUpdate.value = false
                _updateInfo.value = UpdateInfo(newVersion, downloadUrl, body)
            }
            override fun onNoUpdate() {
                _isCheckingUpdate.value = false
                _toastMessage.value = "You are on the latest version"
            }
            override fun onError(message: String) {
                _isCheckingUpdate.value = false
                _toastMessage.value = "Update check failed: $message"
            }
        })
    }

    fun clearUpdateInfo() {
        _updateInfo.value = null
    }

    fun downloadUpdate() {
        _updateInfo.value?.let { info ->
            UpdateManager.downloadAndInstallUpdate(context, info.downloadUrl, "BatteryMonitor_${info.version}.apk")
            _toastMessage.value = "Download started..."
            _updateInfo.value = null
        }
    }

    fun restoreBackup(uri: Uri, force: Boolean = false) {
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

                val (sessions, logs, backupTime) = data
                
                // Perform check against current DB
                if (!force) {
                    val currentDbLatest = repository.getLatestActivityTimestamp()
                    // If DB has data that is newer than the backup's creation time, warn user.
                    if (backupTime > 0 && currentDbLatest > backupTime) {
                         _restoreStatus.value = RestoreStatus.Warning(
                            "The backup file contains older information. Your device already has more recent data.", uri)
                         return@launch
                    }
                }
                
                // If it's valid or forced, restore
                repository.insertSessions(sessions)
                repository.insertUsageLogs(logs)
                
                _restoreStatus.value = RestoreStatus.Success
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Restore failed", e)
                _restoreStatus.value = RestoreStatus.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun resetRestoreStatus() {
        _restoreStatus.value = RestoreStatus.Idle
    }
}
