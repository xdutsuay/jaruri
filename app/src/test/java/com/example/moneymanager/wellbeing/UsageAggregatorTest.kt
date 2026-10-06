package com.example.moneymanager.wellbeing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageAggregatorTest {

    @Test
    fun aggregateToDailyMinutes_sumsSameDayPackageAndDropsSubMinute() {
        val slices = listOf(
            UsageAggregator.ForegroundSlice("2026-10-06", "com.a", 90_000L),
            UsageAggregator.ForegroundSlice("2026-10-06", "com.a", 30_000L),
            UsageAggregator.ForegroundSlice("2026-10-06", "com.b", 59_999L),
            UsageAggregator.ForegroundSlice("2026-10-05", "com.a", 120_000L)
        )
        val rows = UsageAggregator.aggregateToDailyMinutes(slices)
        assertEquals(2, rows.size)
        assertEquals(
            UsageDailyEntity("2026-10-06", "com.a", 2),
            rows.first { it.day == "2026-10-06" && it.packageName == "com.a" }
        )
        assertEquals(
            UsageDailyEntity("2026-10-05", "com.a", 2),
            rows.first { it.day == "2026-10-05" }
        )
        assertTrue(rows.none { it.packageName == "com.b" })
    }

    @Test
    fun aggregateToDailyMinutes_emptyAndInvalid() {
        assertTrue(UsageAggregator.aggregateToDailyMinutes(emptyList()).isEmpty())
        assertTrue(
            UsageAggregator.aggregateToDailyMinutes(
                listOf(
                    UsageAggregator.ForegroundSlice("", "com.a", 60_000L),
                    UsageAggregator.ForegroundSlice("2026-10-06", "", 60_000L),
                    UsageAggregator.ForegroundSlice("2026-10-06", "com.a", 0L)
                )
            ).isEmpty()
        )
    }

    @Test
    fun topApps_and_totals() {
        val rows = listOf(
            UsageDailyEntity("2026-10-06", "com.a", 10),
            UsageDailyEntity("2026-10-05", "com.a", 5),
            UsageDailyEntity("2026-10-06", "com.b", 20),
            UsageDailyEntity("2026-10-06", "com.c", 1)
        )
        assertEquals(36, UsageAggregator.totalMinutes(rows))
        assertEquals(
            listOf("com.b" to 20, "com.a" to 15),
            UsageAggregator.topApps(rows, limit = 2)
        )
    }

    @Test
    fun categoryTotals_groupsByMapper() {
        val rows = listOf(
            UsageDailyEntity("2026-10-06", "com.instagram.android", 30),
            UsageDailyEntity("2026-10-06", "com.netflix.mediaclient", 40),
            UsageDailyEntity("2026-10-06", "com.unknown.app", 10)
        )
        val totals = UsageAggregator.categoryTotals(rows) { pkg ->
            UsageCategoryMapper.categoryFor(pkg)
        }
        assertEquals(
            listOf(
                UsageCategoryMapper.ENTERTAINMENT to 40,
                UsageCategoryMapper.SOCIAL to 30,
                UsageCategoryMapper.OTHER to 10
            ),
            totals
        )
    }
}
