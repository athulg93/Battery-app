package com.example.batterymonitor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.batterymonitor.service.BatteryMonitorService
import com.example.batterymonitor.ui.dashboard.DashboardScreen
import com.example.batterymonitor.ui.theme.BatteryMonitorTheme
import com.example.batterymonitor.utils.PermissionHelper

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startBatteryService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        checkAndRequestPermissions()

        setContent {
            BatteryMonitorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DashboardScreen()
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        if (!PermissionHelper.hasUsageStatsPermission(this)) {
            // In a real production app, we would show a nice dialog explaining WHY we need this
            // before jumping them straight to Settings. For this utility, we'll prompt directly.
            PermissionHelper.requestUsageStatsPermission(this)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                startBatteryService()
            }
        } else {
            startBatteryService()
        }
    }

    private fun startBatteryService() {
        val serviceIntent = Intent(this, BatteryMonitorService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
    }
}
