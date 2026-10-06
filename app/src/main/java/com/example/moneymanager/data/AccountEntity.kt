package com.example.moneymanager.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** CASH, BANK, CREDIT_CARD */
    val type: String,
    /** For CREDIT_CARD: current outstanding / owed. For others: running ledger balance. */
    val balance: Double = 0.0,
    /** Credit limit for CREDIT_CARD; 0 otherwise. */
    val creditLimit: Double = 0.0,
    val last4: String = "",
    val notes: String = "",
    /** Issuer / bank token learned from SMS (agnostic free text). */
    val bankHint: String = "",
    /** Last balance figure reported by an SMS for this instrument. */
    val lastReportedBalance: Double? = null,
    /** Kind of [lastReportedBalance]: AVAILABLE, OUTSTANDING, AVAILABLE_LIMIT. */
    val lastReportedKind: String = "",
    /** reported − ledger at last observation (running difference entry). */
    val runningDifference: Double = 0.0,
    /** How many SMS balance observations we have for this instrument. */
    val observationCount: Int = 0,
    val lastBalanceObservedAt: Long = 0L,
    /** How many times this instrument appeared in parsed SMS. */
    val seenCount: Int = 0
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
