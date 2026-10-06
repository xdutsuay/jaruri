package com.example.moneymanager.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** "INCOME", "EXPENSE", or "TRANSFER". */
    val type: String,
    val category: String,
    val amount: Double,
    val dateTimestamp: Long,
    val memo: String,
    /**
     * Optional link to accounts.id (null = unassigned).
     * For TRANSFER: source (from) account.
     */
    val accountId: Long? = null,
    /** Non-null when moved to recycle bin (epoch millis). */
    val deletedAt: Long? = null,
    /**
     * True when auto-imported and category has not been confirmed by the user yet.
     * Clearing this (via categorize) feeds [CategoryLearnEntity].
     */
    val needsCategoryReview: Boolean = false,
    /** For TRANSFER: destination (to) account. */
    val transferToAccountId: Long? = null,
    /** Resolved merchant / UPI payee (see payees table). */
    val payeeId: Long? = null,
    /** UPI reference / txn id extracted from SMS. */
    val upiRef: String? = null,
    /** RRN / retrieval reference when present. */
    val rrn: String? = null,
    /** Linked original expense when this row is a refund/reversal. */
    val linkedTransactionId: Long? = null,
    /**
     * Refund/reversal accounting rule:
     * When true, this credit still updates account balance (INCOME delta) but is
     * **excluded from income/expense totals** so refunds do not inflate "income".
     * The linked original EXPENSE remains; net cash is on the account balance.
     */
    val isRefundNeutral: Boolean = false
)
