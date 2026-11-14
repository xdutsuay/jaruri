package com.example.moneymanager.wellbeing

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.moneymanager.R
import com.example.moneymanager.viewmodel.TimeViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class UsageWellbeingFragment : Fragment(R.layout.fragment_usage_wellbeing) {

    private val viewModel: UsageWellbeingViewModel by viewModels()
    private lateinit var adapter: AppAdapter
    private val dayLabelFmt = SimpleDateFormat("EEE", Locale.getDefault())
    private val dayKeyFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val monthLabelFmt = SimpleDateFormat("MMM", Locale.getDefault())
    private val monthParseFmt = SimpleDateFormat("yyyy-MM", Locale.US)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val permissionBanner = view.findViewById<LinearLayout>(R.id.usage_permission_banner)
        val disabledBanner = view.findViewById<LinearLayout>(R.id.usage_disabled_banner)
        val content = view.findViewById<LinearLayout>(R.id.usage_content)
        val tvToday = view.findViewById<TextView>(R.id.tv_usage_today)
        val tvWeek = view.findViewById<TextView>(R.id.tv_usage_week)
        val tvAvg = view.findViewById<TextView>(R.id.tv_usage_avg)
        val tvStatus = view.findViewById<TextView>(R.id.tv_usage_status)
        val tvRangeLabel = view.findViewById<TextView>(R.id.tv_usage_range_label)
        val tvHistorySpan = view.findViewById<TextView>(R.id.tv_usage_history_span)
        val tvBarsTitle = view.findViewById<TextView>(R.id.tv_usage_bars_title)
        val weekBars = view.findViewById<LinearLayout>(R.id.usage_week_bars)
        val categoryList = view.findViewById<LinearLayout>(R.id.usage_category_list)
        val categoriesEmpty = view.findViewById<TextView>(R.id.tv_usage_categories_empty)
        val rv = view.findViewById<RecyclerView>(R.id.rv_usage_apps)
        val rangeToggle = view.findViewById<MaterialButtonToggleGroup>(R.id.usage_range_toggle)

        adapter = AppAdapter(
            labelOf = { pkg -> appLabel(pkg) },
            onLongClick = { pkg -> showCategoryPicker(pkg) }
        )
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        rangeToggle.check(R.id.btn_usage_range_week)
        rangeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val range = when (checkedId) {
                R.id.btn_usage_range_month -> UsageWellbeingViewModel.HistoryRange.MONTH
                R.id.btn_usage_range_year -> UsageWellbeingViewModel.HistoryRange.YEAR
                R.id.btn_usage_range_all -> UsageWellbeingViewModel.HistoryRange.ALL
                else -> UsageWellbeingViewModel.HistoryRange.WEEK
            }
            viewModel.setHistoryRange(range)
        }

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
            tvWeek.text = TimeViewModel.formatDuration(summary.rangeMinutes)
            tvAvg.text = TimeViewModel.formatDuration(summary.avgDayMinutes)
            tvRangeLabel.setText(
                when (summary.historyRange) {
                    UsageWellbeingViewModel.HistoryRange.WEEK -> R.string.usage_week
                    UsageWellbeingViewModel.HistoryRange.MONTH -> R.string.usage_range_month_label
                    UsageWellbeingViewModel.HistoryRange.YEAR -> R.string.usage_range_year_label
                    UsageWellbeingViewModel.HistoryRange.ALL -> R.string.usage_range_all_label
                }
            )
            tvBarsTitle.setText(
                when (summary.historyRange) {
                    UsageWellbeingViewModel.HistoryRange.WEEK -> R.string.usage_week_bars
                    UsageWellbeingViewModel.HistoryRange.MONTH -> R.string.usage_month_bars
                    UsageWellbeingViewModel.HistoryRange.YEAR -> R.string.usage_year_bars
                    UsageWellbeingViewModel.HistoryRange.ALL -> R.string.usage_all_bars
                }
            )
            tvHistorySpan.text = if (!summary.earliestDay.isNullOrBlank()) {
                getString(R.string.usage_history_span, summary.earliestDay)
            } else {
                getString(R.string.usage_history_span_empty)
            }
            bindHistoryBars(weekBars, summary.dayTotals, summary.historyRange, summary.barsAreMonths)
            bindCategories(categoryList, categoriesEmpty, summary.categories)
            val appMax = summary.topApps.maxOfOrNull { it.minutes }?.coerceAtLeast(1) ?: 1
            adapter.submit(summary.topApps, appMax)
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

    private fun bindHistoryBars(
        container: LinearLayout,
        dayTotals: List<Pair<String, Int>>,
        range: UsageWellbeingViewModel.HistoryRange,
        barsAreMonths: Boolean
    ) {
        container.removeAllViews()
        val density = resources.displayMetrics.density
        val accent = ContextCompat.getColor(requireContext(), R.color.accent_blue)
        val muted = ContextCompat.getColor(requireContext(), R.color.outline_soft)
        val ink = ContextCompat.getColor(requireContext(), R.color.text_secondary)

        val series: List<Pair<String, Int>> = when {
            barsAreMonths -> dayTotals
            range == UsageWellbeingViewModel.HistoryRange.MONTH -> {
                val byDay = dayTotals.toMap()
                (29 downTo 0).map { offset ->
                    val key = dayKeyFmt.format(
                        Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -offset) }.time
                    )
                    key to (byDay[key] ?: 0)
                }
            }
            else -> {
                val byDay = dayTotals.toMap()
                (6 downTo 0).map { offset ->
                    val key = dayKeyFmt.format(
                        Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -offset) }.time
                    )
                    key to (byDay[key] ?: 0)
                }
            }
        }

        if (series.isEmpty()) return
        val max = series.maxOf { it.second }.coerceAtLeast(1)
        val barWidthDp = when {
            barsAreMonths -> 14f
            range == UsageWellbeingViewModel.HistoryRange.MONTH -> 6f
            else -> 10f
        }

        for ((key, mins) in series) {
            val col = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            }
            val barHeight = ((mins.toFloat() / max) * 72f * density).toInt().coerceAtLeast(
                if (mins > 0) (4 * density).toInt() else (2 * density).toInt()
            )
            val bar = View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams((barWidthDp * density).toInt(), barHeight)
                setBackgroundColor(if (mins > 0) accent else muted)
            }
            val label = TextView(requireContext()).apply {
                text = when {
                    barsAreMonths -> {
                        try {
                            monthLabelFmt.format(monthParseFmt.parse(key)!!).take(1)
                        } catch (_: Exception) {
                            key.takeLast(2)
                        }
                    }
                    range == UsageWellbeingViewModel.HistoryRange.MONTH -> {
                        try {
                            dayKeyFmt.parse(key)?.let {
                                Calendar.getInstance().apply { time = it }.get(Calendar.DAY_OF_MONTH)
                                    .toString()
                            } ?: ""
                        } catch (_: Exception) {
                            ""
                        }
                    }
                    else -> {
                        try {
                            dayLabelFmt.format(dayKeyFmt.parse(key)!!).take(1)
                        } catch (_: Exception) {
                            ""
                        }
                    }
                }
                textSize = if (range == UsageWellbeingViewModel.HistoryRange.MONTH) 8f else 11f
                setTextColor(ink)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).also { it.topMargin = (4 * density).toInt() }
            }
            col.addView(bar)
            col.addView(label)
            container.addView(col)
        }
    }

    private fun bindCategories(
        container: LinearLayout,
        empty: TextView,
        categories: List<Pair<String, Int>>
    ) {
        container.removeAllViews()
        if (categories.isEmpty()) {
            empty.visibility = View.VISIBLE
            container.visibility = View.GONE
            return
        }
        empty.visibility = View.GONE
        container.visibility = View.VISIBLE
        val max = categories.maxOf { it.second }.coerceAtLeast(1)
        val inflater = LayoutInflater.from(requireContext())
        for ((name, mins) in categories) {
            val row = inflater.inflate(R.layout.item_usage_category, container, false)
            row.findViewById<TextView>(R.id.tvUsageCatName).text = name
            row.findViewById<TextView>(R.id.tvUsageCatDuration).text =
                TimeViewModel.formatDuration(mins)
            row.findViewById<ProgressBar>(R.id.progressUsageCat).progress =
                ((mins * 100f) / max).toInt().coerceIn(1, 100)
            container.addView(row)
        }
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
        private var baselineMinutes: Int = 1

        fun submit(list: List<UsageWellbeingViewModel.AppMinutes>, baseline: Int) {
            items = list
            baselineMinutes = baseline.coerceAtLeast(1)
            notifyDataSetChanged()
        }

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.tvUsageAppName)
            val duration: TextView = v.findViewById(R.id.tvUsageAppDuration)
            val category: TextView = v.findViewById(R.id.tvUsageAppCategory)
            val progress: ProgressBar = v.findViewById(R.id.progressUsageApp)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_usage_app, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.name.text = labelOf(item.packageName)
            holder.duration.text = TimeViewModel.formatDuration(item.minutes)
            holder.category.text = item.category
            holder.progress.progress =
                ((item.minutes * 100f) / baselineMinutes).toInt().coerceIn(1, 100)
            holder.itemView.setOnLongClickListener {
                onLongClick(item.packageName)
                true
            }
        }

        override fun getItemCount(): Int = items.size
    }
}
