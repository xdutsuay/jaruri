package com.example.moneymanager.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.moneymanager.models.Category
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CategoryRepository(private val context: Context) {

    companion object {
        private val EXPENSE_CATEGORIES = stringPreferencesKey("expense_categories")
        private val INCOME_CATEGORIES = stringPreferencesKey("income_categories")
        private const val CATEGORY_SEPARATOR = "\n"

        const val TYPE_EXPENSE = "expense"
        const val TYPE_INCOME = "income"

        val DEFAULT_EXPENSE_CATEGORY_NAMES = listOf(
            "Food",
            "Bills",
            "Transportation",
            "Home",
            "Car",
            "Entertainment",
            "Health",
            "Clothing"
        )

        val DEFAULT_INCOME_CATEGORY_NAMES = listOf(
            "Salary",
            "Business",
            "Gift"
        )
    }

    val expenseCategories: Flow<List<Category>> = context.dataStore.data.map { preferences ->
        decodeCategoryNames(
            preferences[EXPENSE_CATEGORIES],
            DEFAULT_EXPENSE_CATEGORY_NAMES
        ).map { Category(it, TYPE_EXPENSE) }
    }

    val incomeCategories: Flow<List<Category>> = context.dataStore.data.map { preferences ->
        decodeCategoryNames(
            preferences[INCOME_CATEGORIES],
            DEFAULT_INCOME_CATEGORY_NAMES
        ).map { Category(it, TYPE_INCOME) }
    }

    suspend fun addCategory(name: String, type: String) {
        val sanitizedName = name.trim()
        if (sanitizedName.isBlank()) return

        context.dataStore.edit { preferences ->
            val key = categoryKey(type)
            val currentCategories = decodeCategoryNames(
                preferences[key],
                defaultCategoryNames(type)
            )

            if (currentCategories.none { it.equals(sanitizedName, ignoreCase = true) }) {
                preferences[key] = encodeCategoryNames(currentCategories + sanitizedName)
            }
        }
    }

    suspend fun deleteCategory(category: Category) {
        context.dataStore.edit { preferences ->
            val key = categoryKey(category.type)
            val currentCategories = decodeCategoryNames(
                preferences[key],
                defaultCategoryNames(category.type)
            )

            preferences[key] = encodeCategoryNames(
                currentCategories.filterNot { it.equals(category.name, ignoreCase = true) }
            )
        }
    }

    suspend fun resetToDefaults() {
        context.dataStore.edit { preferences ->
            preferences.remove(EXPENSE_CATEGORIES)
            preferences.remove(INCOME_CATEGORIES)
        }
    }

    private fun categoryKey(type: String) = when (type.lowercase()) {
        TYPE_EXPENSE -> EXPENSE_CATEGORIES
        TYPE_INCOME -> INCOME_CATEGORIES
        else -> throw IllegalArgumentException("Unsupported category type: $type")
    }

    private fun defaultCategoryNames(type: String): List<String> = when (type.lowercase()) {
        TYPE_EXPENSE -> DEFAULT_EXPENSE_CATEGORY_NAMES
        TYPE_INCOME -> DEFAULT_INCOME_CATEGORY_NAMES
        else -> emptyList()
    }

    private fun decodeCategoryNames(
        storedValue: String?,
        defaultValues: List<String>
    ): List<String> = when {
        storedValue == null -> defaultValues
        storedValue.isBlank() -> emptyList()
        else -> sanitizeCategoryNames(storedValue.split(CATEGORY_SEPARATOR))
    }

    private fun encodeCategoryNames(categoryNames: List<String>): String {
        return sanitizeCategoryNames(categoryNames).joinToString(CATEGORY_SEPARATOR)
    }

    private fun sanitizeCategoryNames(categoryNames: List<String>): List<String> {
        val seenNames = mutableSetOf<String>()
        return categoryNames
            .map { it.replace(Regex("[\\r\\n]+"), " ").trim() }
            .filter { it.isNotEmpty() }
            .filter { seenNames.add(it.lowercase()) }
    }
}
