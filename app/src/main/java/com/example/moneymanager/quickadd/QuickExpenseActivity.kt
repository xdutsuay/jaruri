package com.example.moneymanager.quickadd

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.moneymanager.MainActivity
import com.example.moneymanager.R
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.text.DecimalFormat

/**
 * Translucent "amount → category → note → Save" sheet, opened from the Quick Settings tile,
 * a launcher shortcut, or anything that can launch an app shortcut / activity (Samsung
 * Routines, Tasker, a back-tap gesture). Saves an EXPENSE dated now, offers Undo, closes.
 * Not shown over the lock screen: the tile unlocks first.
 */
class QuickExpenseActivity : AppCompatActivity() {
    // Explicit factory: the shared default instance can hold on to an older Application.
    private val vm: QuickExpenseViewModel by viewModels { ViewModelProvider.AndroidViewModelFactory(application) }
    private lateinit var amountLayout: TextInputLayout
    private lateinit var amount: TextInputEditText
    private lateinit var note: TextInputEditText
    private lateinit var chips: ChipGroup
    private lateinit var save: Button
    private var selected: String? = null
    private val money = DecimalFormat("#,##0.##")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quick_expense)
        selected = savedInstanceState?.getString(STATE_CATEGORY)

        amountLayout = findViewById(R.id.qe_amount_layout)
        amount = findViewById(R.id.qe_amount)
        note = findViewById(R.id.qe_note)
        chips = findViewById(R.id.qe_categories)
        save = findViewById(R.id.qe_save)

        findViewById<View>(R.id.qe_scrim).setOnClickListener { finish() }
        findViewById<Button>(R.id.qe_full).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java).apply {
                action = MainActivity.ACTION_QUICK_ADD
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            })
            finish()
        }
        save.setOnClickListener { submit() }
        note.setOnEditorActionListener { _, id, _ -> (id == EditorInfo.IME_ACTION_DONE).also { if (it) submit() } }
        amount.setOnEditorActionListener { _, id, _ ->
            (id == EditorInfo.IME_ACTION_NEXT).also { if (it) note.requestFocus() }
        }
        amount.doOnTextChange { amountLayout.error = null }

        vm.currencySymbol.observe(this) { amountLayout.prefixText = it }
        vm.categories.observe(this) { names -> showCategories(names) }
        amount.requestFocus()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_CATEGORY, selected)
    }

    private fun showCategories(names: List<String>) {
        if (selected == null || selected !in names) selected = vm.initialCategory(names)
        chips.setOnCheckedStateChangeListener(null)
        chips.removeAllViews()
        var selectedId = View.NO_ID
        names.forEach { name ->
            val chip = Chip(this).apply {
                id = View.generateViewId()
                text = name
                isCheckable = true
                tag = name
            }
            chips.addView(chip)
            if (name == selected) selectedId = chip.id
        }
        // Check after all chips are in: ChipGroup's own bookkeeping decides what is checked.
        if (selectedId != View.NO_ID) chips.check(selectedId)
        chips.setOnCheckedStateChangeListener { group, ids ->
            // Look up by child, not findViewById: generated ids are only unique among themselves.
            val id = ids.firstOrNull()
            selected = (0 until group.childCount).map { group.getChildAt(it) }.firstOrNull { it.id == id }?.tag as? String
        }
        // Bring the remembered category into view.
        chips.post {
            val c = chips.findViewWithTag<View>(selected) ?: return@post
            (chips.parent as? HorizontalScrollView)?.smoothScrollTo(maxOf(0, c.left - c.width / 2), 0)
        }
    }

    private fun submit() {
        vm.save(amount.text?.toString(), selected, note.text?.toString()) { r ->
            when (r) {
                is QuickExpenseViewModel.Result.Invalid -> {
                    amountLayout.error = getString(
                        when (r.error) {
                            QuickExpenseInput.AmountError.EMPTY, QuickExpenseInput.AmountError.ZERO -> R.string.quick_expense_err_empty
                            QuickExpenseInput.AmountError.INVALID -> R.string.quick_expense_err_invalid
                            QuickExpenseInput.AmountError.TOO_LARGE -> R.string.quick_expense_err_large
                        }
                    )
                    amount.requestFocus()
                }
                QuickExpenseViewModel.Result.NoCategory ->
                    Toast.makeText(this, R.string.quick_expense_err_category, Toast.LENGTH_SHORT).show()
                is QuickExpenseViewModel.Result.Saved -> onSaved(r)
            }
        }
    }

    private fun onSaved(r: QuickExpenseViewModel.Result.Saved) {
        save.isEnabled = false
        currentFocus?.let { getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(it.windowToken, 0) }
        val text = getString(R.string.quick_expense_saved, (amountLayout.prefixText ?: "").toString() + money.format(r.amount), r.category)
        Snackbar.make(findViewById(R.id.qe_card), text, UNDO_MS)
            .setAction(R.string.quick_expense_undo) {
                vm.undo(r.id) { ok ->
                    save.isEnabled = true
                    if (ok) Toast.makeText(this, R.string.quick_expense_undone, Toast.LENGTH_SHORT).show()
                }
            }
            .addCallback(object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                override fun onDismissed(bar: Snackbar?, event: Int) {
                    if (event != DISMISS_EVENT_ACTION && !isFinishing) finish()
                }
            })
            .show()
    }

    private fun TextInputEditText.doOnTextChange(block: () -> Unit) {
        addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = block()
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    companion object {
        /** Public action so Routines/Tasker can open the sheet; it never saves without a tap. */
        const val ACTION = "com.kaustubhtripathi.jaruri.action.QUICK_EXPENSE"
        const val SHORTCUT_ID = "quick_expense"
        private const val STATE_CATEGORY = "category"
        internal const val UNDO_MS = 4_000

        fun intent(ctx: Context): Intent = Intent(ctx, QuickExpenseActivity::class.java)
            .setAction(ACTION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
}
