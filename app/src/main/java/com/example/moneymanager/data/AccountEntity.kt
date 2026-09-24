package com.example.moneymanager.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** CASH, BANK, CREDIT_CARD */
    val type: String,
    /** For CREDIT_CARD: current outstanding / owed. For others: optional balance. */
    val balance: Double = 0.0,
    /** Credit limit for CREDIT_CARD; 0 otherwise. */
    val creditLimit: Double = 0.0,
    val last4: String = "",
    val notes: String = ""
) {
    val isCreditCard: Boolean get() = type == TYPE_CREDIT_CARD
    val availableCredit: Double
        get() = if (isCreditCard) (creditLimit - balance).coerceAtLeast(0.0) else 0.0

    companion object {
        const val TYPE_CASH = "CASH"
        const val TYPE_BANK = "BANK"
        const val TYPE_CREDIT_CARD = "CREDIT_CARD"
    }
}
