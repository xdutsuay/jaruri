package com.example.moneymanager.flavor

import android.view.View
import androidx.fragment.app.testing.launchFragmentInContainer
import com.example.moneymanager.R
import com.example.moneymanager.ui.HubFragment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The offline (F-Droid/main) flavor ships no Tunnel. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class OfflineFlavorTest {
    @Test
    fun noTunnelFeature_andHubCardHidden() {
        assertNull(FlavorFeatures.tunnel)
        val scenario = launchFragmentInContainer<HubFragment>(themeResId = R.style.Theme_MoneyManager)
        scenario.onFragment {
            assertEquals(View.GONE, it.requireView().findViewById<View>(R.id.card_tunnel).visibility)
        }
    }

    @Test
    fun tunnelClassesAreNotInThisBuild() {
        for (name in listOf(
            "com.example.moneymanager.tunnel.TunnelVpnService",
            "com.example.moneymanager.tunnel.ui.TunnelActivity",
            "com.example.moneymanager.tunnel.DnsTunnelEngine"
        )) {
            val found = runCatching { Class.forName(name) }.isSuccess
            assertEquals("$name must not exist in the offline flavor", false, found)
        }
    }
}
