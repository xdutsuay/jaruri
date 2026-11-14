package com.example.moneymanager.tunnel.ui

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.moneymanager.R
import com.example.moneymanager.tunnel.TunnelState
import com.example.moneymanager.tunnel.TunnelVpnService
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout

/** Tunnel screen: Status · Apps · Blocklist · Log. */
class TunnelActivity : AppCompatActivity() {
    private val viewModel: TunnelViewModel by viewModels()

    private val vpnConsent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) TunnelVpnService.start(this)
        else Toast.makeText(this, R.string.tunnel_vpn_denied, Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tunnel)
        findViewById<MaterialToolbar>(R.id.toolbar_tunnel).setNavigationOnClickListener { finish() }
        val tabs = findViewById<TabLayout>(R.id.tabs_tunnel)
        TABS.forEach { tabs.addTab(tabs.newTab().setText(it)) }
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = show(tab.position)
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        if (savedInstanceState == null) {
            show(0)
            handleStartIntent(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleStartIntent(intent)
    }

    private fun handleStartIntent(intent: Intent?) {
        if (intent?.action == ACTION_START && !TunnelState.isOn) requestStart()
    }

    private fun show(position: Int) {
        val f: Fragment = when (position) {
            1 -> TunnelAppsFragment()
            2 -> TunnelRulesFragment()
            3 -> TunnelLogFragment()
            else -> TunnelStatusFragment()
        }
        supportFragmentManager.beginTransaction().replace(R.id.tunnel_container, f).commit()
    }

    /** Consent (once) → system VPN dialog (first time) → start. */
    fun requestStart() {
        if (!viewModel.prefs.consentAccepted) {
            AlertDialog.Builder(this)
                .setTitle(R.string.tunnel_consent_title)
                .setMessage(R.string.tunnel_consent_body)
                .setPositiveButton(R.string.tunnel_consent_ok) { _, _ ->
                    viewModel.prefs.consentAccepted = true
                    requestStart()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
            return
        }
        val ask = VpnService.prepare(this)
        if (ask != null) vpnConsent.launch(ask) else TunnelVpnService.start(this)
    }

    companion object {
        const val ACTION_START = "com.kaustubhtripathi.jaruri.tunnel.OPEN_AND_START"
        private val TABS = intArrayOf(R.string.tunnel_tab_status, R.string.tunnel_tab_apps, R.string.tunnel_tab_rules, R.string.tunnel_tab_log)
    }
}
