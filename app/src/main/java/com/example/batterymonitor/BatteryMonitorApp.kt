package com.example.batterymonitor

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.batterymonitor.worker.UsageStatsWorker
import java.util.concurrent.TimeUnit

class BatteryMonitorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        scheduleUsageTracking()
        scheduleBackup()
        UsageStatsWorker.runOnce(this)
        com.example.batterymonitor.worker.BackupWorker.runOnce(this)
    }

    private fun scheduleUsageTracking() {
        // Run once every 6 hours
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()
            
        val workRequest = PeriodicWorkRequestBuilder<UsageStatsWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "UsageStatsLogWork",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    private fun scheduleBackup() {
        // Run once every 24 hours (once a day)
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiresDeviceIdle(true) // Backup is heavy, do it while idle
            .build()

        val workRequest = PeriodicWorkRequestBuilder<com.example.batterymonitor.worker.BackupWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "DataBackupWork",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}
