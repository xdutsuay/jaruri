package com.example.moneymanager.quickadd

import android.content.Context
import android.content.Intent
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.example.moneymanager.R
import com.example.moneymanager.data.AppDatabase
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class QuickExpenseActivityTest {
    private lateinit var ctx: Context
    private val note = "qe-" + (1..8).map { ('a'..'z').random() }.joinToString("")

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        ctx.getSharedPreferences("quick_expense", Context.MODE_PRIVATE).edit()
            .putString("last_category", "Transportation").commit()
    }

    private fun waitFor(check: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!check() && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
            Thread.sleep(20)
        }
        assertTrue("timed out", check())
    }

    private fun open(): ActivityController<QuickExpenseActivity> =
        Robolectric.buildActivity(QuickExpenseActivity::class.java, QuickExpenseActivity.intent(ctx)).setup()

    private fun rowsWithNote() = runBlocking {
        AppDatabase.getDatabase(ctx).transactionDao().getAllActiveList().filter { it.memo == note }
    }

    private fun checkedChip(a: QuickExpenseActivity): String? {
        val g = a.findViewById<ChipGroup>(R.id.qe_categories)
        return (0 until g.childCount).map { g.getChildAt(it) as Chip }.singleOrNull { it.isChecked }?.text?.toString()
    }

    @Test
    fun opensWithAmountFocused_andTheLastCategoryPicked() {
        val a = open().get()
        waitFor { a.findViewById<ChipGroup>(R.id.qe_categories).childCount > 0 }
        assertTrue(a.findViewById<View>(R.id.qe_amount).hasFocus())
        assertEquals("Transportation", checkedChip(a))
        val names = (0 until a.findViewById<ChipGroup>(R.id.qe_categories).childCount)
            .map { (a.findViewById<ChipGroup>(R.id.qe_categories).getChildAt(it) as Chip).text.toString() }
        assertTrue(names.containsAll(listOf("Food", "Bills", "Transportation")))
    }

    @Test
    fun badAmountShowsAnError_andSavesNothing() {
        val a = open().get()
        waitFor { a.findViewById<ChipGroup>(R.id.qe_categories).childCount > 0 }
        a.findViewById<TextInputEditText>(R.id.qe_note).setText(note)
        a.findViewById<TextInputEditText>(R.id.qe_amount).setText("0")
        a.findViewById<Button>(R.id.qe_save).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(ctx.getString(R.string.quick_expense_err_empty), a.findViewById<TextInputLayout>(R.id.qe_amount_layout).error)
        assertEquals(0, rowsWithNote().size)
        a.findViewById<TextInputEditText>(R.id.qe_amount).setText("12.345")
        a.findViewById<Button>(R.id.qe_save).performClick()
        assertEquals(ctx.getString(R.string.quick_expense_err_invalid), a.findViewById<TextInputLayout>(R.id.qe_amount_layout).error)
        assertEquals(0, rowsWithNote().size)
    }

    @Test
    fun saveWritesTheExpense_undoRemovesIt_andTheSheetStaysForAFix() {
        val a = open().get()
        waitFor { a.findViewById<ChipGroup>(R.id.qe_categories).childCount > 0 }
        val g = a.findViewById<ChipGroup>(R.id.qe_categories)
        (0 until g.childCount).map { g.getChildAt(it) as Chip }.first { it.text == "Food" }.performClick()
        a.findViewById<TextInputEditText>(R.id.qe_amount).setText("1,250.50")
        a.findViewById<TextInputEditText>(R.id.qe_note).setText(note)
        a.findViewById<Button>(R.id.qe_save).performClick()
        waitFor { rowsWithNote().size == 1 }
        val row = rowsWithNote().single()
        assertEquals("EXPENSE", row.type)
        assertEquals("Food", row.category)
        assertEquals(1250.5, row.amount, 0.0)
        assertTrue(System.currentTimeMillis() - row.dateTimestamp < 60_000)
        assertEquals("Food", QuickExpenseStore(ctx).lastCategory)

        // A second tap while the Undo bar is up does not add a duplicate.
        a.findViewById<Button>(R.id.qe_save).performClick()
        waitFor { a.findViewById<View>(com.google.android.material.R.id.snackbar_action) != null }
        assertEquals(1, rowsWithNote().size)
        val text = a.findViewById<TextView>(com.google.android.material.R.id.snackbar_text).text.toString()
        assertTrue(text, text.contains("1,250.5") && text.contains("Food"))

        a.findViewById<View>(com.google.android.material.R.id.snackbar_action).performClick()
        waitFor { rowsWithNote().isEmpty() }
        waitFor { a.findViewById<Button>(R.id.qe_save).isEnabled }
        assertTrue(!a.isFinishing)
        assertEquals("1,250.50", a.findViewById<TextInputEditText>(R.id.qe_amount).text.toString())
    }

    @Test
    fun withoutUndo_theSheetClosesAndTheRowStays() {
        val a = open().get()
        waitFor { a.findViewById<ChipGroup>(R.id.qe_categories).childCount > 0 }
        a.findViewById<TextInputEditText>(R.id.qe_amount).setText("50")
        a.findViewById<TextInputEditText>(R.id.qe_note).setText(note)
        a.findViewById<Button>(R.id.qe_save).performClick()
        waitFor { rowsWithNote().size == 1 }
        assertEquals("Transportation", rowsWithNote().single().category)
        waitFor { a.isFinishing }
        assertEquals(1, rowsWithNote().size)
    }

    @Test
    fun tappingOutsideCloses() {
        val a = open().get()
        a.findViewById<View>(R.id.qe_scrim).performClick()
        assertTrue(a.isFinishing)
    }

    @Test
    fun tileAndShortcutTargetTheSheet() {
        val pi = QuickAddTileService.pendingIntent(ctx)
        val sent = shadowOf(pi).savedIntent
        assertEquals(QuickExpenseActivity::class.java.name, sent.component?.className)
        assertEquals(QuickExpenseActivity.ACTION, sent.action)
        assertTrue(sent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(shadowOf(pi).isActivityIntent)
        // The public action resolves to our activity (what Routines/Tasker use).
        val resolved = ctx.packageManager.resolveActivity(Intent(QuickExpenseActivity.ACTION).setPackage(ctx.packageName), 0)
        assertNotNull(resolved)
        assertEquals(QuickExpenseActivity::class.java.name, resolved!!.activityInfo.name)
    }

    @Test
    fun accountChipsPreselectTheLastUsed_andTheExpenseLandsOnIt() {
        val accounts = AppDatabase.getDatabase(ctx).accountDao()
        val name = "Wallet-" + (1..6).map { ('a'..'z').random() }.joinToString("")
        val id = runBlocking { accounts.insert(com.example.moneymanager.data.AccountEntity(name = name, type = "WALLET", balance = 300.0)) }
        com.example.moneymanager.utils.LastAccountPrefs(ctx).lastAccountId = id
        val a = open().get()
        waitFor { a.findViewById<ChipGroup>(R.id.qe_accounts).childCount > 1 && a.findViewById<ChipGroup>(R.id.qe_categories).childCount > 0 }
        assertEquals(View.VISIBLE, a.findViewById<View>(R.id.qe_accounts_scroll).visibility)
        val g = a.findViewById<ChipGroup>(R.id.qe_accounts)
        val checked = (0 until g.childCount).map { g.getChildAt(it) as Chip }.single { it.isChecked }
        assertEquals(name, checked.text.toString())
        a.findViewById<TextInputEditText>(R.id.qe_amount).setText("120")
        a.findViewById<TextInputEditText>(R.id.qe_note).setText(note)
        a.findViewById<Button>(R.id.qe_save).performClick()
        waitFor { rowsWithNote().size == 1 }
        assertEquals(id, rowsWithNote().single().accountId)
        waitFor { runBlocking { accounts.getById(id)!!.balance } == 180.0 }
    }
}
