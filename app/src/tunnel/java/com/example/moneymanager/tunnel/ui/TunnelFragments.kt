package com.example.moneymanager.tunnel.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.moneymanager.R
import com.example.moneymanager.tunnel.TunnelState
import com.example.moneymanager.tunnel.TunnelVpnService
import com.example.moneymanager.tunnel.data.TunnelAppRuleEntity
import com.example.moneymanager.tunnel.data.TunnelDomainRuleEntity
import com.example.moneymanager.tunnel.data.TunnelListEntity
import com.example.moneymanager.tunnel.BundledList
import com.example.moneymanager.tunnel.dns.BlockMode
import com.example.moneymanager.tunnel.log.DnsLogEntity
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date

class TunnelStatusFragment : Fragment(R.layout.fragment_tunnel_status) {
    private val vm: TunnelViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val status = view.findViewById<TextView>(R.id.tv_tunnel_status)
        val toggle = view.findViewById<Button>(R.id.btn_tunnel_toggle)
        val pause = view.findViewById<Button>(R.id.btn_tunnel_pause)
        val today = view.findViewById<TextView>(R.id.tv_tunnel_today)
        val warnings = view.findViewById<TextView>(R.id.tv_tunnel_warnings)

        fun render() {
            val s = vm.status.value ?: TunnelState.Status.Off
            val pausedUntil = vm.prefs.pausedUntil
            val paused = s is TunnelState.Status.On && pausedUntil > System.currentTimeMillis()
            status.text = when {
                paused -> getString(R.string.tunnel_status_paused, DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(pausedUntil)))
                s is TunnelState.Status.On && s.bypassCount > 0 -> getString(R.string.tunnel_status_on_bypass, s.bypassCount)
                s is TunnelState.Status.On -> getString(R.string.tunnel_status_on)
                s is TunnelState.Status.Starting -> getString(R.string.tunnel_status_starting)
                s is TunnelState.Status.Revoked -> getString(R.string.tunnel_status_revoked)
                s is TunnelState.Status.Failed -> getString(R.string.tunnel_status_failed, s.message)
                else -> getString(R.string.tunnel_status_off)
            }
            val on = TunnelState.isOn
            toggle.setText(if (on) R.string.tunnel_turn_off else R.string.tunnel_turn_on)
            pause.visibility = if (s is TunnelState.Status.On) View.VISIBLE else View.GONE
            pause.setText(if (paused) R.string.tunnel_resume else R.string.tunnel_pause_10)
            pause.setOnClickListener {
                if (paused) TunnelVpnService.resume(requireContext()) else TunnelVpnService.pause(requireContext(), 10)
            }
            val w = vm.warnings()
            warnings.text = w.joinToString("\n\n")
            warnings.visibility = if (w.isEmpty()) View.GONE else View.VISIBLE
        }

        toggle.setOnClickListener {
            if (!TunnelState.isOn) { (requireActivity() as TunnelActivity).requestStart(); return@setOnClickListener }
            val q = vm.stopWarning()
            if (q == null) TunnelVpnService.stop(requireContext())
            else AlertDialog.Builder(requireContext())
                .setMessage(q)
                .setPositiveButton(R.string.tunnel_turn_off_anyway) { _, _ -> TunnelVpnService.stop(requireContext()) }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
        vm.status.observe(viewLifecycleOwner) { render() }
        vm.prefsChanged.observe(viewLifecycleOwner) { render() }
        vm.today.observe(viewLifecycleOwner) { today.text = getString(R.string.tunnel_today, it.allowed, it.blocked) }
    }
}

