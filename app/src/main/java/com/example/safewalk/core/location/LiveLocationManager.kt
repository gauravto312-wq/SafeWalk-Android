package com.example.safewalk.core.location

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.safewalk.core.common.ServiceLocator
import com.example.safewalk.domain.model.Location
import com.example.safewalk.service.LiveLocationService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LiveTrackingState {
    IDLE,
    TRACKING,
    PAUSED_NO_GPS,
    STOPPING,
    STOPPED,
    ERROR
}

data class LiveTrackingSession(
    val userId: String,
    val emergencyId: String,
    val role: String, // "REQUESTER" or "RESPONDER"
    val startedAt: Long = System.currentTimeMillis()
) {
    fun matches(targetUserId: String, targetEmergencyId: String): Boolean {
        val normTarget = targetEmergencyId.removePrefix("EMG-")
        val normCurrent = emergencyId.removePrefix("EMG-")
        return userId == targetUserId && normCurrent == normTarget
    }
}

/**
 * Coordinator for SafeWalk continuous live location tracking during active emergencies.
 * Provides singleton coordination, duplicate session protection, and state observation.
 */
class LiveLocationManager private constructor() {

    companion object {
        private const val TAG = "LiveLocationManager"

        @Volatile
        private var instance: LiveLocationManager? = null

        fun getInstance(): LiveLocationManager {
            return instance ?: synchronized(this) {
                instance ?: LiveLocationManager().also { instance = it }
            }
        }
    }

    private val _trackingState = MutableStateFlow(LiveTrackingState.IDLE)
    val trackingState: StateFlow<LiveTrackingState> = _trackingState.asStateFlow()

    private val _currentSession = MutableStateFlow<LiveTrackingSession?>(null)
    val currentSession: StateFlow<LiveTrackingSession?> = _currentSession.asStateFlow()

    private val _lastLocation = MutableStateFlow<Location?>(null)
    val lastLocation: StateFlow<Location?> = _lastLocation.asStateFlow()

    private val _lastSyncedTimestamp = MutableStateFlow<Long?>(null)
    val lastSyncedTimestamp: StateFlow<Long?> = _lastSyncedTimestamp.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    /**
     * Starts continuous live location tracking for an emergency.
     * Guaranteed single active session per USER + ACTIVE EMERGENCY.
     * Returns true if a new tracking session was started, false if already tracking or duplicate.
     */
    @Synchronized
    fun startTracking(
        userId: String,
        emergencyId: String,
        role: String = "REQUESTER",
        context: Context? = ServiceLocator.appContext
    ): Boolean {
        val existingSession = _currentSession.value
        val currentState = _trackingState.value

        // Duplicate Session Protection (Phase 5.5)
        if (existingSession != null && existingSession.matches(userId, emergencyId)) {
            if (currentState == LiveTrackingState.TRACKING || currentState == LiveTrackingState.PAUSED_NO_GPS) {
                Log.i(TAG, "Duplicate session suppressed: already tracking for user $userId on emergency $emergencyId")
                return false
            }
        }

        val session = LiveTrackingSession(
            userId = userId,
            emergencyId = emergencyId,
            role = role
        )
        _currentSession.value = session
        _trackingState.value = LiveTrackingState.TRACKING
        _lastError.value = null

        Log.i(TAG, "Starting live location tracking for session: $session")
        if (context != null) {
            try {
                val intent = Intent(context, LiveLocationService::class.java).apply {
                    action = LiveLocationService.ACTION_START_TRACKING
                    putExtra(LiveLocationService.EXTRA_USER_ID, userId)
                    putExtra(LiveLocationService.EXTRA_EMERGENCY_ID, emergencyId)
                    putExtra(LiveLocationService.EXTRA_ROLE, role)
                }
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.w(TAG, "Notice starting LiveLocationService (may be unit test environment): ${e.message}")
            }
        }
        return true
    }

    /**
     * Stops tracking for the specified emergency or current session.
     */
    @Synchronized
    fun stopTracking(
        emergencyId: String? = null,
        context: Context? = ServiceLocator.appContext
    ) {
        val current = _currentSession.value ?: run {
            Log.d(TAG, "stopTracking called but no session active")
            _trackingState.value = LiveTrackingState.STOPPED
            return
        }

        if (emergencyId != null) {
            val normGiven = emergencyId.removePrefix("EMG-")
            val normCurrent = current.emergencyId.removePrefix("EMG-")
            if (normGiven != normCurrent) {
                Log.d(TAG, "Ignoring stop request for $emergencyId, currently tracking ${current.emergencyId}")
                return
            }
        }

        Log.i(TAG, "Stopping live location tracking for emergency: ${current.emergencyId}")
        _trackingState.value = LiveTrackingState.STOPPING

        if (context != null) {
            try {
                val intent = Intent(context, LiveLocationService::class.java).apply {
                    action = LiveLocationService.ACTION_STOP_TRACKING
                }
                context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Notice stopping LiveLocationService: ${e.message}")
            }
        }

        _trackingState.value = LiveTrackingState.STOPPED
        _currentSession.value = null
    }

    /**
     * Unconditionally halts all tracking and clears session (e.g. on user logout).
     */
    @Synchronized
    fun stopAllTracking(context: Context? = ServiceLocator.appContext) {
        Log.i(TAG, "stopAllTracking invoked (logout/clean cleanup)")
        _trackingState.value = LiveTrackingState.STOPPING
        if (context != null) {
            try {
                val intent = Intent(context, LiveLocationService::class.java).apply {
                    action = LiveLocationService.ACTION_STOP_TRACKING
                }
                context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Notice stopping service on logout: ${e.message}")
            }
        }
        _trackingState.value = LiveTrackingState.IDLE
        _currentSession.value = null
        _lastLocation.value = null
        _lastSyncedTimestamp.value = null
        _lastError.value = null
    }

    // Callbacks from LiveLocationService
    fun onLocationAcquired(location: Location) {
        _lastLocation.value = location
        if (_trackingState.value == LiveTrackingState.PAUSED_NO_GPS) {
            _trackingState.value = LiveTrackingState.TRACKING
        }
    }

    fun onLocationSynced(location: Location) {
        _lastLocation.value = location
        _lastSyncedTimestamp.value = System.currentTimeMillis()
        if (_trackingState.value == LiveTrackingState.PAUSED_NO_GPS) {
            _trackingState.value = LiveTrackingState.TRACKING
        }
    }

    fun onGpsUnavailable() {
        Log.w(TAG, "GPS signal unavailable during live tracking")
        _trackingState.value = LiveTrackingState.PAUSED_NO_GPS
    }

    fun onGpsRecovered() {
        Log.i(TAG, "GPS signal recovered during live tracking")
        _trackingState.value = LiveTrackingState.TRACKING
    }

    fun onError(errorMsg: String) {
        Log.e(TAG, "Live tracking error: $errorMsg")
        _lastError.value = errorMsg
    }

    /**
     * Test helper to reset internal singleton state for isolated test execution.
     */
    fun resetForTests() {
        _trackingState.value = LiveTrackingState.IDLE
        _currentSession.value = null
        _lastLocation.value = null
        _lastSyncedTimestamp.value = null
        _lastError.value = null
    }
}
