package com.example.moneymanager.data

import androidx.test.core.app.ApplicationProvider
import com.example.moneymanager.models.Category
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CategoryRepositoryTest {

    private lateinit var categoryRepository: CategoryRepository

    @Before
    fun setup() = runBlocking {
        categoryRepository = CategoryRepository(ApplicationProvider.getApplicationContext())
        categoryRepository.resetToDefaults()
    }

    @Test
    fun testDefaultCategoriesLoaded() = runBlocking {
        val expenseCategoryNames = categoryRepository.expenseCategories.first().map { it.name }
        val incomeCategoryNames = categoryRepository.incomeCategories.first().map { it.name }

        assertTrue(expenseCategoryNames.contains("Food"))
        assertTrue(expenseCategoryNames.contains("Clothing"))
        assertTrue(incomeCategoryNames.contains("Salary"))
    }

    @Test
    fun testAddedCategoryPersists() = runBlocking {
        categoryRepository.addCategory("Utilities", CategoryRepository.TYPE_EXPENSE)

        val expenseCategoryNames = categoryRepository.expenseCategories.first().map { it.name }

        assertTrue(expenseCategoryNames.contains("Utilities"))
    }

    @Test
    fun testDeletedCategoryIsRemoved() = runBlocking {
        categoryRepository.addCategory("Temporary", CategoryRepository.TYPE_INCOME)
        categoryRepository.deleteCategory(Category("Temporary", CategoryRepository.TYPE_INCOME))

        val incomeCategoryNames = categoryRepository.incomeCategories.first().map { it.name }

        assertFalse(incomeCategoryNames.contains("Temporary"))
    }
}
