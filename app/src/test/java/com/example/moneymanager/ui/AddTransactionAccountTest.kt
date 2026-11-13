package com.example.moneymanager.ui

import android.content.Context
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import androidx.core.os.bundleOf
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.navigation.Navigation
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import com.example.moneymanager.R
import com.example.moneymanager.data.AccountEntity
import com.example.moneymanager.data.AppDatabase
import com.example.moneymanager.data.TransactionEntity
import com.example.moneymanager.utils.LastAccountPrefs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/** Add/Edit: pick the account, remember it, and keep the row's other fields on edit. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AddTransactionAccountTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val db = AppDatabase.getDatabase(ctx)
    private val memo = "acct-" + (1..8).map { ('a'..'z').random() }.joinToString("")

    private fun waitFor(check: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!check() && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
            Thread.sleep(20)
        }
        assertTrue("timed out", check())
    }

    private fun launch(args: android.os.Bundle? = null) =
        launchFragmentInContainer<AddTransactionFragment>(args, themeResId = R.style.Theme_MoneyManager).also { sc ->
            sc.onFragment {
                val nav = TestNavHostController(ctx)
                nav.setGraph(R.navigation.nav_graph)
                nav.setCurrentDestination(R.id.addTransactionFragment)
                Navigation.setViewNavController(it.requireView(), nav)
            }
        }

    private fun rows() = runBlocking { db.transactionDao().getAllActiveList().filter { it.memo == memo } }

    @Test
    fun newExpense_usesTheLastAccount_andMovesItsBalance() {
        val bankName = "Bank-$memo"
        val bank = runBlocking { db.accountDao().insert(AccountEntity(name = bankName, type = AccountEntity.TYPE_BANK, balance = 1000.0)) }
        LastAccountPrefs(ctx).lastAccountId = bank
        val sc = launch()
        sc.onFragment { f ->
            val sp = f.requireView().findViewById<Spinner>(R.id.spAccount)
            waitFor { sp.adapter.count > 1 && sp.selectedItem?.toString() == bankName }
            f.requireView().findViewById<EditText>(R.id.etAmount).setText("200")
            f.requireView().findViewById<EditText>(R.id.etMemo).setText(memo)
            waitFor { f.requireView().findViewById<Spinner>(R.id.spCategory).adapter.count > 0 }
            f.requireView().findViewById<Button>(R.id.btnSave).performClick()
        }
        waitFor { rows().size == 1 }
        assertEquals(bank, rows().single().accountId)
        waitFor { runBlocking { db.accountDao().getById(bank)!!.balance } == 800.0 }
        assertEquals(bank, LastAccountPrefs(ctx).lastAccountId)
    }

    @Test
    fun editKeepsUpiRefAndPayee_andMovesTheAmountToTheChosenAccount() {
        val cardName = "Card-$memo"
        val card = runBlocking { db.accountDao().insert(AccountEntity(name = cardName, type = AccountEntity.TYPE_CREDIT_CARD, balance = 0.0)) }
        val txId = runBlocking {
            db.transactionDao().insertTransaction(
                TransactionEntity(type = "EXPENSE", category = "Food", amount = 75.0, dateTimestamp = 1_700_000_000_000, memo = memo,
                    upiRef = "UPI-REF-1", rrn = "RRN-1")
            )
        }
        val sc = launch(bundleOf("transactionId" to txId))
        sc.onFragment { f ->
            val sp = f.requireView().findViewById<Spinner>(R.id.spAccount)
            waitFor { sp.adapter.count > 1 && f.requireView().findViewById<EditText>(R.id.etMemo).text.toString() == memo }
            assertEquals(0, sp.selectedItemPosition) // row had no account
            val idx = (0 until sp.adapter.count).first { sp.adapter.getItem(it).toString() == cardName }
            sp.setSelection(idx)
            f.requireView().findViewById<Button>(R.id.btnSave).performClick()
        }
        waitFor { runBlocking { db.transactionDao().getById(txId)!!.accountId } == card }
        val row = runBlocking { db.transactionDao().getById(txId)!! }
        assertEquals("UPI-REF-1", row.upiRef)
        assertEquals("RRN-1", row.rrn)
        assertEquals(1_700_000_000_000, row.dateTimestamp)
        waitFor { runBlocking { db.accountDao().getById(card)!!.balance } == 75.0 }
    }
}
