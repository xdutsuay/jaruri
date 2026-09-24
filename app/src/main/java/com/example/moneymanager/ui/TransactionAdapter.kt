package com.example.moneymanager.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.moneymanager.R
import com.example.moneymanager.data.TransactionEntity
import java.text.SimpleDateFormat
import java.util.*

class TransactionAdapter(
    private val onClick: (TransactionEntity) -> Unit,
    private val onLongClick: (TransactionEntity) -> Unit = {}
) : ListAdapter<TransactionEntity, TransactionAdapter.TxViewHolder>(TxDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TxViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_transaction, parent, false)
        return TxViewHolder(view, onClick, onLongClick)
    }

    override fun onBindViewHolder(holder: TxViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class TxViewHolder(
        itemView: View,
        val onClick: (TransactionEntity) -> Unit,
        val onLongClick: (TransactionEntity) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val dateFmt = SimpleDateFormat("dd MMM", Locale.getDefault())

        fun bind(item: TransactionEntity) {
            itemView.findViewById<TextView>(R.id.tvCategory).text = item.category
            itemView.findViewById<TextView>(R.id.tvMemo).text = item.memo
            itemView.findViewById<TextView>(R.id.tvDate).text = dateFmt.format(Date(item.dateTimestamp))

            val amtView = itemView.findViewById<TextView>(R.id.tvAmount)
            amtView.text = String.format("%.2f", item.amount)

            val green = ContextCompat.getColor(itemView.context, R.color.accent_green)
            val red = ContextCompat.getColor(itemView.context, R.color.accent_red)
            amtView.setTextColor(if (item.type == "INCOME") green else red)

            itemView.setOnClickListener { onClick(item) }
            itemView.setOnLongClickListener {
                onLongClick(item)
                true
            }
        }
    }

    class TxDiffCallback : DiffUtil.ItemCallback<TransactionEntity>() {
        override fun areItemsTheSame(oldItem: TransactionEntity, newItem: TransactionEntity) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: TransactionEntity, newItem: TransactionEntity) =
            oldItem == newItem
    }
}
