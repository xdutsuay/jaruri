package com.example.moneymanager.listener

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
    val notes: String = ""
) {
    companion object {
        const val STATE_RECORDING = "RECORDING"
        const val STATE_DONE = "DONE"
        const val STATE_INTERRUPTED = "INTERRUPTED"
    }
}
