package com.example.moneymanager.wellbeing

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.moneymanager.data.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    val todaySummary: LiveData<UiSummary> = combine(
        settings.usageWellbeingEnabled,
        settings.usageCategoryOverrides
    ) { enabled, overridesRaw ->
        enabled to UsageCategoryMapper.decodeOverrides(overridesRaw)
    }.flatMapLatest { (enabled, overrides) ->
        if (!enabled) {
            flowOf(UiSummary.empty(enabled = false))
        } else {
            val today = dayFmt.format(System.currentTimeMillis())
            val weekStart = dayFmt.format(
                Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_MONTH, -6)
                }.time
            )
            repo.observeRange(weekStart, today).map { weekRows ->
                val todayRows = weekRows.filter { it.day == today }
                val categoryOf: (String) -> String = { pkg ->
                    UsageCategoryMapper.categoryFor(pkg, overrides)
                }
                val appsSource = todayRows.ifEmpty { weekRows }
                val top = UsageAggregator.topApps(appsSource, limit = 10).map { (pkg, mins) ->
                    AppMinutes(pkg, mins, categoryOf(pkg))
                }
                UiSummary(
                    enabled = true,
                    todayMinutes = UsageAggregator.totalMinutes(todayRows),
                    weekMinutes = UsageAggregator.totalMinutes(weekRows),
                    topApps = top,
                    categories = UsageAggregator.categoryTotals(appsSource, categoryOf),
                    usingWeekFallback = todayRows.isEmpty() && weekRows.isNotEmpty(),
                    rowCount = weekRows.size
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
                repo.refreshLastDays(7)
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

    data class AppMinutes(
        val packageName: String,
        val minutes: Int,
        val category: String
    )

    data class UiSummary(
        val enabled: Boolean,
        val todayMinutes: Int,
        val weekMinutes: Int,
        val topApps: List<AppMinutes>,
        val categories: List<Pair<String, Int>>,
        val usingWeekFallback: Boolean,
        val rowCount: Int
    ) {
        companion object {
            fun empty(enabled: Boolean) = UiSummary(
                enabled = enabled,
                todayMinutes = 0,
                weekMinutes = 0,
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
