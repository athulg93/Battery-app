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
    }

    private fun scheduleUsageTracking() {
        // Run once every 6 hours, but only when device is idle and charging,
        // so we don't accidentally drain battery by monitoring it!
        val constraints = Constraints.Builder()
            .setRequiresCharging(true)
            .setRequiresDeviceIdle(true)
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
}
