package com.example.moneymanager.utils

import com.example.moneymanager.data.AccountDao

/**
 * Compatibility facade — implementation lives in [InstrumentLedger].
 */
object CreditCardLedger {
    suspend fun resolveOrCreateCard(accountDao: AccountDao, cardLast4: String?): Long? =
        InstrumentLedger.resolveOrCreateCard(accountDao, cardLast4)

    suspend fun applyDebtDelta(
        accountDao: AccountDao,
        accountId: Long?,
        type: String,
        category: String,
        amount: Double,
        reverse: Boolean = false
    ) = InstrumentLedger.applyTxnDelta(accountDao, accountId, type, category, amount, reverse)
}
