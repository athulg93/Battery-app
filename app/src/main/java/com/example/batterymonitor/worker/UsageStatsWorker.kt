package com.example.batterymonitor.worker

import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
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
        
        // Clean up data older than 5 months (approx 150 days)
        val fiveMonthsAgo = endTime - (150L * 24 * 60 * 60 * 1000)
        db.appUsageDao().clearOldLogs(fiveMonthsAgo)
        db.chargeSessionDao().deleteOldSessions(fiveMonthsAgo)

        Log.d("UsageStatsWorker", "Successfully logged ${logs.size} app usage records and cleaned up old data")
        return Result.success()
    }

    companion object {
        fun runOnce(context: Context) {
            val workRequest = OneTimeWorkRequestBuilder<UsageStatsWorker>().build()
            WorkManager.getInstance(context).enqueue(workRequest)
        }
    }
}
