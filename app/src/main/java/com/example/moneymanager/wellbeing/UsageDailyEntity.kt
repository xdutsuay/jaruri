package com.example.moneymanager.wellbeing

import androidx.room.Entity

/**
 * Daily per-app foreground aggregate only — never raw UsageEvents streams.
 * [day] is local calendar day as `yyyy-MM-dd`.
 */
@Entity(
    tableName = "usage_daily",
    primaryKeys = ["day", "packageName"]
)
data class UsageDailyEntity(
    val day: String,
    val packageName: String,
    val minutes: Int
)
