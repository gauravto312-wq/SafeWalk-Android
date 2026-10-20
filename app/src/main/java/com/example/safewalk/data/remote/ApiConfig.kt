package com.example.safewalk.data.remote

import android.util.Log
import com.example.safewalk.BuildConfig

/**
 * Network configuration for SafeWalk Backend API.
 * Single documented configuration point for the SafeWalk backend base URL.
 * Automatically configured with the deployed Railway production backend URL.
 */
object ApiConfig {

    private const val TAG = "ApiConfig"

    /**
     * Verified deployed Railway backend production base URL.
     */
    const val DEFAULT_BASE_URL = "https://safewalk-backend-production-e3a1.up.railway.app/"

    // Backward-compatible constant alias
    const val PROD_BACKEND_URL = DEFAULT_BASE_URL

    @Volatile
    private var customBaseUrl: String? = null

    /**
     * Resolves the default base URL for the application.
     * Uses BuildConfig.DEV_BACKEND_BASE_URL if configured, otherwise falls back to DEFAULT_BASE_URL.
     */
    val defaultBaseUrl: String
        get() {
            val configured = try {
                BuildConfig.DEV_BACKEND_BASE_URL
            } catch (_: Throwable) {
                null
            }
            return if (!configured.isNullOrBlank()) configured else DEFAULT_BASE_URL
        }

    var baseUrl: String
        get() {
            val url = customBaseUrl ?: defaultBaseUrl
            return if (url.endsWith("/")) url else "$url/"
        }
        set(value) {
            val formatted = if (value.endsWith("/")) value else "$value/"
            customBaseUrl = formatted
            NetworkClient.resetClient()
        }

    fun resetToDefault() {
        customBaseUrl = null
        NetworkClient.resetClient()
    }

    const val CONNECT_TIMEOUT_SECONDS = 15L
    const val CLOUD_CONNECT_TIMEOUT_SECONDS = 15L
    const val READ_TIMEOUT_SECONDS = 15L
    const val WRITE_TIMEOUT_SECONDS = 15L

    val connectTimeoutSeconds: Long
        get() = CLOUD_CONNECT_TIMEOUT_SECONDS
}
