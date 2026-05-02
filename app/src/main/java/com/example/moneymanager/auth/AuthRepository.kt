package com.example.moneymanager.auth

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.authDataStore by preferencesDataStore(name = "auth_prefs")

/**
 * Manages authentication state persistence via DataStore.
 * Stores user profile (email, display name, photo URL) and sign-in method.
 */
class AuthRepository(private val context: Context) {

    companion object {
        val IS_SIGNED_IN = booleanPreferencesKey("is_signed_in")
        val USER_EMAIL = stringPreferencesKey("user_email")
        val USER_DISPLAY_NAME = stringPreferencesKey("user_display_name")
        val USER_PHOTO_URL = stringPreferencesKey("user_photo_url")
        val AUTH_METHOD = stringPreferencesKey("auth_method") // "google_signin" or "drive_fallback"
    }

    val isSignedIn: Flow<Boolean> = context.authDataStore.data.map { prefs ->
        prefs[IS_SIGNED_IN] ?: false
    }

    val userEmail: Flow<String> = context.authDataStore.data.map { prefs ->
        prefs[USER_EMAIL] ?: ""
    }

    val userDisplayName: Flow<String> = context.authDataStore.data.map { prefs ->
        prefs[USER_DISPLAY_NAME] ?: ""
    }

    val userPhotoUrl: Flow<String> = context.authDataStore.data.map { prefs ->
        prefs[USER_PHOTO_URL] ?: ""
    }

    val authMethod: Flow<String> = context.authDataStore.data.map { prefs ->
        prefs[AUTH_METHOD] ?: ""
    }

    suspend fun saveSignIn(
        email: String,
        displayName: String,
        photoUrl: String,
        method: String
    ) {
        context.authDataStore.edit { prefs ->
            prefs[IS_SIGNED_IN] = true
            prefs[USER_EMAIL] = email
            prefs[USER_DISPLAY_NAME] = displayName
            prefs[USER_PHOTO_URL] = photoUrl
            prefs[AUTH_METHOD] = method
        }
    }

    suspend fun signOut() {
        context.authDataStore.edit { prefs ->
            prefs[IS_SIGNED_IN] = false
            prefs[USER_EMAIL] = ""
            prefs[USER_DISPLAY_NAME] = ""
            prefs[USER_PHOTO_URL] = ""
            prefs[AUTH_METHOD] = ""
        }
    }

    suspend fun isCurrentlySignedIn(): Boolean {
        return context.authDataStore.data.first()[IS_SIGNED_IN] ?: false
    }
}
