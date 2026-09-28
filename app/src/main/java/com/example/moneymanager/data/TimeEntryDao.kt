package com.example.moneymanager.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TimeEntryDao {
    @Query("SELECT * FROM time_entries ORDER BY startedAt DESC, id DESC")
    fun getAll(): Flow<List<TimeEntryEntity>>

    @Query(
        """
        SELECT * FROM time_entries
        WHERE startedAt >= :start AND startedAt < :end
        ORDER BY startedAt DESC, id DESC
        """
    )
    fun getInRange(start: Long, end: Long): Flow<List<TimeEntryEntity>>

    @Query("SELECT * FROM time_entries WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TimeEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: TimeEntryEntity): Long

    @Update
    suspend fun update(entry: TimeEntryEntity)

    @Delete
    suspend fun delete(entry: TimeEntryEntity)
}
