package com.example.batterymonitor.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "overcharge_events")
data class OverchargeEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val timestamp: Long,
    val durationMs: Long
)
