package com.example.moneymanager.ui

import android.os.Bundle
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
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.moneymanager.R
import com.example.moneymanager.data.RecurringEntity
import com.example.moneymanager.viewmodel.MainViewModel
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class RecurringFragment : Fragment(R.layout.fragment_recurring) {

    private val viewModel: MainViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val rv = view.findViewById<RecyclerView>(R.id.rv_recurring)
        val empty = view.findViewById<TextView>(R.id.tv_recurring_empty)
        val adapter = Adapter(
            symbolProvider = { viewModel.currencySymbol.value ?: "₹" },
            onLongClick = { item -> confirmDelete(item) }
        )
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        viewModel.activeRecurring.observe(viewLifecycleOwner) { list ->
            adapter.submit(list)
            empty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }

        view.findViewById<MaterialButton>(R.id.btn_add_recurring).setOnClickListener {
            showAddDialog()
        }
    }

    private fun showAddDialog() {
        val pad = (16 * resources.displayMetrics.density).toInt()
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad * 2, pad, pad * 2, pad / 2)
        }
        val etCategory = EditText(requireContext()).apply { hint = "Category" }
        val etAmount = EditText(requireContext()).apply {
            hint = "Amount"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val etMemo = EditText(requireContext()).apply { hint = "Memo (optional)" }
        val spType = Spinner(requireContext()).apply {
            adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                listOf("EXPENSE", "INCOME")
            )
        }
        val spFreq = Spinner(requireContext()).apply {
            adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                listOf(
                    getString(R.string.recurring_freq_month),
                    getString(R.string.recurring_freq_week),
                    getString(R.string.recurring_freq_day)
                )
            )
        }
        container.addView(spType)
        container.addView(etCategory)
        container.addView(etAmount)
        container.addView(etMemo)
        container.addView(spFreq)

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.add_recurring)
            .setView(container)
            .setPositiveButton(R.string.save_transaction) { _, _ ->
                val category = etCategory.text.toString().trim()
                val amount = etAmount.text.toString().toDoubleOrNull()
                if (category.isEmpty() || amount == null || amount <= 0) {
                    Toast.makeText(requireContext(), "Category and amount required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val freq = when (spFreq.selectedItemPosition) {
                    1 -> "WEEK"
                    2 -> "DAY"
                    else -> "MONTH"
                }
                val next = Calendar.getInstance().apply {
                    when (freq) {
                        "DAY" -> add(Calendar.DAY_OF_MONTH, 1)
                        "WEEK" -> add(Calendar.WEEK_OF_YEAR, 1)
                        else -> add(Calendar.MONTH, 1)
                    }
                }.timeInMillis
                viewModel.addRecurring(
                    RecurringEntity(
                        type = spType.selectedItem.toString(),
                        category = category,
                        amount = amount,
                        memo = etMemo.text.toString().trim(),
                        frequency = freq,
                        nextDueTimestamp = next
                    )
                )
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDelete(item: RecurringEntity) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete)
            .setMessage(item.category)
            .setPositiveButton(R.string.delete) { _, _ -> viewModel.deleteRecurring(item) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private class Adapter(
        private val symbolProvider: () -> String,
        private val onLongClick: (RecurringEntity) -> Unit
    ) : RecyclerView.Adapter<Adapter.VH>() {
        private var items: List<RecurringEntity> = emptyList()
        private val df = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        fun submit(list: List<RecurringEntity>) {
            items = list
            notifyDataSetChanged()
        }

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val title: TextView = v.findViewById(R.id.tvRecurringTitle)
            val sub: TextView = v.findViewById(R.id.tvRecurringSub)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_recurring, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            val symbol = symbolProvider()
            val freq = when (item.frequency) {
                "DAY" -> holder.itemView.context.getString(R.string.recurring_freq_day)
                "WEEK" -> holder.itemView.context.getString(R.string.recurring_freq_week)
                else -> holder.itemView.context.getString(R.string.recurring_freq_month)
            }
            holder.title.text = "${item.type} · ${item.category} · $symbol${"%.0f".format(item.amount)} · $freq"
            holder.sub.text = holder.itemView.context.getString(
                R.string.recurring_next,
                df.format(Date(item.nextDueTimestamp))
            ) + if (item.memo.isNotBlank()) " · ${item.memo}" else ""
            holder.itemView.setOnLongClickListener {
                onLongClick(item)
                true
            }
        }

        override fun getItemCount() = items.size
    }
}
