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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.safewalk.ui.components.ButtonVariant
import com.example.safewalk.ui.components.EmergencyLocationPreview
import com.example.safewalk.ui.components.EmergencyStatusBadge
import com.example.safewalk.ui.components.MapPlaceholderCard
import com.example.safewalk.ui.components.SafeWalkButton
import com.example.safewalk.ui.components.SafeWalkTopBar
import com.example.safewalk.ui.components.UserAvatar
import com.example.safewalk.ui.theme.SafeWalkRedPrimary
import com.example.safewalk.ui.util.GoogleMapsIntentLauncher
import com.example.safewalk.viewmodel.EmergencyViewModel

@Composable
fun LiveEmergencyScreen(
    emergencyId: String,
    onNavigateBack: () -> Unit,
    viewModel: EmergencyViewModel = viewModel()
) {
    LaunchedEffect(emergencyId) {
        viewModel.loadEmergency(emergencyId)
    }

    val emergency by viewModel.emergency.collectAsState()
    val visibleParticipants by viewModel.visibleParticipants.collectAsState()

    Scaffold(
        topBar = {
            SafeWalkTopBar(
                title = "Live Emergency Monitor",
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
                Text(text = "Loading live telemetry...", style = MaterialTheme.typography.bodyLarge)
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
                                    text = emg.id,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                EmergencyStatusBadge(status = emg.status)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Telemetry channel active • Backend WebSocket live feed",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                }

                // Interactive Map Preview
                item {
                    val context = LocalContext.current
                    EmergencyLocationPreview(
                        latitude = emg.requesterLocation.latitude,
                        longitude = emg.requesterLocation.longitude,
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

                // Coordinates Inspection Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "GPS Telemetry",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Requester: ${emg.requester.name} (${emg.requesterLocation.formattedCoordinates})",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            if (emg.acceptedResponders.isEmpty()) {
                                Text(
                                    text = "Responders: None connected yet",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            } else {
                                emg.acceptedResponders.forEach { resp ->
                                    val loc = emg.responderLocations[resp.id]
                                    val coordText = loc?.formattedCoordinates ?: "Waiting for GPS..."
                                    Text(
                                        text = "Responder: ${resp.name} ($coordText)",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    SafeWalkButton(
                        text = "Back to Details",
                        onClick = onNavigateBack,
                        variant = ButtonVariant.OUTLINED
                    )
                }
            }
        }
    }
}
