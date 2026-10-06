package com.example.moneymanager.wellbeing

import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.moneymanager.R
import com.example.moneymanager.viewmodel.TimeViewModel
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.google.android.material.button.MaterialButton

class UsageWellbeingFragment : Fragment(R.layout.fragment_usage_wellbeing) {

    private val viewModel: UsageWellbeingViewModel by viewModels()
    private lateinit var adapter: AppAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val permissionBanner = view.findViewById<LinearLayout>(R.id.usage_permission_banner)
        val disabledBanner = view.findViewById<LinearLayout>(R.id.usage_disabled_banner)
        val content = view.findViewById<LinearLayout>(R.id.usage_content)
        val tvToday = view.findViewById<TextView>(R.id.tv_usage_today)
        val tvWeek = view.findViewById<TextView>(R.id.tv_usage_week)
        val tvStatus = view.findViewById<TextView>(R.id.tv_usage_status)
        val chart = view.findViewById<PieChart>(R.id.chart_usage_categories)
        val categoriesEmpty = view.findViewById<TextView>(R.id.tv_usage_categories_empty)
        val rv = view.findViewById<RecyclerView>(R.id.rv_usage_apps)

        setupChart(chart)
        adapter = AppAdapter(
            labelOf = { pkg -> appLabel(pkg) },
            onLongClick = { pkg -> showCategoryPicker(pkg) }
        )
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        view.findViewById<MaterialButton>(R.id.btn_usage_grant).setOnClickListener {
            startActivity(UsagePermission.usageAccessSettingsIntent())
        }
        view.findViewById<MaterialButton>(R.id.btn_usage_enable).setOnClickListener {
            viewModel.setFeatureEnabled(true)
            Toast.makeText(requireContext(), R.string.usage_enabled_toast, Toast.LENGTH_SHORT).show()
        }

        viewModel.featureEnabled.observe(viewLifecycleOwner) { enabled ->
            disabledBanner.visibility = if (enabled) View.GONE else View.VISIBLE
            content.visibility = if (enabled) View.VISIBLE else View.GONE
            if (enabled) {
                updatePermissionBanner(permissionBanner)
            } else {
                permissionBanner.visibility = View.GONE
            }
        }

        viewModel.todaySummary.observe(viewLifecycleOwner) { summary ->
            if (!summary.enabled) return@observe
            tvToday.text = TimeViewModel.formatDuration(summary.todayMinutes)
            tvWeek.text = TimeViewModel.formatDuration(summary.weekMinutes)
            bindChart(chart, categoriesEmpty, summary.categories)
            adapter.submit(summary.topApps)
            if (summary.usingWeekFallback) {
                tvStatus.text = getString(R.string.usage_week_fallback)
            }
        }

        viewModel.refreshState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is UsageWellbeingViewModel.RefreshState.Refreshing ->
                    tvStatus.text = getString(R.string.usage_refreshing)
                is UsageWellbeingViewModel.RefreshState.NeedsPermission -> {
                    updatePermissionBanner(permissionBanner)
                    tvStatus.text = getString(R.string.usage_permission_needed)
                }
                is UsageWellbeingViewModel.RefreshState.Empty ->
                    tvStatus.text = getString(R.string.usage_no_data)
                is UsageWellbeingViewModel.RefreshState.Done ->
                    tvStatus.text = getString(R.string.usage_refreshed)
                else -> Unit
            }
        }
    }

    override fun onResume() {
        super.onResume()
        view?.findViewById<LinearLayout>(R.id.usage_permission_banner)?.let {
            if (viewModel.featureEnabled.value == true) updatePermissionBanner(it)
        }
        viewModel.refreshOnOpen()
    }

    private fun updatePermissionBanner(banner: LinearLayout) {
        val granted = UsagePermission.hasUsageAccess(requireContext())
        banner.visibility = if (granted) View.GONE else View.VISIBLE
    }

    private fun setupChart(chart: PieChart) {
        chart.description.isEnabled = false
        chart.legend.isEnabled = true
        chart.setUsePercentValues(false)
        chart.setEntryLabelColor(Color.BLACK)
        chart.setEntryLabelTextSize(11f)
        chart.setHoleColor(Color.WHITE)
        chart.setNoDataText(getString(R.string.usage_no_data))
    }

    private fun bindChart(
        chart: PieChart,
        empty: TextView,
        categories: List<Pair<String, Int>>
    ) {
        if (categories.isEmpty()) {
            chart.clear()
            chart.visibility = View.GONE
            empty.visibility = View.VISIBLE
            return
        }
        empty.visibility = View.GONE
        chart.visibility = View.VISIBLE
        val entries = categories.map { PieEntry(it.second.toFloat(), it.first) }
        val dataSet = PieDataSet(entries, "").apply {
            colors = listOf(
                Color.parseColor("#00695C"),
                Color.parseColor("#0B3D91"),
                Color.parseColor("#FF8F00"),
                Color.parseColor("#757575")
            )
            valueTextSize = 11f
            valueTextColor = Color.BLACK
        }
        chart.data = PieData(dataSet)
        chart.invalidate()
    }

    private fun showCategoryPicker(packageName: String) {
        val cats = UsageCategoryMapper.ALL_CATEGORIES.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.usage_set_category, appLabel(packageName)))
            .setAdapter(
                ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, cats)
            ) { _, which ->
                viewModel.setCategoryOverride(packageName, cats[which])
                Toast.makeText(
                    requireContext(),
                    getString(R.string.usage_category_saved, cats[which]),
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun appLabel(packageName: String): String {
        return try {
            val pm = requireContext().packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName.substringAfterLast('.').ifBlank { packageName }
        }
    }

    private class AppAdapter(
        private val labelOf: (String) -> String,
        private val onLongClick: (String) -> Unit
    ) : RecyclerView.Adapter<AppAdapter.VH>() {
        private var items: List<UsageWellbeingViewModel.AppMinutes> = emptyList()

        fun submit(list: List<UsageWellbeingViewModel.AppMinutes>) {
            items = list
            notifyDataSetChanged()
        }

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val label: TextView = v.findViewById(R.id.tvTimeLabel)
            val duration: TextView = v.findViewById(R.id.tvTimeDuration)
            val sub: TextView = v.findViewById(R.id.tvTimeSub)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
            val v = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_time_entry, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.label.text = labelOf(item.packageName)
            holder.duration.text = TimeViewModel.formatDuration(item.minutes)
            holder.sub.text = "${item.category} · ${item.packageName}"
            holder.itemView.setOnLongClickListener {
                onLongClick(item.packageName)
                true
            }
        }

        override fun getItemCount(): Int = items.size
    }
}
