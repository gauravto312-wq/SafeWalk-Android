package com.example.safewalk.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.safewalk.core.common.ServiceLocator
import com.example.safewalk.domain.model.Notification
import com.example.safewalk.domain.model.NotificationType
import com.example.safewalk.domain.repository.AuthRepository
import com.example.safewalk.domain.repository.NotificationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class NotificationFilter {
    ALL,
    UNREAD,
    EMERGENCIES
}

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationViewModel(
    private val notificationRepository: NotificationRepository = ServiceLocator.notificationRepository,
    private val authRepository: AuthRepository = ServiceLocator.authRepository,
    private val externalScope: CoroutineScope? = null
) : ViewModel() {

    private val scope: CoroutineScope
        get() = externalScope ?: viewModelScope

    private val _selectedFilter = MutableStateFlow(NotificationFilter.ALL)
    val selectedFilter: StateFlow<NotificationFilter> = _selectedFilter.asStateFlow()

    val currentUserId: Flow<String?> = authRepository.currentUser.map { it?.id }

    val unreadCount: StateFlow<Int> = authRepository.currentUser
        .flatMapLatest { user ->
            if (user != null) {
                notificationRepository.getUnreadCount(user.id)
            } else {
                flowOf(0)
            }
        }.stateIn(scope, SharingStarted.WhileSubscribed(5000), 0)

    val notifications: StateFlow<List<Notification>> = combine(
        authRepository.currentUser.flatMapLatest { user ->
            if (user != null) {
                notificationRepository.getNotificationsForUser(user.id)
            } else {
                flowOf(emptyList())
            }
        },
        _selectedFilter
    ) { notifs, filter ->
        when (filter) {
            NotificationFilter.ALL -> notifs
            NotificationFilter.UNREAD -> notifs.filter { !it.isRead }
            NotificationFilter.EMERGENCIES -> notifs.filter {
                it.type == NotificationType.EMERGENCY_SOS_NEARBY ||
                it.type == NotificationType.RESPONDER_ACCEPTED ||
                it.type == NotificationType.RESPONDER_ARRIVED ||
                it.type == NotificationType.EMERGENCY_RESOLVED ||
                it.type == NotificationType.EMERGENCY_CANCELLED ||
                it.type == NotificationType.TRUSTED_CONTACT_ALERT
            }
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setFilter(filter: NotificationFilter) {
        _selectedFilter.value = filter
    }

    fun markAsRead(notificationId: String) {
        scope.launch {
            notificationRepository.markAsRead(notificationId)
        }
    }

    fun markAllAsRead() {
        val user = authRepository.currentUser.value ?: return
        scope.launch {
            notificationRepository.markAllAsRead(user.id)
        }
    }

    fun deleteNotification(notificationId: String) {
        scope.launch {
            notificationRepository.deleteNotification(notificationId)
        }
    }

    fun clearAll() {
        val user = authRepository.currentUser.value ?: return
        scope.launch {
            notificationRepository.clearNotifications(user.id)
        }
    }
}