class TunnelAppsFragment : Fragment(R.layout.fragment_tunnel_apps) {
    private val vm: TunnelViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val loading = view.findViewById<TextView>(R.id.tv_tunnel_apps_loading)
        val adapter = AppsAdapter(vm) { row, mode -> chooseMode(row, mode) }
        view.findViewById<RecyclerView>(R.id.rv_tunnel_apps).apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = adapter
        }
        view.findViewById<EditText>(R.id.et_tunnel_app_search).doAfterTextChanged { adapter.filter(it?.toString().orEmpty()) }
        vm.apps.observe(viewLifecycleOwner) {
            loading.visibility = View.GONE
            adapter.setApps(it)
        }
        vm.appRules.observe(viewLifecycleOwner) { list -> adapter.setModes(list.associate { it.packageName to it.mode }) }
        vm.appCounts.observe(viewLifecycleOwner) { adapter.setCounts(it) }
        if (vm.apps.value == null) vm.loadApps()
    }

    private fun chooseMode(row: AppRow, current: String?) {
        val modes = arrayOf<String?>(null, TunnelAppRuleEntity.MODE_BYPASS, TunnelAppRuleEntity.MODE_BLOCK)
        val labels = arrayOf(getString(R.string.tunnel_mode_default), getString(R.string.tunnel_mode_bypass), getString(R.string.tunnel_mode_block))
        AlertDialog.Builder(requireContext())
            .setTitle(row.label)
            .setSingleChoiceItems(labels, modes.indexOf(current).coerceAtLeast(0)) { d, which ->
                vm.setAppMode(row.packageName, modes[which])
                d.dismiss()
            }
            .setMessage(null)
            .show()
    }

    private class AppsAdapter(
        private val vm: TunnelViewModel,
        private val onClick: (AppRow, String?) -> Unit
    ) : RecyclerView.Adapter<AppsAdapter.VH>() {
        private var all: List<AppRow> = emptyList()
        private var shown: List<AppRow> = emptyList()
        private var modes: Map<String, String> = emptyMap()
        private var counts: Map<String, Int> = emptyMap()
        private var query = ""

        fun setApps(a: List<AppRow>) { all = a; apply() }
        fun setModes(m: Map<String, String>) { modes = m; notifyDataSetChanged() }
        fun setCounts(c: Map<String, Int>) { counts = c; notifyDataSetChanged() }
        fun filter(q: String) { query = q.trim().lowercase(); apply() }

        private fun apply() {
            shown = if (query.isEmpty()) all else all.filter { query in it.label.lowercase() || query in it.packageName }
            // Apps with a rule first, so the important ones are easy to find.
            shown = shown.sortedBy { if (modes.containsKey(it.packageName)) 0 else 1 }
            notifyDataSetChanged()
        }

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val icon: ImageView = v.findViewById(R.id.iv_tunnel_app_icon)
            val label: TextView = v.findViewById(R.id.tv_tunnel_app_label)
            val sub: TextView = v.findViewById(R.id.tv_tunnel_app_sub)
            val mode: TextView = v.findViewById(R.id.tv_tunnel_app_mode)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_tunnel_app, parent, false))

        override fun getItemCount() = shown.size

        override fun onBindViewHolder(h: VH, position: Int) {
            val row = shown[position]
            val ctx = h.itemView.context
            h.icon.setImageDrawable(vm.icon(row.packageName))
            h.label.text = row.label
            val c = counts[row.packageName]
            h.sub.text = if (c != null) row.packageName + " · " + ctx.getString(R.string.tunnel_app_lookups, c) else row.packageName
            val mode = modes[row.packageName]
            h.mode.text = ctx.getString(
                when (mode) {
                    TunnelAppRuleEntity.MODE_BYPASS -> R.string.tunnel_mode_bypass
                    TunnelAppRuleEntity.MODE_BLOCK -> R.string.tunnel_mode_block
                    else -> R.string.tunnel_mode_default
                }
            )
            h.mode.setTextColor(ctx.getColor(if (mode == TunnelAppRuleEntity.MODE_BLOCK) R.color.accent_red else R.color.text_secondary))
            h.itemView.setOnClickListener { onClick(row, mode) }
        }
    }
}

class TunnelRulesFragment : Fragment(R.layout.fragment_tunnel_rules) {
    private val vm: TunnelViewModel by activityViewModels()

