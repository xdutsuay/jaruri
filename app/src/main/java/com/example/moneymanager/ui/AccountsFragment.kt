package com.example.moneymanager.ui

import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.moneymanager.R
import com.example.moneymanager.data.AccountEntity
import com.example.moneymanager.utils.TransactionAccounting
import com.example.moneymanager.viewmodel.MainViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton

class AccountsFragment : Fragment(R.layout.fragment_accounts) {

    private val viewModel: MainViewModel by activityViewModels()
    private var accountsCache: List<AccountEntity> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val rv = view.findViewById<RecyclerView>(R.id.rv_accounts)
        val tvRunway = view.findViewById<TextView>(R.id.tv_accounts_runway)
        val adapter = AccountAdapter(
            symbolProvider = { viewModel.currencySymbol.value ?: "₹" },
            onClick = { account -> showEditDialog(account) },
            onLongClick = { account -> confirmDelete(account) }
        )
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        viewModel.allAccounts.observe(viewLifecycleOwner) { list ->
            accountsCache = list
            adapter.submit(list)
            val symbol = viewModel.currencySymbol.value ?: "₹"
            if (list.isEmpty()) {
                tvRunway.visibility = View.GONE
            } else {
                tvRunway.visibility = View.VISIBLE
                val net = TransactionAccounting.netLiquid(list)
                tvRunway.text = getString(R.string.runway_label, symbol, net)
            }
        }

        view.findViewById<MaterialButton>(R.id.btn_transfer).setOnClickListener {
            showTransferDialog()
        }

