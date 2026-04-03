package com.example.moneymanager.ui

import android.os.Looper
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.example.moneymanager.R
import com.example.moneymanager.data.CategoryRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CategoriesFragmentTest {

    private lateinit var categoryRepository: CategoryRepository

    @Before
    fun setup() = runBlocking {
        categoryRepository = CategoryRepository(ApplicationProvider.getApplicationContext())
        categoryRepository.resetToDefaults()
    }

    @Test
    fun testVisibleCategoryListRefreshesAfterAdd() {
        val scenario = launchFragmentInContainer<CategoriesFragment>(
            themeResId = R.style.Theme_MoneyManager
        )

        runBlocking {
            categoryRepository.addCategory("Utilities", CategoryRepository.TYPE_EXPENSE)
        }
        shadowOf(Looper.getMainLooper()).idle()

        scenario.onFragment { fragment ->
            val recyclerView = fragment.requireView().findViewById<RecyclerView>(R.id.rv_categories)
            assertEquals(
                CategoryRepository.DEFAULT_EXPENSE_CATEGORY_NAMES.size + 1,
                recyclerView.adapter?.itemCount
            )
        }
    }
}
