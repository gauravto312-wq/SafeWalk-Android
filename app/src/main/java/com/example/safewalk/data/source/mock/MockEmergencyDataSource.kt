package com.example.safewalk.data.source.mock

import com.example.safewalk.domain.model.Emergency
import com.example.safewalk.domain.model.EmergencyRole
import com.example.safewalk.domain.model.EmergencyStatus
import com.example.safewalk.domain.model.Location
import com.example.safewalk.domain.model.LocationStatus
import com.example.safewalk.domain.model.ParticipantLocation
import com.example.safewalk.domain.model.User
import com.example.safewalk.domain.model.UserRole
import com.example.safewalk.domain.model.VerificationStatus
import com.example.safewalk.domain.model.NotificationType
import com.example.safewalk.domain.model.RealtimeEmergencyEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory Mock Data Source managing Emergency lifecycles, discovery rules,
 * multi-responder dispatch, arrival verification (<=25m), bidirectional live location,
 * and IDOR-style privacy authorization rules.
 */
class MockEmergencyDataSource(
    private var notificationDataSource: MockNotificationDataSource? = null,
    private var userLookup: (() -> List<User>)? = null,
    private var locationLookup: ((String) -> Location?)? = null
) {

    fun setNotificationDataSource(dataSource: MockNotificationDataSource) {
        this.notificationDataSource = dataSource
    }

    fun setUserLookup(lookup: () -> List<User>) {
        this.userLookup = lookup
    }

    fun setLocationLookup(lookup: (String) -> Location?) {
        this.locationLookup = lookup
    }

    private val emergencies = MutableStateFlow<List<Emergency>>(MockData.initialEmergencies)
    private val _currentEmergency = MutableStateFlow<Emergency?>(null)
    private val _realtimeEvents = MutableSharedFlow<RealtimeEmergencyEvent>(replay = 10, extraBufferCapacity = 64)
    val realtimeEvents: SharedFlow<RealtimeEmergencyEvent> = _realtimeEvents.asSharedFlow()

    val activeEmergencies: StateFlow<List<Emergency>> = MutableStateFlow(
        MockData.initialEmergencies.filter { !it.isTerminal }
    )

    val currentEmergency: StateFlow<Emergency?> = _currentEmergency.asStateFlow()

    private val requesterStepCount = mutableMapOf<String, Int>()
    private val responderStepMap = mutableMapOf<String, Int>() // key = "$emergencyId-$responderId"

    companion object {
        val RESPONDER_MOCK_STEPS = listOf(100.0, 75.0, 50.0, 30.0, 25.0, 24.0)
    }

    private fun refreshActiveList() {
        (activeEmergencies as MutableStateFlow).value = emergencies.value.filter { !it.isTerminal }
    }

    fun getEmergencyById(id: String): Flow<Emergency?> {
        return emergencies.map { list -> list.find { it.id == id } }
    }

    fun getEmergencyHistory(userId: String): Flow<List<Emergency>> {
        return emergencies.map { list ->
            list.filter { it.isTerminal && (it.requester.id == userId || it.isUserAcceptedResponder(userId)) }
        }
    }

    /**
     * Discovery eligibility engine implementing SafeWalk business rules:
     * 1. Requester is NEVER eligible to respond to own SOS.
     * 2. Emergency/trusted contacts added by SafeWalk ID qualify regardless of distance.
     * 3. Normal users: > 50m and <= 2000m (2km).
     * 4. Verified police: <= 2000m (2km) with NO 50m exclusion.
     * 5. Unverified police: excluded from verified-police discovery.
     * 6. No fixed responder cap.
     * 7. Deduplicate qualification.
     */
    fun isUserEligibleForEmergency(user: User, emergency: Emergency, userLocation: Location): Boolean {
        // 0. Unverified authorities (Police & Hospital) must NOT receive/see dispatchable emergencies
        if (user.role.isAuthority && (!user.isVerified || user.verificationStatus != VerificationStatus.VERIFIED)) {
            return false
        }

        // 1. Requester cannot respond to own emergency
        if (emergency.isUserRequester(user.id)) {
            return false
        }

        // 2. Trusted / Emergency contacts qualify regardless of distance
        val isEmergencyContact = emergency.requester.emergencyContacts.any {
            it.equals(user.safeWalkId, ignoreCase = true)
        }
        if (isEmergencyContact) {
            return true
        }

        // Calculate distance in meters
        val distanceMeters = userLocation.distanceTo(emergency.requesterLocation)

        // 3 & 4 & 5. Role-based distance rules
        return when (user.role) {
            UserRole.USER -> {
                // Normal user: > 50m and <= 2km
                distanceMeters > 50.0 && distanceMeters <= 2000.0
            }
            UserRole.POLICE -> {
                // Verified police: <= 2km, NO 50m exclusion
                // Unverified police: excluded from verified police discovery
                if (user.isVerified && (user.verificationStatus == VerificationStatus.VERIFIED || user.verificationStatus == VerificationStatus.APPROVED)) {
                    distanceMeters <= 2000.0
                } else {
                    false
                }
            }
            UserRole.HOSPITAL -> {
                // Verified medical units: <= 2km
                if (user.isVerified && (user.verificationStatus == VerificationStatus.VERIFIED || user.verificationStatus == VerificationStatus.APPROVED)) {
                    distanceMeters <= 2000.0
                } else {
                    false
                }
            }
            UserRole.ADMIN -> false
        }
    }

    /**
     * Filters active emergencies that the given user is eligible to discover.
     */
    fun getEligibleEmergenciesForUser(user: User, userLocation: Location): List<Emergency> {
        return emergencies.value.filter { emg ->
            !emg.isTerminal && isUserEligibleForEmergency(user, emg, userLocation)
        }.distinctBy { it.id }
    }

    /**
     * Checks if a user has privacy permission to view detailed participant locations.
     * Requester and accepted responders can view; unaccepted nearby or unrelated users cannot.
     */
    fun canViewDetailedLocation(emergency: Emergency, user: User?): Boolean {
        if (user == null) return false
        return emergency.isUserRequester(user.id) || emergency.isUserAcceptedResponder(user.id)
    }

    fun createEmergency(requester: User, location: Location): Emergency {
        val uniqueNumber = (1000..9999).random()
        val activeLocation = location.copy(
            timestamp = System.currentTimeMillis(),
            status = LocationStatus.ACTIVE
        )
        val newEmergency = Emergency(
            id = "EMG-2026-$uniqueNumber",
            requester = requester,
            status = EmergencyStatus.ACTIVE,
            createdAt = System.currentTimeMillis(),
            requesterLocation = activeLocation,
            acceptedResponders = emptyList(),
            arrivedResponders = emptyList(),
            arrivedResponder = null,
            responderLocations = emptyMap(),
            responderDistances = emptyMap()
        )
        val updated = listOf(newEmergency) + emergencies.value
        emergencies.value = updated
        _currentEmergency.value = newEmergency
        refreshActiveList()

        // Phase 9 Realtime WebSocket Event
        _realtimeEvents.tryEmit(
            RealtimeEmergencyEvent.StatusChanged(
                emergencyId = newEmergency.id,
                oldStatus = EmergencyStatus.ACTIVE,
                newStatus = EmergencyStatus.ACTIVE
            )
        )

        // Phase 9 Mock FCM Push Dispatch
        notificationDataSource?.let { nds ->
            val allUsers = userLookup?.invoke() ?: MockData.mockUsers

            // 1. Alert trusted contacts (irrespective of distance)
            val trustedContactUsers = allUsers.filter { user ->
                requester.emergencyContacts.any { it.equals(user.safeWalkId, ignoreCase = true) }
            }
            trustedContactUsers.forEach { contactUser ->
                nds.dispatchFcmPush(
                    type = NotificationType.TRUSTED_CONTACT_ALERT,
                    recipientUserId = contactUser.id,
                    title = "Emergency Alert: Trusted Contact",
                    message = "${requester.name} has triggered an SOS emergency and needs assistance immediately.",
                    emergencyId = newEmergency.id,
                    senderId = requester.id,
                    senderName = requester.name,
                    navigationTarget = "emergency_map/${newEmergency.id}"
                )
            }

            // 2. Alert nearby candidate responders (Section 9.4 deduplication: exclude trusted contacts and requester)
            allUsers.filter { user ->
                !newEmergency.isUserRequester(user.id) &&
                !requester.emergencyContacts.any { it.equals(user.safeWalkId, ignoreCase = true) }
            }.forEach { candidate ->
                val candLoc = locationLookup?.invoke(candidate.id) ?: MockData.defaultLocation
                if (isUserEligibleForEmergency(candidate, newEmergency, candLoc)) {
                    nds.dispatchFcmPush(
                        type = NotificationType.EMERGENCY_SOS_NEARBY,
                        recipientUserId = candidate.id,
                        title = "Emergency SOS Nearby",
                        message = "An SOS alert was triggered near your location. Assistance needed.",
                        emergencyId = newEmergency.id,
                        senderId = requester.id,
                        senderName = requester.name,
                        navigationTarget = "emergency_map/${newEmergency.id}"
                    )
                }
            }
        }

        return newEmergency
    }

    /**
     * Accepts emergency. Prevents requester self-acceptance and duplicate responder acceptance.
     * Derives responder coordinates deterministically from Haversine geometry so distance is derived from coordinates.
     */
    fun acceptEmergency(emergencyId: String, responder: User, initialDistanceMeters: Double = 100.0): Result<Emergency> {
        val currentList = emergencies.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == emergencyId }
        if (index == -1) {
            return Result.failure(IllegalArgumentException("Emergency not found"))
        }

        val existing = currentList[index]
        if (existing.isTerminal) {
            return Result.failure(IllegalStateException("Cannot accept a terminal emergency"))
        }

        // Rule: Requester cannot accept own SOS
        if (existing.isUserRequester(responder.id)) {
            return Result.failure(IllegalStateException("Requester cannot accept their own emergency"))
        }

        // Rule: Same responder cannot accept twice
        if (existing.isUserAcceptedResponder(responder.id)) {
            return Result.failure(IllegalStateException("Responder has already accepted this emergency"))
        }

        // Rule: Authority must be verified
        if (responder.role.isAuthority && (!responder.isVerified || responder.verificationStatus != VerificationStatus.VERIFIED)) {
            return Result.failure(IllegalStateException("Account verification pending. Unverified authorities cannot accept emergency requests."))
        }

        // Add responder (Multi-responder support)
        val updatedResponders = existing.acceptedResponders + responder

        // Assign independent coordinates based on responder index & bearing
        val responderIndex = existing.acceptedResponders.size
        val bearing = (responderIndex * 90.0) % 360.0
        val responderLoc = Location.createAtOffset(
            base = existing.requesterLocation,
            distanceMeters = initialDistanceMeters,
            bearingDegrees = bearing,
            address = "En route (${responder.name}) via SafeWalk network"
        )
        val calculatedDistance = responderLoc.distanceTo(existing.requesterLocation)
        val updatedLocations = existing.responderLocations + (responder.id to responderLoc)
        val updatedDistances = existing.responderDistances + (responder.id to calculatedDistance)

        val updatedEmergency = existing.copy(
            status = EmergencyStatus.ACCEPTED,
            acceptedResponders = updatedResponders,
            responderLocations = updatedLocations,
            responderDistances = updatedDistances
        )

        currentList[index] = updatedEmergency
        emergencies.value = currentList
        if (_currentEmergency.value?.id == emergencyId) {
            _currentEmergency.value = updatedEmergency
        }
        refreshActiveList()

        // Phase 9 Realtime WebSocket Events
        _realtimeEvents.tryEmit(
            RealtimeEmergencyEvent.ResponderAccepted(
                emergencyId = emergencyId,
                responder = responder
            )
        )
        _realtimeEvents.tryEmit(
            RealtimeEmergencyEvent.StatusChanged(
                emergencyId = emergencyId,
                oldStatus = existing.status,
                newStatus = updatedEmergency.status
            )
        )

        // Phase 9 Mock FCM Push Dispatch to Requester
        notificationDataSource?.dispatchFcmPush(
            type = NotificationType.RESPONDER_ACCEPTED,
            recipientUserId = existing.requester.id,
            title = "Responder On The Way",
            message = "${responder.name} has accepted your emergency and is navigating towards you.",
            emergencyId = emergencyId,
            senderId = responder.id,
            senderName = responder.name,
            navigationTarget = "emergency_map/$emergencyId"
        )

        return Result.success(updatedEmergency)
    }

    /**
     * Updates simulated distance for a responder.
     * Synchronously derives updated coordinates for backward compatibility.
     */
    fun updateResponderDistance(emergencyId: String, responderId: String, distanceMeters: Double): Result<Emergency> {
        val currentList = emergencies.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == emergencyId }
        if (index == -1) {
            return Result.failure(IllegalArgumentException("Emergency not found"))
        }
        val existing = currentList[index]
        if (existing.isTerminal) {
            return Result.failure(IllegalStateException("Cannot update distance for a terminal emergency"))
        }
        val responderIndex = existing.acceptedResponders.indexOfFirst { it.id == responderId }.coerceAtLeast(0)
        val bearing = (responderIndex * 90.0) % 360.0
        val updatedLoc = Location.createAtOffset(
            base = existing.requesterLocation,
            distanceMeters = distanceMeters,
            bearingDegrees = bearing,
            address = "En route (${existing.acceptedResponders.find { it.id == responderId }?.name ?: "Responder"})"
        )
        val updatedLocations = existing.responderLocations + (responderId to updatedLoc)
        val updatedDistances = existing.responderDistances + (responderId to distanceMeters)
        val updated = existing.copy(
            responderLocations = updatedLocations,
            responderDistances = updatedDistances
        )
        currentList[index] = updated
        emergencies.value = currentList
        if (_currentEmergency.value?.id == emergencyId) {
            _currentEmergency.value = updated
        }
        return Result.success(updated)
    }

    /**
     * Phase 7: Updates requester live location.
     * All accepted responders receive the new requester coordinates, and all responder distances
     * are recalculated from the new coordinates via Haversine. Responder coordinates remain unchanged.
     */
    fun updateRequesterLocation(emergencyId: String, location: Location): Result<Emergency> {
        val currentList = emergencies.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == emergencyId }
        if (index == -1) {
            return Result.failure(IllegalArgumentException("Emergency not found"))
        }
        val existing = currentList[index]
        if (existing.isTerminal) {
            return Result.failure(IllegalStateException("Cannot update location for a terminal emergency"))
        }

        val updatedRequesterLoc = location.copy(
            timestamp = if (location.timestamp > 0) location.timestamp else System.currentTimeMillis(),
            status = LocationStatus.ACTIVE
        )

        // Recalculate distance from new requester coordinates to each responder's independent coordinates
        val updatedDistances = existing.acceptedResponders.associate { resp ->
            val respLoc = existing.responderLocations[resp.id] ?: updatedRequesterLoc
            resp.id to updatedRequesterLoc.distanceTo(respLoc)
        }

        val updatedEmergency = existing.copy(
            requesterLocation = updatedRequesterLoc,
            responderDistances = updatedDistances
        )

        currentList[index] = updatedEmergency
        emergencies.value = currentList
        if (_currentEmergency.value?.id == emergencyId) {
            _currentEmergency.value = updatedEmergency
        }
        refreshActiveList()

        _realtimeEvents.tryEmit(
            RealtimeEmergencyEvent.LocationUpdated(
                emergencyId = emergencyId,
                participantId = existing.requester.id,
                location = updatedRequesterLoc
            )
        )

        return Result.success(updatedEmergency)
    }

    /**
     * Phase 7: Updates a specific responder's live location.
     * Recalculates distance between this responder and requester from coordinates.
     * Requester coordinates and other responders' coordinates remain completely unchanged.
     */
    fun updateResponderLocation(emergencyId: String, responderId: String, location: Location): Result<Emergency> {
        val currentList = emergencies.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == emergencyId }
        if (index == -1) {
            return Result.failure(IllegalArgumentException("Emergency not found"))
        }
        val existing = currentList[index]
        if (existing.isTerminal) {
            return Result.failure(IllegalStateException("Cannot update location for a terminal emergency"))
        }
        if (!existing.isUserAcceptedResponder(responderId)) {
            return Result.failure(IllegalStateException("User is not an accepted responder for this emergency"))
        }

        val updatedResponderLoc = location.copy(
            timestamp = if (location.timestamp > 0) location.timestamp else System.currentTimeMillis(),
            status = LocationStatus.ACTIVE
        )

        val calculatedDist = updatedResponderLoc.distanceTo(existing.requesterLocation)
        val updatedLocations = existing.responderLocations + (responderId to updatedResponderLoc)
        val updatedDistances = existing.responderDistances + (responderId to calculatedDist)

        val isArrived = calculatedDist <= 25.0
        val updatedArrivedResponders = if (isArrived && existing.arrivedResponders.none { it.id == responderId }) {
            val responder = existing.acceptedResponders.first { it.id == responderId }
            existing.arrivedResponders + responder
        } else {
            existing.arrivedResponders
        }

        val updatedEmergency = existing.copy(
            responderLocations = updatedLocations,
            responderDistances = updatedDistances,
            arrivedResponders = updatedArrivedResponders,
            arrivedResponder = if (isArrived && existing.arrivedResponder == null) {
                existing.acceptedResponders.first { it.id == responderId }
            } else {
                existing.arrivedResponder
            }
        )

        currentList[index] = updatedEmergency
        emergencies.value = currentList
        if (_currentEmergency.value?.id == emergencyId) {
            _currentEmergency.value = updatedEmergency
        }
        refreshActiveList()

        _realtimeEvents.tryEmit(
            RealtimeEmergencyEvent.LocationUpdated(
                emergencyId = emergencyId,
                participantId = responderId,
                location = updatedResponderLoc,
                distanceToCounterpartMeters = calculatedDist
            )
        )

        if (isArrived && existing.arrivedResponders.none { it.id == responderId }) {
            val responder = existing.acceptedResponders.first { it.id == responderId }
            _realtimeEvents.tryEmit(
                RealtimeEmergencyEvent.ResponderArrived(
                    emergencyId = emergencyId,
                    responder = responder
                )
            )
            notificationDataSource?.dispatchFcmPush(
                type = NotificationType.RESPONDER_ARRIVED,
                recipientUserId = existing.requester.id,
                title = "Responder Arrived",
                message = "${responder.name} has arrived at your location (within 25m).",
                emergencyId = emergencyId,
                senderId = responder.id,
                senderName = responder.name,
                navigationTarget = "emergency_map/$emergencyId"
            )
        }

        return Result.success(updatedEmergency)
    }

    /**
     * Phase 7: Advances requester along deterministic mock path (Location A -> B -> C).
     */
    fun advanceRequesterLocation(emergencyId: String): Result<Emergency> {
        val existing = emergencies.value.find { it.id == emergencyId }
            ?: return Result.failure(IllegalArgumentException("Emergency not found"))
        if (existing.isTerminal) {
            return Result.failure(IllegalStateException("Cannot advance location for a terminal emergency"))
        }

        val step = (requesterStepCount[emergencyId] ?: 0) + 1
        requesterStepCount[emergencyId] = step

        val nextLocation = Location.createAtOffset(
            base = existing.requesterLocation,
            distanceMeters = 100.0,
            bearingDegrees = 0.0,
            address = "Requester moved (Waypoint $step)"
        )
        return updateRequesterLocation(emergencyId, nextLocation)
    }

    /**
     * Phase 7: Advances responder along deterministic mock movement sequence:
     * 100m -> 75m -> 50m -> 30m -> 25m -> 24m.
     */
    fun advanceResponderLocation(emergencyId: String, responderId: String): Result<Emergency> {
        val existing = emergencies.value.find { it.id == emergencyId }
            ?: return Result.failure(IllegalArgumentException("Emergency not found"))
        if (existing.isTerminal) {
            return Result.failure(IllegalStateException("Cannot advance location for a terminal emergency"))
        }
        if (!existing.isUserAcceptedResponder(responderId)) {
            return Result.failure(IllegalStateException("User is not an accepted responder for this emergency"))
        }

        val key = "$emergencyId-$responderId"
        val currentIdx = responderStepMap[key] ?: 0
        val nextIdx = if (currentIdx + 1 < RESPONDER_MOCK_STEPS.size) currentIdx + 1 else currentIdx
        responderStepMap[key] = nextIdx
        val targetDistance = RESPONDER_MOCK_STEPS[nextIdx]

        val responder = existing.acceptedResponders.find { it.id == responderId }
            ?: return Result.failure(IllegalStateException("Responder not found"))
        val responderIndex = existing.acceptedResponders.indexOfFirst { it.id == responderId }.coerceAtLeast(0)
        val bearing = (responderIndex * 90.0) % 360.0

        val nextLoc = Location.createAtOffset(
            base = existing.requesterLocation,
            distanceMeters = targetDistance,
            bearingDegrees = bearing,
            address = "En route (${responder.name}) - ${targetDistance.toInt()}m"
        )
        return updateResponderLocation(emergencyId, responderId, nextLoc)
    }

    /**
     * Marks a responder as arrived.
     * Arrival rule: distance must be <= 25 meters (<= 25m, not < 25m).
     */
    fun markArrived(emergencyId: String, responder: User, distanceMeters: Double = 25.0): Result<Emergency> {
        val currentList = emergencies.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == emergencyId }
        if (index == -1) {
            return Result.failure(IllegalArgumentException("Emergency not found"))
        }

        val existing = currentList[index]
        if (existing.isTerminal) {
            return Result.failure(IllegalStateException("Cannot arrive at a terminal emergency"))
        }

        if (!existing.isUserAcceptedResponder(responder.id)) {
            return Result.failure(IllegalStateException("User is not an accepted responder for this emergency"))
        }

        // Strict Arrival Rule: distance <= 25 meters
        if (distanceMeters > 25.0) {
            return Result.failure(
                com.example.safewalk.core.error.SafeWalkException.ArrivalDistanceExceededError(distanceMeters)
            )
        }

        // Single Arrival Guard: Only one responder can arrive at the scene (matching backend pessimistic write lock)
        if (existing.arrivedResponder != null) {
            if (existing.arrivedResponder.id == responder.id) {
                return Result.success(existing)
            } else {
                return Result.failure(
                    com.example.safewalk.core.error.SafeWalkException.SingleArrivalConflictError(existing.arrivedResponder.name)
                )
            }
        }

        val arrivedLocation = Location.createAtOffset(
            base = existing.requesterLocation,
            distanceMeters = distanceMeters,
            bearingDegrees = 0.0,
            address = "Arrived on scene (within 25m)"
        )

        val updatedArrivedResponders = listOf(responder)

        val updatedDistances = existing.responderDistances + (responder.id to distanceMeters)
        val finalStatus = if (existing.status == EmergencyStatus.ACTIVE) EmergencyStatus.ACCEPTED else existing.status

        val updatedEmergency = existing.copy(
            status = finalStatus,
            arrivedResponder = existing.arrivedResponder ?: responder,
            arrivedResponders = updatedArrivedResponders,
            responderLocations = existing.responderLocations + (responder.id to arrivedLocation),
            responderDistances = updatedDistances
        )

        currentList[index] = updatedEmergency
        emergencies.value = currentList
        if (_currentEmergency.value?.id == emergencyId) {
            _currentEmergency.value = updatedEmergency
        }
        refreshActiveList()

        _realtimeEvents.tryEmit(
            RealtimeEmergencyEvent.ResponderArrived(
                emergencyId = emergencyId,
                responder = responder
            )
        )
        notificationDataSource?.dispatchFcmPush(
            type = NotificationType.RESPONDER_ARRIVED,
            recipientUserId = existing.requester.id,
            title = "Responder Arrived",
            message = "${responder.name} has arrived at your location (within 25m).",
            emergencyId = emergencyId,
            senderId = responder.id,
            senderName = responder.name,
            navigationTarget = "emergency_map/$emergencyId"
        )

        return Result.success(updatedEmergency)
    }

    /**
     * Stops live location updates for an emergency (marks all participant locations as STOPPED).
     */
    fun stopLiveLocation(emergencyId: String): Result<Unit> {
        val currentList = emergencies.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == emergencyId }
        if (index == -1) {
            return Result.failure(IllegalArgumentException("Emergency not found"))
        }
        val existing = currentList[index]
        val stoppedReqLoc = existing.requesterLocation.copy(status = LocationStatus.STOPPED)
        val stoppedRespLocs = existing.responderLocations.mapValues { (_, loc) ->
            loc.copy(status = LocationStatus.STOPPED)
        }
        val updated = existing.copy(
            requesterLocation = stoppedReqLoc,
            responderLocations = stoppedRespLocs
        )
        currentList[index] = updated
        emergencies.value = currentList
        if (_currentEmergency.value?.id == emergencyId) {
            _currentEmergency.value = updated
        }
        refreshActiveList()
        return Result.success(Unit)
    }

    /**
     * Phase 7 IDOR-style Authorization: Queries participant location with strict access control.
     */
    fun getEmergencyParticipantLocation(emergencyId: String, participantId: String, viewerId: String): Result<Location> {
        val emergency = emergencies.value.find { it.id == emergencyId }
            ?: return Result.failure(IllegalArgumentException("Emergency not found"))

        // Authorization check: Viewer must be requester or accepted responder
        val isViewerRequester = emergency.isUserRequester(viewerId)
        val isViewerAcceptedResponder = emergency.isUserAcceptedResponder(viewerId)
        if (!isViewerRequester && !isViewerAcceptedResponder) {
            return Result.failure(SecurityException("Access Denied: Viewer is not an authorized participant of this emergency"))
        }

        // Target check: Target must be requester or accepted responder of this emergency
        val isTargetRequester = emergency.isUserRequester(participantId)
        val isTargetAcceptedResponder = emergency.isUserAcceptedResponder(participantId)
        if (!isTargetRequester && !isTargetAcceptedResponder) {
            return Result.failure(SecurityException("Access Denied: Requested participant is not part of this emergency"))
        }

        val loc = if (isTargetRequester) {
            emergency.requesterLocation
        } else {
            emergency.responderLocations[participantId]
                ?: return Result.failure(NoSuchElementException("Location for responder not found"))
        }

        val finalLoc = if (emergency.isTerminal) {
            loc.copy(status = LocationStatus.STOPPED)
        } else {
            loc
        }

        return Result.success(finalLoc)
    }

    /**
     * Phase 7: Retrieves all authorized participant live locations for a viewer.
     */
    fun getVisibleParticipantLocations(emergencyId: String, viewerId: String): Result<List<ParticipantLocation>> {
        val emergency = emergencies.value.find { it.id == emergencyId }
            ?: return Result.failure(IllegalArgumentException("Emergency not found"))

        val isViewerRequester = emergency.isUserRequester(viewerId)
        val isViewerAcceptedResponder = emergency.isUserAcceptedResponder(viewerId)
        if (!isViewerRequester && !isViewerAcceptedResponder) {
            return Result.failure(SecurityException("Access Denied: Viewer is not an authorized participant of this emergency"))
        }

        val participants = mutableListOf<ParticipantLocation>()

        val reqStatus = if (emergency.isTerminal) LocationStatus.STOPPED else emergency.requesterLocation.effectiveStatus()
        val reqLoc = emergency.requesterLocation.copy(status = reqStatus)

        participants.add(
            ParticipantLocation(
                userId = emergency.requester.id,
                userName = emergency.requester.name,
                safeWalkId = emergency.requester.safeWalkId,
                role = EmergencyRole.REQUESTER,
                location = reqLoc,
                distanceToCounterpartMeters = null,
                status = reqStatus,
                isArrived = false
            )
        )

        emergency.acceptedResponders.forEach { resp ->
            val loc = emergency.responderLocations[resp.id] ?: emergency.requesterLocation
            val respStatus = if (emergency.isTerminal) LocationStatus.STOPPED else loc.effectiveStatus()
            val dist = emergency.responderDistances[resp.id] ?: reqLoc.distanceTo(loc)
            val isArrived = emergency.isResponderArrived(resp.id) || dist <= 25.0
            participants.add(
                ParticipantLocation(
                    userId = resp.id,
                    userName = resp.name,
                    safeWalkId = resp.safeWalkId,
                    role = EmergencyRole.RESPONDER,
                    location = loc.copy(status = respStatus),
                    distanceToCounterpartMeters = dist,
                    status = respStatus,
                    isArrived = isArrived
                )
            )
        }

        return Result.success(participants)
    }

    /**
     * Confirms resolution under Section 22 Two-Party Rule.
     * Either party may confirm first. Emergency remains ACCEPTED until both confirm.
     * When both confirm (or if no responders ever attached and requester confirms), status becomes RESOLVED.
     */
    fun confirmResolution(emergencyId: String, user: User): Result<Emergency> {
        val currentList = emergencies.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == emergencyId }
        if (index == -1) {
            return Result.failure(IllegalArgumentException("Emergency not found"))
        }

        val existing = currentList[index]
        if (existing.isTerminal) {
            return Result.failure(IllegalStateException("Cannot confirm resolution for a terminal emergency"))
        }

        val isRequester = existing.isUserRequester(user.id)
        val designatedArrived = existing.effectiveArrivedResponder
        val isDesignatedArrivedResponder = designatedArrived != null && user.id == designatedArrived.id

        if (!isRequester && !isDesignatedArrivedResponder) {
            if (existing.isUserAcceptedResponder(user.id)) {
                return Result.failure(
                    com.example.safewalk.core.error.SafeWalkException.ConflictError(
                        "Only the designated arrived responder (${designatedArrived?.name ?: "on-scene responder"}) can confirm emergency resolution."
                    )
                )
            }
            return Result.failure(
                com.example.safewalk.core.error.SafeWalkException.ForbiddenError(
                    "User is not authorized to confirm resolution for this emergency."
                )
            )
        }

        val reqConfirmed = if (isRequester) true else existing.requesterConfirmedResolution
        val respConfirmed = if (isDesignatedArrivedResponder) true else existing.responderConfirmedResolution

        val hasResponders = existing.acceptedResponders.isNotEmpty() || existing.effectiveArrivedResponder != null
        val isBothConfirmed = (!hasResponders && reqConfirmed) || (reqConfirmed && respConfirmed)
        val finalStatus = if (isBothConfirmed) EmergencyStatus.RESOLVED else existing.status

        val stoppedReq = if (isBothConfirmed) existing.requesterLocation.copy(status = LocationStatus.STOPPED) else existing.requesterLocation
        val stoppedResp = if (isBothConfirmed) {
            existing.responderLocations.mapValues { (_, loc) -> loc.copy(status = LocationStatus.STOPPED) }
        } else {
            existing.responderLocations
        }

        val updatedEmergency = existing.copy(
            status = finalStatus,
            requesterLocation = stoppedReq,
            responderLocations = stoppedResp,
            requesterConfirmedResolution = reqConfirmed,
            responderConfirmedResolution = respConfirmed,
            resolvedAt = if (isBothConfirmed) (existing.resolvedAt ?: System.currentTimeMillis()) else existing.resolvedAt
        )

        currentList[index] = updatedEmergency
        emergencies.value = currentList
        if (_currentEmergency.value?.id == emergencyId) {
            _currentEmergency.value = updatedEmergency
        }
        refreshActiveList()

        if (isBothConfirmed) {
            val now = System.currentTimeMillis()
            _realtimeEvents.tryEmit(
                RealtimeEmergencyEvent.EmergencyResolved(
                    emergencyId = emergencyId,
                    resolvedAt = now
                )
            )
            _realtimeEvents.tryEmit(
                RealtimeEmergencyEvent.StatusChanged(
                    emergencyId = emergencyId,
                    oldStatus = existing.status,
                    newStatus = EmergencyStatus.RESOLVED
                )
            )
            notificationDataSource?.let { nds ->
                nds.dispatchFcmPush(
                    type = NotificationType.EMERGENCY_RESOLVED,
                    recipientUserId = existing.requester.id,
                    title = "Emergency Resolved",
                    message = "Emergency $emergencyId has been successfully resolved.",
                    emergencyId = emergencyId,
                    navigationTarget = "emergency_map/$emergencyId"
                )
                existing.acceptedResponders.forEach { resp ->
                    nds.dispatchFcmPush(
                        type = NotificationType.EMERGENCY_RESOLVED,
                        recipientUserId = resp.id,
                        title = "Emergency Resolved",
                        message = "Emergency $emergencyId has been successfully resolved.",
                        emergencyId = emergencyId,
                        navigationTarget = "emergency_map/$emergencyId"
                    )
                }
            }
        } else {
            // One party confirmed; awaiting counterpart confirmation
            val pendingMsg = if (reqConfirmed) {
                "Requester has confirmed resolution. Awaiting arrived responder confirmation."
            } else {
                "Arrived responder has confirmed resolution. Awaiting requester confirmation."
            }
            _realtimeEvents.tryEmit(
                RealtimeEmergencyEvent.ResolutionConfirmationPending(
                    emergencyId = emergencyId,
                    requesterResolveConfirmed = reqConfirmed,
                    responderResolveConfirmed = respConfirmed,
                    message = pendingMsg
                )
            )
            _realtimeEvents.tryEmit(
                RealtimeEmergencyEvent.ResolutionConfirmed(
                    emergencyId = emergencyId,
                    participantId = user.id,
                    isRequester = isRequester
                )
            )
        }

        return Result.success(updatedEmergency)
    }

    /**
     * Direct Requester Resolution action when an accepted responder reaches <= 25m.
     * Transitions emergency state directly to RESOLVED.
     */
    fun resolveEmergency(emergencyId: String, user: User): Result<Emergency> {
        val currentList = emergencies.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == emergencyId }
        if (index == -1) {
            return Result.failure(IllegalArgumentException("Emergency not found"))
        }

        val existing = currentList[index]
        if (existing.isTerminal) {
            return Result.failure(IllegalStateException("Emergency is already in terminal state"))
        }

        if (!existing.canRequesterResolve()) {
            return Result.failure(
                IllegalStateException("Resolve not allowed: at least one accepted responder must be within <= 25m (current distances: ${existing.responderDistances})")
            )
        }

        val stoppedReq = existing.requesterLocation.copy(status = LocationStatus.STOPPED)
        val stoppedResp = existing.responderLocations.mapValues { (_, loc) -> loc.copy(status = LocationStatus.STOPPED) }
        val now = System.currentTimeMillis()

        val updatedEmergency = existing.copy(
            status = EmergencyStatus.RESOLVED,
            requesterLocation = stoppedReq,
            responderLocations = stoppedResp,
            requesterConfirmedResolution = true,
            responderConfirmedResolution = true,
            resolvedAt = now
        )

        currentList[index] = updatedEmergency
        emergencies.value = currentList
        if (_currentEmergency.value?.id == emergencyId) {
            _currentEmergency.value = updatedEmergency
        }
        refreshActiveList()

        _realtimeEvents.tryEmit(
            RealtimeEmergencyEvent.EmergencyResolved(
                emergencyId = emergencyId,
                resolvedAt = now
            )
        )
        _realtimeEvents.tryEmit(
            RealtimeEmergencyEvent.StatusChanged(
                emergencyId = emergencyId,
                oldStatus = existing.status,
                newStatus = EmergencyStatus.RESOLVED
            )
        )

        notificationDataSource?.let { nds ->
            existing.acceptedResponders.forEach { resp ->
                nds.dispatchFcmPush(
                    type = NotificationType.EMERGENCY_RESOLVED,
                    recipientUserId = resp.id,
                    title = "Emergency Resolved",
                    message = "Emergency $emergencyId has been successfully resolved.",
                    emergencyId = emergencyId,
                    navigationTarget = "emergency_map/$emergencyId"
                )
            }
        }

        return Result.success(updatedEmergency)
    }

    /**
     * Responder opts out / rejects an accepted emergency (POST /api/v1/emergencies/{id}/reject).
     * If this was the last remaining responder, status reverts from ACCEPTED back to ACTIVE.
     */
    fun rejectEmergency(emergencyId: String, responder: User): Result<Emergency> {
        val currentList = emergencies.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == emergencyId }
        if (index == -1) {
            return Result.failure(IllegalArgumentException("Emergency not found"))
        }

        val existing = currentList[index]
        if (existing.isTerminal) {
            return Result.failure(IllegalStateException("Cannot withdraw from a terminal emergency"))
        }

        if (!existing.isUserAcceptedResponder(responder.id)) {
            return Result.failure(IllegalStateException("User is not an accepted responder for this emergency"))
        }

        val updatedResponders = existing.acceptedResponders.filter { it.id != responder.id }
        val updatedLocations = existing.responderLocations.filterKeys { it != responder.id }
        val updatedDistances = existing.responderDistances.filterKeys { it != responder.id }
        val updatedArrived = existing.arrivedResponders.filter { it.id != responder.id }
        val newArrivedResponder = if (existing.arrivedResponder?.id == responder.id) null else existing.arrivedResponder

        // Backend FSM invariant: If last responder leaves, revert to ACTIVE!
        val newStatus = if (updatedResponders.isEmpty()) EmergencyStatus.ACTIVE else existing.status

        val updatedEmergency = existing.copy(
            status = newStatus,
            acceptedResponders = updatedResponders,
            responderLocations = updatedLocations,
            responderDistances = updatedDistances,
            arrivedResponders = updatedArrived,
            arrivedResponder = newArrivedResponder
        )

        currentList[index] = updatedEmergency
        emergencies.value = currentList
        if (_currentEmergency.value?.id == emergencyId) {
            _currentEmergency.value = updatedEmergency
        }
        refreshActiveList()

        _realtimeEvents.tryEmit(
            RealtimeEmergencyEvent.EmergencyRejected(
                emergencyId = emergencyId,
                responderId = responder.id,
                remainingRespondersCount = updatedResponders.size
            )
        )
        if (newStatus != existing.status) {
            _realtimeEvents.tryEmit(
                RealtimeEmergencyEvent.StatusChanged(
                    emergencyId = emergencyId,
                    oldStatus = existing.status,
                    newStatus = newStatus
                )
            )
        }

        return Result.success(updatedEmergency)
    }

    /**
     * Cancels emergency within the strict 5.000-second window.
     * Validates emergency password if configured.
     * Stops live location updates.
     */
    fun cancelEmergency(
        emergencyId: String,
        emergencyPassword: String?,
        reason: String
    ): Result<Emergency> {
        val currentList = emergencies.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == emergencyId }
        if (index == -1) {
            return Result.failure(IllegalArgumentException("Emergency not found"))
        }

        val existing = currentList[index]
        if (existing.isTerminal) {
            return Result.failure(IllegalStateException("Emergency is already in terminal state"))
        }

        // Strict 5.000s duration check (server-side exact timestamp check)
        if (!existing.canCancel()) {
            return Result.failure(
                com.example.safewalk.core.error.SafeWalkException.CancellationExpiredError(
                    "Cancellation window has expired. SOS can only be cancelled within 5 seconds of creation."
                )
            )
        }

        // Validate Emergency Password if configured on user (matching backend EmergencyServiceImpl line 530)
        val expectedPassword = existing.requester.emergencyPassword
        if (expectedPassword != null && emergencyPassword != null) {
            if (emergencyPassword.trim() != expectedPassword.trim()) {
                return Result.failure(
                    com.example.safewalk.core.error.SafeWalkException.InvalidEmergencyPasswordError(
                        "Invalid emergency password. Cancellation rejected."
                    )
                )
            }
        }

        val stoppedReqLoc = existing.requesterLocation.copy(status = LocationStatus.STOPPED)
        val stoppedRespLocs = existing.responderLocations.mapValues { (_, loc) ->
            loc.copy(status = LocationStatus.STOPPED)
        }

        val updatedEmergency = existing.copy(
            status = EmergencyStatus.CANCELLED,
            requesterLocation = stoppedReqLoc,
            responderLocations = stoppedRespLocs,
            cancellationReason = reason
        )

        currentList[index] = updatedEmergency
        emergencies.value = currentList
        if (_currentEmergency.value?.id == emergencyId) {
            _currentEmergency.value = updatedEmergency
        }
        refreshActiveList()

        _realtimeEvents.tryEmit(
            RealtimeEmergencyEvent.EmergencyCancelled(
                emergencyId = emergencyId,
                reason = reason
            )
        )
        _realtimeEvents.tryEmit(
            RealtimeEmergencyEvent.StatusChanged(
                emergencyId = emergencyId,
                oldStatus = existing.status,
                newStatus = EmergencyStatus.CANCELLED
            )
        )
        notificationDataSource?.let { nds ->
            existing.acceptedResponders.forEach { resp ->
                nds.dispatchFcmPush(
                    type = NotificationType.EMERGENCY_CANCELLED,
                    recipientUserId = resp.id,
                    title = "Emergency Cancelled",
                    message = "Emergency $emergencyId was cancelled by requester: $reason.",
                    emergencyId = emergencyId,
                    navigationTarget = "emergency_map/$emergencyId"
                )
            }
        }

        return Result.success(updatedEmergency)
    }

    /**
     * Backward-compatible overload for cancelEmergency with reason only.
     */
    fun cancelEmergency(emergencyId: String, reason: String): Result<Emergency> {
        return cancelEmergency(emergencyId = emergencyId, emergencyPassword = null, reason = reason)
    }

    fun selectEmergency(emergencyId: String) {
        val found = emergencies.value.find { it.id == emergencyId }
        _currentEmergency.value = found
    }

    fun addEmergencyForTesting(emergency: Emergency) {
        val currentList = emergencies.value.toMutableList()
        currentList.removeAll { it.id == emergency.id }
        currentList.add(emergency)
        emergencies.value = currentList
        refreshActiveList()
    }

    fun reset() {
        emergencies.value = MockData.initialEmergencies
        _currentEmergency.value = null
        requesterStepCount.clear()
        responderStepMap.clear()
        refreshActiveList()
    }
}
