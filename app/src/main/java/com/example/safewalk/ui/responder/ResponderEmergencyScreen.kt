package com.example.safewalk.ui.responder

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.safewalk.domain.model.EmergencyStatus
import com.example.safewalk.ui.components.ButtonVariant
import com.example.safewalk.ui.components.EmergencyLocationPreview
import com.example.safewalk.ui.components.EmergencyStatusBadge
import com.example.safewalk.ui.components.LiveTrackingStatusCard
import com.example.safewalk.ui.components.SafeWalkButton
import com.example.safewalk.ui.components.SafeWalkTopBar
import com.example.safewalk.ui.components.UserAvatar
import com.example.safewalk.ui.theme.SafeWalkBlueSecondary
import com.example.safewalk.ui.theme.SafeWalkGreenSuccess
import com.example.safewalk.ui.theme.SafeWalkRedPrimary
import com.example.safewalk.ui.util.GoogleMapsIntentLauncher
import com.example.safewalk.viewmodel.EmergencyViewModel

@Composable
fun ResponderEmergencyScreen(
    emergencyId: String,
    onNavigateHome: () -> Unit,
    viewModel: EmergencyViewModel = viewModel()
) {
    LaunchedEffect(emergencyId) {
        viewModel.loadEmergency(emergencyId)
    }

    val emergency by viewModel.emergency.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val liveTrackingState by viewModel.liveTrackingState.collectAsState()
    val visibleParticipants by viewModel.visibleParticipants.collectAsState()

    Scaffold(
        topBar = {
            SafeWalkTopBar(
                title = if (emergency?.isTerminal == true) "Mission Summary" else "Responder Navigation",
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
                Text(text = "Loading responder mission...", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            val emg = emergency!!
            val currentUserId = currentUser?.id ?: ""
            val isCurrentResponderArrived = emg.isResponderArrived(currentUserId)
            val currentResponderDistance: Double = emg.responderDistances[currentUserId] ?: emg.distanceMeters ?: 0.0
            val canMarkArrived = !currentResponderDistance.isNaN() && !currentResponderDistance.isInfinite() && currentResponderDistance >= 0.0 && currentResponderDistance <= 25.0
            val isArrivalEligible = canMarkArrived
            val designated = emg.designatedArrivedResponder
            val isDesignatedArrivedResponder = designated != null && designated.id == currentUserId
            val isAnotherResponderArrived = emg.isAnotherResponderArrived(currentUserId)

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Mission Header
                item {
                    val headerBg = when (emg.status) {
                        EmergencyStatus.RESOLVED -> Color(0xFFE8F5E9)
                        EmergencyStatus.CANCELLED -> Color(0xFFF5F5F5)
                        EmergencyStatus.EXPIRED -> Color(0xFFFFF3E0)
                        else -> Color(0xFFE3F2FD)
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = headerBg),
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
                                        text = "Mission: ${emg.id}",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "Your Role: RESPONDER",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = SafeWalkBlueSecondary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                                EmergencyStatusBadge(status = emg.status)
                            }

                            if (emg.status == EmergencyStatus.CANCELLED) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "🚨 Emergency was cancelled by requester: ${emg.cancellationReason ?: "Cancelled"}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = SafeWalkRedPrimary
                                )
                            } else if (emg.status == EmergencyStatus.RESOLVED) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "🎉 Emergency successfully resolved by both parties!",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = SafeWalkGreenSuccess
                                )
                                if (emg.resolvedAt != null) {
                                    Text(
                                        text = "Resolved at: ${emg.formattedResolvedTime}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.DarkGray
                                    )
                                }
                            } else if (emg.status == EmergencyStatus.EXPIRED) {
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

                // Live Location Tracking Status (Step 5)
                item {
                    LiveTrackingStatusCard(
                        trackingState = liveTrackingState,
                        isTerminal = emg.isTerminal
                    )
                }

                // Map Preview UI (Requester Location)
                item {
                    val context = LocalContext.current
                    val currentResponderLoc = viewModel.getCurrentUserLocation()
                    EmergencyLocationPreview(
                        latitude = emg.requesterLocation.latitude,
                        longitude = emg.requesterLocation.longitude,
                        responderLatitude = currentResponderLoc?.latitude,
                        responderLongitude = currentResponderLoc?.longitude,
                        distanceMeters = emg.distanceMeters ?: currentResponderDistance,
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

                // Requester Contact Information (Post-acceptance visibility)
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
                                    text = "REQUESTER • LIVE LOCATION",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = SafeWalkBlueSecondary
                                )

                                val (liveBadgeText, liveBadgeColor, liveBadgeBg) = when {
                                    emg.isTerminal || emg.requesterLocation.status == com.example.safewalk.domain.model.LocationStatus.STOPPED ->
                                        Triple("STOPPED", Color.Gray, Color(0xFFEEEEEE))
                                    emg.requesterLocation.isStale() ->
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
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                UserAvatar(name = emg.requester.name, role = emg.requester.role, size = 44.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = emg.requester.name,
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "Emergency Phone: ${emg.requester.phone}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.DarkGray
                                    )
                                    Text(
                                        text = "Calculated distance: ${currentResponderDistance.toInt()} meters away",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = SafeWalkBlueSecondary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Exact Destination: ${emg.requesterLocation.address}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                            Text(
                                text = "Coordinates: ${emg.requesterLocation.formattedCoordinates} • Updated: ${emg.requesterLocation.formattedTimestamp}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.DarkGray,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        }
                    }
                }


                // Active Responders Section (Multi-Responder Dispatch)
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
                                    text = "Active Responders (${emg.acceptedResponders.size})",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "Multi-Responder Dispatch",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            emg.acceptedResponders.forEach { responder ->
                                val isArrived = emg.isResponderArrived(responder.id)
                                val distance = emg.responderDistances[responder.id]
                                val isYou = responder.id == currentUserId
                                val isThisDesignated = designated?.id == responder.id

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    UserAvatar(name = responder.name, role = responder.role, size = 38.dp)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = if (isYou) "${responder.name} (You)" else responder.name,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                            )
                                            if (isThisDesignated) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .background(Color(0xFFE3F2FD), RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                                ) {
                                                    Text(
                                                        text = "Designated",
                                                        color = SafeWalkBlueSecondary,
                                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 9.sp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = "${responder.role.displayName} • ${responder.phone}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Gray
                                        )
                                        val distLabel = if (isArrived) {
                                            "ARRIVED (<=25m)"
                                        } else if (distance != null) {
                                            "EN ROUTE (${distance.toInt()}m away)"
                                        } else {
                                            "ACCEPTED"
                                        }
                                        Text(
                                            text = "Status: $distLabel",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = if (isArrived) SafeWalkGreenSuccess else Color.DarkGray,
                                                fontWeight = if (isArrived) FontWeight.Bold else FontWeight.Normal
                                            )
                                        )
                                    }
                                    if (isArrived) {
                                        Box(
                                            modifier = Modifier
                                                .background(Color(0xFFE8F5E9), RoundedCornerShape(8.dp))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "Arrived",
                                                color = SafeWalkGreenSuccess,
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                            )
                                        }
                                    }
                                }
                            }

                            if (designated != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "• Designated Arrived Responder: ${designated.name} (${designated.role.displayName})",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = SafeWalkBlueSecondary
                                )
                            }
                        }
                    }
                }

                // Dual Confirmation Status Card (Two-Party Resolution Engine)
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Two-Party Resolution State",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Resolution requires independent confirmation from requester AND the designated arrived responder.",
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
                            if (designated != null) {
                                Text(
                                    text = "• Required Responder: ${designated.name}",
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

                // Responder Lifecycle Actions
                item {
                    Spacer(modifier = Modifier.height(4.dp))

                    if (!emg.isTerminal) {
                        if (!isCurrentResponderArrived) {
                            if (isAnotherResponderArrived) {
                                // Single-arrival guard: another responder already secured the arrived slot
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFFFF3E0), RoundedCornerShape(12.dp))
                                        .padding(14.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "⚠️ Another responder (${designated?.name}) already arrived on scene. SafeWalk single-arrival protocol designates them as primary responder.",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = Color(0xFFE65100)
                                    )
                                }
                            } else {
                                // Section 13: Arrival Action Button
                                Column {
                                    SafeWalkButton(
                                        text = if (isArrivalEligible) "Mark Arrived on Scene (<=25m) ✓" else "Cannot Mark Arrived (${currentResponderDistance.toInt()}m > 25m)",
                                        onClick = {
                                            viewModel.markArrived(currentResponderDistance)
                                        },
                                        enabled = isArrivalEligible,
                                        isLoading = uiState.isLoading,
                                        variant = if (isArrivalEligible) ButtonVariant.PRIMARY else ButtonVariant.SECONDARY
                                    )
                                    if (!isArrivalEligible) {
                                        Text(
                                            text = "Must be within 25 meters of requester to confirm arrival.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.Gray,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                            }

                            // Responder Withdrawal / Opt-out option
                            Spacer(modifier = Modifier.height(8.dp))
                            SafeWalkButton(
                                text = "Withdraw / Opt Out from Mission",
                                onClick = {
                                    viewModel.rejectEmergency(onSuccess = onNavigateHome)
                                },
                                isLoading = uiState.isLoading,
                                variant = ButtonVariant.SECONDARY
                            )
                        } else {
                            // Current responder has arrived
                            if (isDesignatedArrivedResponder) {
                                // Section 17 & 18: Designated arrived responder can confirm resolution
                                SafeWalkButton(
                                    text = if (emg.responderConfirmedResolution) "Responder Confirmed (Awaiting Requester)" else "Confirm Emergency Resolved",
                                    onClick = { viewModel.confirmResolution() },
                                    enabled = !emg.responderConfirmedResolution,
                                    isLoading = uiState.isLoading,
                                    variant = ButtonVariant.PRIMARY
                                )
                            } else {
                                // Section 20: Non-designated responder cannot satisfy confirmation
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFEEEEEE), RoundedCornerShape(12.dp))
                                        .padding(14.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "ℹ️ Only designated responder (${designated?.name ?: "first arrived"}) can confirm resolution",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = Color.DarkGray
                                    )
                                }
                            }
                        }
                    } else {
                        // Section 21: Terminal State - active actions disabled, navigation allowed
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
}
