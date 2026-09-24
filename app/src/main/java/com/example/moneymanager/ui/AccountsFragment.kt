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
import com.example.moneymanager.viewmodel.MainViewModel
import com.google.android.material.floatingactionbutton.FloatingActionButton

class AccountsFragment : Fragment(R.layout.fragment_accounts) {

    private val viewModel: MainViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val rv = view.findViewById<RecyclerView>(R.id.rv_accounts)
        val adapter = AccountAdapter(
            symbolProvider = { viewModel.currencySymbol.value ?: "₹" },
            onLongClick = { account -> confirmDelete(account) }
        )
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        viewModel.allAccounts.observe(viewLifecycleOwner) { adapter.submit(it) }

        view.findViewById<FloatingActionButton>(R.id.fab_add_account).setOnClickListener {
            showAddDialog()
        }
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
                if (a.last4.isNotBlank()) append(" · XX").append(a.last4)
                if (a.isCreditCard) append(" · Limit ").append(symbol).append(a.creditLimit.toInt())
            }
            holder.balance.text = if (a.isCreditCard) {
                "Outstanding $symbol${"%.0f".format(a.balance)} · Available $symbol${"%.0f".format(a.availableCredit)}"
            } else {
                "$symbol${"%.0f".format(a.balance)}"
            }
            val bg = if (a.isCreditCard) R.color.card_debt else R.color.card_cash
            holder.itemView.setBackgroundColor(ContextCompat.getColor(holder.itemView.context, bg))
            holder.itemView.setOnLongClickListener {
                onLongClick(a)
                true
            }
        }

        override fun getItemCount() = items.size
    }
}
