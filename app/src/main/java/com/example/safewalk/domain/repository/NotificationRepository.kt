package com.example.safewalk.domain.repository

import com.example.safewalk.domain.model.Notification
import com.example.safewalk.domain.model.NotificationType
import kotlinx.coroutines.flow.StateFlow

/**
 * Domain repository contract for Notifications and FCM Push Service.
 * Decoupled from concrete Firebase implementations to allow clean migration from MockFCM to real FCM.
 */
interface NotificationRepository {
    fun getNotificationsForUser(userId: String): StateFlow<List<Notification>>
    fun getUnreadCount(userId: String): StateFlow<Int>

    suspend fun sendNotification(notification: Notification): Result<Notification>
    suspend fun markAsRead(notificationId: String): Result<Unit>
    suspend fun markAllAsRead(userId: String): Result<Unit>
    suspend fun clearNotifications(userId: String): Result<Unit>
    suspend fun deleteNotification(notificationId: String): Result<Unit>

    /**
     * Simulates receiving an FCM push notification payload.
     */
    fun dispatchFcmPush(
        type: NotificationType,
        recipientUserId: String,
        title: String,
        message: String,
        emergencyId: String? = null,
        senderId: String? = null,
        senderName: String? = null,
        navigationTarget: String? = null
    ): Notification
}
