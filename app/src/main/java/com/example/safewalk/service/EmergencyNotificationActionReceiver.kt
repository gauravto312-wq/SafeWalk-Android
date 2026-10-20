package com.example.safewalk.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.safewalk.MainActivity
import com.example.safewalk.core.common.ServiceLocator
import com.example.safewalk.core.notification.EmergencyNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles action button clicks from high-priority SafeWalk emergency notifications.
 *
 * Semantic semantics:
 * - ACTION_ACCEPT_EMERGENCY: Accepts emergency via existing authenticated emergency acceptance flow.
 * - ACTION_DISMISS_NOTIFICATION (labeled "CANCEL"): Local dismissal ONLY. Does NOT call backend cancel
 *   endpoint; emergency remains completely LIVE and ACTIVE.
 */
class EmergencyNotificationActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "EmergencyNotifAction"
        const val ACTION_ACCEPT_EMERGENCY = "com.example.safewalk.action.ACCEPT_EMERGENCY"
        const val ACTION_DISMISS_NOTIFICATION = "com.example.safewalk.action.DISMISS_NOTIFICATION"
        const val ACTION_VIEW_EMERGENCY = "com.example.safewalk.action.VIEW_EMERGENCY"

        const val EXTRA_EMERGENCY_ID = "extra_emergency_id"
        const val EXTRA_REQUESTER_ID = "extra_requester_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val emergencyId = intent.getStringExtra(EXTRA_EMERGENCY_ID) ?: return

        when (action) {
            ACTION_DISMISS_NOTIFICATION -> {
                // SEMANTIC CANCEL: Local notification dismissal ONLY.
                // DOES NOT call backend cancel endpoint!
                // DOES NOT notify requester!
                // Emergency remains ACTIVE and discoverable.
                Log.i(TAG, "Dismissing emergency notification locally for emergency ID: $emergencyId (SOS remains ACTIVE)")
                EmergencyNotificationHelper.cancelEmergencyNotification(context, emergencyId)
            }

            ACTION_ACCEPT_EMERGENCY -> {
                Log.i(TAG, "Processing ACCEPT action from notification for emergency ID: $emergencyId")
                EmergencyNotificationHelper.cancelEmergencyNotification(context, emergencyId)

                val sessionManager = ServiceLocator.sessionManager
                val currentUser = sessionManager?.getUser()

                if (currentUser != null && sessionManager.hasActiveSession()) {
                    // Execute acceptance through existing authenticated emergency flow
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val repo = ServiceLocator.remoteEmergencyRepository
                            val result = repo?.acceptEmergency(emergencyId, currentUser)
                            if (result?.isSuccess == true) {
                                Log.i(TAG, "Emergency $emergencyId accepted successfully via notification action")
                            } else {
                                Log.w(TAG, "Emergency acceptance returned failure: ${result?.exceptionOrNull()?.message}")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Exception accepting emergency from notification: ${e.message}", e)
                        } finally {
                            // Launch MainActivity targeting this accepted emergency
                            launchMainActivity(context, emergencyId)
                            pendingResult.finish()
                        }
                    }
                } else {
                    Log.w(TAG, "User not authenticated; directing to app login/home")
                    launchMainActivity(context, emergencyId)
                }
            }
        }
    }

    private fun launchMainActivity(context: Context, emergencyId: String) {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_EMERGENCY_ID, emergencyId)
            action = ACTION_VIEW_EMERGENCY
        }
        context.startActivity(launchIntent)
    }
}
