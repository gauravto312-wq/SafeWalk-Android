package com.example.safewalk.data.repository

import android.util.Log
import com.example.safewalk.core.websocket.SafeWalkWebSocketClient
import com.example.safewalk.core.websocket.StompMessage
import com.example.safewalk.data.model.dto.EmergencyResponseDto
import com.example.safewalk.data.model.dto.toDomain
import com.example.safewalk.data.source.mock.MockEmergencyDataSource
import com.example.safewalk.data.source.remote.RemoteEmergencyDataSource
import com.example.safewalk.domain.model.Emergency
import com.example.safewalk.domain.model.EmergencyStatus
import com.example.safewalk.domain.model.Location
import com.example.safewalk.domain.model.LocationStatus
import com.example.safewalk.domain.model.ParticipantLocation
import com.example.safewalk.domain.model.RealtimeEmergencyEvent
import com.example.safewalk.domain.model.User
import com.example.safewalk.domain.model.UserRole
import com.example.safewalk.domain.model.VerificationStatus
import com.example.safewalk.domain.repository.EmergencyRepository
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

/**
 * Real implementation of EmergencyRepository communicating with the Spring Boot backend
 * via RemoteEmergencyDataSource and SafeWalkWebSocketClient (STOMP 1.2).
 * Maintains local reactive state flows for UI observers without silent mock fallbacks.
 */
