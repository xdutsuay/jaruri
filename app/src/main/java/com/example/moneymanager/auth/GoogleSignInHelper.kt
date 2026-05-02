package com.example.moneymanager.auth

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task

/**
 * Primary authentication path — wraps the standard Google Sign-In library.
 *
 * This is the recommended path. It requires:
 * - A properly configured OAuth 2.0 client ID in Google Cloud Console
 * - The SHA-1 fingerprint of the signing key registered in the console
 *
 * If this fails (common during dev when SHA-1 isn't configured), the app
 * falls back to [DriveBackupAuth] which uses Drive scope directly.
 */
class GoogleSignInHelper(private val context: Context) {

    private val gso: GoogleSignInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestProfile()
        .build()

    private val client: GoogleSignInClient = GoogleSignIn.getClient(context, gso)

    /**
     * Returns the sign-in intent to launch with startActivityForResult.
     */
    fun getSignInIntent(): Intent = client.signInIntent

    /**
     * Parses the result from the sign-in activity.
     * @return [GoogleSignInAccount] on success, null on failure.
     */
    fun handleSignInResult(data: Intent?): GoogleSignInAccount? {
        return try {
            val task: Task<GoogleSignInAccount> = GoogleSignIn.getSignedInAccountFromIntent(data)
            task.getResult(ApiException::class.java)
        } catch (e: ApiException) {
            android.util.Log.e("GoogleSignInHelper", "Sign-in failed: code=${e.statusCode}", e)
            null
        }
    }

    /**
     * Check if user is already signed in via Google Sign-In.
     */
    fun getLastSignedInAccount(): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }

    /**
     * Sign out from Google Sign-In.
     */
    fun signOut(onComplete: () -> Unit) {
        client.signOut().addOnCompleteListener { onComplete() }
    }
}
