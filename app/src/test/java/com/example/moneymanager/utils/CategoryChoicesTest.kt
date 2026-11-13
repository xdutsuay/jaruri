package com.example.moneymanager.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryChoicesTest {
    @Test
    fun usesTheUserListDeduplicated() {
        assertEquals(listOf("Food", "Bills"), CategoryChoices.forPicker(listOf("Food", " food ", "Bills", "")))
    }

    @Test
    fun keepsTheRowsOwnCategoryWhenItIsNotListed() {
        assertEquals(listOf("Food", "Bills", "Fuel"), CategoryChoices.forPicker(listOf("Food", "Bills"), "Fuel"))
        assertEquals(listOf("Food", "Bills"), CategoryChoices.forPicker(listOf("Food", "Bills"), "bills"))
        assertEquals(listOf("Food"), CategoryChoices.forPicker(listOf("Food"), " "))
    }

    @Test
    fun indexIgnoresCase() {
        assertEquals(1, CategoryChoices.indexOf(listOf("Food", "Bills"), "BILLS"))
        assertEquals(-1, CategoryChoices.indexOf(listOf("Food"), null))
    }
}
