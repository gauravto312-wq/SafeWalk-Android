package com.example.safewalk.domain.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Domain model representing a notification event in SafeWalk.
 * Designed to interface cleanly with both MockFCM and future real Firebase Cloud Messaging (FCM).
 */
data class Notification(
    val id: String,
    val recipientUserId: String,
    val type: NotificationType,
    val title: String,
    val message: String,
    val relatedEmergencyId: String? = null,
    val senderId: String? = null,
    val senderName: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val navigationTarget: String? = null
) {
    val formattedTime: String
        get() {
            val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }

    /**
     * Logical deduplication key to prevent duplicate alerts for the same event and recipient.
     */
    val deduplicationKey: String
        get() = "${recipientUserId}_${type.name}_${relatedEmergencyId ?: "none"}"
}
