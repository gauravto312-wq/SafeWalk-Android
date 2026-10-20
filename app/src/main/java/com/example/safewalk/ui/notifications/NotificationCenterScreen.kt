package com.example.safewalk.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.safewalk.domain.model.Notification
import com.example.safewalk.domain.model.NotificationType
import com.example.safewalk.ui.components.SafeWalkTopBar
import com.example.safewalk.ui.theme.SafeWalkBlueSecondary
import com.example.safewalk.ui.theme.SafeWalkGreenSuccess
import com.example.safewalk.ui.theme.SafeWalkRedPrimary
import com.example.safewalk.viewmodel.NotificationFilter
import com.example.safewalk.viewmodel.NotificationViewModel

@Composable
fun NotificationCenterScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEmergency: (String) -> Unit = {},
    viewModel: NotificationViewModel = viewModel()
) {
    val notifications by viewModel.notifications.collectAsState()
    val unreadCount by viewModel.unreadCount.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()

    Scaffold(
        topBar = {
            SafeWalkTopBar(
                title = "Notification Center",
                onBackClick = onNavigateBack,
                actions = {
                    Row(
                        modifier = Modifier.padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { viewModel.markAllAsRead() }) {
                            Text("Mark All Read", fontSize = 12.sp)
                        }
                        TextButton(onClick = { viewModel.clearAll() }) {
                            Text("Clear", color = SafeWalkRedPrimary, fontSize = 12.sp)
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Filter Chips + Unread counter
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedFilter == NotificationFilter.ALL,
                        onClick = { viewModel.setFilter(NotificationFilter.ALL) },
                        label = { Text("All") }
                    )
                    FilterChip(
                        selected = selectedFilter == NotificationFilter.UNREAD,
                        onClick = { viewModel.setFilter(NotificationFilter.UNREAD) },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Unread")
                                if (unreadCount > 0) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(SafeWalkRedPrimary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "$unreadCount",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    )
                    FilterChip(
                        selected = selectedFilter == NotificationFilter.EMERGENCIES,
                        onClick = { viewModel.setFilter(NotificationFilter.EMERGENCIES) },
                        label = { Text("Emergencies") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (notifications.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "🔔", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No notifications yet",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "All safe and quiet. New alerts will appear here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(notifications, key = { it.id }) { notif ->
                        NotificationCard(
                            notification = notif,
                            onMarkAsRead = { viewModel.markAsRead(notif.id) },
                            onDelete = { viewModel.deleteNotification(notif.id) },
                            onEmergencyClick = {
                                if (notif.relatedEmergencyId != null) {
                                    viewModel.markAsRead(notif.id)
                                    onNavigateToEmergency(notif.relatedEmergencyId)
                                }
                            }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(
    notification: Notification,
    onMarkAsRead: () -> Unit,
    onDelete: () -> Unit,
    onEmergencyClick: () -> Unit
) {
    val (icon, tint) = when (notification.type) {
        NotificationType.EMERGENCY_SOS_NEARBY -> "🚨" to SafeWalkRedPrimary
        NotificationType.TRUSTED_CONTACT_ALERT -> "🆘" to SafeWalkRedPrimary
        NotificationType.RESPONDER_ACCEPTED -> "🏃" to SafeWalkBlueSecondary
        NotificationType.RESPONDER_ARRIVED -> "📍" to SafeWalkGreenSuccess
        NotificationType.EMERGENCY_RESOLVED -> "🛡️" to SafeWalkGreenSuccess
        NotificationType.EMERGENCY_CANCELLED -> "🛑" to Color(0xFFE65100)
        NotificationType.VERIFICATION_APPROVED -> "✅" to SafeWalkGreenSuccess
        NotificationType.VERIFICATION_REJECTED -> "⚠️" to SafeWalkRedPrimary
        NotificationType.SYSTEM_ALERT -> "📢" to Color(0xFF555555)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (!notification.isRead) {
                    onMarkAsRead()
                }
                if (notification.relatedEmergencyId != null) {
                    onEmergencyClick()
                }
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (notification.isRead) MaterialTheme.colorScheme.surface else Color(0xFFF0F4FF)
        ),
        elevation = CardDefaults.cardElevation(if (notification.isRead) 1.dp else 3.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Text(text = icon, fontSize = 22.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = notification.title,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = if (notification.isRead) FontWeight.Medium else FontWeight.Bold
                                )
                            )
                            if (!notification.isRead) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(SafeWalkBlueSecondary)
                                )
                            }
                        }
                        Text(
                            text = notification.formattedTime,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = notification.message,
                style = MaterialTheme.typography.bodySmall,
                color = if (notification.isRead) Color.DarkGray else Color.Black
            )

            if (notification.relatedEmergencyId != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Ref: ${notification.relatedEmergencyId}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                    TextButton(onClick = onEmergencyClick) {
                        Text("View Emergency →", fontSize = 12.sp, color = SafeWalkBlueSecondary)
                    }
                }
            }
        }
    }
}
