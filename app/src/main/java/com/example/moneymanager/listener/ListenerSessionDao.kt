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

    @Query("SELECT * FROM listener_sessions WHERE id = :id")
    fun observeById(id: Long): Flow<ListenerSessionEntity?>

    @Query("SELECT * FROM listener_sessions WHERE state = :state")
    suspend fun getByState(state: String): List<ListenerSessionEntity>

    @Insert
    suspend fun insert(session: ListenerSessionEntity): Long

    @Update
    suspend fun update(session: ListenerSessionEntity)

    @Query("UPDATE listener_sessions SET title = :title WHERE id = :id")
    suspend fun rename(id: Long, title: String)

    @Query("UPDATE listener_sessions SET userNotes = :notes WHERE id = :id")
    suspend fun setUserNotes(id: Long, notes: String)

    @Query("UPDATE listener_sessions SET audioDeletedAt = :at, bytes = 0 WHERE id = :id")
    suspend fun markAudioDeleted(id: Long, at: Long)

    @Query("SELECT COALESCE(SUM(bytes), 0) FROM listener_sessions")
    fun observeTotalBytes(): Flow<Long>

    @Query("DELETE FROM listener_sessions WHERE id = :id")
    suspend fun deleteById(id: Long)
}
