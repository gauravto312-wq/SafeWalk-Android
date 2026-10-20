package com.example.safewalk.data.source.mock

import com.example.safewalk.domain.model.Notification
import com.example.safewalk.domain.model.NotificationType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * In-memory Mock FCM and Notification Data Source.
 * Simulates server-driven push notifications and user-level in-app notification center.
 */
class MockNotificationDataSource {
    private val notificationList = mutableListOf<Notification>()
    private val _notifications = MutableStateFlow<List<Notification>>(emptyList())
    val notifications: StateFlow<List<Notification>> = _notifications.asStateFlow()

    private val userNotificationFlows = ConcurrentHashMap<String, MutableStateFlow<List<Notification>>>()
    private val userUnreadCountFlows = ConcurrentHashMap<String, MutableStateFlow<Int>>()

    private val nextNotificationId = AtomicInteger(1001)

    init {
        // Seed initial welcoming notifications for mock accounts
        seedInitialNotifications()
    }

    private fun seedInitialNotifications() {
        val initial = listOf(
            Notification(
                id = "NOTIF-SEED-1",
                recipientUserId = "user-001",
                type = NotificationType.SYSTEM_ALERT,
                title = "Welcome to SafeWalk",
                message = "Your account is active. Stay safe and connect with your local community network.",
                timestamp = System.currentTimeMillis() - 3600000L,
                isRead = true
            ),
            Notification(
                id = "NOTIF-SEED-2",
                recipientUserId = "user-002",
                type = NotificationType.SYSTEM_ALERT,
                title = "Responder Network Ready",
                message = "You are registered as a community responder. Keep notifications enabled to receive nearby alerts.",
                timestamp = System.currentTimeMillis() - 7200000L,
                isRead = false
            )
        )
        synchronized(notificationList) {
            notificationList.addAll(initial)
            syncFlows()
        }
    }

    private fun syncFlows() {
        val currentList = synchronized(notificationList) { notificationList.toList() }
        _notifications.value = currentList
        val allUserIds = (userNotificationFlows.keys + userUnreadCountFlows.keys).toSet()
        allUserIds.forEach { userId ->
            val userNotifs = currentList.filter { it.recipientUserId == userId }
            userNotificationFlows[userId]?.value = userNotifs
            userUnreadCountFlows[userId]?.value = userNotifs.count { !it.isRead }
        }
    }

    fun getNotificationsForUser(userId: String): StateFlow<List<Notification>> {
        return userNotificationFlows.computeIfAbsent(userId) {
            val userNotifs = synchronized(notificationList) { notificationList.filter { it.recipientUserId == userId } }
            MutableStateFlow(userNotifs)
        }.asStateFlow()
    }

    fun getUnreadCount(userId: String): StateFlow<Int> {
        return userUnreadCountFlows.computeIfAbsent(userId) {
            val unread = synchronized(notificationList) { notificationList.count { it.recipientUserId == userId && !it.isRead } }
            MutableStateFlow(unread)
        }.asStateFlow()
    }

    /**
     * Dispatches a mock FCM push notification with deduplication.
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
    ): Notification {
        val newNotif = Notification(
            id = "NOTIF-${nextNotificationId.getAndIncrement()}",
            recipientUserId = recipientUserId,
            type = type,
            title = title,
            message = message,
            relatedEmergencyId = emergencyId,
            senderId = senderId,
            senderName = senderName,
            timestamp = System.currentTimeMillis(),
            isRead = false,
            navigationTarget = navigationTarget
        )

        synchronized(notificationList) {
            // Deduplication rule (Section 9.4):
            // Check if identical notification (same recipient, type, relatedEmergencyId) already exists
            val isDuplicate = notificationList.any { existing ->
                existing.recipientUserId == recipientUserId &&
                existing.type == type &&
                existing.relatedEmergencyId == emergencyId &&
                (emergencyId != null || existing.title == title) &&
                !existing.isRead &&
                (System.currentTimeMillis() - existing.timestamp) < 60000L // 60s deduplication window
            }

            if (!isDuplicate) {
                notificationList.add(0, newNotif) // Most recent first
                syncFlows()
            }
        }
        return newNotif
    }

    fun markAsRead(notificationId: String): Result<Unit> {
        synchronized(notificationList) {
            val idx = notificationList.indexOfFirst { it.id == notificationId }
            if (idx != -1) {
                notificationList[idx] = notificationList[idx].copy(isRead = true)
                syncFlows()
                return Result.success(Unit)
            }
        }
        return Result.failure(IllegalArgumentException("Notification not found"))
    }

    fun markAllAsRead(userId: String): Result<Unit> {
        synchronized(notificationList) {
            notificationList.indices.forEach { i ->
                if (notificationList[i].recipientUserId == userId && !notificationList[i].isRead) {
                    notificationList[i] = notificationList[i].copy(isRead = true)
                }
            }
            syncFlows()
        }
        return Result.success(Unit)
    }

    fun deleteNotification(notificationId: String): Result<Unit> {
        synchronized(notificationList) {
            val removed = notificationList.removeAll { it.id == notificationId }
            if (removed) {
                syncFlows()
                return Result.success(Unit)
            }
        }
        return Result.failure(IllegalArgumentException("Notification not found"))
    }

    fun clearNotifications(userId: String): Result<Unit> {
        synchronized(notificationList) {
            notificationList.removeAll { it.recipientUserId == userId }
            syncFlows()
        }
        return Result.success(Unit)
    }

    fun reset() {
        synchronized(notificationList) {
            notificationList.clear()
            seedInitialNotifications()
            syncFlows()
        }
    }
}
