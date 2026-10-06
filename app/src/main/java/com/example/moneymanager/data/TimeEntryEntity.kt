package com.example.moneymanager.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "time_entries")
data class TimeEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** What you spent time on (app, activity, or free label). */
    val label: String,
    /** Bucket such as Work, Social, Entertainment, Learning, Health, Other. */
    val category: String,
    /**
     * Duration in whole minutes. Zero is allowed for journal-only notes
     * (voice dump with no timed block).
     */
    val durationMinutes: Int,
    /** When this block started (or the day it belongs to). */
    val startedAt: Long,
    /** Journal / free-form text (primary payload for voice entries). */
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    /** [SOURCE_MANUAL] or [SOURCE_VOICE]. Raw audio is not stored. */
    val source: String = SOURCE_MANUAL
) {
    companion object {
        const val SOURCE_MANUAL = "MANUAL"
        const val SOURCE_VOICE = "VOICE"
    }
}
