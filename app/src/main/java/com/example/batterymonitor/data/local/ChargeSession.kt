package com.example.batterymonitor.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "charge_sessions")
data class ChargeSession(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val startTime: Long,
    val endTime: Long,
    val startLevel: Int,
    val endLevel: Int,
    val isComplete: Boolean,
    val startVoltage: Int,
    val endVoltage: Int,
    val startTemperature: Int,
    val endTemperature: Int
)
