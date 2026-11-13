package com.example.moneymanager.tunnel

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.TextView
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.moneymanager.R
import com.example.moneymanager.data.AppDatabase
import com.example.moneymanager.flavor.FlavorFeatures
import com.example.moneymanager.tunnel.data.TunnelAppRuleEntity
import com.example.moneymanager.tunnel.data.TunnelDomainRuleEntity
import com.example.moneymanager.tunnel.log.DnsLogWriter
import com.example.moneymanager.tunnel.log.TunnelLogDatabase
import com.example.moneymanager.tunnel.ui.TunnelActivity
import com.example.moneymanager.ui.HubFragment
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TunnelAndroidTest {
    private lateinit var ctx: Context

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(ctx)
        TunnelState.set(TunnelState.Status.Off)
        org.robolectric.shadows.ShadowLog.stream = System.out
    }

    private fun idleUntil(check: () -> Boolean) {
        val end = System.currentTimeMillis() + 5_000
        while (!check() && System.currentTimeMillis() < end) {
            shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
            Thread.sleep(20)
        }
        assertTrue("timed out; status=${TunnelState.status.value}", check())
    }

    @Test
    fun hubShowsTunnelCardAndOpensTunnelScreen() {
        assertNotNull(FlavorFeatures.tunnel)
        val scenario = launchFragmentInContainer<HubFragment>(themeResId = R.style.Theme_MoneyManager)
        scenario.onFragment {
            val card = it.requireView().findViewById<View>(R.id.card_tunnel)
            assertEquals(View.VISIBLE, card.visibility)
            card.performClick()
            val next = shadowOf(it.requireActivity()).nextStartedActivity
            assertEquals(TunnelActivity::class.java.name, next.component?.className)
        }
    }

    @Test
    fun bundledListLoadsFromAssets_andImportBuildsPolicy() = runBlocking {
        val b = Blocklists(ctx)
        val bundled = b.bundled()
        assertTrue("bundled size ${bundled.size}", bundled.size in 70_000..90_000)

        val n = b.import(ByteArrayInputStream("# my list\n0.0.0.0 tracker.mine\nplain.mine\n127.0.0.1 localhost\n".toByteArray()), "mine.txt")
        assertEquals(2, n)
        val dao = AppDatabase.getDatabase(ctx).tunnelRuleDao()
        dao.putDomainRule(TunnelDomainRuleEntity(pattern = "*.blocked.example", action = TunnelDomainRuleEntity.ACTION_BLOCK, createdAt = 1))
        dao.putDomainRule(TunnelDomainRuleEntity(pattern = "plain.mine", action = TunnelDomainRuleEntity.ACTION_ALLOW, createdAt = 1))
        dao.setAppRule(TunnelAppRuleEntity("com.bank", TunnelAppRuleEntity.MODE_BYPASS, 1))
        dao.setAppRule(TunnelAppRuleEntity("com.social", TunnelAppRuleEntity.MODE_BLOCK, 1))

        val prefs = TunnelPrefs(ctx)
        val snap = b.snapshot(prefs) { if (it == "com.social") 10234 else null }
        assertEquals(setOf("com.bank"), snap.bypass)
        assertEquals(DnsDecision.BLOCKED_APP, snap.policy.decide("x.org", 10234).decision)
        assertEquals(DnsDecision.BLOCKED_DOMAIN, snap.policy.decide("a.blocked.example", -1).decision)
        assertEquals("list:mine.txt", snap.policy.decide("tracker.mine", -1).ref)
        assertEquals(DnsDecision.ALLOWED_RULE, snap.policy.decide("plain.mine", -1).decision)

        prefs.bundledListEnabled = false
        assertEquals(1, b.snapshot(prefs) { null }.policy.lists.size)
        prefs.bundledListEnabled = true
        dao.lists().forEach { b.delete(it) }
        assertTrue(dao.lists().isEmpty())
    }

    @Test
    fun tunnelScreenTabsRender_logShowsEntries() = runBlocking {
        val log = TunnelLogDatabase.get(ctx)
        log.dao().clearLog()
        DnsLogWriter(log, { "com.chat" }, CoroutineScope(Dispatchers.Unconfined)).write(
            listOf(DnsEvent(System.currentTimeMillis(), 10100, "ads.tracker.test", 1, DnsDecision.BLOCKED_DOMAIN, "list:StevenBlack", 3, 0))
        )
        val scenario = ActivityScenario.launch<TunnelActivity>(Intent(ctx, TunnelActivity::class.java))
        scenario.onActivity { a ->
            val status = a.findViewById<TextView>(R.id.tv_tunnel_status)
            assertEquals(a.getString(R.string.tunnel_status_off), status.text.toString())
            a.findViewById<TabLayout>(R.id.tabs_tunnel).getTabAt(3)!!.select()
        }
        var list: RecyclerView? = null
        idleUntil {
            var ok = false
            scenario.onActivity { a ->
                list = a.findViewById(R.id.rv_tunnel_log)
                ok = (list?.adapter?.itemCount ?: 0) >= 1
            }
            ok
        }
        scenario.onActivity { a ->
            a.findViewById<TabLayout>(R.id.tabs_tunnel).getTabAt(2)!!.select()
        }
        idleUntil {
            var ok = false
            scenario.onActivity { a -> ok = a.findViewById<View?>(R.id.sw_tunnel_bundled) != null }
            ok
        }
        scenario.close()
    }

    @Test
    fun serviceStartWhenInterfaceCannotBeBuilt_failsCleanly_andStopResets() {
        val controller = org.robolectric.Robolectric.buildService(TunnelVpnService::class.java,
            Intent(ctx, TunnelVpnService::class.java).setAction(TunnelVpnService.ACTION_START)).create()
        controller.startCommand(0, 1)
        idleUntil { TunnelState.status.value !is TunnelState.Status.Starting }
        // Robolectric cannot build a VPN interface (VpnService.Builder needs hidden platform APIs),
        // so this exercises the failure path: Failed, never stuck in Starting.
        val s = TunnelState.status.value
        assertTrue("status was $s", s is TunnelState.Status.Failed)
        TunnelVpnService.stop(ctx)
        controller.destroy()
        assertTrue(!TunnelState.isOn)
    }
}
