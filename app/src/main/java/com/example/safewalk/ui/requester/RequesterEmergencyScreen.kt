package com.example.safewalk.ui.requester

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.safewalk.domain.model.EmergencyStatus
import com.example.safewalk.ui.components.ButtonVariant
import com.example.safewalk.ui.components.EmergencyStatusBadge
import androidx.compose.ui.platform.LocalContext
import com.example.safewalk.ui.components.EmergencyLocationPreview
import com.example.safewalk.ui.components.LiveTrackingStatusCard
import com.example.safewalk.ui.util.GoogleMapsIntentLauncher
import com.example.safewalk.ui.components.SafeWalkButton
import com.example.safewalk.ui.components.SafeWalkTextField
import com.example.safewalk.ui.components.SafeWalkTopBar
import com.example.safewalk.ui.components.UserAvatar
import com.example.safewalk.ui.theme.SafeWalkBlueSecondary
import com.example.safewalk.ui.theme.SafeWalkGreenSuccess
import com.example.safewalk.ui.theme.SafeWalkRedPrimary
import com.example.safewalk.viewmodel.EmergencyViewModel

@Composable
fun RequesterEmergencyScreen(
    emergencyId: String,
    onNavigateHome: () -> Unit,
    viewModel: EmergencyViewModel = viewModel()
) {
    LaunchedEffect(emergencyId) {
        viewModel.loadEmergency(emergencyId)
    }

    DisposableEffect(emergencyId) {
        onDispose {
            viewModel.stopCancellationCountdown()
        }
    }

    val currentUser by viewModel.currentUser.collectAsState()
    val emergency by viewModel.emergency.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val canCancel by viewModel.canCancel.collectAsState()
    val remainingMillis by viewModel.cancellationRemainingMillis.collectAsState()
    val liveTrackingState by viewModel.liveTrackingState.collectAsState()
    val visibleParticipants by viewModel.visibleParticipants.collectAsState()

    var showCancelDialog by remember { mutableStateOf(false) }
    var cancelPassword by remember(currentUser) {
        mutableStateOf(currentUser?.emergencyPassword ?: "Password123!")
    }
    var cancelReason by remember { mutableStateOf("User requested cancellation") }

    Scaffold(
        topBar = {
            SafeWalkTopBar(
                title = if (emergency?.isTerminal == true) "Emergency Details" else "Emergency SOS Active",
                onBackClick = onNavigateHome
            )
        }
    ) { innerPadding ->
        if (emergency == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "Loading emergency state...", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            val emg = emergency!!

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Emergency Alert Header
                item {
                    val containerColor = when (emg.status) {
                        EmergencyStatus.RESOLVED -> Color(0xFFE8F5E9)
                        EmergencyStatus.CANCELLED -> Color(0xFFF5F5F5)
                        EmergencyStatus.EXPIRED -> Color(0xFFFFF3E0)
                        else -> Color(0xFFFFEBEE)
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = containerColor),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Emergency ID: ${emg.numericId}",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "Your Role: REQUESTER",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = SafeWalkRedPrimary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    Text(
                                        text = "Created ${emg.formattedTime}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.DarkGray
                                    )
                                }
                                EmergencyStatusBadge(status = emg.status)
                            }

                            when (emg.status) {
                                EmergencyStatus.ACTIVE -> {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .background(SafeWalkRedPrimary, CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Searching for nearby eligible responders...",
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = SafeWalkRedPrimary
                                        )
                                    }
                                }
                                EmergencyStatus.ACCEPTED -> {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .background(SafeWalkBlueSecondary, CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Responders assigned! Tracking live assistance on scene...",
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = SafeWalkBlueSecondary
                                        )
                                    }
                                }
                                EmergencyStatus.RESOLVED -> {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "🎉 Emergency successfully resolved by both parties!",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = SafeWalkGreenSuccess
                                    )
                                    if (emg.formattedResolvedTime != null) {
                                        Text(
                                            text = "Resolved at: ${emg.formattedResolvedTime}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.DarkGray
                                        )
                                    }
                                }
                                EmergencyStatus.CANCELLED -> {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "Emergency cancelled: ${emg.cancellationReason ?: "Cancelled by requester"}",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = Color.Gray
                                    )
                                }
                                EmergencyStatus.EXPIRED -> {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "⌛ Emergency expired after 5 hours.",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = Color(0xFFE65100)
                                    )
                                    if (emg.formattedExpiredTime != null) {
                                        Text(
                                            text = "Expired at: ${emg.formattedExpiredTime}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.DarkGray
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 5-second SOS Cancellation Banner (strictly within 5.000s of emergency.createdAt)
                if (!emg.isTerminal && canCancel && emg.canCancel()) {
                    item {
                        val secondsLeft = (remainingMillis / 1000L).let { if (it > 0) it else 1L }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Cancellation available for ${secondsLeft}s",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = SafeWalkRedPrimary,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                                SafeWalkButton(
                                    text = "Cancel SOS Emergency",
                                    onClick = {
                                        showCancelDialog = true
                                    },
                                    variant = ButtonVariant.DANGER
                                )
                            }
                        }
                    }
                }

                // Live Location Tracking Status (Step 5)
                item {
                    LiveTrackingStatusCard(
                        trackingState = liveTrackingState,
                        isTerminal = emg.isTerminal
                    )
                }

                // Map Preview UI
                item {
                    val context = LocalContext.current
                    EmergencyLocationPreview(
                        latitude = emg.requesterLocation.latitude,
                        longitude = emg.requesterLocation.longitude,
                        distanceMeters = null,
                        address = emg.requesterLocation.address,
                        emergencyId = emg.id,
                        participants = visibleParticipants,
                        onOpenGoogleMaps = {
                            GoogleMapsIntentLauncher.launch(
                                context = context,
                                latitude = emg.requesterLocation.latitude,
                                longitude = emg.requesterLocation.longitude,
                                label = "SafeWalk Emergency #${emg.numericId}"
                            )
                        }
                    )
                }

                // Accepted Responders Section (Multi-responder support!)
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "LIVE RESPONDERS (${emg.acceptedResponders.size})",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "Bidirectional Live GPS",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SafeWalkBlueSecondary
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            if (emg.acceptedResponders.isEmpty()) {
                                Text(
                                    text = "Alerts sent. Waiting for nearby responders or emergency contacts to accept...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            } else {
                                emg.acceptedResponders.forEach { responder ->
                                    val isArrived = emg.isResponderArrived(responder.id)
                                    val distance = emg.responderDistances[responder.id]
                                    val respLoc = emg.responderLocations[responder.id]

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        UserAvatar(name = responder.name, role = responder.role, size = 42.dp)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = responder.name,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                            )
                                            Text(
                                                text = "RESPONDER • ${responder.safeWalkId}",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                                color = SafeWalkBlueSecondary
                                            )
                                            Text(
                                                text = if (distance != null) "${distance.toInt()} m away" else "Calculating...",
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                                color = Color.DarkGray
                                            )
                                            if (respLoc != null) {
                                                Text(
                                                    text = "GPS: ${respLoc.formattedCoordinates} • ${respLoc.formattedTimestamp}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.Gray,
                                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                                )
                                            }
                                        }

                                        Column(
                                            horizontalAlignment = Alignment.End,
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            if (isArrived) {
                                                Box(
                                                    modifier = Modifier
                                                        .background(Color(0xFFE8F5E9), RoundedCornerShape(6.dp))
                                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "ARRIVED",
                                                        color = SafeWalkGreenSuccess,
                                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                                    )
                                                }
                                            }

                                            val (liveBadgeText, liveBadgeColor, liveBadgeBg) = when {
                                                emg.isTerminal || respLoc?.status == com.example.safewalk.domain.model.LocationStatus.STOPPED ->
                                                    Triple("STOPPED", Color.Gray, Color(0xFFEEEEEE))
                                                respLoc == null ->
                                                    Triple("WAITING FOR GPS", Color(0xFF757575), Color(0xFFF5F5F5))
                                                respLoc.isStale() ->
                                                    Triple("LOCATION STALE", Color(0xFFE65100), Color(0xFFFFF3E0))
                                                else ->
                                                    Triple("LIVE", SafeWalkGreenSuccess, Color(0xFFE8F5E9))
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .background(liveBadgeBg, RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = liveBadgeText,
                                                    color = liveBadgeColor,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Dual Confirmation Status Section
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Dual-Confirmation Resolution State",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Two-party resolution: Requires independent confirmation from both requester and the designated arrived responder.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "• Requester Confirmation: ${if (emg.requesterConfirmedResolution) "CONFIRMED ✓" else "PENDING"}",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = if (emg.requesterConfirmedResolution) SafeWalkGreenSuccess else Color.Gray
                            )
                            Text(
                                text = "• Responder Confirmation: ${if (emg.responderConfirmedResolution) "CONFIRMED ✓" else "PENDING"}",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = if (emg.responderConfirmedResolution) SafeWalkGreenSuccess else Color.Gray
                            )

                            val designated = emg.designatedArrivedResponder
                            if (designated != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "• Designated Arrived Responder: ${designated.name} (${designated.role.displayName})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.DarkGray
                                )
                            }
                        }
                    }
                }

                // Error Feedback
                if (uiState.errorMessage != null) {
                    item {
                        Text(
                            text = uiState.errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }

                // Interactive Action Buttons
                item {
                    Spacer(modifier = Modifier.height(4.dp))

                    if (!emg.isTerminal) {
                        // Section 23: Requester Resolve Action (Available only when any accepted responder reaches <= 25m)
                        if (emg.canRequesterResolve()) {
                            if (!emg.requesterConfirmedResolution) {
                                SafeWalkButton(
                                    text = "CONFIRM EMERGENCY RESOLVED",
                                    onClick = { viewModel.confirmResolution() },
                                    isLoading = uiState.isLoading,
                                    variant = ButtonVariant.PRIMARY
                                )
                            } else {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "⏳ Requester Resolution Confirmed",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = Color(0xFFB78103)
                                        )
                                        Text(
                                            text = "Awaiting designated responder confirmation to finalize resolution.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.DarkGray
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Terminal state - actions disabled, navigation allowed
                        SafeWalkButton(
                            text = "Return to Dashboard",
                            onClick = onNavigateHome,
                            variant = ButtonVariant.SECONDARY
                        )
                    }
                }
            }
        }
    }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = {
                Text(
                    text = "Verify Cancellation PIN",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Enter your Emergency PIN to authorize cancelling this SOS emergency:",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.DarkGray
                    )
                    SafeWalkTextField(
                        value = cancelPassword,
                        onValueChange = { cancelPassword = it },
                        label = "Emergency PIN",
                        placeholder = "Default: 1234"
                    )
                    SafeWalkTextField(
                        value = cancelReason,
                        onValueChange = { cancelReason = it },
                        label = "Cancellation Reason",
                        placeholder = "e.g. False alarm"
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCancelDialog = false
                        viewModel.cancelEmergency(
                            emergencyPassword = cancelPassword,
                            reason = cancelReason
                        ) {
                            onNavigateHome()
                        }
                    }
                ) {
                    Text(
                        text = "Confirm Cancel",
                        color = SafeWalkRedPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) {
                    Text("Dismiss")
                }
            }
        )
    }
}
