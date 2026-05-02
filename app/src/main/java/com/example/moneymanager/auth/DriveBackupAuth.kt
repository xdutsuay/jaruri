package com.example.moneymanager.auth

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Secondary authentication path — uses Google Drive as a plain-text auth store.
 *
 * This bypasses the usual Firebase/Google Sign-In library flow entirely.
 * Instead, it:
 * 1. Requests Google Sign-In with **Drive appdata scope** (simpler to configure)
 * 2. Writes a plain-text auth file (`user_auth.txt`) to Drive's hidden appDataFolder
 * 3. Reads it back on subsequent launches to verify identity
 *
 * This approach works even without:
 * - google-services.json
 * - Firebase project setup
 * - Proper SHA-1 fingerprint registration for the primary Sign-In client ID
 *
 * The appDataFolder is private to this app — other apps can't see it.
 */
class DriveBackupAuth(private val context: Context) {

    companion object {
        private const val AUTH_FILE_NAME = "user_auth.txt"
        private const val TAG = "DriveBackupAuth"
    }

    private val gso: GoogleSignInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestProfile()
        .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
        .build()

    private val client: GoogleSignInClient = GoogleSignIn.getClient(context, gso)

    /**
     * Returns the sign-in intent for the Drive-scoped flow.
     */
    fun getSignInIntent(): Intent = client.signInIntent

    /**
     * Parses the result from the sign-in activity.
     */
    fun handleSignInResult(data: Intent?): GoogleSignInAccount? {
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            task.getResult(ApiException::class.java)
        } catch (e: ApiException) {
            android.util.Log.e(TAG, "Drive auth sign-in failed: code=${e.statusCode}", e)
            null
        }
    }

    /**
     * After successful sign-in, write the user's auth info as plain text to Drive.
     * This acts as a simple identity persistence layer.
     */
    suspend fun writeAuthToDrive(account: GoogleSignInAccount): Boolean = withContext(Dispatchers.IO) {
        try {
            val driveService = buildDriveService(account)

            // Check if auth file already exists
            val existingFileId = findAuthFile(driveService)

            val authContent = buildAuthContent(account)
            val contentStream = ByteArrayContent.fromString("text/plain", authContent)

            if (existingFileId != null) {
                // Update existing file
                driveService.files().update(existingFileId, null, contentStream).execute()
                android.util.Log.d(TAG, "Updated auth file in Drive")
            } else {
                // Create new file in appDataFolder
                val fileMetadata = com.google.api.services.drive.model.File().apply {
                    name = AUTH_FILE_NAME
                    parents = listOf("appDataFolder")
                }
                driveService.files().create(fileMetadata, contentStream)
                    .setFields("id")
                    .execute()
                android.util.Log.d(TAG, "Created auth file in Drive")
            }
            true
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to write auth to Drive", e)
            false
        }
    }

    /**
     * Read the stored auth info from Drive.
     * Returns the plain-text content or null if not found.
     */
    suspend fun readAuthFromDrive(account: GoogleSignInAccount): String? = withContext(Dispatchers.IO) {
        try {
            val driveService = buildDriveService(account)
            val fileId = findAuthFile(driveService) ?: return@withContext null

            val inputStream = driveService.files().get(fileId).executeMediaAsInputStream()
            val reader = BufferedReader(InputStreamReader(inputStream))
            val content = reader.readText()
            reader.close()

            android.util.Log.d(TAG, "Read auth from Drive: ${content.take(50)}...")
            content
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to read auth from Drive", e)
            null
        }
    }

    /**
     * Sign out from the Drive-scoped Google Sign-In.
     */
    fun signOut(onComplete: () -> Unit) {
        client.signOut().addOnCompleteListener { onComplete() }
    }

    // --- Private helpers ---

    private fun buildDriveService(account: GoogleSignInAccount): Drive {
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(DriveScopes.DRIVE_APPDATA)
        )
        credential.selectedAccount = account.account

        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName("MoneyManager")
            .build()
    }

    private fun findAuthFile(driveService: Drive): String? {
        val result = driveService.files().list()
            .setSpaces("appDataFolder")
            .setQ("name = '$AUTH_FILE_NAME'")
            .setFields("files(id, name)")
            .setPageSize(1)
            .execute()

        return result.files?.firstOrNull()?.id
    }

    private fun buildAuthContent(account: GoogleSignInAccount): String {
        return buildString {
            appendLine("=== MoneyManager Auth ===")
            appendLine("email=${account.email ?: "unknown"}")
            appendLine("displayName=${account.displayName ?: "User"}")
            appendLine("photoUrl=${account.photoUrl?.toString() ?: ""}")
            appendLine("signedInAt=${System.currentTimeMillis()}")
            appendLine("method=drive_fallback")
            appendLine("=========================")
        }
    }
}
