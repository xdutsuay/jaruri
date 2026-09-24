package com.example.moneymanager.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
     * When true, empty DB may be filled with demo history once.
     * Default ON so first install is easy to verify; existing rows are never wiped.
     */
    val sampleDataEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SAMPLE_DATA] ?: true
    }

    val demoHistorySeeded: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[DEMO_HISTORY_SEEDED] ?: false
    }

    /** Default OFF — user must opt in before SMS_RECEIVED auto-adds transactions. */
    val autoImportSmsEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[AUTO_IMPORT_SMS] ?: false
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
}
