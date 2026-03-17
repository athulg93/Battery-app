package com.example.batterymonitor.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChargeSessionDao {
    @Insert
    suspend fun insertSession(session: ChargeSession): Long

    @Update
    suspend fun updateSession(session: ChargeSession)

    @Query("SELECT * FROM charge_sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<ChargeSession>>
    
    @Query("SELECT * FROM charge_sessions ORDER BY startTime DESC LIMIT 1")
    suspend fun getLastSession(): ChargeSession?
}
