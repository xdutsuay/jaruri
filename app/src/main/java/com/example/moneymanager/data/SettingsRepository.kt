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
    }

    val currencySymbol: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[CURRENCY_SYMBOL] ?: "$"
    }

    val dateFormat: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[DATE_FORMAT] ?: "yyyy-MM-dd"
    }

    val theme: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[THEME] ?: "system"
    }

    val sampleDataEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SAMPLE_DATA] ?: true
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
}
