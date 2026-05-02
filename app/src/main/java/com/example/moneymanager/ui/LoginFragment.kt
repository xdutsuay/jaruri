package com.example.moneymanager.ui

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.moneymanager.R
import com.example.moneymanager.auth.AuthRepository
import com.example.moneymanager.auth.DriveBackupAuth
import com.example.moneymanager.auth.GoogleSignInHelper
import com.example.moneymanager.databinding.FragmentLoginBinding
import kotlinx.coroutines.launch

/**
 * Login screen offering two authentication paths:
 *
 * 1. **Primary** — Standard Google Sign-In via [GoogleSignInHelper]
 * 2. **Secondary** — Drive backup auth via [DriveBackupAuth], which stores
 *    user info as plain text in Google Drive's appDataFolder. This bypasses
 *    the usual library auth path when it fails (no google-services.json needed).
 */
class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private lateinit var authRepository: AuthRepository
    private lateinit var googleSignInHelper: GoogleSignInHelper
    private lateinit var driveBackupAuth: DriveBackupAuth

    // Activity result launcher for primary Google Sign-In
    private val primarySignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val account = googleSignInHelper.handleSignInResult(result.data)
            if (account != null) {
                saveAuthAndNavigate(
                    email = account.email ?: "",
                    displayName = account.displayName ?: "User",
                    photoUrl = account.photoUrl?.toString() ?: "",
                    method = "google_signin"
                )
            } else {
                showError("Google Sign-In failed. Try the Drive backup option.")
            }
        } else {
            showError("Sign-in cancelled.")
        }
        hideLoading()
    }

    // Activity result launcher for secondary Drive auth
    private val driveAuthLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val account = driveBackupAuth.handleSignInResult(result.data)
            if (account != null) {
                // Write auth info to Drive as plain text
                lifecycleScope.launch {
                    showLoading()
                    val success = driveBackupAuth.writeAuthToDrive(account)
                    if (success) {
                        saveAuthAndNavigate(
                            email = account.email ?: "",
                            displayName = account.displayName ?: "User",
                            photoUrl = account.photoUrl?.toString() ?: "",
                            method = "drive_fallback"
                        )
                    } else {
                        showError("Failed to write auth to Drive. Check network.")
                    }
                    hideLoading()
                }
            } else {
                showError("Drive auth failed.")
                hideLoading()
            }
        } else {
            showError("Drive auth cancelled.")
            hideLoading()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        authRepository = AuthRepository(requireContext())
        googleSignInHelper = GoogleSignInHelper(requireContext())
        driveBackupAuth = DriveBackupAuth(requireContext())

        // Check if already signed in
        lifecycleScope.launch {
            if (authRepository.isCurrentlySignedIn()) {
                navigateToHome()
                return@launch
            }
        }

        // Primary: Google Sign-In
        binding.btnGoogleSignIn.setOnClickListener {
            showLoading()
            primarySignInLauncher.launch(googleSignInHelper.getSignInIntent())
        }

        // Secondary: Drive Backup Auth
        binding.btnDriveBackupAuth.setOnClickListener {
            showLoading()
            driveAuthLauncher.launch(driveBackupAuth.getSignInIntent())
        }

        // Skip
        binding.btnSkip.setOnClickListener {
            navigateToHome()
        }
    }

    private fun saveAuthAndNavigate(
        email: String,
        displayName: String,
        photoUrl: String,
        method: String
    ) {
        lifecycleScope.launch {
            authRepository.saveSignIn(email, displayName, photoUrl, method)
            Toast.makeText(
                requireContext(),
                "Welcome, $displayName!",
                Toast.LENGTH_SHORT
            ).show()
            navigateToHome()
        }
    }

    private fun navigateToHome() {
        findNavController().navigate(R.id.action_login_to_home)
    }

    private fun showLoading() {
        binding.loadingOverlay.visibility = View.VISIBLE
    }

    private fun hideLoading() {
        binding.loadingOverlay.visibility = View.GONE
    }

    private fun showError(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
