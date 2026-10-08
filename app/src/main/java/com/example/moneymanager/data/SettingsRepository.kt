package com.example.moneymanager.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    companion object {
        val CURRENCY_SYMBOL = stringPreferencesKey("currency_symbol")
        val DATE_FORMAT = stringPreferencesKey("date_format")
        val THEME = stringPreferencesKey("theme")
        val SAMPLE_DATA = booleanPreferencesKey("sample_data")
        val AUTO_IMPORT_SMS = booleanPreferencesKey("auto_import_sms")
        val DEMO_HISTORY_SEEDED = booleanPreferencesKey("demo_history_seeded")
        val SMS_CATEGORY_BACKFILL_V2 = booleanPreferencesKey("sms_category_backfill_v2")
        val SMS_LEDGER_CLEANUP_V3 = booleanPreferencesKey("sms_ledger_cleanup_v3")
        val SMS_BALANCE_REPLAY_V1 = booleanPreferencesKey("sms_balance_replay_v1")
        val LEGACY_EXPORT_LOADED_V1 = booleanPreferencesKey("legacy_export_loaded_v1")
        val REALME_MERGE_V1 = booleanPreferencesKey("realme_phone_merge_v1")
        val USAGE_WELLBEING_ENABLED = booleanPreferencesKey("usage_wellbeing_enabled")
        val USAGE_CATEGORY_OVERRIDES = stringPreferencesKey("usage_category_overrides")
        val FISCAL_YEAR_MODE = stringPreferencesKey("fiscal_year_mode")
        val SMS_HISTORY_BACKFILL_CURSOR = longPreferencesKey("sms_history_backfill_cursor")
        val SMS_HISTORY_MAX_SCAN = intPreferencesKey("sms_history_max_scan")

        const val FY_CALENDAR = "CALENDAR"
        const val FY_APR_MAR = "APR_MAR"
        const val DEFAULT_SMS_HISTORY_MAX_SCAN = 8000

        /** Display label → symbol stored in prefs / shown on dashboard. */
        val CURRENCY_OPTIONS = listOf(
            "₹ Indian Rupee" to "₹",
            "$ US Dollar" to "$",
            "€ Euro" to "€",
            "£ British Pound" to "£",
            "¥ Japanese Yen" to "¥",
            "AED Dirham" to "AED",
            "₩ Won" to "₩"
        )
    }

    val currencySymbol: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[CURRENCY_SYMBOL] ?: "₹"
    }

    val dateFormat: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[DATE_FORMAT] ?: "yyyy-MM-dd"
    }

    val theme: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[THEME] ?: "system"
    }

    /**
     * Legacy pref — kept so old installs don't break DataStore reads.
     * Demo seeding is removed from the app; default is always off.
     */
    val sampleDataEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SAMPLE_DATA] ?: false
    }

    val demoHistorySeeded: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[DEMO_HISTORY_SEEDED] ?: false
    }

    /** Default OFF — user must opt in before SMS_RECEIVED auto-adds transactions. */
    val autoImportSmsEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[AUTO_IMPORT_SMS] ?: false
    }

    val smsCategoryBackfillDone: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SMS_CATEGORY_BACKFILL_V2] ?: false
    }

    val smsLedgerCleanupV3Done: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SMS_LEDGER_CLEANUP_V3] ?: false
    }

    val smsBalanceReplayV1Done: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SMS_BALANCE_REPLAY_V1] ?: false
    }

    val legacyExportLoaded: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[LEGACY_EXPORT_LOADED_V1] ?: false
    }

    val realmeMergeDone: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[REALME_MERGE_V1] ?: false
    }

    /** Opt-in screen-time aggregates from UsageStatsManager. Default off. */
    val usageWellbeingEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[USAGE_WELLBEING_ENABLED] ?: false
    }

    /** Encoded package→category overrides for usage wellbeing. */
    val usageCategoryOverrides: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[USAGE_CATEGORY_OVERRIDES] ?: ""
    }

    /** CALENDAR or APR_MAR (Indian financial year). */
    val fiscalYearMode: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[FISCAL_YEAR_MODE] ?: FY_CALENDAR
    }

    /** Epoch millis cursor for historical SMS backfill resume (0 = not started). */
    val smsHistoryBackfillCursor: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[SMS_HISTORY_BACKFILL_CURSOR] ?: 0L
    }

    val smsHistoryMaxScan: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[SMS_HISTORY_MAX_SCAN] ?: DEFAULT_SMS_HISTORY_MAX_SCAN
    }

    suspend fun setCurrencySymbol(symbol: String) {
        context.dataStore.edit { preferences ->
            preferences[CURRENCY_SYMBOL] = symbol
        }
    }

    suspend fun setDateFormat(format: String) {
        context.dataStore.edit { preferences ->
            preferences[DATE_FORMAT] = format
        }
    }

    suspend fun setTheme(themeValue: String) {
        context.dataStore.edit { preferences ->
            preferences[THEME] = themeValue
        }
    }

    suspend fun setSampleDataEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SAMPLE_DATA] = enabled
        }
    }

    suspend fun setDemoHistorySeeded(seeded: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[DEMO_HISTORY_SEEDED] = seeded
        }
    }

    suspend fun setAutoImportSmsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_IMPORT_SMS] = enabled
        }
    }

    suspend fun setSmsCategoryBackfillDone(done: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SMS_CATEGORY_BACKFILL_V2] = done
        }
    }

    suspend fun setSmsLedgerCleanupV3Done(done: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SMS_LEDGER_CLEANUP_V3] = done
        }
    }

    suspend fun setSmsBalanceReplayV1Done(done: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SMS_BALANCE_REPLAY_V1] = done
        }
    }

    suspend fun setLegacyExportLoaded(done: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[LEGACY_EXPORT_LOADED_V1] = done
        }
    }

    suspend fun setRealmeMergeDone(done: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[REALME_MERGE_V1] = done
        }
    }

    suspend fun setUsageWellbeingEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[USAGE_WELLBEING_ENABLED] = enabled
        }
    }

    suspend fun setUsageCategoryOverrides(encoded: String) {
        context.dataStore.edit { preferences ->
            preferences[USAGE_CATEGORY_OVERRIDES] = encoded
        }
    }

    suspend fun setFiscalYearMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[FISCAL_YEAR_MODE] =
                if (mode == FY_APR_MAR) FY_APR_MAR else FY_CALENDAR
        }
    }

    suspend fun setSmsHistoryBackfillCursor(cursorMillis: Long) {
        context.dataStore.edit { preferences ->
            preferences[SMS_HISTORY_BACKFILL_CURSOR] = cursorMillis
        }
    }

    suspend fun setSmsHistoryMaxScan(maxScan: Int) {
        context.dataStore.edit { preferences ->
            preferences[SMS_HISTORY_MAX_SCAN] = maxScan.coerceIn(500, 50_000)
        }
    }
}
