package com.example.moneymanager.listener.transcribe

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TranscriptDao {
    @Query("SELECT * FROM transcript_segments WHERE sessionId = :sessionId ORDER BY startMs, id")
    fun observeForSession(sessionId: Long): Flow<List<TranscriptSegmentEntity>>

    @Query("SELECT * FROM transcript_segments WHERE sessionId = :sessionId ORDER BY startMs, id")
    suspend fun forSession(sessionId: Long): List<TranscriptSegmentEntity>

    @Insert
    suspend fun insertAll(segments: List<TranscriptSegmentEntity>)

    /** Drops partial output of chunks at or after [fromChunk] (used on resume). */
    @Query("DELETE FROM transcript_segments WHERE sessionId = :sessionId AND chunkIndex >= :fromChunk")
    suspend fun deleteFromChunk(sessionId: Long, fromChunk: Int)

    @Query("DELETE FROM transcript_segments WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: Long)

    /** [match] must come from [FtsQuery.build]; never pass raw user input. */
    @Query(
        """
        SELECT s.id AS segmentId, s.sessionId AS sessionId, s.startMs AS startMs, s.endMs AS endMs,
               s.text AS text, ls.title AS sessionTitle, ls.startedAt AS sessionStartedAt
        FROM transcript_segments s
        JOIN transcript_fts ON s.id = transcript_fts.rowid
        JOIN listener_sessions ls ON ls.id = s.sessionId
        WHERE transcript_fts MATCH :match
        ORDER BY ls.startedAt DESC, s.startMs ASC
        LIMIT :limit
        """
    )
    suspend fun search(match: String, limit: Int = 200): List<TranscriptHit>
}
