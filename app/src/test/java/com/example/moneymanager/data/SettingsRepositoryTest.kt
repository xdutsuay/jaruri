package com.example.moneymanager.data

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingsRepositoryTest {

    private lateinit var settingsRepository: SettingsRepository

    @Before
    fun setup() {
        settingsRepository = SettingsRepository(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun testCurrencySymbolPersistence() = runBlocking {
        // Default
        assertEquals("₹", settingsRepository.currencySymbol.first())

        // Save new
        settingsRepository.setCurrencySymbol("€")
        assertEquals("€", settingsRepository.currencySymbol.first())
    }

    @Test
    fun testDateFormatPersistence() = runBlocking {
        assertEquals("yyyy-MM-dd", settingsRepository.dateFormat.first())

        settingsRepository.setDateFormat("dd/MM/yyyy")
        assertEquals("dd/MM/yyyy", settingsRepository.dateFormat.first())
    }

    @Test
    fun testSampleDataPersistence() = runBlocking {
        assertEquals(true, settingsRepository.sampleDataEnabled.first())

        settingsRepository.setSampleDataEnabled(false)
        assertEquals(false, settingsRepository.sampleDataEnabled.first())
    }

    @Test
    fun testAutoImportSmsDefaultsOffAndPersists() = runBlocking {
        assertEquals(false, settingsRepository.autoImportSmsEnabled.first())

        settingsRepository.setAutoImportSmsEnabled(true)
        assertEquals(true, settingsRepository.autoImportSmsEnabled.first())

        settingsRepository.setAutoImportSmsEnabled(false)
        assertEquals(false, settingsRepository.autoImportSmsEnabled.first())
    }
}
