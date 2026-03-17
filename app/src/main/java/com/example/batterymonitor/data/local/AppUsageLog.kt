package com.example.batterymonitor.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_usage_logs")
data class AppUsageLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val packageName: String,
    val timestamp: Long,
    val foregroundTimeMs: Long
)
