package com.example.batterymonitor.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.batterymonitor.data.local.AppDatabase
import com.example.batterymonitor.utils.BackupUtils
import kotlinx.coroutines.flow.first

class BackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.d("BackupWorker", "Starting backup process...")
        
        return try {
            val db = AppDatabase.getDatabase(applicationContext)
            
            // Get all sessions and logs
            val sessions = db.chargeSessionDao().getAllSessions().first()
            val logs = db.appUsageDao().getUsageSinceTime(0) // Get all logs for backup
            
            // Create JSON
            val json = BackupUtils.createBackupJson(sessions, logs)
            
            // Save to file
            val success = BackupUtils.saveBackupToFile(applicationContext, json)
            
            if (success) {
                Log.d("BackupWorker", "Backup successfully saved to external storage")
                Result.success()
            } else {
                Log.e("BackupWorker", "Failed to save backup file")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e("BackupWorker", "Error during backup", e)
            Result.failure()
        }
    }

    companion object {
        fun runOnce(context: Context) {
            val workRequest = OneTimeWorkRequestBuilder<BackupWorker>().build()
            WorkManager.getInstance(context).enqueue(workRequest)
        }
    }
}
