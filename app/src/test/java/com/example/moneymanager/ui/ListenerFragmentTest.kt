package com.example.moneymanager.ui

import android.os.Looper
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.navigation.Navigation
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import com.example.moneymanager.R
import com.google.android.material.button.MaterialButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ListenerFragmentTest {

    @Test
    fun hubListenerCardOpensListener() {
        val navController = TestNavHostController(ApplicationProvider.getApplicationContext())
        val scenario = launchFragmentInContainer<HubFragment>(themeResId = R.style.Theme_MoneyManager)
        scenario.onFragment {
            navController.setGraph(R.navigation.nav_graph)
            navController.setCurrentDestination(R.id.nav_hub)
            Navigation.setViewNavController(it.requireView(), navController)
            // 4th card sits below the fold on Robolectric's small screen; click directly.
            it.requireView().findViewById<android.view.View>(R.id.card_listener).performClick()
        }
        assertEquals(R.id.nav_listener, navController.currentDestination?.id)
    }

    @Test
    fun idleScreenShowsStartAndFirstStartAsksForConsent() {
        val scenario = launchFragmentInContainer<ListenerFragment>(themeResId = R.style.Theme_MoneyManager)
        scenario.onFragment {
            val btn = it.requireView().findViewById<MaterialButton>(R.id.btn_listener_toggle)
            assertEquals(it.getString(R.string.listener_start), btn.text.toString())
            btn.performClick()
        }
        // Consent flag is read from DataStore on a background dispatcher; let it land.
        var dialog: android.app.Dialog? = null
        val deadline = System.currentTimeMillis() + 5_000
        while (dialog == null && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            dialog = ShadowDialog.getLatestDialog()
            if (dialog == null) Thread.sleep(20)
        }
        assertNotNull("consent dialog should appear on first start", dialog)
        assertTrue(dialog!!.isShowing)
        val msg = (dialog as AlertDialog).findViewById<TextView>(android.R.id.message)
        assertTrue(msg!!.text.toString().contains("Tell everyone"))
    }
}
