package com.example.moneymanager.listener

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ListenerSessionDao {
    @Query("SELECT * FROM listener_sessions ORDER BY startedAt DESC, id DESC")
    fun getAll(): Flow<List<ListenerSessionEntity>>

    @Query("SELECT * FROM listener_sessions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ListenerSessionEntity?

    @Query("SELECT * FROM listener_sessions WHERE state = :state")
    suspend fun getByState(state: String): List<ListenerSessionEntity>

    @Insert
    suspend fun insert(session: ListenerSessionEntity): Long

    @Update
    suspend fun update(session: ListenerSessionEntity)

    @Query("UPDATE listener_sessions SET title = :title WHERE id = :id")
    suspend fun rename(id: Long, title: String)

    @Query("DELETE FROM listener_sessions WHERE id = :id")
    suspend fun deleteById(id: Long)
}
