package com.example.safewalk.domain.repository

import com.example.safewalk.domain.model.Emergency
import com.example.safewalk.domain.model.Location
import com.example.safewalk.domain.model.ParticipantLocation
import com.example.safewalk.domain.model.User
import com.example.safewalk.domain.model.RealtimeEmergencyEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Domain repository contract for managing Emergency states, discovery, dispatch,
 * lifecycles, and Phase 7 bidirectional live location & privacy.
 */
interface EmergencyRepository {
    val activeEmergencies: StateFlow<List<Emergency>>
    val currentEmergency: StateFlow<Emergency?>
    val realtimeEvents: Flow<RealtimeEmergencyEvent>

    fun getEmergencyById(id: String): Flow<Emergency?>
    fun getEmergencyHistory(userId: String): Flow<List<Emergency>>
    fun isUserEligibleForEmergency(user: User, emergency: Emergency, userLocation: Location): Boolean
    fun getEligibleEmergenciesForUser(user: User, userLocation: Location): List<Emergency>
    fun canViewDetailedLocation(emergency: Emergency, user: User?): Boolean
    suspend fun refreshActiveEmergencies(): Result<List<Emergency>>

    suspend fun createEmergency(requester: User, location: Location): Emergency
    suspend fun acceptEmergency(emergencyId: String, responder: User): Result<Emergency>
    suspend fun rejectEmergency(emergencyId: String, responder: User): Result<Emergency>
    suspend fun markArrived(emergencyId: String, responder: User, distanceMeters: Double = 25.0): Result<Emergency>
    suspend fun updateResponderDistance(emergencyId: String, responderId: String, distanceMeters: Double): Result<Emergency>
    suspend fun confirmResolution(emergencyId: String, user: User): Result<Emergency>
    suspend fun resolveEmergency(emergencyId: String, user: User): Result<Emergency>
    suspend fun cancelEmergency(emergencyId: String, reason: String = "User requested cancellation"): Result<Emergency>
    suspend fun cancelEmergency(emergencyId: String, emergencyPassword: String?, reason: String): Result<Emergency>
    suspend fun selectEmergency(emergencyId: String)

    // Phase 7 Bidirectional Live Location & Privacy operations
    suspend fun updateRequesterLocation(emergencyId: String, location: Location): Result<Emergency>
    suspend fun updateResponderLocation(emergencyId: String, responderId: String, location: Location): Result<Emergency>
    suspend fun advanceRequesterLocation(emergencyId: String): Result<Emergency>
    suspend fun advanceResponderLocation(emergencyId: String, responderId: String): Result<Emergency>
    suspend fun stopLiveLocation(emergencyId: String): Result<Unit>
    fun getEmergencyParticipantLocation(emergencyId: String, participantId: String, viewerId: String): Result<Location>
    fun getVisibleParticipantLocations(emergencyId: String, viewerId: String): Result<List<ParticipantLocation>>
}
