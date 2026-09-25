package com.example.moneymanager.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.moneymanager.R
import com.example.moneymanager.data.TransactionEntity
import com.example.moneymanager.viewmodel.MainViewModel
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecycleBinFragment : Fragment(R.layout.fragment_recycle_bin) {

    private val viewModel: MainViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val rv = view.findViewById<RecyclerView>(R.id.rvRecycle)
        val tvEmpty = view.findViewById<TextView>(R.id.tvEmpty)
        val adapter = Adapter(
            onRestore = { viewModel.restoreTransaction(it) },
            onPurge = { viewModel.permanentlyDelete(it) }
        )
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        view.findViewById<MaterialButton>(R.id.btnEmptyBin).setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.recycle_bin_empty)
                .setMessage(R.string.recycle_bin_empty_confirm)
                .setPositiveButton(R.string.delete) { _, _ -> viewModel.emptyRecycleBin() }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        viewModel.deletedTransactions.observe(viewLifecycleOwner) { list ->
            adapter.submit(list)
            tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private class Adapter(
        private val onRestore: (TransactionEntity) -> Unit,
        private val onPurge: (TransactionEntity) -> Unit
    ) : RecyclerView.Adapter<Adapter.VH>() {
        private var items: List<TransactionEntity> = emptyList()
        private val df = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        fun submit(list: List<TransactionEntity>) {
            items = list
            notifyDataSetChanged()
        }

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val title: TextView = v.findViewById(R.id.tvTitle)
            val sub: TextView = v.findViewById(R.id.tvSub)
            val restore: MaterialButton = v.findViewById(R.id.btnRestore)
            val purge: MaterialButton = v.findViewById(R.id.btnPurge)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_recycle_bin, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val tx = items[position]
            holder.title.text = "${tx.type} · ${tx.category} · ₹${"%.2f".format(tx.amount)}"
            holder.sub.text = "${df.format(Date(tx.dateTimestamp))} · ${tx.memo.take(80)}"
            holder.restore.setOnClickListener { onRestore(tx) }
            holder.purge.setOnClickListener { onPurge(tx) }
        }

        override fun getItemCount() = items.size
    }
}
