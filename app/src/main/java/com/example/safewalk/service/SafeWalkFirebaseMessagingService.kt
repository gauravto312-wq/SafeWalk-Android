package com.example.safewalk.service

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.example.safewalk.core.common.ServiceLocator
import com.example.safewalk.core.notification.EmergencyNotificationHelper
import com.example.safewalk.core.notification.FcmTokenManager
import com.example.safewalk.data.remote.ApiConfig
import com.example.safewalk.data.remote.NetworkClient
import com.example.safewalk.domain.model.Notification
import com.example.safewalk.domain.model.NotificationType
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request

/**
 * Handles incoming FCM push notifications and token refresh events for SafeWalk.
 *
 * Supported backend event types:
 * - SOS_CREATED: High-priority heads-up notification with [ACCEPT] and [CANCEL] actions.
 * - SOS_CANCELLED: Dismisses active emergency notification.
 * - SOS_RESOLVED: Dismisses active emergency notification.
 * - SOS_ACCEPTED, RESPONDER_ARRIVED: In-app notification center sync.
 */
class SafeWalkFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "SafeWalkFCMService"
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i(TAG, "New FCM token received from Firebase")
        FcmTokenManager.onNewToken(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.i(TAG, "FCM message received from: ${remoteMessage.from}")

        val data = remoteMessage.data
        if (data.isEmpty()) {
            Log.d(TAG, "FCM message contains no data payload")
            return
        }

        val eventType = data["type"] ?: data["eventType"] ?: ""
        val emergencyId = data["emergencyId"] ?: ""
        Log.i(TAG, "Processing FCM event [type=$eventType, emergencyId=$emergencyId]")

        when (eventType) {
            "SOS_CREATED" -> {
                handleSosCreated(data)
            }
            "SOS_CANCELLED" -> {
                handleSosCancelled(emergencyId, data)
            }
            "SOS_RESOLVED" -> {
                handleSosResolved(emergencyId, data)
            }
            "SOS_ACCEPTED" -> {
                handleSosAccepted(emergencyId, data)
            }
            "RESPONDER_ARRIVED" -> {
                handleResponderArrived(emergencyId, data)
            }
            "VERIFICATION_STATUS_CHANGED", "VERIFICATION_UPDATED" -> {
                handleVerificationUpdated(data)
            }
            else -> {
                Log.d(TAG, "Unhandled FCM notification event: $eventType")
            }
        }
    }

    private fun handleSosCreated(data: Map<String, String>) {
        val emergencyId = data["emergencyId"] ?: return
        val requesterId = data["requesterId"] ?: "0"
        val requesterName = data["requesterName"] ?: "Someone in need"
        val distance = data["distance"] ?: data["distanceKm"]?.let { "$it km" } ?: data["distanceMeters"]?.let { "$it m" } ?: "Nearby"
        val isEmergencyContact = data["isEmergencyContact"] == "true" ||
                distance.contains("Trusted Contact", ignoreCase = true)
        val rawAvatarUrl = data["avatarUrl"] ?: data["requesterAvatarUrl"]

        val currentUser = ServiceLocator.sessionManager?.getUser()

        // Do not notify the requester of their own SOS
        if (currentUser != null && currentUser.id == requesterId) {
            Log.d(TAG, "Skipping SOS_CREATED notification for self-created emergency")
            return
        }

        serviceScope.launch {
            // Download requester avatar with strict timeout, falling back gracefully to null on failure/timeout
            val avatarBitmap: Bitmap? = if (!rawAvatarUrl.isNullOrBlank() && !rawAvatarUrl.startsWith("mock_")) {
                withTimeoutOrNull(3000L) {
                    try {
                        val fullUrl = if (rawAvatarUrl.startsWith("http://") || rawAvatarUrl.startsWith("https://")) {
                            rawAvatarUrl
                        } else {
                            val base = ApiConfig.baseUrl.trimEnd('/')
                            val path = if (rawAvatarUrl.startsWith("/")) rawAvatarUrl else "/$rawAvatarUrl"
                            "$base$path"
                        }
                        val request = Request.Builder().url(fullUrl).build()
                        val client = NetworkClient.getOkHttpClient(ServiceLocator.sessionManager)
                        client.newCall(request).execute().use { response ->
                            if (response.isSuccessful) {
                                val bytes = response.body?.bytes()
                                if (bytes != null && bytes.isNotEmpty()) {
                                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                } else null
                            } else null
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to download avatar from $rawAvatarUrl: ${e.message}")
                        null
                    }
                }
            } else null

            // 1. Post high-priority OS notification (ACCEPT/CANCEL for responders, OPEN EMERGENCY/CANCEL for trusted contacts)
            EmergencyNotificationHelper.showEmergencySosNotification(
                context = this@SafeWalkFirebaseMessagingService,
                emergencyId = emergencyId,
                requesterId = requesterId,
                requesterName = requesterName,
                approxDistance = distance,
                isEmergencyContact = isEmergencyContact,
                avatarBitmap = avatarBitmap,
                avatarUrl = rawAvatarUrl
            )

            // 2. Synchronize with In-App Notification Center
            if (currentUser != null) {
                try {
                    val notifTitle = if (isEmergencyContact) {
                        "🚨 EMERGENCY SOS - TRUSTED CONTACT ALERT!"
                    } else {
                        "🚨 Emergency SOS Nearby"
                    }
                    val notifMsg = if (isEmergencyContact) {
                        "Your trusted contact ($requesterName) triggered an SOS! Tap to monitor & assist."
                    } else {
                        "$requesterName needs help! Approx. $distance away."
                    }

                    val notif = Notification(
                        id = "FCM-${System.currentTimeMillis()}-${emergencyId.hashCode()}",
                        recipientUserId = currentUser.id,
                        type = NotificationType.EMERGENCY_SOS_NEARBY,
                        title = notifTitle,
                        message = notifMsg,
                        relatedEmergencyId = emergencyId,
                        senderId = requesterId,
                        senderName = requesterName,
                        navigationTarget = "emergency/$emergencyId"
                    )
                    ServiceLocator.notificationRepository.sendNotification(notif)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to record notification in local repo: ${e.message}")
                }
            }
        }
    }

    private fun handleSosCancelled(emergencyId: String, data: Map<String, String>) {
        if (emergencyId.isNotBlank()) {
            Log.i(TAG, "Emergency $emergencyId cancelled; removing active notification")
            EmergencyNotificationHelper.cancelEmergencyNotification(this, emergencyId)
        }

        val currentUser = ServiceLocator.sessionManager?.getUser() ?: return
        serviceScope.launch {
            try {
                val notif = Notification(
                    id = "FCM-${System.currentTimeMillis()}-${emergencyId.hashCode()}",
                    recipientUserId = currentUser.id,
                    type = NotificationType.EMERGENCY_CANCELLED,
                    title = "⚠️ Emergency Cancelled",
                    message = "Emergency #$emergencyId was cancelled.",
                    relatedEmergencyId = emergencyId
                )
                ServiceLocator.notificationRepository.sendNotification(notif)
            } catch (_: Exception) {}
        }
    }

    private fun handleSosResolved(emergencyId: String, data: Map<String, String>) {
        if (emergencyId.isNotBlank()) {
            Log.i(TAG, "Emergency $emergencyId resolved; removing active notification")
            EmergencyNotificationHelper.cancelEmergencyNotification(this, emergencyId)
        }

        val currentUser = ServiceLocator.sessionManager?.getUser() ?: return
        serviceScope.launch {
            try {
                val notif = Notification(
                    id = "FCM-${System.currentTimeMillis()}-${emergencyId.hashCode()}",
                    recipientUserId = currentUser.id,
                    type = NotificationType.EMERGENCY_RESOLVED,
                    title = "✅ Emergency Resolved",
                    message = "Emergency #$emergencyId has been successfully resolved.",
                    relatedEmergencyId = emergencyId
                )
                ServiceLocator.notificationRepository.sendNotification(notif)
            } catch (_: Exception) {}
        }
    }

    private fun handleSosAccepted(emergencyId: String, data: Map<String, String>) {
        val currentUser = ServiceLocator.sessionManager?.getUser() ?: return
        serviceScope.launch {
            try {
                val notif = Notification(
                    id = "FCM-${System.currentTimeMillis()}-${emergencyId.hashCode()}",
                    recipientUserId = currentUser.id,
                    type = NotificationType.RESPONDER_ACCEPTED,
                    title = "🏃 Responder En Route",
                    message = "A responder is en route to emergency #$emergencyId.",
                    relatedEmergencyId = emergencyId
                )
                ServiceLocator.notificationRepository.sendNotification(notif)
            } catch (_: Exception) {}
        }
    }

    private fun handleResponderArrived(emergencyId: String, data: Map<String, String>) {
        val currentUser = ServiceLocator.sessionManager?.getUser() ?: return
        serviceScope.launch {
            try {
                val notif = Notification(
                    id = "FCM-${System.currentTimeMillis()}-${emergencyId.hashCode()}",
                    recipientUserId = currentUser.id,
                    type = NotificationType.RESPONDER_ARRIVED,
                    title = "📍 Responder Arrived",
                    message = "A responder has arrived at emergency #$emergencyId.",
                    relatedEmergencyId = emergencyId
                )
                ServiceLocator.notificationRepository.sendNotification(notif)
            } catch (_: Exception) {}
        }
    }

    private fun handleVerificationUpdated(data: Map<String, String>) {
        val currentUser = ServiceLocator.sessionManager?.getUser() ?: return
        val status = data["verificationStatus"] ?: data["status"] ?: ""
        val isVerified = status.equals("VERIFIED", ignoreCase = true) || status.equals("APPROVED", ignoreCase = true)
        val isRejected = status.equals("REJECTED", ignoreCase = true)
        val reason = data["reason"] ?: data["rejectionReason"]

        val title = if (isVerified) "🎉 Account Verified" else if (isRejected) "⚠️ Verification Update" else "📢 Verification Status Update"
        val message = data["message"] ?: if (isVerified) {
            "Your official verification credentials have been approved! You are now eligible for emergency response dispatch."
        } else if (isRejected) {
            if (!reason.isNullOrBlank()) "Your verification was rejected: $reason" else "Your verification was rejected. Please review credentials and resubmit."
        } else {
            "Your verification status has been updated to $status."
        }

        val notifType = if (isVerified) {
            NotificationType.VERIFICATION_APPROVED
        } else if (isRejected) {
            NotificationType.VERIFICATION_REJECTED
        } else {
            NotificationType.SYSTEM_ALERT
        }

        serviceScope.launch {
            try {
                ServiceLocator.authRepository?.restoreSession()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to restore session after verification update: ${e.message}")
            }
            try {
                val notif = Notification(
                    id = "FCM-${System.currentTimeMillis()}-VERIF",
                    recipientUserId = currentUser.id,
                    type = notifType,
                    title = title,
                    message = message
                )
                ServiceLocator.notificationRepository.sendNotification(notif)
            } catch (_: Exception) {}
        }
    }
}