    private val pickList = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        vm.importList(uri) { r ->
            val ctx = context ?: return@importList
            r.fold(
                onSuccess = { Toast.makeText(ctx, ctx.getString(R.string.tunnel_imported, it), Toast.LENGTH_LONG).show() },
                onFailure = { Toast.makeText(ctx, ctx.getString(R.string.tunnel_import_failed, it.message ?: it.javaClass.simpleName), Toast.LENGTH_LONG).show() }
            )
        }
    }

    private fun listLabel(l: BundledList): String = getString(
        when (l) {
            BundledList.PERMISSIVE -> R.string.tunnel_list_permissive
            BundledList.STEVENBLACK -> R.string.tunnel_list_stevenblack
        }
    )

    private fun chooseBundledList() {
        val choices = BundledList.choices
        val current = choices.indexOf(vm.prefs.bundledChoice)
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.tunnel_list_choose)
            .setSingleChoiceItems(choices.map { listLabel(it) }.toTypedArray(), current) { d, which ->
                vm.chooseBundled(choices[which])
                view?.findViewById<SwitchMaterial>(R.id.sw_tunnel_bundled)?.isChecked = true
                d.dismiss()
            }
            .setNeutralButton(R.string.tunnel_licences) { _, _ -> showLicences() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showLicences() {
        val pad = (16 * resources.displayMetrics.density).toInt()
        val text = TextView(requireContext()).apply {
            text = vm.licences()
            textSize = 12f
            setTextIsSelectable(true)
            setPadding(pad, pad, pad, pad)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.tunnel_licences)
            .setView(android.widget.ScrollView(requireContext()).apply { addView(text) })
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val bundledTitle = view.findViewById<TextView>(R.id.tv_tunnel_bundled_title)
        val bundledSw = view.findViewById<SwitchMaterial>(R.id.sw_tunnel_bundled)
        val zero = view.findViewById<SwitchMaterial>(R.id.sw_tunnel_zero_ip)
        bundledSw.isChecked = vm.prefs.bundledListEnabled
        bundledSw.setOnCheckedChangeListener { _, on -> vm.setBundled(on) }
        zero.isChecked = vm.prefs.blockMode == BlockMode.ZERO_IP
        zero.setOnCheckedChangeListener { _, on -> vm.setZeroIp(on) }
        view.findViewById<View>(R.id.row_tunnel_bundled).setOnClickListener { chooseBundledList() }
        vm.bundledCount.observe(viewLifecycleOwner) { n ->
            val list = vm.prefs.bundledChoice
            bundledTitle.text = getString(
                R.string.tunnel_bundled_title_named, listLabel(list),
                n?.let { NumberFormat.getIntegerInstance().format(it) } ?: "…"
            )
        }
        if (vm.bundledCount.value == null) vm.loadBundledCount()
        view.findViewById<Button>(R.id.btn_tunnel_licences).setOnClickListener { showLicences() }

        view.findViewById<Button>(R.id.btn_tunnel_add_rule).setOnClickListener { addRuleDialog(this, vm, "") }
        view.findViewById<Button>(R.id.btn_tunnel_import).setOnClickListener {
            try {
                pickList.launch(arrayOf("text/*", "application/octet-stream"))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(requireContext(), R.string.tunnel_no_picker, Toast.LENGTH_SHORT).show()
            }
        }

        val adapter = RulesAdapter(
            onRuleLong = { r -> confirm(r.pattern) { vm.deleteRule(r.id) } },
            onListToggle = { l, on -> vm.setListEnabled(l.id, on) },
            onListLong = { l -> confirm(l.name) { vm.deleteList(l) } }
        )
        view.findViewById<RecyclerView>(R.id.rv_tunnel_rules).apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = adapter
        }
        vm.domainRules.observe(viewLifecycleOwner) { adapter.rules = it; adapter.notifyDataSetChanged() }
        vm.lists.observe(viewLifecycleOwner) { adapter.lists = it; adapter.notifyDataSetChanged() }
    }

    private fun confirm(name: String, action: () -> Unit) {
        AlertDialog.Builder(requireContext())
            .setMessage(getString(R.string.tunnel_delete_q, name))
            .setPositiveButton(R.string.delete) { _, _ -> action() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private class RulesAdapter(
        val onRuleLong: (TunnelDomainRuleEntity) -> Unit,
        val onListToggle: (TunnelListEntity, Boolean) -> Unit,
        val onListLong: (TunnelListEntity) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        var rules: List<TunnelDomainRuleEntity> = emptyList()
        var lists: List<TunnelListEntity> = emptyList()

        // rows: header, rules (or empty note), header, lists
        private fun rows(): List<Any> = buildList {
            add(R.string.tunnel_rules_header)
            if (rules.isEmpty()) add(EMPTY) else addAll(rules)
            if (lists.isNotEmpty()) { add(R.string.tunnel_lists_header); addAll(lists) }
        }

        override fun getItemCount() = rows().size
        override fun getItemViewType(position: Int) = if (rows()[position] is Int || rows()[position] === EMPTY) 0 else 1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val v = if (viewType == 0) TextView(parent.context).apply {
                setPadding(24, 24, 24, 8)
                setTextColor(parent.context.getColor(R.color.text_secondary))
            } else LayoutInflater.from(parent.context).inflate(R.layout.item_tunnel_row, parent, false)
            return object : RecyclerView.ViewHolder(v) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = rows()[position]
            val v = holder.itemView
            val ctx = v.context
            when (item) {
                is Int -> (v as TextView).setText(item)
                EMPTY -> (v as TextView).setText(R.string.tunnel_rules_empty)
                is TunnelDomainRuleEntity -> {
                    v.findViewById<TextView>(R.id.tv_row_title).text = item.pattern
                    v.findViewById<TextView>(R.id.tv_row_sub).text = ctx.getString(
                        if (item.action == TunnelDomainRuleEntity.ACTION_ALLOW) R.string.tunnel_rule_allow else R.string.tunnel_rule_block
                    )
                    v.findViewById<View>(R.id.sw_row).visibility = View.GONE
                    v.setOnLongClickListener { onRuleLong(item); true }
                }
                is TunnelListEntity -> {
                    v.findViewById<TextView>(R.id.tv_row_title).text = item.name
                    v.findViewById<TextView>(R.id.tv_row_sub).text = ctx.getString(R.string.tunnel_list_item, ctx.getString(R.string.tunnel_rule_block), item.entryCount)
                    val sw = v.findViewById<SwitchMaterial>(R.id.sw_row)
                    sw.visibility = View.VISIBLE
                    sw.setOnCheckedChangeListener(null)
                    sw.isChecked = item.enabled
                    sw.setOnCheckedChangeListener { _, on -> onListToggle(item, on) }
                    v.setOnLongClickListener { onListLong(item); true }
                }
            }
        }

        companion object {
            private val EMPTY = Any()
        }
    }
}

