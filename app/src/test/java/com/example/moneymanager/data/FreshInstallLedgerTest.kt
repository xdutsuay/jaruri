package com.example.moneymanager.data

import android.app.Application
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.moneymanager.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * A fresh install must not ship or auto-load a personal ledger.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FreshInstallLedgerTest {

    @Test
    fun inMemoryDatabaseStartsWithZeroTransactions() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals(0, db.transactionDao().getCount())
        } finally {
            db.close()
        }
    }

    @Test
    fun personalExportAssetsAreNotBundled() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val names = context.assets.list("")?.toSet().orEmpty()
        assertFalse("legacy export must not ship in the APK", names.contains("legacy_money_manager_export.csv"))
        assertFalse("phone export must not ship in the APK", names.contains("realme_phone_export.csv"))
    }

    @Test
    fun firstLaunchDoesNotInsertTransactions() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        MainViewModel(app)
        val settings = SettingsRepository(app)
        withTimeout(20_000) {
            while (!settings.smsLedgerCleanupV3Done.first()) {
                shadowOf(Looper.getMainLooper()).idle()
                delay(20)
            }
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, AppDatabase.getDatabase(app).transactionDao().getCount())
    }
}
