package com.example.moneymanager.listener

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * v15: a bookmark the user dropped while recording ("Mark" in the notification
 * or on the Listener screen). [atMs] is session time, the same clock the
 * transcript and the player use.
 */
@Entity(tableName = "listener_markers", indices = [Index("sessionId")])
data class ListenerMarkerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val atMs: Long,
    val createdAt: Long,
    val label: String = ""
)

@Dao
interface ListenerMarkerDao {
    @Insert
    suspend fun insert(marker: ListenerMarkerEntity): Long

    @Query("SELECT * FROM listener_markers WHERE sessionId = :sessionId ORDER BY atMs, id")
    fun observeForSession(sessionId: Long): Flow<List<ListenerMarkerEntity>>

    @Query("SELECT * FROM listener_markers WHERE sessionId = :sessionId ORDER BY atMs, id")
    suspend fun forSession(sessionId: Long): List<ListenerMarkerEntity>

    @Query("SELECT COUNT(*) FROM listener_markers WHERE sessionId = :sessionId")
    suspend fun count(sessionId: Long): Int

    @Query("UPDATE listener_markers SET label = :label WHERE id = :id")
    suspend fun setLabel(id: Long, label: String)

    @Query("DELETE FROM listener_markers WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM listener_markers WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: Long)
}
