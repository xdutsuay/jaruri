package com.example.moneymanager.utils

import android.content.Context
import android.util.Log
import com.example.moneymanager.data.AppDatabase
import com.example.moneymanager.data.SettingsRepository
import kotlinx.coroutines.flow.first

/**
 * One-shot: wipe active ledger and load the Money Manager export shipped in assets.
 */
object LegacyExportBootstrap {

    private const val TAG = "LegacyExportBootstrap"
    private const val ASSET = "legacy_money_manager_export.csv"

    suspend fun runOnce(context: Context) {
        val app = context.applicationContext
        val settings = SettingsRepository(app)
        if (settings.legacyExportLoaded.first()) return

        val text = try {
            app.assets.open(ASSET).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.w(TAG, "No asset $ASSET", e)
            settings.setLegacyExportLoaded(true)
            return
        }

        val rows = LegacySpreadsheetParser.parse(text)
        if (rows.isEmpty()) {
            Log.w(TAG, "Parsed 0 rows from $ASSET")
            settings.setLegacyExportLoaded(true)
            return
        }

        val dao = AppDatabase.getDatabase(app).transactionDao()
        dao.deleteAllActive()
        dao.insertAll(rows.map { it.copy(id = 0, deletedAt = null) })
        settings.setLegacyExportLoaded(true)
        Log.i(TAG, "RESULT imported=${rows.size}")
    }
}
