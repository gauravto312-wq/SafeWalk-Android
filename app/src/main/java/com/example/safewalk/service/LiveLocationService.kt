package com.example.safewalk.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.safewalk.MainActivity
import com.example.safewalk.R
import com.example.safewalk.core.common.ServiceLocator
import com.example.safewalk.core.location.LiveLocationManager
import com.example.safewalk.core.location.LiveTrackingState
import com.example.safewalk.core.location.LocationResult
import com.example.safewalk.domain.model.Location
import com.example.safewalk.domain.repository.LocationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * Android Foreground Service providing continuous real GPS tracking during active emergencies.
 * Operates reliably in foreground, background, locked-screen, and app-switched states.
 */
class LiveLocationService : Service() {

    companion object {
        private const val TAG = "LiveLocationService"
        const val ACTION_START_TRACKING = "com.example.safewalk.action.START_TRACKING"
        const val ACTION_STOP_TRACKING = "com.example.safewalk.action.STOP_TRACKING"

        const val EXTRA_USER_ID = "extra_user_id"
        const val EXTRA_EMERGENCY_ID = "extra_emergency_id"
        const val EXTRA_ROLE = "extra_role"

        private const val CHANNEL_ID = "safewalk_live_location_channel"
        private const val NOTIFICATION_ID = 9110
        private const val UPDATE_INTERVAL_MS = 5000L
        private const val MIN_DISTANCE_METERS = 5f
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var locationJob: Job? = null
    private val liveLocationManager = LiveLocationManager.getInstance()
    private val locationRepository: LocationRepository
        get() = ServiceLocator.locationRepository

    private var activeEmergencyId: String? = null
    private var activeUserId: String? = null
    private var activeRole: String = "REQUESTER"
    private var providerReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "LiveLocationService onCreate")
        createNotificationChannel()
        registerProviderChangeReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            ACTION_STOP_TRACKING -> {
                Log.i(TAG, "Received ACTION_STOP_TRACKING")
                stopTrackingService()
                return START_NOT_STICKY
            }
            ACTION_START_TRACKING -> {
                activeUserId = intent.getStringExtra(EXTRA_USER_ID)
                activeEmergencyId = intent.getStringExtra(EXTRA_EMERGENCY_ID)
                activeRole = intent.getStringExtra(EXTRA_ROLE) ?: "REQUESTER"

                Log.i(TAG, "Received ACTION_START_TRACKING for Emergency $activeEmergencyId (Role: $activeRole)")
                startForegroundTracking()
                startLocationUpdates()
                return START_NOT_STICKY
            }
            else -> return START_NOT_STICKY
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SafeWalk Emergency Live Location",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Continuous emergency GPS tracking channel"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SafeWalk Emergency Active")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun startForegroundTracking() {
        val text = if (activeRole == "REQUESTER") {
            "Broadcasting real-time GPS location to responders"
        } else {
            "Transmitting responder location to emergency dispatch"
        }
        val notification = buildNotification(text)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startLocationUpdates() {
        locationJob?.cancel()
        locationJob = serviceScope.launch {
            Log.i(TAG, "Beginning continuous location updates pipeline (interval: ${UPDATE_INTERVAL_MS}ms)")

            locationRepository.getLocationUpdates(
                intervalMs = UPDATE_INTERVAL_MS,
                minDistanceMeters = MIN_DISTANCE_METERS
            )
                .catch { ex ->
                    Log.e(TAG, "Error in continuous location flow: ${ex.message}", ex)
                    liveLocationManager.onError(ex.message ?: "Unknown tracking error")
                }
                .collect { result ->
                    when (result) {
                        is LocationResult.Success -> {
                            handleLocationSuccess(result.location)
                        }
                        is LocationResult.Failure.LocationDisabled -> {
                            Log.w(TAG, "Location provider disabled during tracking")
                            liveLocationManager.onGpsUnavailable()
                            updateNotificationText("Device location is off. Please turn on location.")
                        }
                        is LocationResult.Failure.PermissionDenied -> {
                            Log.e(TAG, "Location permission revoked during tracking")
                            liveLocationManager.onError("Location permission revoked")
                            stopTrackingService()
                        }
                        is LocationResult.Failure.Error -> {
                            Log.w(TAG, "Temporary location acquisition error: ${result.detailMessage}")
                            liveLocationManager.onError(result.detailMessage)
                        }
                    }
                }
        }
    }

    private suspend fun handleLocationSuccess(location: Location) {
        liveLocationManager.onLocationAcquired(location)

        // Transmit coordinates to backend PUT /api/v1/location
        try {
            val transmitResult = locationRepository.sendLocationToBackend(location)
            if (transmitResult.isSuccess) {
                liveLocationManager.onLocationSynced(location)
                Log.d(TAG, "Live location cloud sync success")
                val text = if (activeRole == "REQUESTER") {
                    "Live GPS: ${location.formattedCoordinates} • Synced"
                } else {
                    "Responder GPS: ${location.formattedCoordinates} • Synced"
                }
                updateNotificationText(text)
            } else {
                val err = transmitResult.exceptionOrNull()?.localizedMessage ?: "Sync failed"
                Log.w(TAG, "Location acquired but cloud sync failed (temporary network issue): $err")
                liveLocationManager.onError("Network sync pending: $err")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Exception transmitting live location: ${e.message}")
            liveLocationManager.onError("Network error: ${e.message}")
        }
    }

    private fun updateNotificationText(newText: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, buildNotification(newText))
    }

    private fun stopTrackingService() {
        Log.i(TAG, "Stopping LiveLocationService")
        locationJob?.cancel()
        locationJob = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.cancel(NOTIFICATION_ID)
        stopSelf()
    }

    private fun registerProviderChangeReceiver() {
        val filter = IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == LocationManager.PROVIDERS_CHANGED_ACTION) {
                    val lm = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                    val isGpsOn = lm?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
                    if (isGpsOn) {
                        Log.i(TAG, "Location provider restored; resuming live updates")
                        liveLocationManager.onGpsRecovered()
                        if (activeEmergencyId != null) {
                            startLocationUpdates()
                        }
                    } else {
                        Log.w(TAG, "Location provider disabled; pausing live updates")
                        liveLocationManager.onGpsUnavailable()
                        updateNotificationText("Device location is off. Please turn on location.")
                    }
                }
            }
        }
        providerReceiver = receiver
        registerReceiver(receiver, filter)
    }

    private fun unregisterProviderChangeReceiver() {
        try {
            providerReceiver?.let { unregisterReceiver(it) }
            providerReceiver = null
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        Log.i(TAG, "LiveLocationService onDestroy")
        unregisterProviderChangeReceiver()
        locationJob?.cancel()
        serviceScope.cancel()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.cancel(NOTIFICATION_ID)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
