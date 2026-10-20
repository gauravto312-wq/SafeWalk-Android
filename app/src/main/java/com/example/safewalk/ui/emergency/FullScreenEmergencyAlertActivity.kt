package com.example.safewalk.ui.emergency

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.safewalk.MainActivity
import com.example.safewalk.core.common.ServiceLocator
import com.example.safewalk.core.notification.EmergencyNotificationHelper
import com.example.safewalk.domain.model.UserRole
import com.example.safewalk.service.EmergencyNotificationActionReceiver
import com.example.safewalk.ui.components.UserAvatar
import com.example.safewalk.ui.theme.SafewalkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Full-screen urgent emergency alert activity displayed when the device is locked
 * or when an urgent incoming SOS notification is triggered via Full Screen Intent.
 *
 * Strict Semantics:
 * - [ACCEPT]: Accepts emergency via existing authenticated emergency acceptance flow,
 *   dismisses alert, registers responder, and navigates to live emergency screen.
 * - [CANCEL]: Local dismissal ONLY. Dismisses the full-screen alert and notification.
 *   MUST NEVER invoke the backend cancel emergency API; emergency remains completely ACTIVE.
 */
class FullScreenEmergencyAlertActivity : ComponentActivity() {

    companion object {
        const val EXTRA_EMERGENCY_ID = "extra_emergency_id"
        const val EXTRA_REQUESTER_ID = "extra_requester_id"
        const val EXTRA_REQUESTER_NAME = "extra_requester_name"
        const val EXTRA_APPROX_DISTANCE = "extra_distance"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val EXTRA_IS_EMERGENCY_CONTACT = "extra_is_emergency_contact"
        const val EXTRA_REQUESTER_AVATAR_URL = "extra_requester_avatar_url"

        fun createIntent(
            context: Context,
            emergencyId: String,
            requesterId: String,
            requesterName: String,
            approxDistance: String,
            notificationId: Int,
            isEmergencyContact: Boolean = false,
            avatarUrl: String? = null
        ): Intent {
            return Intent(context, FullScreenEmergencyAlertActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_EMERGENCY_ID, emergencyId)
                putExtra(EXTRA_REQUESTER_ID, requesterId)
                putExtra(EXTRA_REQUESTER_NAME, requesterName)
                putExtra(EXTRA_APPROX_DISTANCE, approxDistance)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                putExtra(EXTRA_IS_EMERGENCY_CONTACT, isEmergencyContact)
                putExtra(EXTRA_REQUESTER_AVATAR_URL, avatarUrl)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ensure activity displays over lock screen and turns on display
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        val emergencyId = intent.getStringExtra(EXTRA_EMERGENCY_ID) ?: ""
        if (emergencyId.isBlank()) {
            finish()
            return
        }
        val requesterId = intent.getStringExtra(EXTRA_REQUESTER_ID) ?: "0"
        val requesterName = intent.getStringExtra(EXTRA_REQUESTER_NAME) ?: "Someone in need"
        val approxDistance = intent.getStringExtra(EXTRA_APPROX_DISTANCE) ?: "Nearby"
        val isEmergencyContact = intent.getBooleanExtra(EXTRA_IS_EMERGENCY_CONTACT, false)
        val avatarUrl = intent.getStringExtra(EXTRA_REQUESTER_AVATAR_URL)

        setContent {
            SafewalkTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF1A0000) // Deep red/dark background for high urgency
                ) {
                    FullScreenEmergencyAlertContent(
                        emergencyId = emergencyId,
                        requesterId = requesterId,
                        requesterName = requesterName,
                        approxDistance = approxDistance,
                        isEmergencyContact = isEmergencyContact,
                        avatarUrl = avatarUrl,
                        onAccept = {
                            if (isEmergencyContact) {
                                handleOpenEmergency(emergencyId)
                            } else {
                                handleAccept(emergencyId)
                            }
                        },
                        onDismiss = { handleDismiss(emergencyId) }
                    )
                }
            }
        }
    }

    private fun handleOpenEmergency(emergencyId: String) {
        EmergencyNotificationHelper.cancelEmergencyNotification(this, emergencyId)
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EmergencyNotificationActionReceiver.EXTRA_EMERGENCY_ID, emergencyId)
            action = EmergencyNotificationActionReceiver.ACTION_VIEW_EMERGENCY
        }
        startActivity(mainIntent)
        finish()
    }

    private fun handleAccept(emergencyId: String) {
        // Dismiss notification
        EmergencyNotificationHelper.cancelEmergencyNotification(this, emergencyId)

        val sessionManager = ServiceLocator.sessionManager
        val currentUser = sessionManager?.getUser()

        // Asynchronously call acceptEmergency on remote repository
        if (currentUser != null && sessionManager.hasActiveSession()) {
            val repo = ServiceLocator.remoteEmergencyRepository
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                try {
                    repo?.acceptEmergency(emergencyId, currentUser)
                } catch (e: Exception) {
                    android.util.Log.e("FullScreenEmergency", "Error accepting emergency: ${e.message}", e)
                }
            }
        }

        // Launch MainActivity targeting this accepted emergency
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EmergencyNotificationActionReceiver.EXTRA_EMERGENCY_ID, emergencyId)
            action = EmergencyNotificationActionReceiver.ACTION_VIEW_EMERGENCY
        }
        startActivity(mainIntent)
        finish()
    }

    private fun handleDismiss(emergencyId: String) {
        // Semantic CANCEL: Local dismissal ONLY.
        // NEVER calls backend cancel API. Emergency remains ACTIVE!
        EmergencyNotificationHelper.cancelEmergencyNotification(this, emergencyId)
        finish()
    }
}

