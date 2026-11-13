package com.example.moneymanager.viewmodel

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.moneymanager.data.CategoryRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Regression: the Home "categorise" picker showed 5 options or 18 depending on whether another
 * screen had observed MainViewModel.expenseCategories first (asLiveData has no value until
 * observed, and the picker then used a hard-coded 5-item fallback).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CategoryListConsistencyTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun unobservedLiveDataIsEmpty_butCategoryNamesIsAlwaysTheFullList() = runBlocking {
        // DataStore is process-wide in tests, so compare with the repository, not literals.
        val repo = CategoryRepository(app)
        val unique = "Fuel-" + System.nanoTime()
        repo.addCategory(unique, CategoryRepository.TYPE_EXPENSE)
        val vm = MainViewModel(app)
        assertNull("this is why reading .value was wrong", vm.expenseCategories.value)
        val expense = vm.categoryNames("EXPENSE")
        assertEquals(repo.expenseCategories.first().map { it.name }, expense)
        assertTrue(expense.containsAll(CategoryRepository.DEFAULT_EXPENSE_CATEGORY_NAMES) && unique in expense)
        assertTrue("more than the old 5-item fallback", expense.size > 5)
        assertEquals(repo.incomeCategories.first().map { it.name }, vm.categoryNames("INCOME"))
    }

    @Test
    fun noScreenHardCodesItsOwnCategoryList() {
        val offenders = File("src/main/java/com/example/moneymanager").walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != "CategoryRepository.kt" }
            .filter { f -> Regex("""listOf\(\s*"Others"\s*,\s*"Food"""").containsMatchIn(f.readText()) }
            .map { it.name }.toList()
        assertTrue("hard-coded category lists in $offenders", offenders.isEmpty())
    }
}
