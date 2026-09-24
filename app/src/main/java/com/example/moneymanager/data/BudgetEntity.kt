package com.example.moneymanager.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,
    val monthlyLimit: Double,
    /** 0–11, or -1 for every month */
    val month: Int = -1,
    val year: Int = -1
)
