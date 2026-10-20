package com.example.safewalk.data.session

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.example.safewalk.domain.model.User
import com.google.gson.Gson

/**
 * Secure session and token management.
 * Persists JWT Bearer token and current User profile in SharedPreferences.
 * Uses EncryptedSharedPreferences where available, falling back to standard SharedPreferences
 * in unit test environments or platforms lacking Android Keystore support.
 */
class SessionManager(
    private val context: Context? = null,
    injectedPrefs: SharedPreferences? = null,
    private val gson: Gson = Gson()
) {

    companion object {
        private const val TAG = "SessionManager"
        private const val PREFS_FILE = "safewalk_auth_session"
        private const val KEY_JWT_TOKEN = "jwt_token"
        private const val KEY_USER_DATA = "user_json"
        private const val KEY_FCM_TOKEN = "fcm_device_token"
    }

    private val prefs: SharedPreferences by lazy {
        if (injectedPrefs != null) {
            return@lazy injectedPrefs
        }
        val ctx = checkNotNull(context) { "SessionManager requires Context when SharedPreferences is not injected" }
        try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                PREFS_FILE,
                masterKeyAlias,
                ctx,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.w(TAG, "EncryptedSharedPreferences unavailable; falling back to private SharedPreferences: ${e.message}")
            ctx.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
        }
    }

    @Volatile
    private var cachedToken: String? = null

    @Volatile
    private var cachedUser: User? = null

    @Volatile
    private var cachedFcmToken: String? = null

    init {
        // Hydrate in-memory cache from persistence
        cachedToken = prefs.getString(KEY_JWT_TOKEN, null)
        cachedFcmToken = prefs.getString(KEY_FCM_TOKEN, null)
        val userJson = prefs.getString(KEY_USER_DATA, null)
        if (!userJson.isNullOrBlank()) {
            try {
                cachedUser = gson.fromJson(userJson, User::class.java)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to deserialize cached user: ${e.message}")
            }
        }
    }

    fun saveSession(token: String, user: User) {
        cachedToken = token
        cachedUser = user
        prefs.edit()
            .putString(KEY_JWT_TOKEN, token)
            .putString(KEY_USER_DATA, gson.toJson(user))
            .apply()
    }

    fun updateCurrentUser(user: User) {
        cachedUser = user
        prefs.edit()
            .putString(KEY_USER_DATA, gson.toJson(user))
            .apply()
    }

    fun getToken(): String? {
        if (cachedToken == null) {
            cachedToken = prefs.getString(KEY_JWT_TOKEN, null)
        }
        return cachedToken
    }

    fun getUser(): User? {
        if (cachedUser == null) {
            val userJson = prefs.getString(KEY_USER_DATA, null)
            if (!userJson.isNullOrBlank()) {
                try {
                    cachedUser = gson.fromJson(userJson, User::class.java)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to deserialize cached user: ${e.message}")
                }
            }
        }
        return cachedUser
    }

    fun hasActiveSession(): Boolean {
        val token = getToken()
        return !token.isNullOrBlank() && getUser() != null
    }

    fun saveFcmToken(token: String) {
        cachedFcmToken = token
        prefs.edit().putString(KEY_FCM_TOKEN, token).apply()
    }

    fun getFcmToken(): String? {
        if (cachedFcmToken == null) {
            cachedFcmToken = prefs.getString(KEY_FCM_TOKEN, null)
        }
        return cachedFcmToken
    }

    fun clearFcmToken() {
        cachedFcmToken = null
        prefs.edit().remove(KEY_FCM_TOKEN).apply()
    }

    fun clearSession() {
        cachedToken = null
        cachedUser = null
        cachedFcmToken = null
        prefs.edit().clear().apply()
    }
}