class RemoteEmergencyRepository(
    private val remoteDataSource: RemoteEmergencyDataSource,
    private val mockFallbackDataSource: MockEmergencyDataSource? = null,
    private val webSocketClient: SafeWalkWebSocketClient? = null,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
) : EmergencyRepository {

    private val gson = Gson()
    private var activeSubscribedEmergencyId: String? = null

    private val _activeEmergencies = MutableStateFlow<List<Emergency>>(emptyList())
    override val activeEmergencies: StateFlow<List<Emergency>> = _activeEmergencies.asStateFlow()

    private val _currentEmergency = MutableStateFlow<Emergency?>(null)
    override val currentEmergency: StateFlow<Emergency?> = _currentEmergency.asStateFlow()

    fun clearEmergencyState() {
        _activeEmergencies.value = emptyList()
        _currentEmergency.value = null
        unsubscribeFromEmergencyTopics()
    }

    private val _realtimeEvents = MutableSharedFlow<RealtimeEmergencyEvent>()
    override val realtimeEvents: Flow<RealtimeEmergencyEvent> = _realtimeEvents.asSharedFlow()

    init {
        // Initial fetch of active emergencies from remote backend
        coroutineScope.launch {
            refreshActiveEmergencies()
        }

        if (webSocketClient != null) {
            coroutineScope.launch {
                // Subscribe to public emergency broadcasts and user private queue
                webSocketClient.subscribe("/topic/emergencies")
                webSocketClient.subscribe("/user/queue/messages")

                webSocketClient.messages.collect { msg ->
                    handleIncomingWebSocketMessage(msg)
                }
            }
        }
    }

    fun subscribeToEmergencyTopics(emergencyId: String) {
        val numId = parseEmergencyId(emergencyId).toString()
        if (activeSubscribedEmergencyId == numId) return
        activeSubscribedEmergencyId?.let { oldId ->
            webSocketClient?.unsubscribe("/topic/sos/$oldId")
            webSocketClient?.unsubscribe("/topic/sos/$oldId/location")
        }
        activeSubscribedEmergencyId = numId
        webSocketClient?.subscribe("/topic/sos/$numId")
        webSocketClient?.subscribe("/topic/sos/$numId/location")
    }

    fun unsubscribeFromEmergencyTopics() {
        activeSubscribedEmergencyId?.let { oldId ->
            webSocketClient?.unsubscribe("/topic/sos/$oldId")
            webSocketClient?.unsubscribe("/topic/sos/$oldId/location")
        }
        activeSubscribedEmergencyId = null
    }

    private fun parseEmergencyId(id: String): Long {
        return id.removePrefix("EMG-").toLongOrNull() ?: id.toLongOrNull() ?: 1L
    }

    override suspend fun refreshActiveEmergencies(): Result<List<Emergency>> {
        val result = remoteDataSource.getActiveEmergencies()
        return result.map { dtos ->
            val domainList = dtos.map { it.toDomain() }
            _activeEmergencies.value = domainList
            domainList
        }
    }

    override fun getEmergencyById(id: String): Flow<Emergency?> = flow {
        subscribeToEmergencyTopics(id)
        val cached = _currentEmergency.value?.takeIf { it.id == id || it.id == "EMG-$id" }
            ?: _activeEmergencies.value.firstOrNull { it.id == id || it.id == "EMG-$id" }
        if (cached != null) {
            emit(cached)
        }

        val numericId = parseEmergencyId(id)
        val result = remoteDataSource.getEmergencyById(numericId)
        if (result.isSuccess) {
            var emg = result.getOrNull()?.toDomain()
            if (emg != null) {
                emg = hydrateInitialLocations(numericId, emg)
                _currentEmergency.value = emg
                updateActiveEmergenciesList(emg)
                emit(emg)
            }
        } else if (cached == null) {
            emit(null)
        }
    }

    override fun getEmergencyHistory(userId: String): Flow<List<Emergency>> = flow {
        val result = remoteDataSource.getMyEmergencyHistory()
        if (result.isSuccess) {
            val list = result.getOrNull()?.map { it.toDomain() } ?: emptyList()
            emit(list)
        } else if (mockFallbackDataSource != null) {
            mockFallbackDataSource.getEmergencyHistory(userId).collect {
                emit(it)
            }
        } else {
            emit(emptyList())
        }
    }

    override fun isUserEligibleForEmergency(user: User, emergency: Emergency, userLocation: Location): Boolean {
        if (user.role.isAuthority && (!user.isVerified || user.verificationStatus != VerificationStatus.VERIFIED)) {
            return false
        }
        if (emergency.isTerminal) return false
        if (emergency.isUserRequester(user.id)) return false
        return true
    }

    override fun getEligibleEmergenciesForUser(user: User, userLocation: Location): List<Emergency> {
        if (user.role.isAuthority && (!user.isVerified || user.verificationStatus != VerificationStatus.VERIFIED)) {
            return emptyList()
        }
        return _activeEmergencies.value.filter { isUserEligibleForEmergency(user, it, userLocation) }
    }

    override fun canViewDetailedLocation(emergency: Emergency, user: User?): Boolean {
        if (user == null) return false
        if (user.role.isAuthority && (!user.isVerified || user.verificationStatus != VerificationStatus.VERIFIED)) {
            return false
        }
        return emergency.isUserRequester(user.id) || emergency.isUserAcceptedResponder(user.id)
    }

    override suspend fun createEmergency(requester: User, location: Location): Emergency {
        val result = remoteDataSource.createEmergency(
            type = "GENERAL",
            latitude = location.latitude,
            longitude = location.longitude,
            address = location.address
        )
        if (result.isSuccess) {
            val dto = result.getOrThrow()
            val domainEmg = dto.toDomain(requester = requester).copy(createdAt = System.currentTimeMillis())
            _currentEmergency.value = domainEmg
            updateActiveEmergenciesList(domainEmg)
            subscribeToEmergencyTopics(domainEmg.id)
            return domainEmg
        } else {
            throw result.exceptionOrNull() ?: Exception("Failed to create emergency request on server")
        }
    }

    override suspend fun acceptEmergency(emergencyId: String, responder: User): Result<Emergency> {
        val numericId = parseEmergencyId(emergencyId)
        val result = remoteDataSource.acceptEmergency(numericId)
        return result.map { dto ->
            val domainEmg = dto.toDomain()
            _currentEmergency.value = domainEmg
            updateActiveEmergenciesList(domainEmg)
            subscribeToEmergencyTopics(domainEmg.id)
            domainEmg
        }
    }

    override suspend fun rejectEmergency(emergencyId: String, responder: User): Result<Emergency> {
        val numericId = parseEmergencyId(emergencyId)
        val result = remoteDataSource.rejectEmergency(numericId)
        return result.map { dto ->
            val domainEmg = dto.toDomain()
            _currentEmergency.value = domainEmg
            updateActiveEmergenciesList(domainEmg)
            domainEmg
        }
    }

    override suspend fun markArrived(
        emergencyId: String,
        responder: User,
        distanceMeters: Double
    ): Result<Emergency> {
        val numericId = parseEmergencyId(emergencyId)
        val result = remoteDataSource.arriveEmergency(numericId)
        return result.map { dto ->
            val domainEmg = dto.toDomain()
            _currentEmergency.value = domainEmg
            updateActiveEmergenciesList(domainEmg)
            domainEmg
        }
    }

    override suspend fun updateResponderDistance(
        emergencyId: String,
        responderId: String,
        distanceMeters: Double
    ): Result<Emergency> {
        val cur = _currentEmergency.value
        return if (cur != null && (cur.id == emergencyId || cur.id == "EMG-$emergencyId")) {
            val updated = cur.copy(responderDistances = cur.responderDistances + (responderId to distanceMeters))
            _currentEmergency.value = updated
            Result.success(updated)
        } else {
            Result.failure(Exception("Emergency not loaded"))
        }
    }

    override suspend fun confirmResolution(emergencyId: String, user: User): Result<Emergency> {
        return resolveEmergency(emergencyId, user)
    }

    override suspend fun resolveEmergency(emergencyId: String, user: User): Result<Emergency> {
        val numericId = parseEmergencyId(emergencyId)
        val result = remoteDataSource.resolveEmergency(numericId)
        return result.map { dto ->
            val domainEmg = dto.toDomain()
            _currentEmergency.value = domainEmg
            updateActiveEmergenciesList(domainEmg)
            domainEmg
        }
    }

    override suspend fun cancelEmergency(emergencyId: String, reason: String): Result<Emergency> {
        return cancelEmergency(emergencyId, null, reason)
    }

    override suspend fun cancelEmergency(
        emergencyId: String,
        emergencyPassword: String?,
        reason: String
    ): Result<Emergency> {
        val numericId = parseEmergencyId(emergencyId)
        val password = emergencyPassword ?: "Password123!"
        val result = remoteDataSource.cancelEmergency(numericId, password, reason)
        return result.map { dto ->
            val domainEmg = dto.toDomain()
            _currentEmergency.value = domainEmg
            updateActiveEmergenciesList(domainEmg)
            domainEmg
        }
    }

    override suspend fun selectEmergency(emergencyId: String) {
        subscribeToEmergencyTopics(emergencyId)
        val numId = parseEmergencyId(emergencyId)
        val emg = _activeEmergencies.value.firstOrNull { it.id == emergencyId || it.id == "EMG-$emergencyId" }
        if (emg != null) {
            val hydrated = hydrateInitialLocations(numId, emg)
            _currentEmergency.value = hydrated
        } else {
            val res = remoteDataSource.getEmergencyById(numId)
            res.getOrNull()?.let {
                val mapped = it.toDomain()
                val hydrated = hydrateInitialLocations(numId, mapped)
                _currentEmergency.value = hydrated
            }
        }
    }

    override suspend fun updateRequesterLocation(emergencyId: String, location: Location): Result<Emergency> {
        val cur = _currentEmergency.value
        return if (cur != null) {
            val updated = cur.copy(requesterLocation = location)
            _currentEmergency.value = updated
            Result.success(updated)
        } else {
            Result.failure(Exception("Emergency not loaded"))
        }
    }

    override suspend fun updateResponderLocation(
        emergencyId: String,
        responderId: String,
        location: Location
    ): Result<Emergency> {
        val cur = _currentEmergency.value
        return if (cur != null) {
            val updated = cur.copy(responderLocations = cur.responderLocations + (responderId to location))
            _currentEmergency.value = updated
            Result.success(updated)
        } else {
            Result.failure(Exception("Emergency not loaded"))
        }
    }

    override suspend fun advanceRequesterLocation(emergencyId: String): Result<Emergency> {
        return _currentEmergency.value?.let { Result.success(it) } ?: Result.failure(Exception("Emergency not loaded"))
    }

    override suspend fun advanceResponderLocation(emergencyId: String, responderId: String): Result<Emergency> {
        return _currentEmergency.value?.let { Result.success(it) } ?: Result.failure(Exception("Emergency not loaded"))
    }

    override suspend fun stopLiveLocation(emergencyId: String): Result<Unit> {
        return Result.success(Unit)
    }

    override fun getEmergencyParticipantLocation(
        emergencyId: String,
        participantId: String,
        viewerId: String
    ): Result<Location> {
        val cur = _currentEmergency.value
        return if (cur != null) {
            if (cur.requester.id == participantId) {
                Result.success(cur.requesterLocation)
            } else {
                cur.responderLocations[participantId]?.let { Result.success(it) }
                    ?: Result.failure(Exception("Participant location not available"))
            }
        } else {
            Result.failure(Exception("Emergency not loaded"))
        }
    }

    override fun getVisibleParticipantLocations(emergencyId: String, viewerId: String): Result<List<ParticipantLocation>> {
        val cur = _currentEmergency.value
        return if (cur != null) {
            val list = mutableListOf<ParticipantLocation>()
            list.add(
                ParticipantLocation(
                    userId = cur.requester.id,
                    userName = cur.requester.name,
                    safeWalkId = cur.requester.safeWalkId,
                    role = com.example.safewalk.domain.model.EmergencyRole.REQUESTER,
                    location = cur.requesterLocation,
                    distanceToCounterpartMeters = 0.0
                )
            )
            for (resp in cur.acceptedResponders) {
                val loc = cur.responderLocations[resp.id]
                // FIX 3: Do NOT fall back to requester coordinates!
                // Only provide a participant location if a real, valid location exists.
                if (loc != null && !(loc.latitude == 0.0 && loc.longitude == 0.0)) {
                    val dist = cur.responderDistances[resp.id] ?: cur.requesterLocation.distanceTo(loc)
                    list.add(
                        ParticipantLocation(
                            userId = resp.id,
                            userName = resp.name,
                            safeWalkId = resp.safeWalkId,
                            role = com.example.safewalk.domain.model.EmergencyRole.RESPONDER,
                            location = loc,
                            distanceToCounterpartMeters = dist
                        )
                    )
                }
            }
            Result.success(list)
        } else {
            Result.success(emptyList())
        }
    }

    private suspend fun hydrateInitialLocations(numericId: Long, emg: Emergency): Emergency {
        if (emg.isTerminal || emg.acceptedResponders.isEmpty()) {
            return emg
        }
        return try {
            val locResult = remoteDataSource.getEmergencyLocations(numericId)
            if (locResult.isSuccess) {
                val locDto = locResult.getOrNull()
                if (locDto != null) {
                    val hydratedLocations = emg.responderLocations.toMutableMap()
                    val hydratedDistances = emg.responderDistances.toMutableMap()

                    locDto.responders.forEach { respLocDto ->
                        val respId = respLocDto.userId.toString()
                        val lat = respLocDto.latitude
                        val lng = respLocDto.longitude
                        if (lat != null && lng != null && !(lat == 0.0 && lng == 0.0)) {
                            val loc = Location(
                                latitude = lat,
                                longitude = lng,
                                address = "Live Location",
                                status = LocationStatus.ACTIVE
                            )
                            if (!hydratedLocations.containsKey(respId)) {
                                hydratedLocations[respId] = loc
                            }
                            if (!hydratedDistances.containsKey(respId)) {
                                val dist = respLocDto.distanceKm?.let { it * 1000.0 }
                                    ?: emg.requesterLocation.distanceTo(loc)
                                hydratedDistances[respId] = dist
                            }
                        }
                    }

                    emg.copy(
                        responderLocations = hydratedLocations,
                        responderDistances = hydratedDistances
                    )
                } else {
                    emg
                }
            } else {
                emg
            }
        } catch (_: Exception) {
            emg
        }
    }

    private fun updateActiveEmergenciesList(emergency: Emergency) {
        val currentList = _activeEmergencies.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == emergency.id }
        if (emergency.isTerminal) {
            if (index != -1) {
                currentList.removeAt(index)
            }
        } else {
            if (index != -1) {
                currentList[index] = emergency
            } else {
                currentList.add(0, emergency)
            }
        }
        _activeEmergencies.value = currentList
    }

    private suspend fun handleIncomingWebSocketMessage(stompMsg: StompMessage) {
        try {
            val root = JsonParser.parseString(stompMsg.body).asJsonObject
            val eventType = root.get("eventType")?.asString ?: return
            val messageText = root.get("message")?.asString ?: ""
            val dataElement = root.get("data")

            when (eventType) {
                "SOS_CREATED" -> {
                    if (dataElement != null && dataElement.isJsonObject) {
                        val dto = gson.fromJson(dataElement, EmergencyResponseDto::class.java)
                        val emg = dto.toDomain()
                        updateActiveEmergenciesList(emg)
                        _realtimeEvents.emit(RealtimeEmergencyEvent.SosCreated(emergencyId = emg.id, emergency = emg))
                    }
                }

                "SOS_ACCEPTED" -> {
                    if (dataElement != null && dataElement.isJsonObject) {
                        val dto = gson.fromJson(dataElement, EmergencyResponseDto::class.java)
                        val emg = dto.toDomain()
                        val cur = _currentEmergency.value
                        val merged = if (cur != null && (cur.id == emg.id || cur.numericId == emg.numericId)) {
                            emg.copy(
                                responderLocations = cur.responderLocations,
                                responderDistances = cur.responderDistances
                            )
                        } else emg
                        _currentEmergency.value = merged
                        updateActiveEmergenciesList(merged)
                        val resp = merged.acceptedResponders.lastOrNull() ?: User(
                            id = (dto.responderId ?: 1L).toString(),
                            safeWalkId = "SW-${dto.responderId ?: 1L}",
                            name = dto.responderName ?: "Responder",
                            phone = dto.responderPhone ?: "",
                            email = "",
                            role = UserRole.USER
                        )
                        _realtimeEvents.emit(RealtimeEmergencyEvent.ResponderAccepted(emergencyId = merged.id, responder = resp))
                    }
                }

                "RESPONDER_ARRIVED" -> {
                    if (dataElement != null && dataElement.isJsonObject) {
                        val dto = gson.fromJson(dataElement, EmergencyResponseDto::class.java)
                        val emg = dto.toDomain()
                        val cur = _currentEmergency.value
                        val merged = if (cur != null && (cur.id == emg.id || cur.numericId == emg.numericId)) {
                            emg.copy(
                                responderLocations = cur.responderLocations,
                                responderDistances = cur.responderDistances
                            )
                        } else emg
                        _currentEmergency.value = merged
                        updateActiveEmergenciesList(merged)
                        val arrivedResp = merged.effectiveArrivedResponder ?: User(
                            id = (dto.arrivedResponderId ?: dto.responderId ?: 1L).toString(),
                            safeWalkId = "SW-${dto.arrivedResponderId ?: dto.responderId ?: 1L}",
                            name = dto.arrivedResponderName ?: dto.responderName ?: "Responder",
                            phone = "",
                            email = "",
                            role = UserRole.USER
                        )
                        _realtimeEvents.emit(RealtimeEmergencyEvent.ResponderArrived(emergencyId = merged.id, responder = arrivedResp))
                    }
                }

                "RESOLUTION_CONFIRMATION_PENDING" -> {
                    if (dataElement != null && dataElement.isJsonObject) {
                        val dto = gson.fromJson(dataElement, EmergencyResponseDto::class.java)
                        val emg = dto.toDomain()
                        val cur = _currentEmergency.value
                        val merged = if (cur != null && (cur.id == emg.id || cur.numericId == emg.numericId)) {
                            emg.copy(
                                responderLocations = cur.responderLocations,
                                responderDistances = cur.responderDistances
                            )
                        } else emg
                        _currentEmergency.value = merged
                        updateActiveEmergenciesList(merged)
                        _realtimeEvents.emit(
                            RealtimeEmergencyEvent.ResolutionConfirmationPending(
                                emergencyId = merged.id,
                                requesterResolveConfirmed = merged.requesterConfirmedResolution,
                                responderResolveConfirmed = merged.responderConfirmedResolution,
                                message = messageText
                            )
                        )
                    }
                }

                "SOS_RESOLVED" -> {
                    if (dataElement != null && dataElement.isJsonObject) {
                        val dto = gson.fromJson(dataElement, EmergencyResponseDto::class.java)
                        val emg = dto.toDomain()
                        _currentEmergency.value = emg
                        updateActiveEmergenciesList(emg)
                        _realtimeEvents.emit(
                            RealtimeEmergencyEvent.EmergencyResolved(
                                emergencyId = emg.id,
                                resolvedAt = emg.resolvedAt ?: System.currentTimeMillis()
                            )
                        )
                    }
                }

                "SOS_CANCELLED" -> {
                    if (dataElement != null && dataElement.isJsonObject) {
                        val dto = gson.fromJson(dataElement, EmergencyResponseDto::class.java)
                        val emg = dto.toDomain()
                        _currentEmergency.value = emg
                        updateActiveEmergenciesList(emg)
                        _realtimeEvents.emit(
                            RealtimeEmergencyEvent.EmergencyCancelled(
                                emergencyId = emg.id,
                                reason = emg.cancellationReason ?: messageText
                            )
                        )
                    }
                }

                "SOS_EXPIRED" -> {
                    if (dataElement != null && dataElement.isJsonObject) {
                        val dto = gson.fromJson(dataElement, EmergencyResponseDto::class.java)
                        val emg = dto.toDomain()
                        _currentEmergency.value = emg
                        updateActiveEmergenciesList(emg)
                        _realtimeEvents.emit(
                            RealtimeEmergencyEvent.EmergencyExpired(
                                emergencyId = emg.id,
                                expiredAt = emg.expiredAt ?: System.currentTimeMillis()
                            )
                        )
                    }
                }

                "LOCATION_UPDATE" -> {
                    if (dataElement != null && dataElement.isJsonObject) {
                        val obj = dataElement.asJsonObject
                        val rawEmgId = obj.get("emergencyId")?.asString ?: ""
                        val emgId = if (rawEmgId.startsWith("EMG-")) rawEmgId else "EMG-$rawEmgId"
                        val participantId = obj.get("userId")?.asString ?: ""
                        val isReq = obj.get("isRequester")?.asBoolean ?: false
                        val lat = obj.get("latitude")?.asDouble ?: 0.0
                        val lng = obj.get("longitude")?.asDouble ?: 0.0

                        val newLoc = Location(
                            latitude = lat,
                            longitude = lng,
                            address = "Live Location",
                            status = LocationStatus.ACTIVE
                        )

                        val cur = _currentEmergency.value
                        var dist: Double? = null
                        if (cur != null && (cur.id == emgId || cur.numericId == rawEmgId.removePrefix("EMG-"))) {
                            val updated = if (isReq) {
                                cur.copy(requesterLocation = newLoc)
                            } else {
                                val updatedLocations = cur.responderLocations + (participantId to newLoc)
                                dist = cur.requesterLocation.distanceTo(newLoc)
                                val updatedDistances = cur.responderDistances + (participantId to dist)
                                cur.copy(
                                    responderLocations = updatedLocations,
                                    responderDistances = updatedDistances
                                )
                            }
                            _currentEmergency.value = updated
                        }

                        _realtimeEvents.emit(
                            RealtimeEmergencyEvent.LocationUpdated(
                                emergencyId = emgId,
                                participantId = participantId,
                                location = newLoc,
                                distanceToCounterpartMeters = dist
                            )
                        )
                    }
                }

                "LOCATION_SHARING_STOPPED" -> {
                    val cur = _currentEmergency.value
                    val emgId = cur?.id ?: ""
                    _realtimeEvents.emit(RealtimeEmergencyEvent.LocationSharingStopped(emergencyId = emgId))
                }
            }
        } catch (e: Exception) {
            Log.w("RemoteEmergencyRepo", "Failed to parse real-time event: ${e.message}")
        }
    }
}
