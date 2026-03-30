package com.example.batterymonitor.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChargeSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChargeSession): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<ChargeSession>)

    @Update
    suspend fun updateSession(session: ChargeSession)

    @Query("SELECT * FROM charge_sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<ChargeSession>>
    
    @Query("SELECT * FROM charge_sessions ORDER BY startTime DESC LIMIT 1")
    suspend fun getLastSession(): ChargeSession?

    @Query("SELECT * FROM charge_sessions WHERE isComplete = 0")
    suspend fun getIncompleteSessions(): List<ChargeSession>

    @Query("SELECT * FROM charge_sessions WHERE endLevel = 100 ORDER BY startTime DESC LIMIT 1")
    suspend fun getLastFullCharge(): ChargeSession?

    @Query("DELETE FROM charge_sessions WHERE startTime < :threshold")
    suspend fun deleteOldSessions(threshold: Long)
}
