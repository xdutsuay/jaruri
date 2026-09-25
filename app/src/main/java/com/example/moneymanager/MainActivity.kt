package com.example.moneymanager

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.example.moneymanager.databinding.ActivityMainBinding
import com.google.android.material.navigation.NavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_home,
                R.id.nav_chart,
                R.id.nav_categories,
                R.id.nav_accounts,
                R.id.nav_budgets,
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
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}
