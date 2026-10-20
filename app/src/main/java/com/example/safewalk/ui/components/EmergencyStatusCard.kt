package com.example.safewalk.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.safewalk.domain.model.EmergencyStatus
import com.example.safewalk.ui.theme.SafeWalkAmberAlert
import com.example.safewalk.ui.theme.SafeWalkBlueSecondary
import com.example.safewalk.ui.theme.SafeWalkGreenSuccess
import com.example.safewalk.ui.theme.SafeWalkRedPrimary

@Composable
fun EmergencyStatusBadge(
    status: EmergencyStatus,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor, dotColor) = when (status) {
        EmergencyStatus.ACTIVE -> Triple(Color(0xFFFFEBEE), SafeWalkRedPrimary, SafeWalkRedPrimary)
        EmergencyStatus.ACCEPTED -> Triple(Color(0xFFE3F2FD), SafeWalkBlueSecondary, SafeWalkBlueSecondary)
        EmergencyStatus.RESOLVED -> Triple(Color(0xFFE8F5E9), SafeWalkGreenSuccess, SafeWalkGreenSuccess)
        EmergencyStatus.CANCELLED -> Triple(Color(0xFFEEEEEE), Color(0xFF616161), Color(0xFF757575))
        EmergencyStatus.EXPIRED -> Triple(Color(0xFFFFF3E0), Color(0xFFE65100), Color(0xFFEF6C00))
    }

    Box(
        modifier = modifier
            .background(bgColor, shape = RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(dotColor, shape = CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = status.displayName,
                color = textColor,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            )
        }
    }
}
