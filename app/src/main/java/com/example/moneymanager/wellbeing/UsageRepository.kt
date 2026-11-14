package com.example.moneymanager.wellbeing

import android.content.Context
import com.example.moneymanager.data.AppDatabase
import com.example.moneymanager.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class UsageRepository(context: Context) {
    private val appContext = context.applicationContext
    private val dao = AppDatabase.getDatabase(appContext).usageDailyDao()
    private val settings = SettingsRepository(appContext)
    private val reader = UsageStatsReader(appContext)

    fun observeRange(startDay: String, endDay: String): Flow<List<UsageDailyEntity>> =
        dao.observeRange(startDay, endDay)

    fun observeEarliestDay(): Flow<String?> = dao.observeEarliestDay()

    suspend fun categoryOverrides(): Map<String, String> =
        UsageCategoryMapper.decodeOverrides(settings.usageCategoryOverrides.first())

    suspend fun setCategoryOverride(packageName: String, category: String) {
        val current = categoryOverrides().toMutableMap()
        current[packageName.trim()] = UsageCategoryMapper.normalizeCategory(category)
        settings.setUsageCategoryOverrides(UsageCategoryMapper.encodeOverrides(current))
    }

    /**
     * Refresh the last [days] of daily aggregates from the system UsageStats API.
     * Rows are upserted into [usage_daily] and **never pruned** — historical days stay
     * forever so multi-year trends remain available after the OS drops old stats.
     * No-op when feature disabled or usage access missing.
     * @return rows written (0 if empty / skipped)
     */
    suspend fun refreshLastDays(days: Int = 14): Int = withContext(Dispatchers.IO) {
        if (!settings.usageWellbeingEnabled.first()) return@withContext 0
        if (!UsagePermission.hasUsageAccess(appContext)) return@withContext 0

        val endExclusive = System.currentTimeMillis() + 1
        val start = UsageStatsReader.startOfLocalDayOffset((days - 1).coerceAtLeast(0))
        val slices = reader.queryDailyForeground(start, endExclusive)
        val aggregates = UsageAggregator.aggregateToDailyMinutes(slices)
        if (aggregates.isNotEmpty()) {
            dao.upsertAll(aggregates)
        }
        aggregates.size
    }

    companion object {
        /** OS UsageStats windows are short; we only re-query this many recent days. */
        const val SYSTEM_REFRESH_DAYS = 14
    }
}