@Composable
fun FullScreenEmergencyAlertContent(
    emergencyId: String,
    requesterId: String,
    requesterName: String,
    approxDistance: String,
    isEmergencyContact: Boolean = false,
    avatarUrl: String? = null,
    onAccept: () -> Unit,
    onDismiss: () -> Unit
) {
    val cleanId = emergencyId.removePrefix("EMG-")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Header Section
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!avatarUrl.isNullOrBlank()) {
                UserAvatar(
                    name = requesterName,
                    role = UserRole.USER,
                    size = 96.dp,
                    avatarUrl = avatarUrl
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .background(
                            if (isEmergencyContact) Color(0xFFE65100) else Color(0xFFD32F2F),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isEmergencyContact) "🛡️" else "🚨",
                        fontSize = 44.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (isEmergencyContact) "TRUSTED CONTACT ALERT" else "SAFEWALK EMERGENCY",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp
                ),
                color = if (isEmergencyContact) Color(0xFFFFB74D) else Color(0xFFFF5252),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isEmergencyContact)
                    "Your trusted contact triggered an Emergency SOS!"
                else
                    "Immediate assistance requested nearby",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFECEFF1).copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
        }

        // Details Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF2C1010)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                EmergencyDetailRow(label = "Requester", value = requesterName, isHighlight = true)
                Divider(color = Color(0xFF4A1C1C), modifier = Modifier.padding(vertical = 10.dp))
                EmergencyDetailRow(label = "Emergency ID", value = "#$cleanId")
                Divider(color = Color(0xFF4A1C1C), modifier = Modifier.padding(vertical = 10.dp))
                EmergencyDetailRow(label = "Requester ID", value = requesterId)
                Divider(color = Color(0xFF4A1C1C), modifier = Modifier.padding(vertical = 10.dp))
                EmergencyDetailRow(
                    label = if (isEmergencyContact) "Status" else "Approximate Distance",
                    value = if (isEmergencyContact) "Designated Trusted Contact" else approxDistance,
                    isHighlight = true
                )
            }
        }

        // Actions Section: [ ACCEPT / OPEN EMERGENCY ] and [ CANCEL ]
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onAccept,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isEmergencyContact) Color(0xFFE65100) else Color(0xFF2E7D32),
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = if (isEmergencyContact) "OPEN EMERGENCY" else "ACCEPT",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                )
            }

            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFFB0BEC5)
                )
            ) {
                Text(
                    text = "CANCEL",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }

            Text(
                text = "Tapping Cancel dismisses this alert locally. The emergency remains active.",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF90A4AE).copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            )
        }
    }
}

@Composable
private fun EmergencyDetailRow(
    label: String,
    value: String,
    isHighlight: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFB0BEC5)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.Medium
            ),
            color = if (isHighlight) Color(0xFFFF8A80) else Color(0xFFECEFF1)
        )
    }
}
