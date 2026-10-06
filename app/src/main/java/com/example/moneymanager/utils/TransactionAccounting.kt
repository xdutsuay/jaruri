package com.example.moneymanager.utils

import com.example.moneymanager.data.AccountEntity
import com.example.moneymanager.data.TransactionEntity
import java.util.Locale

/**
 * Pure helpers for income/expense totals and transfer / CC-payment neutrality.
 */
object TransactionAccounting {

    const val TYPE_INCOME = "INCOME"
    const val TYPE_EXPENSE = "EXPENSE"
    const val TYPE_TRANSFER = "TRANSFER"

    const val CAT_TRANSFER = "Transfer"
    const val CAT_CC_PAYMENT = "Credit Card Payment"

    fun isTransferType(type: String): Boolean =
        type.equals(TYPE_TRANSFER, ignoreCase = true)

    /**
     * True when the row must not count toward income/expense totals.
     *
     * Refund rule: [TransactionEntity.isRefundNeutral] credits are ledger-neutral for
     * headlines (still move account balance via InstrumentLedger) so matching a refund
     * to an expense does not double-count as income.
     */
    fun isNeutralForTotals(tx: TransactionEntity): Boolean {
        if (tx.isRefundNeutral) return true
        if (isTransferType(tx.type)) return true
        val cat = tx.category.trim().lowercase(Locale.ROOT)
        return cat == CAT_TRANSFER.lowercase(Locale.ROOT) ||
            cat == CAT_CC_PAYMENT.lowercase(Locale.ROOT)
    }

    data class Totals(val income: Double, val expense: Double) {
        val balance: Double get() = income - expense
    }

    fun sumTotals(list: Iterable<TransactionEntity>): Totals {
        var income = 0.0
        var expense = 0.0
        for (tx in list) {
            if (isNeutralForTotals(tx)) continue
            when {
                tx.type.equals(TYPE_INCOME, ignoreCase = true) -> income += tx.amount
                tx.type.equals(TYPE_EXPENSE, ignoreCase = true) -> expense += tx.amount
            }
        }
        return Totals(income, expense)
    }

    /**
     * Liquid runway: cash + bank balances minus credit-card outstanding.
     */
    fun netLiquid(accounts: List<AccountEntity>): Double {
        var cashBank = 0.0
        var ccOutstanding = 0.0
        for (a in accounts) {
            when (a.type) {
                AccountEntity.TYPE_CREDIT_CARD -> ccOutstanding += a.balance.coerceAtLeast(0.0)
                AccountEntity.TYPE_CASH, AccountEntity.TYPE_BANK -> cashBank += a.balance
            }
        }
        return cashBank - ccOutstanding
    }

    fun cashAndBankTotal(accounts: List<AccountEntity>): Double =
        accounts.filter {
            it.type == AccountEntity.TYPE_CASH || it.type == AccountEntity.TYPE_BANK
        }.sumOf { it.balance }

    fun creditCardOutstanding(accounts: List<AccountEntity>): Double =
        accounts.filter { it.isCreditCard }.sumOf { it.balance.coerceAtLeast(0.0) }
}
