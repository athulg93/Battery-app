package com.example.batterymonitor.worker

import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.batterymonitor.data.local.AppDatabase
import com.example.batterymonitor.data.local.AppUsageLog
import com.example.batterymonitor.utils.PermissionHelper

class UsageStatsWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (!PermissionHelper.hasUsageStatsPermission(applicationContext)) {
            Log.w("UsageStatsWorker", "Missing PACKAGE_USAGE_STATS permission")
            return Result.failure()
        }

        val usageStatsManager = applicationContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val startTime = endTime - (1000 * 60 * 60 * 24) // Last 24 hours

        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )

        if (stats == null || stats.isEmpty()) {
            return Result.success()
        }

        val logs = stats
            .filter { it.totalTimeInForeground > 0 }
            .map {
                AppUsageLog(
                    packageName = it.packageName,
                    timestamp = endTime,
                    foregroundTimeMs = it.totalTimeInForeground
                )
            }

        val db = AppDatabase.getDatabase(applicationContext)
        db.appUsageDao().insertUsageLogs(logs)
        
        // Clean up logs older than 7 days
        val sevenDaysAgo = endTime - (1000L * 60 * 60 * 24 * 7)
        db.appUsageDao().clearOldLogs(sevenDaysAgo)

        Log.d("UsageStatsWorker", "Successfully logged ${logs.size} app usage records")
        return Result.success()
    }
}