/** Shared "add rule" dialog (Rules tab, and long-press in the Log). */
internal fun addRuleDialog(f: Fragment, vm: TunnelViewModel, prefill: String) {
    val ctx = f.requireContext()
    val pad = (16 * ctx.resources.displayMetrics.density).toInt()
    val input = EditText(ctx).apply { hint = ctx.getString(R.string.tunnel_rule_hint); setText(prefill); isSingleLine = true }
    val group = RadioGroup(ctx).apply {
        addView(RadioButton(ctx).apply { id = View.generateViewId(); text = ctx.getString(R.string.tunnel_rule_block); isChecked = true })
        addView(RadioButton(ctx).apply { id = View.generateViewId(); text = ctx.getString(R.string.tunnel_rule_allow) })
    }
    val box = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(pad, pad / 2, pad, 0)
        addView(input); addView(group)
    }
    AlertDialog.Builder(ctx)
        .setTitle(R.string.tunnel_add_rule)
        .setView(box)
        .setPositiveButton(R.string.save_transaction) { _, _ ->
            val allow = group.indexOfChild(group.findViewById(group.checkedRadioButtonId)) == 1
            val ok = vm.putRule(input.text.toString(), if (allow) TunnelDomainRuleEntity.ACTION_ALLOW else TunnelDomainRuleEntity.ACTION_BLOCK)
            Toast.makeText(ctx, if (ok) R.string.tunnel_rule_added else R.string.tunnel_rule_invalid, Toast.LENGTH_SHORT).show()
        }
        .setNegativeButton(R.string.cancel, null)
        .show()
}

