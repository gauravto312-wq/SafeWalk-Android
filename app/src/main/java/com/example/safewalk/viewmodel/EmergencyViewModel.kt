package com.example.safewalk.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.safewalk.core.common.ServiceLocator
import com.example.safewalk.core.location.LiveTrackingState
import com.example.safewalk.data.source.mock.MockData
import com.example.safewalk.domain.model.Emergency
import com.example.safewalk.domain.model.Location
import com.example.safewalk.domain.model.ParticipantLocation
import com.example.safewalk.domain.model.RealtimeEmergencyEvent
import com.example.safewalk.domain.model.User
import com.example.safewalk.domain.model.VerificationStatus
import com.example.safewalk.domain.repository.AuthRepository
import com.example.safewalk.domain.repository.EmergencyRepository
import com.example.safewalk.domain.repository.LocationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class EmergencyUiState(
    val isLoading: Boolean = false,
    val message: String? = null,
    val errorMessage: String? = null
)

class EmergencyViewModel(
    private val emergencyRepository: EmergencyRepository = ServiceLocator.emergencyRepository,
    private val authRepository: AuthRepository = ServiceLocator.authRepository,
    private val locationRepository: LocationRepository = ServiceLocator.locationRepository,
    private val externalScope: CoroutineScope? = null
) : ViewModel() {

    private val scope: CoroutineScope
        get() = externalScope ?: viewModelScope

    val currentUser: StateFlow<User?> = authRepository.currentUser

    private val _emergency = MutableStateFlow<Emergency?>(null)
    val emergency: StateFlow<Emergency?> = _emergency.asStateFlow()

    private val _visibleParticipants = MutableStateFlow<List<ParticipantLocation>>(emptyList())
    val visibleParticipants: StateFlow<List<ParticipantLocation>> = _visibleParticipants.asStateFlow()

    private val _uiState = MutableStateFlow(EmergencyUiState())
    val uiState: StateFlow<EmergencyUiState> = _uiState.asStateFlow()

    // 5-second SOS cancellation window states
    private val _canCancel = MutableStateFlow(false)
    val canCancel: StateFlow<Boolean> = _canCancel.asStateFlow()

    private val _cancellationRemainingMillis = MutableStateFlow(0L)
    val cancellationRemainingMillis: StateFlow<Long> = _cancellationRemainingMillis.asStateFlow()

    val liveTrackingState: StateFlow<LiveTrackingState> = ServiceLocator.liveLocationManager.trackingState

    private var cancellationTimerJob: Job? = null
    private var loadEmergencyJob: Job? = null
    private var realtimeCollectorJob: Job? = null

    init {
        val collectorScope = if (externalScope != null) {
            CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
        } else {
            viewModelScope
        }
        realtimeCollectorJob = collectorScope.launch {
            emergencyRepository.realtimeEvents.collect { event ->
                val cur = _emergency.value
                val matches = cur != null && (cur.id == event.emergencyId || cur.numericId == event.emergencyId.removePrefix("EMG-"))
                if (matches) {
                    emergencyRepository.currentEmergency.value?.let { updated ->
                        _emergency.value = updated
                        refreshVisibleParticipants(updated)
                    }

                    when (event) {
                        is RealtimeEmergencyEvent.EmergencyResolved,
                        is RealtimeEmergencyEvent.EmergencyCancelled -> {
                            ServiceLocator.liveLocationManager.stopTracking(event.emergencyId)
                            stopCancellationCountdown()
                        }
                        is RealtimeEmergencyEvent.ResponderAccepted -> {
                            val user = currentUser.value
                            if (user != null && user.id == event.responder.id) {
                                ServiceLocator.liveLocationManager.startTracking(
                                    userId = user.id,
                                    emergencyId = event.emergencyId,
                                    role = "RESPONDER"
                                )
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    fun getCurrentUserLocation(): Location? {
        val user = currentUser.value ?: return null
        return locationRepository.getCurrentLocation(user.id)
    }

    fun refreshVisibleParticipants(emg: Emergency? = _emergency.value) {
        val target = emg ?: _emergency.value
        val user = currentUser.value
        if (target != null && user != null) {
            val res = emergencyRepository.getVisibleParticipantLocations(target.id, user.id)
            if (res.isSuccess) {
                _visibleParticipants.value = res.getOrNull() ?: emptyList()
            }
        }
    }

    fun loadEmergency(emergencyId: String) {
        loadEmergencyJob?.cancel()
        loadEmergencyJob = scope.launch {
            try {
                emergencyRepository.selectEmergency(emergencyId)
            } catch (_: Exception) {}
            emergencyRepository.getEmergencyById(emergencyId).collect { emg ->
                _emergency.value = emg
                refreshVisibleParticipants(emg)
                if (emg != null) {
                    startCancellationCountdown(emg)
                    val user = currentUser.value
                    if (user != null) {
                        if (emg.isTerminal) {
                            ServiceLocator.liveLocationManager.stopTracking(emg.id)
                        } else if (emg.isUserRequester(user.id)) {
                            ServiceLocator.liveLocationManager.startTracking(
                                userId = user.id,
                                emergencyId = emg.id,
                                role = "REQUESTER"
                            )
                        } else if (emg.isUserAcceptedResponder(user.id)) {
                            ServiceLocator.liveLocationManager.startTracking(
                                userId = user.id,
                                emergencyId = emg.id,
                                role = "RESPONDER"
                            )
                        }
                    }
                } else {
                    stopCancellationCountdown()
                }
            }
        }
    }

    /**
     * Checks if current user has privacy clearance to view detailed participant locations.
     */
    fun canViewDetailedLocation(): Boolean {
        val emg = _emergency.value ?: return false
        val user = currentUser.value ?: return false
        return emergencyRepository.canViewDetailedLocation(emg, user)
    }

    /**
     * Starts or refreshes the cancellation countdown based on emergency.createdAt.
     * Calculated as: remainingTime = 5000ms - (currentTime - emergency.createdAt)
     */
    fun startCancellationCountdown(
        emg: Emergency? = _emergency.value,
        currentTimeProvider: () -> Long = { System.currentTimeMillis() }
    ) {
        val target = emg ?: _emergency.value
        if (target == null || target.isTerminal) {
            stopCancellationCountdown()
            _canCancel.value = false
            _cancellationRemainingMillis.value = 0L
            return
        }

        val now = currentTimeProvider()
        val remaining = target.remainingCancellationTimeMillis(now)
        val eligible = target.canCancel(now)

        _canCancel.value = eligible
        _cancellationRemainingMillis.value = remaining

        if (!eligible) {
            stopCancellationCountdown()
            return
        }

        if (cancellationTimerJob?.isActive == true) {
            return
        }

        cancellationTimerJob = scope.launch {
            while (isActive) {
                val current = _emergency.value ?: break
                if (current.isTerminal) {
                    _canCancel.value = false
                    _cancellationRemainingMillis.value = 0L
                    break
                }
                val currentNow = currentTimeProvider()
                val rem = current.remainingCancellationTimeMillis(currentNow)
                val can = current.canCancel(currentNow)

                _canCancel.value = can
                _cancellationRemainingMillis.value = rem

                if (!can || rem <= 0L) {
                    _canCancel.value = false
                    _cancellationRemainingMillis.value = 0L
                    break
                }

                val sleepTime = minOf(100L, rem)
                delay(sleepTime)
            }
        }
    }

    fun stopCancellationCountdown() {
        cancellationTimerJob?.cancel()
        cancellationTimerJob = null
    }

    override fun onCleared() {
        super.onCleared()
        realtimeCollectorJob?.cancel()
        loadEmergencyJob?.cancel()
        stopCancellationCountdown()
    }

    fun acceptEmergency(onSuccess: (() -> Unit)? = null) {
        val user = currentUser.value
        if (user == null) {
            _uiState.value = _uiState.value.copy(errorMessage = "Must be logged in to accept emergency")
            return
        }
        val currentEmg = _emergency.value
        if (currentEmg == null) {
            _uiState.value = _uiState.value.copy(errorMessage = "Emergency record not loaded")
            return
        }
        if (currentEmg.isUserRequester(user.id)) {
            _uiState.value = _uiState.value.copy(errorMessage = "Requester cannot accept their own emergency")
            return
        }
        if (currentEmg.isUserAcceptedResponder(user.id)) {
            _uiState.value = _uiState.value.copy(errorMessage = "Responder has already accepted this emergency")
            return
        }
        if (user.role.isAuthority && (!user.isVerified || user.verificationStatus != VerificationStatus.VERIFIED)) {
            _uiState.value = _uiState.value.copy(errorMessage = "Account verification pending. Unverified authorities cannot accept emergency requests.")
            return
        }

        scope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = emergencyRepository.acceptEmergency(currentEmg.id, user)
            result.onSuccess { updated ->
                _emergency.value = updated
                try {
                    ServiceLocator.liveLocationManager.startTracking(
                        userId = user.id,
                        emergencyId = updated.id,
                        role = "RESPONDER"
                    )
                } catch (_: Exception) {}
                _uiState.value = _uiState.value.copy(isLoading = false, message = "Emergency Accepted")
                onSuccess?.invoke()
            }.onFailure { ex ->
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = ex.message)
            }
        }
    }

    /**
     * Updates simulated distance for current responder.
     */
    fun updateResponderDistance(distanceMeters: Double) {
        val user = currentUser.value ?: return
        val currentEmg = _emergency.value ?: return
        scope.launch {
            val result = emergencyRepository.updateResponderDistance(currentEmg.id, user.id, distanceMeters)
            result.onSuccess { updated ->
                _emergency.value = updated
                refreshVisibleParticipants(updated)
            }
        }
    }

    /**
     * Marks arrival at scene.
     * Enforces the strict rule: distance <= 25 meters (not < 25m).
     */
    fun markArrived(distanceMeters: Double = 25.0, onSuccess: (() -> Unit)? = null) {
        val user = currentUser.value ?: return
        val currentEmg = _emergency.value ?: return

        if (distanceMeters.isNaN() || distanceMeters.isInfinite() || distanceMeters > 25.0) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Arrival not allowed: distance must be 25 meters or less (current: ${distanceMeters.toInt()}m)"
            )
            return
        }

        scope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = emergencyRepository.markArrived(currentEmg.id, user, distanceMeters)
            result.onSuccess { updated ->
                _emergency.value = updated
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "Simulated arrival verified within 25 meters"
                )
                onSuccess?.invoke()
            }.onFailure { ex ->
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = ex.message)
            }
        }
    }

    fun confirmResolution() {
        val user = currentUser.value ?: return
        val currentEmg = _emergency.value ?: return
        scope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = emergencyRepository.confirmResolution(currentEmg.id, user)
            result.onSuccess { updated ->
                _emergency.value = updated
                if (updated.isTerminal) {
                    try {
                        ServiceLocator.liveLocationManager.stopTracking(updated.id)
                    } catch (_: Exception) {}
                }
                val msg = if (updated.isFullyResolved) {
                    "Emergency successfully resolved by both parties!"
                } else {
                    "Resolution confirmed. Awaiting counterpart confirmation."
                }
                _uiState.value = _uiState.value.copy(isLoading = false, message = msg)
            }.onFailure { ex ->
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = ex.message)
            }
        }
    }

    /**
     * Resolves emergency when any accepted responder reaches <= 25m.
     * Transitions emergency state to RESOLVED.
     */
    fun resolveEmergency(onSuccess: (() -> Unit)? = null) {
        val user = currentUser.value ?: return
        val currentEmg = _emergency.value ?: return
        scope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = emergencyRepository.resolveEmergency(currentEmg.id, user)
            result.onSuccess { updated ->
                _emergency.value = updated
                try {
                    ServiceLocator.liveLocationManager.stopTracking(updated.id)
                } catch (_: Exception) {}
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "Emergency successfully resolved!"
                )
                onSuccess?.invoke()
            }.onFailure { ex ->
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = ex.message)
            }
        }
    }

    fun rejectEmergency(onSuccess: (() -> Unit)? = null) {
        val user = currentUser.value
        if (user == null) {
            _uiState.value = _uiState.value.copy(errorMessage = "Must be logged in to reject emergency")
            return
        }
        val currentEmg = _emergency.value
        if (currentEmg == null) {
            _uiState.value = _uiState.value.copy(errorMessage = "Emergency record not loaded")
            return
        }
        scope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = emergencyRepository.rejectEmergency(currentEmg.id, user)
            result.onSuccess { updated ->
                _emergency.value = updated
                try {
                    ServiceLocator.liveLocationManager.stopTracking(updated.id)
                } catch (_: Exception) {}
                _uiState.value = _uiState.value.copy(isLoading = false, message = "Successfully withdrawn from emergency")
                onSuccess?.invoke()
            }.onFailure { ex ->
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = ex.message)
            }
        }
    }

    fun cancelEmergency(reason: String = "Cancelled by user", onCancelled: (() -> Unit)? = null) {
        cancelEmergency(emergencyPassword = null, reason = reason, onCancelled = onCancelled)
    }

    fun cancelEmergency(
        emergencyPassword: String?,
        reason: String = "Cancelled by user",
        onCancelled: (() -> Unit)? = null
    ) {
        val currentEmg = _emergency.value ?: return
        scope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = if (emergencyPassword != null) {
                emergencyRepository.cancelEmergency(currentEmg.id, emergencyPassword, reason)
            } else {
                emergencyRepository.cancelEmergency(currentEmg.id, reason)
            }
            result.onSuccess { updated ->
                _emergency.value = updated
                try {
                    ServiceLocator.liveLocationManager.stopTracking(updated.id)
                } catch (_: Exception) {}
                stopCancellationCountdown()
                _canCancel.value = false
                _cancellationRemainingMillis.value = 0L
                _uiState.value = _uiState.value.copy(isLoading = false, message = "Emergency Cancelled")
                onCancelled?.invoke()
            }.onFailure { ex ->
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = ex.message)
            }
        }
    }

    fun clearFeedback() {
        _uiState.value = _uiState.value.copy(message = null, errorMessage = null)
    }

    /**
     * Phase 5 Testing & Sandbox Simulation Helpers:
     * Allows simulating responder lifecycle events while on the Requester Emergency Screen.
     */
    fun simulateResponderAccept(responder: User) {
        val currentEmg = _emergency.value ?: return
        scope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = emergencyRepository.acceptEmergency(currentEmg.id, responder)
            result.onSuccess { updated ->
                _emergency.value = updated
                _uiState.value = _uiState.value.copy(isLoading = false, message = "${responder.name} accepted emergency")
            }.onFailure { ex ->
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = ex.message)
            }
        }
    }

    fun simulateResponderMovement(responderId: String, distanceMeters: Double) {
        val currentEmg = _emergency.value ?: return
        scope.launch {
            val result = emergencyRepository.updateResponderDistance(currentEmg.id, responderId, distanceMeters)
            result.onSuccess { updated ->
                _emergency.value = updated
            }
        }
    }

    fun simulateResponderArrival(responder: User, distanceMeters: Double = 25.0) {
        val currentEmg = _emergency.value ?: return
        scope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = emergencyRepository.markArrived(currentEmg.id, responder, distanceMeters)
            result.onSuccess { updated ->
                _emergency.value = updated
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "${responder.name} arrived on scene (<=25m)"
                )
            }.onFailure { ex ->
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = ex.message)
            }
        }
    }

    fun simulateResponderConfirmResolution(responder: User) {
        val currentEmg = _emergency.value ?: return
        scope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = emergencyRepository.confirmResolution(currentEmg.id, responder)
            result.onSuccess { updated ->
                _emergency.value = updated
                val msg = if (updated.isFullyResolved) {
                    "Emergency successfully resolved by both parties!"
                } else {
                    "${responder.name} confirmed resolution. Awaiting counterpart confirmation."
                }
                _uiState.value = _uiState.value.copy(isLoading = false, message = msg)
            }.onFailure { ex ->
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = ex.message)
            }
        }
    }

    /**
     * Phase 6 Responder Sandbox: Allows simulating requester confirming resolution.
     */
    fun simulateRequesterConfirmResolution() {
        val currentEmg = _emergency.value ?: return
        scope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = emergencyRepository.confirmResolution(currentEmg.id, currentEmg.requester)
            result.onSuccess { updated ->
                _emergency.value = updated
                val msg = if (updated.isFullyResolved) {
                    "Emergency successfully resolved by both parties!"
                } else {
                    "Requester confirmed resolution. Awaiting designated responder confirmation."
                }
                _uiState.value = _uiState.value.copy(isLoading = false, message = msg)
            }.onFailure { ex ->
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = ex.message)
            }
        }
    }

    /**
     * Phase 6 Responder Sandbox: Allows simulating requester cancelling emergency.
     */
    fun simulateRequesterCancellation(emergencyPassword: String? = "1234", reason: String = "Cancelled by requester") {
        val currentEmg = _emergency.value ?: return
        scope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = emergencyRepository.cancelEmergency(currentEmg.id, emergencyPassword, reason)
            result.onSuccess { updated ->
                _emergency.value = updated
                _uiState.value = _uiState.value.copy(isLoading = false, message = "Emergency cancelled by requester")
            }.onFailure { ex ->
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = ex.message)
            }
        }
    }

    /**
     * Phase 7: Moves requester along deterministic mock path (Location A -> B -> C)
     * and recalculates distances to all accepted responders.
     */
    fun simulateRequesterMovement() {
        val currentEmg = _emergency.value ?: return
        scope.launch {
            val result = emergencyRepository.advanceRequesterLocation(currentEmg.id)
            result.onSuccess { updated ->
                _emergency.value = updated
                refreshVisibleParticipants(updated)
                _uiState.value = _uiState.value.copy(
                    message = "Requester moved! Live coordinates updated, all responder distances recalculated."
                )
            }.onFailure { ex ->
                _uiState.value = _uiState.value.copy(errorMessage = ex.message)
            }
        }
    }

    /**
     * Phase 7: Advances a specific responder along deterministic mock movement sequence:
     * 100m -> 75m -> 50m -> 30m -> 25m -> 24m.
     */
    fun simulateResponderAdvance(responderId: String) {
        val currentEmg = _emergency.value ?: return
        scope.launch {
            val result = emergencyRepository.advanceResponderLocation(currentEmg.id, responderId)
            result.onSuccess { updated ->
                _emergency.value = updated
                refreshVisibleParticipants(updated)
                val resp = updated.acceptedResponders.find { it.id == responderId }
                val dist = updated.responderDistances[responderId]?.toInt() ?: 0
                _uiState.value = _uiState.value.copy(
                    message = "${resp?.name ?: "Responder"} moved. Distance to requester: ${dist}m"
                )
            }.onFailure { ex ->
                _uiState.value = _uiState.value.copy(errorMessage = ex.message)
            }
        }
    }

    /**
     * Phase 7: Updates requester location explicitly.
     */
    fun updateRequesterLocation(location: Location) {
        val currentEmg = _emergency.value ?: return
        scope.launch {
            val result = emergencyRepository.updateRequesterLocation(currentEmg.id, location)
            result.onSuccess { updated ->
                _emergency.value = updated
                refreshVisibleParticipants(updated)
            }.onFailure { ex ->
                _uiState.value = _uiState.value.copy(errorMessage = ex.message)
            }
        }
    }

    /**
     * Phase 7: Updates responder location explicitly.
     */
    fun updateResponderLocation(responderId: String, location: Location) {
        val currentEmg = _emergency.value ?: return
        scope.launch {
            val result = emergencyRepository.updateResponderLocation(currentEmg.id, responderId, location)
            result.onSuccess { updated ->
                _emergency.value = updated
                refreshVisibleParticipants(updated)
            }.onFailure { ex ->
                _uiState.value = _uiState.value.copy(errorMessage = ex.message)
            }
        }
    }

    /**
     * Phase 7: Accepts Rohit Kumar (user-004) as a third responder for testing multi-responder live telemetry.
     */
    fun simulateThirdResponderAcceptance() {
        val rohit = MockData.mockUsers.find { it.id == "user-004" } ?: return
        simulateResponderAccept(rohit)
    }
}


