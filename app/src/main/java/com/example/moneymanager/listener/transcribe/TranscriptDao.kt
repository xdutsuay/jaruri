package com.example.moneymanager.listener.transcribe

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class TranscriptDao {
    @Query("SELECT * FROM transcript_segments WHERE sessionId = :sessionId ORDER BY startMs, id")
    abstract fun observeForSession(sessionId: Long): Flow<List<TranscriptSegmentEntity>>

    @Query("SELECT * FROM transcript_segments WHERE sessionId = :sessionId ORDER BY startMs, id")
    abstract suspend fun forSession(sessionId: Long): List<TranscriptSegmentEntity>

    @Insert
    abstract suspend fun insertAll(segments: List<TranscriptSegmentEntity>)

    /** Drops partial output of chunks at or after [fromChunk] (used on resume). */
    @Query("DELETE FROM transcript_segments WHERE sessionId = :sessionId AND chunkIndex >= :fromChunk")
    abstract suspend fun deleteFromChunk(sessionId: Long, fromChunk: Int)

    @Query("DELETE FROM transcript_segments WHERE sessionId = :sessionId")
    abstract suspend fun deleteForSession(sessionId: Long)

    /** [match] must come from [FtsQuery.build]; never pass raw user input. */
    @Query(
        """
        SELECT s.id AS segmentId, s.sessionId AS sessionId, s.startMs AS startMs, s.endMs AS endMs,
               s.text AS text, ls.title AS sessionTitle, ls.startedAt AS sessionStartedAt
        FROM transcript_segments s
        JOIN transcript_fts ON s.id = transcript_fts.rowid
        JOIN listener_sessions ls ON ls.id = s.sessionId
        WHERE transcript_fts MATCH :match AND ls.startedAt >= :sinceMs
        ORDER BY ls.startedAt DESC, s.startMs ASC
        LIMIT :limit
        """
    )
    abstract suspend fun search(match: String, limit: Int = 200, sinceMs: Long = 0): List<TranscriptHit>

    /** Hand correction; the FTS index follows via Room's update triggers. */
    @Query("UPDATE transcript_segments SET text = :text, edited = 1 WHERE id = :segmentId")
    abstract suspend fun editText(segmentId: Long, text: String)

    @Query("SELECT COUNT(*) FROM transcript_segments WHERE sessionId = :sessionId AND edited = 1")
    abstract suspend fun editedCount(sessionId: Long): Int

    @Query(
        "UPDATE listener_sessions SET transcriptState = :state, transcriptError = :error WHERE id = :sessionId"
    )
    abstract suspend fun setState(sessionId: Long, state: String, error: String?)

    /** Moves to RUNNING unless the user already cancelled; returns rows changed. */
    @Query(
        "UPDATE listener_sessions SET transcriptState = 'RUNNING', transcriptError = NULL " +
            "WHERE id = :sessionId AND transcriptState != 'CANCELLED'"
    )
    abstract suspend fun markRunningUnlessCancelled(sessionId: Long): Int

    @Query("UPDATE listener_sessions SET transcribedChunks = :done WHERE id = :sessionId")
    abstract suspend fun setTranscribedChunks(sessionId: Long, done: Int)

    @Query("UPDATE listener_sessions SET transcriptState = :state, transcriptError = NULL, transcribedChunks = 0 WHERE id = :sessionId")
    abstract suspend fun resetProgress(sessionId: Long, state: String)

    /** One chunk's output and the resume pointer land together or not at all. */
    @Transaction
    open suspend fun commitChunk(sessionId: Long, chunkIndex: Int, segments: List<TranscriptSegmentEntity>) {
        deleteFromChunk(sessionId, chunkIndex)
        if (segments.isNotEmpty()) insertAll(segments)
        setTranscribedChunks(sessionId, chunkIndex + 1)
    }
}
