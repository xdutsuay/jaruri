package com.example.moneymanager.wellbeing

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UsageDailyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<UsageDailyEntity>)

    @Query(
        """
        SELECT * FROM usage_daily
        WHERE day >= :startDay AND day <= :endDay
        ORDER BY minutes DESC
        """
    )
    fun observeRange(startDay: String, endDay: String): Flow<List<UsageDailyEntity>>

    @Query(
        """
        SELECT * FROM usage_daily
        WHERE day >= :startDay AND day <= :endDay
        ORDER BY minutes DESC
        """
    )
    suspend fun getRange(startDay: String, endDay: String): List<UsageDailyEntity>

    @Query("DELETE FROM usage_daily WHERE day < :beforeDay")
    suspend fun deleteBefore(beforeDay: String)

    @Query("SELECT COUNT(*) FROM usage_daily")
    suspend fun count(): Int
}
