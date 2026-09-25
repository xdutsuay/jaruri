package com.example.moneymanager.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.moneymanager.R
import com.example.moneymanager.data.TransactionEntity
import com.example.moneymanager.utils.CategoryIcons
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

sealed class LedgerRow {
    data class Header(
        val dayStartMillis: Long,
        val label: String,
        val expenseTotal: Double
    ) : LedgerRow()

    data class Tx(val entity: TransactionEntity) : LedgerRow()
}

class TransactionAdapter(
    private val onClick: (TransactionEntity) -> Unit,
    private val onLongClick: (TransactionEntity) -> Unit = {},
    private val currencySymbol: () -> String = { "₹" }
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var rows: List<LedgerRow> = emptyList()

    fun submitGrouped(transactions: List<TransactionEntity>) {
        rows = buildGrouped(transactions)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is LedgerRow.Header -> TYPE_HEADER
        is LedgerRow.Tx -> TYPE_TX
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderVH(inflater.inflate(R.layout.item_day_header, parent, false))
        } else {
            TxVH(inflater.inflate(R.layout.item_transaction, parent, false), onClick, onLongClick, currencySymbol)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is LedgerRow.Header -> (holder as HeaderVH).bind(row, currencySymbol())
            is LedgerRow.Tx -> (holder as TxVH).bind(row.entity)
        }
    }

    override fun getItemCount(): Int = rows.size

    class HeaderVH(view: View) : RecyclerView.ViewHolder(view) {
        private val label: TextView = view.findViewById(R.id.tvDayLabel)
        private val total: TextView = view.findViewById(R.id.tvDayTotal)

        fun bind(row: LedgerRow.Header, symbol: String) {
            label.text = row.label
            total.text = itemView.context.getString(
                R.string.day_expenses_total,
                symbol,
                String.format(Locale.getDefault(), "%.0f", row.expenseTotal)
            )
        }
    }

    class TxVH(
        itemView: View,
        val onClick: (TransactionEntity) -> Unit,
        val onLongClick: (TransactionEntity) -> Unit,
        val currencySymbol: () -> String
    ) : RecyclerView.ViewHolder(itemView) {
        fun bind(item: TransactionEntity) {
            itemView.findViewById<TextView>(R.id.tvCategory).text = item.category
            val memoView = itemView.findViewById<TextView>(R.id.tvMemo)
            if (item.memo.isBlank()) {
                memoView.visibility = View.GONE
            } else {
                memoView.visibility = View.VISIBLE
                memoView.text = item.memo
            }
            CategoryIcons.bind(
                itemView.findViewById(R.id.vIconBg),
                itemView.findViewById(R.id.tvIconLetter),
                item.category
            )

            val amtView = itemView.findViewById<TextView>(R.id.tvAmount)
            val green = ContextCompat.getColor(itemView.context, R.color.accent_green)
            val red = ContextCompat.getColor(itemView.context, R.color.accent_red)
            if (item.type == "INCOME") {
                amtView.text = String.format(Locale.getDefault(), "+ %.0f", item.amount)
                amtView.setTextColor(green)
            } else {
                amtView.text = String.format(Locale.getDefault(), "- %.0f", item.amount)
                amtView.setTextColor(red)
            }

            itemView.setOnClickListener { onClick(item) }
            itemView.setOnLongClickListener {
                onLongClick(item)
                true
            }
        }
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_TX = 1

        fun buildGrouped(transactions: List<TransactionEntity>): List<LedgerRow> {
            if (transactions.isEmpty()) return emptyList()
            val cal = Calendar.getInstance()
            val dayFmt = SimpleDateFormat("MM/dd EEE", Locale.getDefault())
            val grouped = transactions
                .sortedByDescending { it.dateTimestamp }
                .groupBy { tx ->
                    cal.timeInMillis = tx.dateTimestamp
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    cal.timeInMillis
                }
            val out = mutableListOf<LedgerRow>()
            for ((dayStart, list) in grouped) {
                val expense = list.filter { it.type == "EXPENSE" }.sumOf { it.amount }
                out += LedgerRow.Header(
                    dayStartMillis = dayStart,
                    label = dayFmt.format(Date(dayStart)),
                    expenseTotal = expense
                )
                list.forEach { out += LedgerRow.Tx(it) }
            }
            return out
        }
    }
}
