package com.example.moneymanager.ui

import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.navigation.Navigation
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import com.example.moneymanager.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HomeFragmentTest {

    @Test
    fun testNavigateToAddTransaction() {
        // Create a TestNavHostController
        val navController = TestNavHostController(
            ApplicationProvider.getApplicationContext()
        )

        // Launch the fragment in a container
        val scenario = launchFragmentInContainer<HomeFragment>(themeResId = R.style.Theme_MoneyManager)

        scenario.onFragment {
            // Set the graph on the NavController
            navController.setGraph(R.navigation.nav_graph)

            // Set the NavController on the fragment
            Navigation.setViewNavController(it.requireView(), navController)
        }

        // Click the FAB
        onView(withId(R.id.fab_add)).perform(click())

        // Verify that we've navigated to the AddTransactionFragment
        assertEquals(R.id.addTransactionFragment, navController.currentDestination?.id)
    }
    @Test
    fun testNavigateToChartOnIncomeClick() {
        val navController = TestNavHostController(
            ApplicationProvider.getApplicationContext()
        )
        val scenario = launchFragmentInContainer<HomeFragment>(themeResId = R.style.Theme_MoneyManager)

        scenario.onFragment {
            navController.setGraph(R.navigation.nav_graph)
            Navigation.setViewNavController(it.requireView(), navController)
        }

        onView(withId(R.id.income_layout)).perform(click())
        assertEquals(R.id.nav_chart, navController.currentDestination?.id)
    }

    @Test
    fun testNavigateToChartOnExpenseClick() {
        val navController = TestNavHostController(
            ApplicationProvider.getApplicationContext()
        )
        val scenario = launchFragmentInContainer<HomeFragment>(themeResId = R.style.Theme_MoneyManager)

        scenario.onFragment {
            navController.setGraph(R.navigation.nav_graph)
            Navigation.setViewNavController(it.requireView(), navController)
        }

        onView(withId(R.id.expense_layout)).perform(click())
        assertEquals(R.id.nav_chart, navController.currentDestination?.id)
    }
}
