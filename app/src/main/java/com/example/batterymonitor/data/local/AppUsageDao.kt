package com.example.batterymonitor.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AppUsageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsageLogs(logs: List<AppUsageLog>)

    @Query("SELECT * FROM app_usage_logs WHERE timestamp >= :since ORDER BY foregroundTimeMs DESC")
    fun getUsageSince(since: Long): Flow<List<AppUsageLog>>
    
    @Query("DELETE FROM app_usage_logs WHERE timestamp < :olderThan")
    suspend fun clearOldLogs(olderThan: Long)
}
