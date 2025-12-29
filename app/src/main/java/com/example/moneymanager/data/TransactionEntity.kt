package com.example.moneymanager.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,        // "INCOME" or "EXPENSE"
    val category: String,
    val amount: Double,
    val dateTimestamp: Long, // Storing as Long makes sorting/filtering easier
    val memo: String
)
