package com.example.moneymanager.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,        // "INCOME" or "EXPENSE"
    val category: String,
    val amount: Double,
    val dateTimestamp: Long,
    val memo: String,
    /** Optional link to accounts.id (null = unassigned). */
    val accountId: Long? = null
)
