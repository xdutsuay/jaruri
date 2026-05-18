package com.example.moneymanager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthNavigationTest {

    @Test
    fun signOutNavOptionsClearsGraphBackStack() {
        val graphId = 123

        val navOptions = AuthNavigation.signOutNavOptions(graphId)

        assertEquals(graphId, navOptions.popUpToId)
        assertTrue(navOptions.isPopUpToInclusive)
        assertTrue(navOptions.shouldLaunchSingleTop())
    }
}