class TunnelLogFragment : Fragment(R.layout.fragment_tunnel_log) {
    private val vm: TunnelViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val empty = view.findViewById<TextView>(R.id.tv_tunnel_log_empty)
        val adapter = LogAdapter { showActions(it) }
        view.findViewById<RecyclerView>(R.id.rv_tunnel_log).apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = adapter
        }
        view.findViewById<SwitchMaterial>(R.id.sw_tunnel_blocked_only).setOnCheckedChangeListener { _, on -> vm.setBlockedOnly(on) }
        view.findViewById<EditText>(R.id.et_tunnel_log_search).doAfterTextChanged { vm.setLogQuery(it?.toString().orEmpty()) }
        view.findViewById<Button>(R.id.btn_tunnel_export).setOnClickListener { chooseExport() }
        view.findViewById<Button>(R.id.btn_tunnel_clear).setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setMessage(R.string.tunnel_log_clear_q)
                .setPositiveButton(R.string.tunnel_log_clear) { _, _ -> vm.clearLog() }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
        vm.log.observe(viewLifecycleOwner) {
            adapter.items = it
            adapter.notifyDataSetChanged()
            empty.visibility = if (it.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun showActions(row: DnsLogEntity) {
        val parent = row.name.substringAfter('.', "").takeIf { it.contains('.') }
        val labels = buildList {
            add(getString(R.string.tunnel_log_block_exact, row.name))
            if (parent != null) add(getString(R.string.tunnel_log_block_suffix, parent))
            add(getString(R.string.tunnel_log_allow, row.name))
            add(getString(R.string.tunnel_log_copy))
        }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(row.name)
            .setItems(labels) { _, which ->
                val label = labels[which]
                val ctx = requireContext()
                when {
                    label == getString(R.string.tunnel_log_block_exact, row.name) -> rule(row.name, TunnelDomainRuleEntity.ACTION_BLOCK)
                    parent != null && label == getString(R.string.tunnel_log_block_suffix, parent) -> rule("*.$parent", TunnelDomainRuleEntity.ACTION_BLOCK)
                    label == getString(R.string.tunnel_log_allow, row.name) -> rule(row.name, TunnelDomainRuleEntity.ACTION_ALLOW)
                    else -> ctx.getSystemService(ClipboardManager::class.java)
                        .setPrimaryClip(ClipData.newPlainText("domain", row.name))
                }
            }
            .show()
    }

    private fun rule(pattern: String, action: String) {
        val ok = vm.putRule(pattern, action)
        Toast.makeText(requireContext(), if (ok) R.string.tunnel_rule_added else R.string.tunnel_rule_invalid, Toast.LENGTH_SHORT).show()
    }

    private fun chooseExport() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.tunnel_log_export)
            .setItems(arrayOf(getString(R.string.tunnel_log_export_csv), getString(R.string.tunnel_log_export_md))) { _, which ->
                export(markdown = which == 1)
            }
            .show()
    }

    private fun export(markdown: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            val file = vm.exportLog(markdown)
            if (file == null) {
                Toast.makeText(requireContext(), R.string.tunnel_log_empty, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = if (markdown) "text/markdown" else "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, file.name)
                clipData = ClipData.newRawUri(file.name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                startActivity(Intent.createChooser(send, getString(R.string.tunnel_log_export)))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(requireContext(), R.string.tunnel_no_picker, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private class LogAdapter(private val onLong: (DnsLogEntity) -> Unit) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        var items: List<DnsLogEntity> = emptyList()
        private val tf = DateFormat.getTimeInstance(DateFormat.MEDIUM)
        override fun getItemCount() = items.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
            object : RecyclerView.ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_tunnel_row, parent, false)) {}

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val r = items[position]
            val v = holder.itemView
            val ctx = v.context
            val title = v.findViewById<TextView>(R.id.tv_row_title)
            title.text = r.name
            val blocked = r.decision.startsWith("BLOCKED")
            title.setTextColor(ctx.getColor(if (blocked) R.color.accent_red else R.color.text_primary))
            val decision = when (r.decision) {
                "ALLOWED" -> ctx.getString(R.string.tunnel_decision_ALLOWED)
                "ALLOWED_RULE" -> ctx.getString(R.string.tunnel_decision_ALLOWED_RULE)
                "PAUSED" -> ctx.getString(R.string.tunnel_decision_PAUSED)
                "BLOCKED_APP" -> ctx.getString(R.string.tunnel_decision_BLOCKED_APP)
                "BLOCKED_DOMAIN" -> ctx.getString(R.string.tunnel_decision_BLOCKED_DOMAIN)
                "SERVFAIL" -> ctx.getString(R.string.tunnel_decision_SERVFAIL)
                else -> r.decision
            }
            v.findViewById<TextView>(R.id.tv_row_sub).text =
                listOf(tf.format(Date(r.ts)), r.pkg ?: ctx.getString(R.string.tunnel_log_unknown_app), decision).joinToString(" · ")
            v.setOnLongClickListener { onLong(r); true }
            v.setOnClickListener { onLong(r) }
        }
    }
}
