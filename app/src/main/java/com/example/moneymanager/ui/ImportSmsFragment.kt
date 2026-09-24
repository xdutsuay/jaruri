package com.example.moneymanager.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.moneymanager.R
import com.example.moneymanager.utils.ParsedSms
import com.example.moneymanager.utils.SmsInboxReader
import com.example.moneymanager.utils.SmsParser
import com.example.moneymanager.viewmodel.MainViewModel

/**
 * Import transactions from device SMS inbox or pasted text.
 * Always shows an editable preview — never silent-saves.
 */
class ImportSmsFragment : Fragment(R.layout.fragment_import_sms) {

    private val viewModel: MainViewModel by activityViewModels()

    private var expenseCategoryNames: List<String> = emptyList()
    private var incomeCategoryNames: List<String> = emptyList()
    private var inboxItems: List<SelectableInbox> = emptyList()
    private var proposalViews: MutableList<ProposalBinding> = mutableListOf()

    private data class SelectableInbox(
        val sms: SmsInboxReader.InboxSms,
        var selected: Boolean = false
    )

    private data class ProposalBinding(
        val root: View,
        val parsed: ParsedSms,
        val cbInclude: CheckBox,
        val rgType: RadioGroup,
        val rbIncome: RadioButton,
        val rbExpense: RadioButton,
        val etAmount: EditText,
        val spCategory: Spinner,
        val etMemo: EditText,
        val categoryAdapter: ArrayAdapter<String>
    )

