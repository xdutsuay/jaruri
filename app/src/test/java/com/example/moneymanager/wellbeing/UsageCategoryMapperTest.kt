package com.example.moneymanager.wellbeing

import org.junit.Assert.assertEquals
import org.junit.Test

class UsageCategoryMapperTest {

    @Test
    fun defaults_and_heuristics() {
        assertEquals(
            UsageCategoryMapper.SOCIAL,
            UsageCategoryMapper.categoryFor("com.instagram.android")
        )
        assertEquals(
            UsageCategoryMapper.WORK,
            UsageCategoryMapper.categoryFor("com.microsoft.teams")
        )
        assertEquals(
            UsageCategoryMapper.ENTERTAINMENT,
            UsageCategoryMapper.categoryFor("com.google.android.youtube")
        )
        assertEquals(
            UsageCategoryMapper.SOCIAL,
            UsageCategoryMapper.categoryFor("com.example.tiktok.lite")
        )
        assertEquals(
            UsageCategoryMapper.WORK,
            UsageCategoryMapper.categoryFor("com.corp.slack.client")
        )
        assertEquals(
            UsageCategoryMapper.OTHER,
            UsageCategoryMapper.categoryFor("com.completely.unknown.pkg")
        )
    }

    @Test
    fun overrides_win_over_defaults() {
        val overrides = mapOf("com.instagram.android" to "Work")
        assertEquals(
            UsageCategoryMapper.WORK,
            UsageCategoryMapper.categoryFor("com.instagram.android", overrides)
        )
    }

    @Test
    fun normalizeCategory_unknownBecomesOther() {
        assertEquals(UsageCategoryMapper.OTHER, UsageCategoryMapper.normalizeCategory(null))
        assertEquals(UsageCategoryMapper.OTHER, UsageCategoryMapper.normalizeCategory("Games"))
        assertEquals(UsageCategoryMapper.SOCIAL, UsageCategoryMapper.normalizeCategory("social"))
    }

    @Test
    fun encodeDecode_roundTrip() {
        val map = mapOf(
            "com.a" to UsageCategoryMapper.SOCIAL,
            "com.b" to UsageCategoryMapper.WORK
        )
        val encoded = UsageCategoryMapper.encodeOverrides(map)
        assertEquals(map, UsageCategoryMapper.decodeOverrides(encoded))
        assertEquals(emptyMap<String, String>(), UsageCategoryMapper.decodeOverrides(""))
        assertEquals(emptyMap<String, String>(), UsageCategoryMapper.decodeOverrides(null))
    }
}
