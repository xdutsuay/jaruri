package com.example.moneymanager.wellbeing

import android.content.Context
import com.example.moneymanager.data.AppDatabase
import com.example.moneymanager.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.Calendar

class UsageRepository(context: Context) {
    private val appContext = context.applicationContext
    private val dao = AppDatabase.getDatabase(appContext).usageDailyDao()
    private val settings = SettingsRepository(appContext)
    private val reader = UsageStatsReader(appContext)

    fun observeRange(startDay: String, endDay: String): Flow<List<UsageDailyEntity>> =
        dao.observeRange(startDay, endDay)

    suspend fun categoryOverrides(): Map<String, String> =
        UsageCategoryMapper.decodeOverrides(settings.usageCategoryOverrides.first())

    suspend fun setCategoryOverride(packageName: String, category: String) {
        val current = categoryOverrides().toMutableMap()
        current[packageName.trim()] = UsageCategoryMapper.normalizeCategory(category)
        settings.setUsageCategoryOverrides(UsageCategoryMapper.encodeOverrides(current))
    }

    /**
     * Refresh last [days] of daily aggregates from the system and prune older rows.
     * No-op when feature disabled or usage access missing.
     * @return rows written (0 if empty / skipped)
     */
    suspend fun refreshLastDays(days: Int = 7): Int = withContext(Dispatchers.IO) {
        if (!settings.usageWellbeingEnabled.first()) return@withContext 0
        if (!UsagePermission.hasUsageAccess(appContext)) return@withContext 0

        val endExclusive = System.currentTimeMillis() + 1
        val start = UsageStatsReader.startOfLocalDayOffset((days - 1).coerceAtLeast(0))
        val slices = reader.queryDailyForeground(start, endExclusive)
        val aggregates = UsageAggregator.aggregateToDailyMinutes(slices)
        if (aggregates.isNotEmpty()) {
            dao.upsertAll(aggregates)
        }
        val pruneBefore = UsageStatsReader.formatDay(
            Calendar.getInstance().apply {
                add(Calendar.DAY_OF_MONTH, -RETENTION_DAYS)
            }.timeInMillis
        )
        dao.deleteBefore(pruneBefore)
        aggregates.size
    }

    companion object {
        const val RETENTION_DAYS = 90
    }
}
