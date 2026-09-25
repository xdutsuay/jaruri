package com.example.moneymanager.utils

import android.content.Context
import android.util.Log
import com.example.moneymanager.data.AppDatabase
import com.example.moneymanager.data.SettingsRepository
import com.example.moneymanager.viewmodel.MainViewModel
import kotlinx.coroutines.flow.first

/**
 * One-shot: merge the Realme phone Money Manager export into the active ledger.
 */
object PhoneMergeBootstrap {

    private const val TAG = "PhoneMergeBootstrap"
    private const val ASSET = "realme_phone_export.csv"

    suspend fun runOnce(context: Context) {
        val app = context.applicationContext
        val settings = SettingsRepository(app)
        if (settings.realmeMergeDone.first()) return

        val text = try {
            app.assets.open(ASSET).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.w(TAG, "No asset $ASSET", e)
            settings.setRealmeMergeDone(true)
            return
        }

        val rows = LegacySpreadsheetParser.parse(text)
        if (rows.isEmpty()) {
            settings.setRealmeMergeDone(true)
            return
        }

        val dao = AppDatabase.getDatabase(app).transactionDao()
        val existing = dao.getAllActiveList()
        val keys = existing.map { MainViewModel.fingerprint(it) }.toMutableSet()
        var added = 0
        var skipped = 0
        for (row in rows) {
            val clean = row.copy(id = 0, deletedAt = null)
            val fp = MainViewModel.fingerprint(clean)
            if (fp in keys) {
                skipped++
                continue
            }
            dao.insertTransaction(clean)
            keys += fp
            added++
        }
        settings.setRealmeMergeDone(true)
        Log.i(TAG, "RESULT added=$added skipped=$skipped")
    }
}