        view.findViewById<FloatingActionButton>(R.id.fab_add_account).setOnClickListener {
            showAddDialog()
        }
    }

    private fun showTransferDialog() {
        if (accountsCache.size < 2) {
            Toast.makeText(requireContext(), R.string.transfer_need_two_accounts, Toast.LENGTH_SHORT).show()
            return
        }
        val names = accountsCache.map { "${it.name} (${it.type.replace('_', ' ')})" }
        val pad = (16 * resources.displayMetrics.density).toInt()
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad * 2, pad, pad * 2, pad / 2)
        }
        val spFrom = Spinner(requireContext()).apply {
            adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                names
            )
        }
        val spTo = Spinner(requireContext()).apply {
            adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                names
            )
            if (names.size > 1) setSelection(1)
        }
        val etAmount = EditText(requireContext()).apply {
            hint = getString(R.string.transfer_amount)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val etMemo = EditText(requireContext()).apply {
            hint = getString(R.string.transfer_memo)
        }
        val labelFrom = TextView(requireContext()).apply { text = getString(R.string.transfer_from) }
        val labelTo = TextView(requireContext()).apply { text = getString(R.string.transfer_to) }
        container.addView(labelFrom)
        container.addView(spFrom)
        container.addView(labelTo)
        container.addView(spTo)
        container.addView(etAmount)
        container.addView(etMemo)

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.transfer_title)
            .setView(container)
            .setPositiveButton(R.string.save_transaction) { _, _ ->
                val from = accountsCache.getOrNull(spFrom.selectedItemPosition) ?: return@setPositiveButton
                val to = accountsCache.getOrNull(spTo.selectedItemPosition) ?: return@setPositiveButton
                if (from.id == to.id) {
                    Toast.makeText(requireContext(), R.string.transfer_same_account, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val amount = etAmount.text.toString().toDoubleOrNull()
                if (amount == null || amount <= 0) {
                    Toast.makeText(requireContext(), "Enter an amount", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val memo = etMemo.text.toString().trim()
                val category = if (to.isCreditCard) {
                    TransactionAccounting.CAT_CC_PAYMENT
                } else {
                    TransactionAccounting.CAT_TRANSFER
                }
                viewModel.addTransfer(
                    fromAccountId = from.id,
                    toAccountId = to.id,
                    amount = amount,
                    memo = memo.ifBlank {
                        "Transfer: ${from.name} → ${to.name}"
                    },
                    category = category
                )
                Toast.makeText(requireContext(), R.string.transfer_saved, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showEditDialog(account: AccountEntity) {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
        }
        val etName = EditText(requireContext()).apply {
            hint = "Name"
            setText(account.name)
        }
        val etBalance = EditText(requireContext()).apply {
            hint = "Balance / outstanding"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(if (account.balance == 0.0) "" else account.balance.toString())
        }
        val etLimit = EditText(requireContext()).apply {
            hint = "Credit limit (cards only)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(if (account.creditLimit == 0.0) "" else account.creditLimit.toString())
            visibility = if (account.isCreditCard) View.VISIBLE else View.GONE
        }
        val etLast4 = EditText(requireContext()).apply {
            hint = "Last 4 digits"
            inputType = InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(InputFilter.LengthFilter(4))
            setText(account.last4)
            visibility = if (account.isCreditCard) View.VISIBLE else View.GONE
        }
        container.addView(etName)
        container.addView(etBalance)
        container.addView(etLimit)
        container.addView(etLast4)

        AlertDialog.Builder(requireContext())
            .setTitle(account.name)
            .setView(container)
            .setPositiveButton(R.string.save_transaction) { _, _ ->
                val name = etName.text.toString().trim().ifBlank { account.name }
                viewModel.updateAccount(
                    account.copy(
                        name = name,
                        balance = etBalance.text.toString().toDoubleOrNull() ?: account.balance,
                        creditLimit = etLimit.text.toString().toDoubleOrNull() ?: account.creditLimit,
                        last4 = etLast4.text.toString().trim()
                    )
                )
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showAddDialog() {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
        }
        val etName = EditText(requireContext()).apply { hint = "Name" }
        val spType = Spinner(requireContext())
        val types = listOf(
            AccountEntity.TYPE_CASH,
            AccountEntity.TYPE_BANK,
            AccountEntity.TYPE_CREDIT_CARD
        )
        spType.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            types
        )
        val etBalance = EditText(requireContext()).apply {
            hint = "Balance / outstanding"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val etLimit = EditText(requireContext()).apply {
            hint = "Credit limit (cards only)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val etLast4 = EditText(requireContext()).apply {
            hint = "Last 4 digits (optional)"
            inputType = InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(InputFilter.LengthFilter(4))
        }
        container.addView(etName)
        container.addView(spType)
        container.addView(etBalance)
        container.addView(etLimit)
        container.addView(etLast4)

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.add_account)
            .setView(container)
            .setPositiveButton(R.string.save_transaction) { _, _ ->
                val name = etName.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(requireContext(), "Name required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val type = types[spType.selectedItemPosition]
                val balance = etBalance.text.toString().toDoubleOrNull() ?: 0.0
                val limit = etLimit.text.toString().toDoubleOrNull() ?: 0.0
                viewModel.addAccount(
                    AccountEntity(
                        name = name,
                        type = type,
                        balance = balance,
                        creditLimit = if (type == AccountEntity.TYPE_CREDIT_CARD) limit else 0.0,
                        last4 = etLast4.text.toString().trim()
                    )
                )
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDelete(account: AccountEntity) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete)
            .setMessage(account.name)
            .setPositiveButton(R.string.delete) { _, _ -> viewModel.deleteAccount(account) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private class AccountAdapter(
        private val symbolProvider: () -> String,
        private val onClick: (AccountEntity) -> Unit,
        private val onLongClick: (AccountEntity) -> Unit
    ) : RecyclerView.Adapter<AccountAdapter.VH>() {
        private var items: List<AccountEntity> = emptyList()

        fun submit(list: List<AccountEntity>) {
            items = list
            notifyDataSetChanged()
        }

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView = view.findViewById(R.id.tvAccountName)
            val meta: TextView = view.findViewById(R.id.tvAccountMeta)
            val balance: TextView = view.findViewById(R.id.tvAccountBalance)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_account, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val a = items[position]
            val symbol = symbolProvider()
            holder.name.text = a.name
            holder.meta.text = buildString {
                append(a.type.replace('_', ' '))
                if (a.bankHint.isNotBlank()) append(" · ").append(a.bankHint)
                if (a.last4.isNotBlank()) append(" · XX").append(a.last4)
                if (a.isCreditCard && a.creditLimit > 0) {
                    append(" · Limit ").append(symbol).append(a.creditLimit.toInt())
                }
                if (a.seenCount > 0) append(" · seen ").append(a.seenCount)
            }
            holder.balance.text = buildString {
                if (a.isCreditCard) {
                    append("Outstanding $symbol${"%.0f".format(a.balance)}")
                    append(" · Available $symbol${"%.0f".format(a.availableCredit)}")
                } else {
                    append("$symbol${"%.0f".format(a.balance)}")
                }
                val reported = a.lastReportedBalance
                if (reported != null) {
                    append('\n')
                    append("SMS $symbol${"%.0f".format(reported)}")
                    if (a.lastReportedKind.isNotBlank()) append(" (").append(a.lastReportedKind).append(')')
                    append(" · Diff ")
                    val diff = a.runningDifference
                    if (diff > 0) append('+')
                    append(symbol).append("%.0f".format(diff))
                }
            }
            val bg = if (a.isCreditCard) R.color.card_debt else R.color.card_cash
            holder.itemView.setBackgroundColor(ContextCompat.getColor(holder.itemView.context, bg))
            holder.itemView.setOnClickListener { onClick(a) }
            holder.itemView.setOnLongClickListener {
                onLongClick(a)
                true
            }
        }

        override fun getItemCount() = items.size
    }
}
