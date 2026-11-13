package com.example.moneymanager.utils

import android.content.Context
import com.example.moneymanager.data.AccountEntity

/** Shared rules for account pickers and signed balances (pure parts are unit-tested). */
object AccountChoices {
    /**
     * What the account holds for you: positive = money you have, negative = money you owe.
     * Cards store outstanding (owed) as a positive number, so they flip sign here; an overpaid
     * card (negative outstanding) shows as a positive credit.
     */
    fun signedBalance(a: AccountEntity): Double = if (a.isCreditCard) -a.balance else a.balance

    fun netBalance(accounts: List<AccountEntity>): Double = accounts.sumOf { signedBalance(it) }

    fun label(a: AccountEntity): String = buildString {
        append(a.name)
        if (a.last4.isNotBlank() && !a.name.contains(a.last4)) append(" ··").append(a.last4)
    }

    /** The remembered account if it still exists, else none (never guess an account). */
    fun pickInitial(accounts: List<AccountEntity>, lastUsedId: Long?): Long? =
        lastUsedId?.takeIf { id -> accounts.any { it.id == id } }
}

/** Last account used when adding by hand (Add screen and quick expense share it). */
class LastAccountPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("account_prefs", Context.MODE_PRIVATE)

    var lastAccountId: Long?
        get() = prefs.getLong(KEY, -1L).takeIf { it > 0 }
        set(v) { prefs.edit().putLong(KEY, v ?: -1L).apply() }

    private companion object { const val KEY = "last_account_id" }
}
