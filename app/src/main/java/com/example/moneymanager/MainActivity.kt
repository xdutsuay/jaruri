package com.example.moneymanager

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.example.moneymanager.auth.AuthRepository
import com.example.moneymanager.auth.DriveBackupAuth
import com.example.moneymanager.auth.GoogleSignInHelper
import com.example.moneymanager.databinding.ActivityMainBinding
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        authRepository = AuthRepository(this)

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
                R.id.nav_import_sms,
                R.id.nav_export,
                R.id.nav_settings
            ),
            drawerLayout
        )

        setupActionBarWithNavController(navController, appBarConfiguration)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.nav_login -> {
                    supportActionBar?.hide()
                    drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
                }
                else -> {
                    supportActionBar?.show()
                    drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
                }
            }
        }

        updateNavHeader(navView)

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
                R.id.nav_link_google -> {
                    navController.navigate(R.id.nav_login)
                    drawerLayout.closeDrawers()
                    true
                }
                R.id.nav_sign_out -> {
                    lifecycleScope.launch {
                        authRepository.signOut()
                        GoogleSignInHelper(this@MainActivity).signOut {}
                        DriveBackupAuth(this@MainActivity).signOut {}
                        val options = NavOptions.Builder()
                            .setPopUpTo(navController.graph.id, true)
                            .setLaunchSingleTop(true)
                            .build()
                        navController.navigate(R.id.nav_home, null, options)
                    }
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

    private fun updateNavHeader(navView: NavigationView) {
        val headerView = navView.getHeaderView(0)
        val tvUserName = headerView.findViewById<android.widget.TextView>(R.id.tv_user_name)
        val tvUserEmail = headerView.findViewById<android.widget.TextView>(R.id.tv_user_email)

        lifecycleScope.launch {
            authRepository.isSignedIn.collect { isSignedIn ->
                if (isSignedIn) {
                    val name = authRepository.userDisplayName.first()
                    val email = authRepository.userEmail.first()
                    tvUserName?.text = name.ifEmpty { getString(R.string.nav_header_local) }
                    tvUserEmail?.text = email.ifEmpty { getString(R.string.nav_header_local_sub) }
                } else {
                    tvUserName?.text = getString(R.string.nav_header_local)
                    tvUserEmail?.text = getString(R.string.nav_header_local_sub)
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}
