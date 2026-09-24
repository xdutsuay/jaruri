package com.example.moneymanager.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recurring_transactions")
data class RecurringEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val category: String,
    val amount: Double,
    val memo: String = "",
    /** DAY, WEEK, MONTH */
    val frequency: String = "MONTH",
    val nextDueTimestamp: Long,
    val accountId: Long? = null,
    val active: Boolean = true
)
