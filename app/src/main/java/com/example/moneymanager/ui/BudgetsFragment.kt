package com.example.moneymanager.ui

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.moneymanager.R
import com.example.moneymanager.data.BudgetEntity
import com.example.moneymanager.data.TransactionEntity
import com.example.moneymanager.viewmodel.MainViewModel
import com.google.android.material.button.MaterialButton
import java.util.Calendar

class BudgetsFragment : Fragment(R.layout.fragment_budgets) {

    private val viewModel: MainViewModel by activityViewModels()
    private var transactions: List<TransactionEntity> = emptyList()
    private var budgets: List<BudgetEntity> = emptyList()
    private var adapter: BudgetAdapter? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val rv = view.findViewById<RecyclerView>(R.id.rv_budgets)
        adapter = BudgetAdapter(
            symbolProvider = { viewModel.currencySymbol.value ?: "₹" },
            onLongClick = { budget -> confirmDelete(budget) }
        )
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        viewModel.allBudgets.observe(viewLifecycleOwner) {
            budgets = it
            refresh()
        }
        viewModel.allTransactions.observe(viewLifecycleOwner) {
            transactions = it
            refresh()
        }
        viewModel.currencySymbol.observe(viewLifecycleOwner) { refresh() }

        view.findViewById<MaterialButton>(R.id.btn_add_budget).setOnClickListener {
            showAddDialog()
        }
    }

    private fun refresh() {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH)
        val monthSpend = MainViewModel.filterByMonth(transactions, year, month)
            .filter { it.type == "EXPENSE" }
            .groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { it.amount } }

        adapter?.submit(budgets.map { b ->
            BudgetRow(b, monthSpend[b.category] ?: 0.0)
        })
    }

    private fun showAddDialog() {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
        }
        val etCategory = EditText(requireContext()).apply { hint = "Category" }
        val etLimit = EditText(requireContext()).apply {
            hint = "Monthly limit"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        container.addView(etCategory)
        container.addView(etLimit)

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.add_budget)
            .setView(container)
            .setPositiveButton(R.string.save_transaction) { _, _ ->
                val category = etCategory.text.toString().trim()
                val limit = etLimit.text.toString().toDoubleOrNull()
                if (category.isEmpty() || limit == null || limit <= 0) {
                    Toast.makeText(requireContext(), "Category and limit required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                viewModel.addBudget(category, limit)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDelete(budget: BudgetEntity) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete)
            .setMessage(budget.category)
            .setPositiveButton(R.string.delete) { _, _ -> viewModel.deleteBudget(budget) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    data class BudgetRow(val budget: BudgetEntity, val spent: Double)

    private class BudgetAdapter(
        private val symbolProvider: () -> String,
        private val onLongClick: (BudgetEntity) -> Unit
    ) : RecyclerView.Adapter<BudgetAdapter.VH>() {
        private var items: List<BudgetRow> = emptyList()

        fun submit(list: List<BudgetRow>) {
            items = list
            notifyDataSetChanged()
        }

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val category: TextView = view.findViewById(R.id.tvBudgetCategory)
            val progress: TextView = view.findViewById(R.id.tvBudgetProgress)
            val bar: ProgressBar = view.findViewById(R.id.progressBudget)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_budget, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val row = items[position]
            val symbol = symbolProvider()
            val limit = row.budget.monthlyLimit
            val pct = if (limit > 0) ((row.spent / limit) * 100).toInt().coerceIn(0, 100) else 0
            holder.category.text = row.budget.category
            holder.progress.text = "$symbol${"%.0f".format(row.spent)} / $symbol${"%.0f".format(limit)}"
            holder.bar.progress = pct
            val over = row.spent > limit
            val tint = ContextCompat.getColor(
                holder.itemView.context,
                if (over) R.color.accent_red else R.color.accent_green
            )
            holder.bar.progressDrawable?.setTint(tint)
            holder.itemView.setOnLongClickListener {
                onLongClick(row.budget)
                true
            }
        }

        override fun getItemCount() = items.size
    }
}
