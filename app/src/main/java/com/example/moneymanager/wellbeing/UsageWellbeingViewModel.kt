package com.example.moneymanager.wellbeing

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.moneymanager.data.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalCoroutinesApi::class)
class UsageWellbeingViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = UsageRepository(application)
    private val settings = SettingsRepository(application)

    private val _refreshState = MutableLiveData<RefreshState>(RefreshState.Idle)
    val refreshState: LiveData<RefreshState> = _refreshState

    val featureEnabled: LiveData<Boolean> = settings.usageWellbeingEnabled.asLiveData()

    private val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getDefault()
    }

    enum class HistoryRange(val daySpan: Int?) {
        WEEK(7),
        MONTH(30),
        YEAR(365),
        ALL(null)
    }

    private val selectedRange = MutableStateFlow(HistoryRange.WEEK)

    fun setHistoryRange(range: HistoryRange) {
        selectedRange.value = range
    }

    val todaySummary: LiveData<UiSummary> = combine(
        settings.usageWellbeingEnabled,
        settings.usageCategoryOverrides,
        selectedRange,
        repo.observeEarliestDay()
    ) { enabled, overridesRaw, range, earliest ->
        SummaryParams(
            enabled = enabled,
            overrides = UsageCategoryMapper.decodeOverrides(overridesRaw),
            range = range,
            earliest = earliest
        )
    }.flatMapLatest { params ->
        if (!params.enabled) {
            flowOf(UiSummary.empty(enabled = false))
        } else {
            val today = dayFmt.format(System.currentTimeMillis())
            val startDay = when (val span = params.range.daySpan) {
                null -> params.earliest?.takeIf { it.isNotBlank() } ?: today
                else -> dayFmt.format(
                    Calendar.getInstance().apply {
                        add(Calendar.DAY_OF_MONTH, -(span - 1))
                    }.time
                )
            }
            val observeStart =
                if (params.range == HistoryRange.ALL &&
                    params.earliest != null &&
                    params.earliest < startDay
                ) {
                    params.earliest
                } else {
                    startDay
                }
            repo.observeRange(observeStart, today).map { rangeRows ->
                val todayRows = rangeRows.filter { it.day == today }
                val categoryOf: (String) -> String = { pkg ->
                    UsageCategoryMapper.categoryFor(pkg, params.overrides)
                }
                val appsSource = todayRows.ifEmpty { rangeRows }
                val todayMins = UsageAggregator.totalMinutes(todayRows)
                val rangeMins = UsageAggregator.totalMinutes(rangeRows)
                val daysPresent = rangeRows.map { it.day }.toSet().size.coerceAtLeast(1)
                val top = UsageAggregator.topApps(appsSource, limit = 10).map { (pkg, mins) ->
                    AppMinutes(pkg, mins, categoryOf(pkg))
                }
                val dayTotals = when (params.range) {
                    HistoryRange.YEAR, HistoryRange.ALL ->
                        UsageAggregator.monthlyTotals(rangeRows)
                    else -> UsageAggregator.dailyTotals(rangeRows)
                }
                UiSummary(
                    enabled = true,
                    todayMinutes = todayMins,
                    rangeMinutes = rangeMins,
                    avgDayMinutes = rangeMins / daysPresent,
                    dayTotals = dayTotals,
                    topApps = top,
                    categories = UsageAggregator.categoryTotals(appsSource, categoryOf),
                    usingWeekFallback = todayRows.isEmpty() && rangeRows.isNotEmpty(),
                    rowCount = rangeRows.size,
                    historyRange = params.range,
                    earliestDay = params.earliest,
                    daysStored = daysPresent,
                    barsAreMonths = params.range == HistoryRange.YEAR ||
                        params.range == HistoryRange.ALL
                )
            }
        }
    }.asLiveData()

    fun refreshOnOpen() {
        viewModelScope.launch {
            if (!settings.usageWellbeingEnabled.first()) {
                _refreshState.value = RefreshState.Idle
                return@launch
            }
            if (!UsagePermission.hasUsageAccess(getApplication())) {
                _refreshState.value = RefreshState.NeedsPermission
                return@launch
            }
            _refreshState.value = RefreshState.Refreshing
            val written = try {
                repo.refreshLastDays(UsageRepository.SYSTEM_REFRESH_DAYS)
            } catch (_: Exception) {
                0
            }
            _refreshState.value = if (written == 0) {
                RefreshState.Empty
            } else {
                RefreshState.Done(written)
            }
        }
    }

    fun setFeatureEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settings.setUsageWellbeingEnabled(enabled)
            if (enabled) refreshOnOpen()
        }
    }

    fun setCategoryOverride(packageName: String, category: String) {
        viewModelScope.launch {
            repo.setCategoryOverride(packageName, category)
        }
    }

    private data class SummaryParams(
        val enabled: Boolean,
        val overrides: Map<String, String>,
        val range: HistoryRange,
        val earliest: String?
    )

    data class AppMinutes(
        val packageName: String,
        val minutes: Int,
        val category: String
    )

    data class UiSummary(
        val enabled: Boolean,
        val todayMinutes: Int,
        val rangeMinutes: Int,
        val avgDayMinutes: Int,
        val dayTotals: List<Pair<String, Int>>,
        val topApps: List<AppMinutes>,
        val categories: List<Pair<String, Int>>,
        val usingWeekFallback: Boolean,
        val rowCount: Int,
        val historyRange: HistoryRange = HistoryRange.WEEK,
        val earliestDay: String? = null,
        val daysStored: Int = 0,
        val barsAreMonths: Boolean = false
    ) {
        companion object {
            fun empty(enabled: Boolean) = UiSummary(
                enabled = enabled,
                todayMinutes = 0,
                rangeMinutes = 0,
                avgDayMinutes = 0,
                dayTotals = emptyList(),
                topApps = emptyList(),
                categories = emptyList(),
                usingWeekFallback = false,
                rowCount = 0
            )
        }
    }

    sealed class RefreshState {
        data object Idle : RefreshState()
        data object Refreshing : RefreshState()
        data object NeedsPermission : RefreshState()
        data object Empty : RefreshState()
        data class Done(val rows: Int) : RefreshState()
    }
}
