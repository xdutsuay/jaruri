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
    /** Duration in whole minutes. */
    val durationMinutes: Int,
    /** When this block started (or the day it belongs to). */
    val startedAt: Long,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
