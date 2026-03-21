package com.example.batterymonitor.utils

import com.example.batterymonitor.data.local.AppUsageLog
import com.example.batterymonitor.data.local.ChargeSession
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BackupUtilsTest {

    @Test
    fun testCreateBackupJson() {
        val sessions = listOf(
            ChargeSession(
                id = 1,
                startTime = 1000L,
                endTime = 2000L,
                startLevel = 20,
                endLevel = 80,
                isComplete = true,
                startVoltage = 3800,
                endVoltage = 4200,
                startTemperature = 30,
                endTemperature = 35
            )
        )

        val logs = listOf(
            AppUsageLog(
                id = 1,
                packageName = "com.android.chrome",
                timestamp = 1500L,
                foregroundTimeMs = 5000L
            )
        )

        val jsonString = BackupUtils.createBackupJson(sessions, logs)
        val json = JSONObject(jsonString)

        assertTrue(json.has("sessions"))
        assertTrue(json.has("usage_logs"))
        assertTrue(json.has("backup_timestamp"))
        assertEquals(1, json.getInt("version"))

        val sessionsArray = json.getJSONArray("sessions")
        assertEquals(1, sessionsArray.length())
        val sessionObj = sessionsArray.getJSONObject(0)
        assertEquals(1, sessionObj.getInt("id"))
        assertEquals(1000L, sessionObj.getLong("startTime"))

        val logsArray = json.getJSONArray("usage_logs")
        assertEquals(1, logsArray.length())
        val logObj = logsArray.getJSONObject(0)
        assertEquals("com.android.chrome", logObj.getString("packageName"))
    }
}
