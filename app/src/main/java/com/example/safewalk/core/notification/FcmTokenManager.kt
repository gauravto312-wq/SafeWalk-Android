package com.example.safewalk.core.notification

import android.util.Log
import com.example.safewalk.core.common.ServiceLocator
import com.example.safewalk.data.model.dto.FcmTokenRequestDto
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Manages FCM device token retrieval, persistence, and synchronization with the SafeWalk backend.
 *
 * Responsibilities:
 * - Obtains the current device FCM registration token.
 * - Saves the token securely in SessionManager.
 * - Synchronizes the device token with POST /api/v1/notifications/device-token when user is authenticated.
 * - Handles token rotation via onNewToken.
 * - Deactivates the token with DELETE /api/v1/notifications/device-token on user logout.
 */
object FcmTokenManager {

    private const val TAG = "FcmTokenManager"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Obtains the FCM token from Firebase and synchronizes it with the backend if the user is authenticated.
     */
    fun fetchAndSyncToken() {
        try {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w(TAG, "Fetching FCM registration token failed: ${task.exception?.message}")
                    return@addOnCompleteListener
                }

                val token = task.result
                if (!token.isNullOrBlank()) {
                    Log.i(TAG, "FCM registration token acquired successfully")
                    registerTokenWithBackend(token)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "FirebaseMessaging is unavailable: ${e.message}")
        }
    }

    /**
     * Stores the token locally and, if an active user session exists, registers it with the SafeWalk backend.
     */
    fun registerTokenWithBackend(token: String) {
        val sessionManager = ServiceLocator.sessionManager ?: return
        sessionManager.saveFcmToken(token)

        if (!sessionManager.hasActiveSession()) {
            Log.d(TAG, "Token saved locally; user not yet authenticated, deferring backend sync")
            return
        }

        scope.launch {
            try {
                val apiService = ServiceLocator.notificationApiService
                val response = apiService.registerDeviceToken(
                    FcmTokenRequestDto(token = token, platform = "ANDROID")
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    Log.i(TAG, "FCM device token synchronized with backend successfully")
                } else {
                    Log.w(TAG, "Failed to register FCM device token: HTTP ${response.code()}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Network error synchronizing FCM token with backend: ${e.message}")
            }
        }
    }

    /**
     * Called by SafeWalkFirebaseMessagingService when Firebase rotates the device token.
     */
    fun onNewToken(token: String) {
        Log.i(TAG, "Firebase rotated device token; updating local and backend registration")
        registerTokenWithBackend(token)
    }

    /**
     * Deactivates the token on the backend when the user logs out.
     * Suspends until the DELETE request finishes or times out, ensuring session/token
     * data is preserved while the request is in flight.
     */
    suspend fun unregisterTokenFromBackendSuspend() {
        val sessionManager = ServiceLocator.sessionManager ?: return
        val token = sessionManager.getFcmToken()

        if (!token.isNullOrBlank() && sessionManager.hasActiveSession()) {
            try {
                kotlinx.coroutines.withTimeoutOrNull(5000L) {
                    val apiService = ServiceLocator.notificationApiService
                    val response = apiService.removeDeviceToken(token)
                    if (response.isSuccessful) {
                        Log.i(TAG, "FCM device token deactivated on backend successfully")
                    } else {
                        Log.w(TAG, "Failed to deactivate FCM device token: HTTP ${response.code()}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Network error deactivating FCM token on backend: ${e.message}")
            } finally {
                sessionManager.clearFcmToken()
            }
        } else {
            sessionManager.clearFcmToken()
        }
    }

    /**
     * Deactivates the token on the backend when the user logs out.
     */
    fun unregisterTokenFromBackend() {
        scope.launch {
            unregisterTokenFromBackendSuspend()
        }
    }
}
