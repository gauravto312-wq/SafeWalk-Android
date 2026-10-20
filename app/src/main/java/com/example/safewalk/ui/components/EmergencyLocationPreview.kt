package com.example.safewalk.ui.components

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.safewalk.domain.model.Location
import com.example.safewalk.domain.model.ParticipantLocation
import com.example.safewalk.ui.map.SafeWalkMapView
import com.example.safewalk.ui.theme.SafeWalkBlueSecondary
import com.example.safewalk.ui.theme.SafeWalkRedPrimary
import java.util.Locale

/**
 * State-driven map preview component for SafeWalk emergencies.
 *
 * Requirements:
 * - In-app interactive MapLibre map with MapTiler Streets style.
 * - Shows requester location marker, current user marker, and multiple independent responder markers.
 * - "Open in Google Maps" action launcher.
 * - State-driven: dynamically renders coordinates passed in from ViewModel/Backend.
 */
@Composable
fun EmergencyLocationPreview(
    latitude: Double?,
    longitude: Double?,
    distanceMeters: Double? = null,
    address: String? = null,
    emergencyId: String? = null,
    responderLatitude: Double? = null,
    responderLongitude: Double? = null,
    participants: List<ParticipantLocation> = emptyList(),
    onOpenGoogleMaps: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val hasValidCoords = latitude != null && longitude != null && !(latitude == 0.0 && longitude == 0.0)
    val hasValidResponder = responderLatitude != null && responderLongitude != null && !(responderLatitude == 0.0 && responderLongitude == 0.0)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row: Title and Distance Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(SafeWalkRedPrimary)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Requester Location",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (distanceMeters != null) {
                    val distText = if (distanceMeters >= 1000.0) {
                        String.format(Locale.getDefault(), "%.1f km away", distanceMeters / 1000.0)
                    } else {
                        String.format(Locale.getDefault(), "%.0f m away", distanceMeters)
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SafeWalkBlueSecondary.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = distText,
                            color = SafeWalkBlueSecondary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (!hasValidCoords) {
                // Graceful fallback card when coordinates are missing
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "📍",
                                fontSize = 28.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Coordinates not available yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                val reqLocation = if (hasValidCoords) {
                    Location(latitude!!, longitude!!, address ?: "Requester Location")
                } else null

                val userLocation = if (hasValidResponder) {
                    Location(responderLatitude!!, responderLongitude!!, "Your Location")
                } else null

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFE8ECEF))
                ) {
                    SafeWalkMapView(
                        requesterLocation = reqLocation,
                        currentUserLocation = userLocation,
                        participants = participants,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Coordinate metadata
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = (if (hasValidResponder) "📍 Req: " else "") + String.format(Locale.US, "%.6f, %.6f", latitude, longitude),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (!address.isNullOrBlank()) {
                            Text(
                                text = address,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }

                    if (hasValidResponder) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "👤 You: " + String.format(Locale.US, "%.6f, %.6f", responderLatitude!!, responderLongitude!!),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = SafeWalkBlueSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // "Open in Google Maps" Button
                OutlinedButton(
                    onClick = { onOpenGoogleMaps?.invoke() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = SafeWalkBlueSecondary
                    )
                ) {
                    Text(
                        text = "🗺 Open in Google Maps",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
