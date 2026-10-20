package com.example.safewalk.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.safewalk.core.location.LiveTrackingState
import com.example.safewalk.ui.theme.SafeWalkGreenSuccess
import com.example.safewalk.ui.theme.SafeWalkRedPrimary

@Composable
fun LiveTrackingStatusCard(
    trackingState: LiveTrackingState,
    isTerminal: Boolean,
    modifier: Modifier = Modifier
) {
    if (isTerminal) return

    AnimatedVisibility(
        visible = trackingState == LiveTrackingState.TRACKING || trackingState == LiveTrackingState.PAUSED_NO_GPS,
        modifier = modifier
    ) {
        val isPaused = trackingState == LiveTrackingState.PAUSED_NO_GPS
        val bgColor = if (isPaused) Color(0xFFFFF3E0) else Color(0xFFE8F5E9)
        val accentColor = if (isPaused) Color(0xFFE65100) else SafeWalkGreenSuccess
        val badgeBg = if (isPaused) Color(0xFFFFE0B2) else Color(0xFFC8E6C9)
        val titleText = if (isPaused) "GPS PAUSED • Waiting for Signal" else "LIVE LOCATION • Sharing Active"
        val subtitleText = if (isPaused) {
            "Location updates paused. Tracking will automatically resume when GPS is enabled."
        } else {
            "Background tracking active — your location continues sharing if you switch apps or lock the screen."
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = bgColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
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
                                .background(accentColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = titleText,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = accentColor
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(badgeBg, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isPaused) "PAUSED" else "LIVE GPS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = accentColor,
                                fontSize = 10.sp
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.DarkGray,
                        lineHeight = 16.sp
                    )
                )
            }
        }
    }
}
