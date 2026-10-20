package com.example.safewalk.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.safewalk.MainActivity
import com.example.safewalk.R
import com.example.safewalk.service.EmergencyNotificationActionReceiver

/**
 * Helper class for creating channels, building high-priority emergency notifications,
 * and managing active notification lifecycles.
 */
object EmergencyNotificationHelper {

    const val CHANNEL_EMERGENCY_ALERTS = "safewalk_alerts"
    private const val CHANNEL_NAME = "SafeWalk Emergency Alerts"
    private const val CHANNEL_DESC = "High-priority emergency alerts and notifications for nearby SOS requests"

    /**
     * Initializes the high-priority emergency notification channel.
     * Guaranteed safe to call multiple times.
     */
    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .build()

            val channel = NotificationChannel(
                CHANNEL_EMERGENCY_ALERTS,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC
                enableLights(true)
                lightColor = android.graphics.Color.RED
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 250, 500, 250, 500)
                setSound(soundUri, audioAttributes)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }

            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Deterministic, collision-safe notification ID calculation per emergency.
     * Ensures distinct emergency alerts do not overwrite one another.
     */
    fun getNotificationId(emergencyId: String): Int {
        val cleanId = emergencyId.removePrefix("EMG-")
        return cleanId.toLongOrNull()?.hashCode() ?: emergencyId.hashCode()
    }

    /**
     * Displays a high-priority heads-up emergency alert for an eligible responder.
     *
     * Actions:
     * - [ACCEPT]: Accepts emergency through existing authenticated emergency flow.
     * - [CANCEL]: Local dismissal ONLY. Does NOT call backend cancel endpoint; emergency remains LIVE.
     */
    fun showEmergencySosNotification(
        context: Context,
        emergencyId: String,
        requesterId: String,
        requesterName: String,
        approxDistance: String,
        isEmergencyContact: Boolean = false,
        avatarBitmap: android.graphics.Bitmap? = null,
        avatarUrl: String? = null
    ) {
        createNotificationChannels(context)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = getNotificationId(emergencyId)

        // 1. Content tap intent: Opens MainActivity targeting this emergency
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EmergencyNotificationActionReceiver.EXTRA_EMERGENCY_ID, emergencyId)
            action = EmergencyNotificationActionReceiver.ACTION_VIEW_EMERGENCY
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 2. [ACCEPT] Action intent (for nearby responders)
        val acceptIntent = Intent(context, EmergencyNotificationActionReceiver::class.java).apply {
            action = EmergencyNotificationActionReceiver.ACTION_ACCEPT_EMERGENCY
            putExtra(EmergencyNotificationActionReceiver.EXTRA_EMERGENCY_ID, emergencyId)
            putExtra(EmergencyNotificationActionReceiver.EXTRA_REQUESTER_ID, requesterId)
            putExtra(EmergencyNotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val acceptPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId * 10 + 1,
            acceptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 3. [CANCEL] Action intent: DISMISS NOTIFICATION ONLY (does NOT cancel the SOS!)
        val dismissIntent = Intent(context, EmergencyNotificationActionReceiver::class.java).apply {
            action = EmergencyNotificationActionReceiver.ACTION_DISMISS_NOTIFICATION
            putExtra(EmergencyNotificationActionReceiver.EXTRA_EMERGENCY_ID, emergencyId)
            putExtra(EmergencyNotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId * 10 + 2,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 4. Full-screen intent for locked screens / urgent heads-up alert
        val fullScreenIntent = com.example.safewalk.ui.emergency.FullScreenEmergencyAlertActivity.createIntent(
            context = context,
            emergencyId = emergencyId,
            requesterId = requesterId,
            requesterName = requesterName,
            approxDistance = approxDistance,
            notificationId = notificationId,
            isEmergencyContact = isEmergencyContact,
            avatarUrl = avatarUrl
        )
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            notificationId * 10 + 3,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cleanId = emergencyId.removePrefix("EMG-")
        val notificationTitle = if (isEmergencyContact) {
            "🚨 EMERGENCY SOS - TRUSTED CONTACT ALERT!"
        } else {
            "🚨 Emergency Alert"
        }

        val shortContent = if (isEmergencyContact) {
            "Your trusted contact ($requesterName) triggered an SOS! Tap to monitor & assist."
        } else {
            "Requester: $requesterName • Approx. Distance: $approxDistance"
        }

        val bigText = if (isEmergencyContact) {
            StringBuilder().apply {
                append("🚨 TRUSTED CONTACT ALERT!\n")
                append("Your trusted contact (").append(requesterName).append(") has triggered an Emergency SOS!\n")
                append("Emergency ID: #").append(cleanId).append("\n")
                append("Requester ID: ").append(requesterId).append("\n")
                append("Status: Active SOS\n")
                append("Tap OPEN EMERGENCY to monitor live coordinates and access emergency actions.")
            }.toString()
        } else {
            StringBuilder().apply {
                append("Requester: ").append(requesterName).append("\n")
                append("Emergency ID: #").append(cleanId).append("\n")
                append("Requester ID: ").append(requesterId).append("\n")
                append("Approximate Distance: ").append(approxDistance)
            }.toString()
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_EMERGENCY_ALERTS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .apply {
                if (avatarBitmap != null) {
                    setLargeIcon(avatarBitmap)
                }
            }
            .setContentTitle(notificationTitle)
            .setContentText(shortContent)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(bigText)
                    .setBigContentTitle(notificationTitle)
                    .setSummaryText("SafeWalk Emergency #$cleanId")
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(false)
            .setOngoing(true) // Stays prominent until user accepts or dismisses
            .setContentIntent(contentPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)

        if (isEmergencyContact) {
            // Trusted contacts get [OPEN EMERGENCY] and [CANCEL]
            builder.addAction(0, "OPEN EMERGENCY", contentPendingIntent)
            builder.addAction(0, "CANCEL", dismissPendingIntent)
        } else {
            // Nearby responders get [ACCEPT] and [CANCEL]
            builder.addAction(0, "ACCEPT", acceptPendingIntent)
            builder.addAction(0, "CANCEL", dismissPendingIntent)
        }

        val notification = builder
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    /**
     * Cancels / dismisses the emergency notification for a specific emergency ID.
     */
    fun cancelEmergencyNotification(context: Context, emergencyId: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = getNotificationId(emergencyId)
        notificationManager.cancel(notificationId)
    }
}
