package com.example.moneymanager

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.example.moneymanager.databinding.ActivityMainBinding
import com.example.moneymanager.viewmodel.MainViewModel
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
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        val data: Uri = intent?.data ?: return
        if (data.scheme != "jaruri") return
        if (data.host != "add" && data.path != "/add") {
            // jaruri://add?... → host=add
            if (data.host != "add") return
        }
        val amount = data.getQueryParameter("amount")?.toDoubleOrNull() ?: return
        val category = data.getQueryParameter("category")
        val note = data.getQueryParameter("note")
        val silent = data.getQueryParameter("silent") == "1" ||
            data.getQueryParameter("silent").equals("true", ignoreCase = true)
        val type = data.getQueryParameter("type") ?: "EXPENSE"

        val vm = ViewModelProvider(this)[MainViewModel::class.java]
        vm.addFromDeepLink(amount, category, note, type, silent) { id ->
            if (id > 0) {
                if (!silent) {
                    Toast.makeText(
                        this,
                        getString(R.string.money_quick_add_done, amount),
                        Toast.LENGTH_SHORT
                    ).show()
                    try {
                        findNavController(R.id.nav_host_fragment).navigate(R.id.nav_home)
                    } catch (_: Exception) {
                    }
                }
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
                action = "com.example.moneymanager.action.QUICK_ADD"
            }
            val shortcut = ShortcutInfoCompat.Builder(this, "quick_add")
                .setShortLabel(getString(R.string.money_shortcut_quick_add))
                .setLongLabel(getString(R.string.money_shortcut_quick_add_long))
                .setIcon(IconCompat.createWithResource(this, R.drawable.ic_add))
                .setIntent(quickAdd)
                .build()
            ShortcutManagerCompat.setDynamicShortcuts(this, listOf(shortcut))
            if (intent?.action == "com.example.moneymanager.action.QUICK_ADD") {
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
}