    private val requestSmsPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            loadInbox()
        } else {
            Toast.makeText(
                requireContext(),
                R.string.import_sms_permission_denied,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnReadInbox = view.findViewById<Button>(R.id.btnReadInbox)
        val btnLoadSamples = view.findViewById<Button>(R.id.btnLoadSamples)
        val btnParsePaste = view.findViewById<Button>(R.id.btnParsePaste)
        val btnPreviewSelected = view.findViewById<Button>(R.id.btnPreviewSelected)
        val btnConfirmImport = view.findViewById<Button>(R.id.btnConfirmImport)
        val etPasteSms = view.findViewById<EditText>(R.id.etPasteSms)
        val rvInbox = view.findViewById<RecyclerView>(R.id.rvInbox)
        val previewContainer = view.findViewById<LinearLayout>(R.id.previewContainer)

        viewModel.expenseCategories.observe(viewLifecycleOwner) { cats ->
            expenseCategoryNames = cats.map { it.name }
            refreshProposalCategories()
        }
        viewModel.incomeCategories.observe(viewLifecycleOwner) { cats ->
            incomeCategoryNames = cats.map { it.name }
            refreshProposalCategories()
        }

        btnReadInbox.setOnClickListener { ensurePermissionAndRead() }
        btnLoadSamples.setOnClickListener {
            etPasteSms.setText(SmsParser.samplePasteText())
            Toast.makeText(requireContext(), R.string.import_sms_samples_loaded, Toast.LENGTH_SHORT)
                .show()
        }
        btnParsePaste.setOnClickListener {
            val parsed = SmsParser.parseBatch(etPasteSms.text.toString())
            if (parsed.isEmpty()) {
                Toast.makeText(requireContext(), R.string.import_sms_none_found, Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }
            showProposals(parsed, previewContainer, btnConfirmImport, view)
        }
        btnPreviewSelected.setOnClickListener {
            val selectedBodies = inboxItems.filter { it.selected }.map { it.sms.body }
            if (selectedBodies.isEmpty()) {
                Toast.makeText(requireContext(), R.string.import_sms_select_one, Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }
            val parsed = selectedBodies.mapNotNull { SmsParser.parse(it) }
            if (parsed.isEmpty()) {
                Toast.makeText(requireContext(), R.string.import_sms_none_found, Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }
            showProposals(parsed, previewContainer, btnConfirmImport, view)
        }
        btnConfirmImport.setOnClickListener { confirmImport() }

        rvInbox.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun ensurePermissionAndRead() {
        val granted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            loadInbox()
        } else {
            requestSmsPermission.launch(Manifest.permission.READ_SMS)
        }
    }

    private fun loadInbox() {
        val view = requireView()
        val progress = view.findViewById<ProgressBar>(R.id.progressInbox)
        val rvInbox = view.findViewById<RecyclerView>(R.id.rvInbox)
        val tvInboxSection = view.findViewById<TextView>(R.id.tvInboxSection)
        val btnPreviewSelected = view.findViewById<Button>(R.id.btnPreviewSelected)

        progress.visibility = View.VISIBLE
        val messages = SmsInboxReader.readFinancialSms(requireContext(), limit = 80)
        progress.visibility = View.GONE

        if (messages.isEmpty()) {
            Toast.makeText(requireContext(), R.string.import_sms_inbox_empty, Toast.LENGTH_LONG)
                .show()
            return
        }

        inboxItems = messages.map { SelectableInbox(it) }
        rvInbox.adapter = InboxAdapter(inboxItems)
        rvInbox.visibility = View.VISIBLE
        tvInboxSection.visibility = View.VISIBLE
        btnPreviewSelected.visibility = View.VISIBLE
    }

    private fun showProposals(
        parsed: List<ParsedSms>,
        container: LinearLayout,
        confirmBtn: Button,
        root: View
    ) {
        container.removeAllViews()
        proposalViews.clear()
        val inflater = LayoutInflater.from(requireContext())

        for (p in parsed) {
            val card = inflater.inflate(R.layout.item_sms_proposal, container, false)
            val cbInclude = card.findViewById<CheckBox>(R.id.cbInclude)
            val rgType = card.findViewById<RadioGroup>(R.id.rgType)
            val rbIncome = card.findViewById<RadioButton>(R.id.rbIncome)
            val rbExpense = card.findViewById<RadioButton>(R.id.rbExpense)
            val etAmount = card.findViewById<EditText>(R.id.etAmount)
            val spCategory = card.findViewById<Spinner>(R.id.spCategory)
            val etMemo = card.findViewById<EditText>(R.id.etMemo)
            val tvMode = card.findViewById<TextView>(R.id.tvMode)

            if (p.isIncome == true) rbIncome.isChecked = true else rbExpense.isChecked = true
            etAmount.setText(p.amount?.let { String.format("%.2f", it) }.orEmpty())
            etMemo.setText(p.toMemo())
            tvMode.text = getString(R.string.import_sms_mode_label, p.modeOfPayment)

            val adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                mutableListOf<String>()
            )
            spCategory.adapter = adapter

            val binding = ProposalBinding(
                root = card,
                parsed = p,
                cbInclude = cbInclude,
                rgType = rgType,
                rbIncome = rbIncome,
                rbExpense = rbExpense,
                etAmount = etAmount,
                spCategory = spCategory,
                etMemo = etMemo,
                categoryAdapter = adapter
            )
            fillCategories(binding)
            rgType.setOnCheckedChangeListener { _, _ -> fillCategories(binding) }

            proposalViews += binding
            container.addView(card)
        }

        container.visibility = View.VISIBLE
        root.findViewById<TextView>(R.id.tvPreviewSection).visibility = View.VISIBLE
        confirmBtn.visibility = View.VISIBLE
    }

    private fun fillCategories(binding: ProposalBinding) {
        val names = if (binding.rbIncome.isChecked) incomeCategoryNames else expenseCategoryNames
        binding.categoryAdapter.clear()
        binding.categoryAdapter.addAll(names)
        binding.categoryAdapter.notifyDataSetChanged()
        if (names.isNotEmpty()) binding.spCategory.setSelection(0)
    }

    private fun refreshProposalCategories() {
        proposalViews.forEach { fillCategories(it) }
    }

    private fun confirmImport() {
        val seenMemos = viewModel.allTransactions.value?.map { it.memo }.orEmpty().toMutableList()
        var imported = 0
        var skippedDup = 0
        var skippedInvalid = 0

        for (b in proposalViews) {
            if (!b.cbInclude.isChecked) continue

            val amount = b.etAmount.text.toString().toDoubleOrNull()
            if (amount == null || amount <= 0) {
                skippedInvalid++
                continue
            }
            if (b.categoryAdapter.count == 0) {
                skippedInvalid++
                continue
            }

            val hash = b.parsed.smsHash
            if (hash.isNotEmpty() &&
                seenMemos.any { SmsParser.memoContainsHash(it, hash) }
            ) {
                skippedDup++
                continue
            }

            val type = if (b.rbIncome.isChecked) "INCOME" else "EXPENSE"
            val category = b.spCategory.selectedItem?.toString()
                ?: if (type == "INCOME") "Salary" else "Food"
            val memo = b.etMemo.text.toString().ifBlank { b.parsed.toMemo() }

            viewModel.addTransaction(
                type = type,
                category = category,
                amount = amount,
                date = b.parsed.dateTimestamp,
                memo = memo
            )
            seenMemos += memo
            imported++
        }

        val msg = when {
            imported == 0 && skippedDup > 0 ->
                getString(R.string.import_sms_all_dup, skippedDup)
            imported == 0 ->
                getString(R.string.import_sms_nothing_saved)
            else ->
                getString(R.string.import_sms_saved, imported, skippedDup)
        }
        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()

        if (imported > 0) {
            findNavController().popBackStack()
        }
    }

    private class InboxAdapter(
        private val items: List<SelectableInbox>
    ) : RecyclerView.Adapter<InboxAdapter.VH>() {

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val cb: CheckBox = view.findViewById(R.id.cbSelect)
            val address: TextView = view.findViewById(R.id.tvAddress)
            val body: TextView = view.findViewById(R.id.tvBody)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_sms_inbox, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.cb.setOnCheckedChangeListener(null)
            holder.cb.isChecked = item.selected
            holder.address.text = item.sms.address.ifBlank { "Unknown" }
            holder.body.text = item.sms.body
            holder.cb.setOnCheckedChangeListener { _, checked ->
                item.selected = checked
            }
            holder.itemView.setOnClickListener {
                item.selected = !item.selected
                holder.cb.isChecked = item.selected
            }
        }

        override fun getItemCount(): Int = items.size
    }
}
