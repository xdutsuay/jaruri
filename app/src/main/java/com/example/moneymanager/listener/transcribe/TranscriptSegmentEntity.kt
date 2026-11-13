package com.example.moneymanager.listener.transcribe

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One recognised utterance. [startMs]/[endMs] are relative to the start of the
 * whole session (chunk offsets already applied), so they map straight onto
 * the session player via [com.example.moneymanager.listener.transcribe.ChunkTimeline].
 */
@Entity(
    tableName = "transcript_segments",
    indices = [Index("sessionId")]
)
data class TranscriptSegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    /** 0-based position of the audio chunk in recording order. */
    val chunkIndex: Int,
    val startMs: Long,
    val endMs: Long,
    val text: String,
    /** v15: true once the user corrected the text by hand. */
    @ColumnInfo(defaultValue = "0")
    val edited: Boolean = false
)

/** Full-text index over [TranscriptSegmentEntity.text]; Room keeps it in sync via triggers. */
@Fts4(contentEntity = TranscriptSegmentEntity::class)
@Entity(tableName = "transcript_fts")
data class TranscriptFtsEntity(
    val text: String
)

/** Row returned by transcript search. */
data class TranscriptHit(
    val segmentId: Long,
    val sessionId: Long,
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val sessionTitle: String,
    val sessionStartedAt: Long
)
