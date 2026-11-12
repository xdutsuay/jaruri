package com.example.moneymanager.listener

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One manual Listener recording. Audio lives in app-private
 * `filesDir/listener/<dirName>/seg-NNN.m4a`; this row is the index.
 */
@Entity(tableName = "listener_sessions")
data class ListenerSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val startedAt: Long,
    /** Null while [STATE_RECORDING]. */
    val endedAt: Long? = null,
    val durationMs: Long = 0,
    val segmentCount: Int = 0,
    val bytes: Long = 0,
    /** [STATE_RECORDING], [STATE_DONE] or [STATE_INTERRUPTED]. */
    val state: String = STATE_RECORDING,
    /** Folder name under `filesDir/listener/`. */
    val dirName: String,
    /** Why it stopped (see [ListenerStopReason]) or free text. */
    val notes: String = "",
    /** v13: see [TRANSCRIPT_NONE] etc. */
    @ColumnInfo(defaultValue = "'NONE'")
    val transcriptState: String = TRANSCRIPT_NONE,
    /** v13: chunks (in recording order) fully transcribed; resume point. */
    @ColumnInfo(defaultValue = "0")
    val transcribedChunks: Int = 0,
    /** v13: last transcription failure, shown to the user. */
    val transcriptError: String? = null
) {
    companion object {
        const val STATE_RECORDING = "RECORDING"
        const val STATE_DONE = "DONE"
        const val STATE_INTERRUPTED = "INTERRUPTED"

        const val TRANSCRIPT_NONE = "NONE"
        const val TRANSCRIPT_QUEUED = "QUEUED"
        const val TRANSCRIPT_RUNNING = "RUNNING"
        const val TRANSCRIPT_DONE = "DONE"
        const val TRANSCRIPT_FAILED = "FAILED"
        const val TRANSCRIPT_CANCELLED = "CANCELLED"
    }
}
