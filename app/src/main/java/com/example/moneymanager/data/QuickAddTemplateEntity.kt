package com.example.moneymanager.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "quick_add_templates")
data class QuickAddTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amount: Double,
    val category: String,
    /** INCOME or EXPENSE */
    val type: String,
    val note: String = "",
    /** Lower = higher in pin list. */
    val pinOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
