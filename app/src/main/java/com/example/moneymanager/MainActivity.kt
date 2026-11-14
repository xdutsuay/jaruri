package com.example.moneymanager

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.example.moneymanager.quickadd.QuickExpenseActivity
import com.example.moneymanager.databinding.ActivityMainBinding
import com.example.moneymanager.listener.ListenerService
import com.example.moneymanager.ui.AddTransactionFragment
import com.example.moneymanager.ui.ListenerFragment
import com.google.android.material.navigation.NavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        installAppShortcuts()

        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_hub,
                R.id.nav_time,
                R.id.nav_usage,
                R.id.nav_listener,
                R.id.nav_home,
                R.id.nav_chart,
                R.id.nav_categories,
                R.id.nav_accounts,
                R.id.nav_budgets,
                R.id.nav_recurring,
                R.id.nav_import_sms,
                R.id.nav_export,
                R.id.nav_recycle_bin,
                R.id.nav_settings
            ),
            drawerLayout
        )

        setupActionBarWithNavController(navController, appBarConfiguration)

        val headerView = navView.getHeaderView(0)
        headerView.findViewById<android.widget.TextView>(R.id.tv_user_name)?.text =
            getString(R.string.nav_header_local)
        headerView.findViewById<android.widget.TextView>(R.id.tv_user_email)?.text =
            getString(R.string.nav_header_local_sub)

        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_about -> {
                    val version = try {
                        packageManager.getPackageInfo(packageName, 0).versionName ?: "—"
                    } catch (_: Exception) {
                        "—"
                    }
                    AlertDialog.Builder(this)
                        .setTitle(R.string.menu_about)
                        .setMessage(getString(R.string.about_message, version))
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                    drawerLayout.closeDrawers()
                    true
                }
                else -> {
                    val handled = androidx.navigation.ui.NavigationUI
                        .onNavDestinationSelected(menuItem, navController)
                    if (handled) drawerLayout.closeDrawers()
                    handled
                }
            }
        }

        handleDeepLink(intent)
        if (savedInstanceState == null) handleListenerIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
        handleListenerIntent(intent)
    }

    /** Listener QS tile / notification → Listener screen (tile also asks it to start). */
    private fun handleListenerIntent(intent: Intent?) {
        val action = intent?.action ?: return
        if (action != ListenerService.ACTION_OPEN_LISTENER &&
            action != ListenerService.ACTION_START_FROM_TILE
        ) return
        if (action == ListenerService.ACTION_START_FROM_TILE) {
            ListenerFragment.pendingAutoStart.set(true)
        }
        intent.action = null // don't replay on recreation
        binding.root.post {
            try {
                val nav = findNavController(R.id.nav_host_fragment)
                if (nav.currentDestination?.id != R.id.nav_listener) {
                    nav.navigate(R.id.nav_listener)
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun handleDeepLink(intent: Intent?) {
        val data: Uri = intent?.data ?: return
        if (data.scheme != "jaruri") return
        if (data.host != "add" && data.path != "/add") {
            // jaruri://add?... → host=add
            if (data.host != "add") return
        }
        intent?.data = null // don't replay on recreation
        val amount = data.getQueryParameter("amount")?.toDoubleOrNull()
            ?.takeIf { it.isFinite() && it > 0 && it <= MAX_DEEP_LINK_AMOUNT } ?: return
        val category = data.getQueryParameter("category")?.take(MAX_DEEP_LINK_TEXT)
        val note = data.getQueryParameter("note")?.take(MAX_DEEP_LINK_TEXT)
        val type = data.getQueryParameter("type") ?: "EXPENSE"

        // Any installed app can fire this link, so it only prefills the form; the user taps Save.
        val args = bundleOf(
            AddTransactionFragment.ARG_PREFILL_AMOUNT to
                amount.toBigDecimal().stripTrailingZeros().toPlainString(),
            AddTransactionFragment.ARG_PREFILL_TYPE to type,
            AddTransactionFragment.ARG_PREFILL_CATEGORY to category,
            AddTransactionFragment.ARG_PREFILL_NOTE to note
        )
        binding.root.post {
            try {
                findNavController(R.id.nav_host_fragment).navigate(R.id.addTransactionFragment, args)
            } catch (_: Exception) {
            }
        }
    }

    private fun installAppShortcuts() {
        try {
            val addIntent = Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = Uri.parse("jaruri://add?amount=0")
                // amount=0 is ignored; open quick add instead via host path without amount handled below
            }
            // Prefer navigating to quick-add screen via dedicated shortcut action
            val quickAdd = Intent(this, MainActivity::class.java).apply {
                action = ACTION_QUICK_ADD
            }
            val shortcut = ShortcutInfoCompat.Builder(this, "quick_add")
                .setShortLabel(getString(R.string.money_shortcut_quick_add))
                .setLongLabel(getString(R.string.money_shortcut_quick_add_long))
                .setIcon(IconCompat.createWithResource(this, R.drawable.ic_add))
                .setIntent(quickAdd)
                .build()
            // The light sheet (also what back-tap / Routines / Tasker should launch).
            val quickExpense = ShortcutInfoCompat.Builder(this, QuickExpenseActivity.SHORTCUT_ID)
                .setShortLabel(getString(R.string.quick_expense_title))
                .setLongLabel(getString(R.string.quick_expense_shortcut_long))
                .setIcon(IconCompat.createWithResource(this, R.drawable.ic_add))
                .setIntent(Intent(this, QuickExpenseActivity::class.java).setAction(QuickExpenseActivity.ACTION))
                .build()
            ShortcutManagerCompat.setDynamicShortcuts(this, listOf(quickExpense, shortcut))
            if (intent?.action == ACTION_QUICK_ADD) {
                binding.root.post {
                    try {
                        findNavController(R.id.nav_host_fragment).navigate(R.id.quickAddFragment)
                    } catch (_: Exception) {
                    }
                }
            }
            // suppress unused
            @Suppress("UNUSED_VARIABLE")
            val unused = addIntent
        } catch (_: Exception) {
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    companion object {
        const val ACTION_QUICK_ADD = "com.example.moneymanager.action.QUICK_ADD"
        private const val MAX_DEEP_LINK_AMOUNT = 1_00_00_000.0
        private const val MAX_DEEP_LINK_TEXT = 120
    }
}
