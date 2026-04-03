package com.example.moneymanager.ui

import android.os.Looper
import android.widget.RadioGroup
import android.widget.Spinner
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.core.app.ApplicationProvider
import com.example.moneymanager.R
import com.example.moneymanager.data.CategoryRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AddTransactionFragmentTest {

    private lateinit var categoryRepository: CategoryRepository

    @Before
    fun setup() = runBlocking {
        categoryRepository = CategoryRepository(ApplicationProvider.getApplicationContext())
        categoryRepository.resetToDefaults()
    }

    @Test
    fun testSpinnerUpdatesWhenTransactionTypeChanges() {
        val scenario = launchFragmentInContainer<AddTransactionFragment>(
            themeResId = R.style.Theme_MoneyManager
        )

        shadowOf(Looper.getMainLooper()).idle()

        scenario.onFragment { fragment ->
            val spinner = fragment.requireView().findViewById<Spinner>(R.id.spCategory)
            val radioGroup = fragment.requireView().findViewById<RadioGroup>(R.id.rgType)

            assertEquals("Food", spinner.adapter.getItem(0))

            radioGroup.check(R.id.rbIncome)
            shadowOf(Looper.getMainLooper()).idle()

            assertEquals("Salary", spinner.adapter.getItem(0))
        }
    }

    @Test
    fun testNewExpenseCategoryAppearsWhileFragmentIsVisible() {
        val scenario = launchFragmentInContainer<AddTransactionFragment>(
            themeResId = R.style.Theme_MoneyManager
        )

        runBlocking {
            categoryRepository.addCategory("Utilities", CategoryRepository.TYPE_EXPENSE)
        }
        shadowOf(Looper.getMainLooper()).idle()

        scenario.onFragment { fragment ->
            val spinner = fragment.requireView().findViewById<Spinner>(R.id.spCategory)
            val spinnerItems = (0 until spinner.adapter.count).map {
                spinner.adapter.getItem(it).toString()
            }

            assertTrue(spinnerItems.contains("Utilities"))
        }
    }
}
