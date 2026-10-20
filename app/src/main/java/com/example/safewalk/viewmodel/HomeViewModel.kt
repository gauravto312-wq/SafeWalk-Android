package com.example.safewalk.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.safewalk.core.common.ServiceLocator
import com.example.safewalk.domain.model.Emergency
import com.example.safewalk.domain.model.Location
import com.example.safewalk.domain.model.RealtimeEmergencyEvent
import com.example.safewalk.domain.model.User
import com.example.safewalk.domain.repository.AuthRepository
import com.example.safewalk.domain.repository.EmergencyRepository
import com.example.safewalk.domain.repository.LocationRepository
import com.example.safewalk.domain.model.VerificationStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val isCreatingSos: Boolean = false,
    val errorMessage: String? = null
)

class HomeViewModel(
    private val authRepository: AuthRepository = ServiceLocator.authRepository,
    private val emergencyRepository: EmergencyRepository = ServiceLocator.emergencyRepository,
    private val locationRepository: LocationRepository = ServiceLocator.locationRepository,
    private val externalScope: CoroutineScope? = null
) : ViewModel() {

    private val scope: CoroutineScope
        get() = externalScope ?: viewModelScope

    val currentUser: StateFlow<User?> = authRepository.currentUser
    val activeEmergencies: StateFlow<List<Emergency>> = emergencyRepository.activeEmergencies

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var realtimeCollectorJob: Job? = null
    private var trackingCheckJob: Job? = null

    init {
        refreshActiveEmergencies()
        val collectorScope = if (externalScope != null) {
            CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
        } else {
            viewModelScope
        }
        realtimeCollectorJob = collectorScope.launch {
            emergencyRepository.realtimeEvents.collect { event ->
                when (event) {
                    is RealtimeEmergencyEvent.SosCreated,
                    is RealtimeEmergencyEvent.EmergencyCancelled,
                    is RealtimeEmergencyEvent.EmergencyResolved,
                    is RealtimeEmergencyEvent.ResponderAccepted -> {
                        refreshActiveEmergencies()
                    }
                    else -> {}
                }
            }
        }
        trackingCheckJob = collectorScope.launch {
            activeEmergencies.collect { list ->
                val user = currentUser.value
                val session = ServiceLocator.liveLocationManager.currentSession.value
                if (user != null && session != null) {
                    val hasActiveParticipation = list.any { emg ->
                        (emg.isUserRequester(user.id) || emg.isUserAcceptedResponder(user.id)) && !emg.isTerminal
                    }
                    if (!hasActiveParticipation) {
                        ServiceLocator.liveLocationManager.stopTracking(session.emergencyId)
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        realtimeCollectorJob?.cancel()
        trackingCheckJob?.cancel()
    }

    fun refreshActiveEmergencies() {
        scope.launch {
            try {
                emergencyRepository.refreshActiveEmergencies()
            } catch (e: Exception) {
                // Ignore network errors during refresh
            }
        }
    }

    fun getDefaultUserLocation(): Location {
        return locationRepository.getDefaultMockLocation()
    }

    /**
     * Checks if current user is eligible to respond to an emergency based on SafeWalk discovery rules.
     */
    fun isEligibleToRespond(emergency: Emergency, userLocation: Location = getDefaultUserLocation()): Boolean {
        val user = currentUser.value ?: return false
        if (user.role.isAuthority && (!user.isVerified || user.verificationStatus != VerificationStatus.VERIFIED)) {
            return false
        }
        return emergencyRepository.isUserEligibleForEmergency(user, emergency, userLocation)
    }

    /**
     * Returns geodesic distance to requester in meters.
     */
    fun getDistanceToEmergencyMeters(emergency: Emergency, userLocation: Location = getDefaultUserLocation()): Double {
        return userLocation.distanceTo(emergency.requesterLocation)
    }

    /**
     * Retrieves all active emergencies that the current user is eligible to see in their discovery feed.
     */
    fun getEligibleEmergenciesForCurrentUser(userLocation: Location = getDefaultUserLocation()): List<Emergency> {
        val user = currentUser.value ?: return emptyList()
        if (user.role.isAuthority && (!user.isVerified || user.verificationStatus != VerificationStatus.VERIFIED)) {
            return emptyList()
        }
        return emergencyRepository.getEligibleEmergenciesForUser(user, userLocation)
    }

    fun triggerSosEmergency(onEmergencyCreated: (String) -> Unit) {
        val user = currentUser.value ?: return
        scope.launch {
            _uiState.value = _uiState.value.copy(isCreatingSos = true, errorMessage = null)
            val realLocation = try {
                if (locationRepository.hasLocationPermission()) {
                    locationRepository.acquireCurrentLocation().getOrNull()
                        ?: locationRepository.getCurrentLocation(user.id)
                } else {
                    locationRepository.getCurrentLocation(user.id)
                }
            } catch (e: Exception) {
                locationRepository.getCurrentLocation(user.id)
            }

            try {
                try {
                    locationRepository.sendLocationToBackend(realLocation)
                } catch (_: Exception) {}

                val newEmergency = emergencyRepository.createEmergency(
                    requester = user,
                    location = realLocation
                )
                try {
                    ServiceLocator.liveLocationManager.startTracking(
                        userId = user.id,
                        emergencyId = newEmergency.id,
                        role = "REQUESTER"
                    )
                } catch (_: Exception) {}
                _uiState.value = _uiState.value.copy(isCreatingSos = false)
                onEmergencyCreated(newEmergency.id)
            } catch (e: Exception) {
                refreshActiveEmergencies()
                _uiState.value = _uiState.value.copy(
                    isCreatingSos = false,
                    errorMessage = e.message ?: "Failed to trigger SOS emergency"
                )
            }
        }
    }
}
