package com.example.batterymonitor.utils

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.example.batterymonitor.data.local.AppUsageLog
import com.example.batterymonitor.data.local.ChargeSession
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStreamWriter
import java.io.InputStreamReader
import java.io.BufferedReader

object BackupUtils {

    private const val TAG = "BackupUtils"
    private const val BACKUP_FILE_NAME = "volt_monitor_backup.json"

    fun createBackupJson(sessions: List<ChargeSession>, logs: List<AppUsageLog>): String {
        val root = JSONObject()
        
        val sessionsArray = JSONArray()
        sessions.forEach { session ->
            val obj = JSONObject()
            obj.put("id", session.id)
            obj.put("startTime", session.startTime)
            obj.put("endTime", session.endTime)
            obj.put("startLevel", session.startLevel)
            obj.put("endLevel", session.endLevel)
            obj.put("isComplete", session.isComplete)
            obj.put("startVoltage", session.startVoltage)
            obj.put("endVoltage", session.endVoltage)
            obj.put("startTemperature", session.startTemperature)
            obj.put("endTemperature", session.endTemperature)
            sessionsArray.put(obj)
        }
        root.put("sessions", sessionsArray)

        val logsArray = JSONArray()
        logs.forEach { log ->
            val obj = JSONObject()
            obj.put("id", log.id)
            obj.put("packageName", log.packageName)
            obj.put("timestamp", log.timestamp)
            obj.put("foregroundTimeMs", log.foregroundTimeMs)
            logsArray.put(obj)
        }
        root.put("usage_logs", logsArray)
        root.put("backup_timestamp", System.currentTimeMillis())
        root.put("version", 1)

        return root.toString(2)
    }

    fun saveBackupToFile(context: Context, jsonContent: String): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveToMediaStore(context, jsonContent)
        } else {
            saveToExternalStorageLegacy(context, jsonContent)
        }
    }

    private fun saveToMediaStore(context: Context, jsonContent: String): Boolean {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, BACKUP_FILE_NAME)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/VoltMonitor")
        }

        val uri: Uri? = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
        
        return try {
            uri?.let {
                resolver.openOutputStream(it)?.use { outputStream ->
                    OutputStreamWriter(outputStream).use { writer ->
                        writer.write(jsonContent)
                    }
                }
                Log.d(TAG, "Backup saved to Documents/VoltMonitor via MediaStore")
                true
            } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Error saving backup to MediaStore", e)
            false
        }
    }

    private fun saveToExternalStorageLegacy(context: Context, jsonContent: String): Boolean {
        val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "VoltMonitor")
        if (!directory.exists()) {
            directory.mkdirs()
        }
        val file = File(directory, BACKUP_FILE_NAME)
        return try {
            file.writeText(jsonContent)
            Log.d(TAG, "Backup saved to ${file.absolutePath} via legacy storage")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving backup legacy", e)
            false
        }
    }

    fun readBackupFromUri(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    reader.readText()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading backup from URI", e)
            null
        }
    }

    fun parseBackupJson(json: String): Triple<List<ChargeSession>, List<AppUsageLog>, Long>? {
        return try {
            val root = JSONObject(json)
            val backupTime = root.optLong("backup_timestamp", 0L)
            val sessionsList = mutableListOf<ChargeSession>()
            val logsList = mutableListOf<AppUsageLog>()

            val sessionsArray = root.optJSONArray("sessions")
            if (sessionsArray != null) {
                for (i in 0 until sessionsArray.length()) {
                    val obj = sessionsArray.getJSONObject(i)
                    sessionsList.add(
                        ChargeSession(
                            startTime = obj.getLong("startTime"),
                            endTime = obj.getLong("endTime"),
                            startLevel = obj.getInt("startLevel"),
                            endLevel = obj.getInt("endLevel"),
                            isComplete = obj.getBoolean("isComplete"),
                            startVoltage = obj.optInt("startVoltage", 0),
                            endVoltage = obj.optInt("endVoltage", 0),
                            startTemperature = obj.optInt("startTemperature", 0),
                            endTemperature = obj.optInt("endTemperature", 0)
                        )
                    )
                }
            }

            val logsArray = root.optJSONArray("usage_logs")
            if (logsArray != null) {
                for (i in 0 until logsArray.length()) {
                    val obj = logsArray.getJSONObject(i)
                    logsList.add(
                        AppUsageLog(
                            packageName = obj.getString("packageName"),
                            timestamp = obj.getLong("timestamp"),
                            foregroundTimeMs = obj.getLong("foregroundTimeMs")
                        )
                    )
                }
            }
            Triple(sessionsList, logsList, backupTime)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing backup JSON", e)
            null
        }
    }
}
