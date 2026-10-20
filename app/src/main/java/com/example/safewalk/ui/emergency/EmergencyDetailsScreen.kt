package com.example.safewalk.ui.emergency

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.safewalk.domain.model.EmergencyStatus
import com.example.safewalk.ui.components.ButtonVariant
import com.example.safewalk.ui.components.EmergencyLocationPreview
import com.example.safewalk.ui.components.EmergencyStatusBadge
import com.example.safewalk.ui.components.MapPlaceholderCard
import com.example.safewalk.ui.components.SafeWalkButton
import com.example.safewalk.ui.components.SafeWalkTopBar
import com.example.safewalk.ui.components.UserAvatar
import com.example.safewalk.ui.theme.SafeWalkBlueSecondary
import com.example.safewalk.ui.theme.SafeWalkGreenSuccess
import com.example.safewalk.ui.theme.SafeWalkRedPrimary
import com.example.safewalk.ui.util.GoogleMapsIntentLauncher
import com.example.safewalk.viewmodel.EmergencyViewModel

@Composable
fun EmergencyDetailsScreen(
    emergencyId: String,
    onNavigateToResponderFlow: (String) -> Unit,
    onNavigateToLiveEmergency: (String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: EmergencyViewModel = viewModel()
) {
    LaunchedEffect(emergencyId) {
        viewModel.loadEmergency(emergencyId)
    }

    val emergency by viewModel.emergency.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val visibleParticipants by viewModel.visibleParticipants.collectAsState()

    Scaffold(
        topBar = {
            SafeWalkTopBar(
                title = "Emergency Details",
                onBackClick = onNavigateBack
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
                Text(text = "Loading emergency record...", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            val emg = emergency!!
            val isAlreadyAccepted = currentUser != null && emg.isUserAcceptedResponder(currentUser!!.id)
            val isRequester = currentUser != null && emg.isUserRequester(currentUser!!.id)
            val isTrustedContact = emg.isTrustedContactViewer ||
                    (currentUser != null && emg.requester.emergencyContacts.any { it.equals(currentUser!!.safeWalkId, ignoreCase = true) })
            val canViewDetailedLocation = isRequester || isAlreadyAccepted || isTrustedContact

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Status Card
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
                                    text = "Emergency ID: ${emg.numericId}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                EmergencyStatusBadge(status = emg.status)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Reported at ${emg.formattedTime}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                }

                // Requester Information Card
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
                                    text = "Requester Information",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                if (isRequester) {
                                    Box(
                                        modifier = Modifier
                                            .background(SafeWalkRedPrimary.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "YOUR SOS (REQUESTER)",
                                            color = SafeWalkRedPrimary,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }
                                } else if (isTrustedContact) {
                                    Box(
                                        modifier = Modifier
                                            .background(Color(0xFFFFF3E0), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "TRUSTED CONTACT MONITORING",
                                            color = Color(0xFFE65100),
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                UserAvatar(name = emg.requester.name, role = emg.requester.role, size = 44.dp, avatarUrl = emg.requester.avatarUrl)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = emg.requester.name,
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = if (canViewDetailedLocation) "Phone: ${emg.requester.phone}" else "Contact: Available upon acceptance",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.DarkGray
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))

                            // Requester Location Info
                            Text(
                                text = "Location: ${emg.requesterLocation.address.ifEmpty { "Lat ${emg.requesterLocation.latitude}, Lng ${emg.requesterLocation.longitude}" }}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.DarkGray
                            )
                            Text(
                                text = "Coordinates: ${emg.requesterLocation.formattedCoordinates}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                        }
                    }
                }

                // Map Preview
                item {
                    val context = LocalContext.current
                    val viewerLoc = if (!isRequester) viewModel.getCurrentUserLocation() else null
                    EmergencyLocationPreview(
                        latitude = emg.requesterLocation.latitude,
                        longitude = emg.requesterLocation.longitude,
                        responderLatitude = viewerLoc?.latitude,
                        responderLongitude = viewerLoc?.longitude,
                        distanceMeters = emg.distanceMeters,
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

                // Accepted Responders Section (Multi-responder support)
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
                                    text = "Accepted Responders (${emg.acceptedResponders.size})",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "No Responder Cap",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            if (emg.acceptedResponders.isEmpty()) {
                                Text(
                                    text = "No responders have accepted yet. Be the first to assist!",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            } else {
                                emg.acceptedResponders.forEach { responder ->
                                    val isArrived = emg.isResponderArrived(responder.id)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        UserAvatar(name = responder.name, role = responder.role, size = 32.dp, avatarUrl = responder.avatarUrl)
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(text = responder.name, fontWeight = FontWeight.SemiBold)
                                            Text(
                                                text = "${responder.role.displayName} • ${if (isArrived) "Arrived on Scene" else "En route"}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (isArrived) SafeWalkGreenSuccess else Color.Gray
                                            )
                                        }
                                        if (isArrived) {
                                            Box(
                                                modifier = Modifier
                                                    .background(Color(0xFFE8F5E9), RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "ARRIVED (<=25m)",
                                                    color = SafeWalkGreenSuccess,
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

                // Error Message if any
                if (uiState.errorMessage != null) {
                    item {
                        Text(
                            text = uiState.errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }

                // Interactive Actions
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    if (!emg.isTerminal) {
                        if (isAlreadyAccepted) {
                            SafeWalkButton(
                                text = "Go to Responder Navigation →",
                                onClick = { onNavigateToResponderFlow(emg.id) },
                                variant = ButtonVariant.SECONDARY
                            )
                        } else if (isRequester) {
                            // Section 28: Requester CANNOT accept own SOS
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFEEEEEE), RoundedCornerShape(12.dp))
                                    .padding(14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "You created this SOS emergency (Requester)",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color.DarkGray
                                )
                            }
                        } else if (isTrustedContact) {
                            // 🛡️ Trusted Contact Monitoring Mode
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                                elevation = CardDefaults.cardElevation(2.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "🛡️ Trusted Contact Monitoring Mode",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFFE65100)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "You are monitoring this emergency as a designated trusted contact. You can view live coordinates and assist remotely without accepting as a physical responder.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF5D4037)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val context = LocalContext.current
                                        SafeWalkButton(
                                            text = "📞 Call Requester",
                                            onClick = {
                                                val phone = emg.requester.phone
                                                if (phone.isNotBlank()) {
                                                    val intent = android.content.Intent(
                                                        android.content.Intent.ACTION_DIAL,
                                                        android.net.Uri.parse("tel:$phone")
                                                    )
                                                    context.startActivity(intent)
                                                }
                                            },
                                            modifier = Modifier.weight(1f),
                                            variant = ButtonVariant.PRIMARY
                                        )
                                        SafeWalkButton(
                                            text = "🚨 Call Police (112)",
                                            onClick = {
                                                val intent = android.content.Intent(
                                                    android.content.Intent.ACTION_DIAL,
                                                    android.net.Uri.parse("tel:112")
                                                )
                                                context.startActivity(intent)
                                            },
                                            modifier = Modifier.weight(1f),
                                            variant = ButtonVariant.SECONDARY
                                        )
                                    }
                                }
                            }
                        } else {
                            // Eligible responder can accept
                            SafeWalkButton(
                                text = "Accept Emergency & Respond",
                                onClick = {
                                    viewModel.acceptEmergency {
                                        onNavigateToResponderFlow(emg.id)
                                    }
                                },
                                isLoading = uiState.isLoading,
                                variant = ButtonVariant.PRIMARY
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    SafeWalkButton(
                        text = "View Live Emergency Tracking",
                        onClick = { onNavigateToLiveEmergency(emg.id) },
                        variant = ButtonVariant.OUTLINED
                    )
                }
            }
        }
    }
}
