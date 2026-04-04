package com.example.batterymonitor.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OverchargeDao {
    @Insert
    suspend fun insertEvent(event: OverchargeEvent)

    @Query("SELECT * FROM overcharge_events ORDER BY timestamp DESC")
    fun getAllEventsFlow(): Flow<List<OverchargeEvent>>

    @Query("SELECT * FROM overcharge_events ORDER BY timestamp DESC")
    suspend fun getAllEvents(): List<OverchargeEvent>
}
