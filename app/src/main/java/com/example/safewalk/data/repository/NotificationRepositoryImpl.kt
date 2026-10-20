package com.example.safewalk.data.repository

import com.example.safewalk.data.source.mock.MockNotificationDataSource
import com.example.safewalk.domain.model.Notification
import com.example.safewalk.domain.model.NotificationType
import com.example.safewalk.domain.repository.NotificationRepository
import kotlinx.coroutines.flow.StateFlow

/**
 * Concrete implementation of NotificationRepository using MockNotificationDataSource.
 * Architecturally isolated so that real FCM service / REST API integration can replace it seamlessly.
 */
class NotificationRepositoryImpl(
    private val dataSource: MockNotificationDataSource
) : NotificationRepository {

    override fun getNotificationsForUser(userId: String): StateFlow<List<Notification>> {
        return dataSource.getNotificationsForUser(userId)
    }

    override fun getUnreadCount(userId: String): StateFlow<Int> {
        return dataSource.getUnreadCount(userId)
    }

    override suspend fun sendNotification(notification: Notification): Result<Notification> {
        val created = dataSource.dispatchFcmPush(
            type = notification.type,
            recipientUserId = notification.recipientUserId,
            title = notification.title,
            message = notification.message,
            emergencyId = notification.relatedEmergencyId,
            senderId = notification.senderId,
            senderName = notification.senderName,
            navigationTarget = notification.navigationTarget
        )
        return Result.success(created)
    }

    override suspend fun markAsRead(notificationId: String): Result<Unit> {
        return dataSource.markAsRead(notificationId)
    }

    override suspend fun markAllAsRead(userId: String): Result<Unit> {
        return dataSource.markAllAsRead(userId)
    }

    override suspend fun clearNotifications(userId: String): Result<Unit> {
        return dataSource.clearNotifications(userId)
    }

    override suspend fun deleteNotification(notificationId: String): Result<Unit> {
        return dataSource.deleteNotification(notificationId)
    }

    override fun dispatchFcmPush(
        type: NotificationType,
        recipientUserId: String,
        title: String,
        message: String,
        emergencyId: String?,
        senderId: String?,
        senderName: String?,
        navigationTarget: String?
    ): Notification {
        return dataSource.dispatchFcmPush(
            type = type,
            recipientUserId = recipientUserId,
            title = title,
            message = message,
            emergencyId = emergencyId,
            senderId = senderId,
            senderName = senderName,
            navigationTarget = navigationTarget
        )
    }
}
