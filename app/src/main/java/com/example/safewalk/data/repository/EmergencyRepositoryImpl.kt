package com.example.safewalk.data.repository

import com.example.safewalk.data.source.mock.MockEmergencyDataSource
import com.example.safewalk.data.source.mock.MockLocationDataSource
import com.example.safewalk.domain.model.RealtimeEmergencyEvent
import com.example.safewalk.domain.model.Emergency
import com.example.safewalk.domain.model.Location
import com.example.safewalk.domain.model.ParticipantLocation
import com.example.safewalk.domain.model.User
import com.example.safewalk.domain.repository.EmergencyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Implementation of EmergencyRepository delegating to MockEmergencyDataSource
 * with optional synchronization to MockLocationDataSource.
 */
class EmergencyRepositoryImpl(
    private val dataSource: MockEmergencyDataSource,
    private val locationDataSource: MockLocationDataSource? = null
) : EmergencyRepository {

    override val activeEmergencies: StateFlow<List<Emergency>> = dataSource.activeEmergencies

    override val currentEmergency: StateFlow<Emergency?> = dataSource.currentEmergency

    override val realtimeEvents: Flow<RealtimeEmergencyEvent> = dataSource.realtimeEvents

    override fun getEmergencyById(id: String): Flow<Emergency?> {
        return dataSource.getEmergencyById(id)
    }

    override fun getEmergencyHistory(userId: String): Flow<List<Emergency>> {
        return dataSource.getEmergencyHistory(userId)
    }

    override fun isUserEligibleForEmergency(user: User, emergency: Emergency, userLocation: Location): Boolean {
        return dataSource.isUserEligibleForEmergency(user, emergency, userLocation)
    }

    override fun getEligibleEmergenciesForUser(user: User, userLocation: Location): List<Emergency> {
        return dataSource.getEligibleEmergenciesForUser(user, userLocation)
    }

    override fun canViewDetailedLocation(emergency: Emergency, user: User?): Boolean {
        return dataSource.canViewDetailedLocation(emergency, user)
    }

    override suspend fun refreshActiveEmergencies(): Result<List<Emergency>> {
        return Result.success(activeEmergencies.value)
    }

    override suspend fun createEmergency(requester: User, location: Location): Emergency {
        val emg = dataSource.createEmergency(requester, location)
        locationDataSource?.updateLocation(requester.id, emg.requesterLocation)
        return emg
    }

    override suspend fun acceptEmergency(emergencyId: String, responder: User): Result<Emergency> {
        val result = dataSource.acceptEmergency(emergencyId, responder)
        result.onSuccess { emg ->
            emg.responderLocations[responder.id]?.let { loc ->
                locationDataSource?.updateLocation(responder.id, loc)
            }
        }
        return result
    }

    override suspend fun rejectEmergency(emergencyId: String, responder: User): Result<Emergency> {
        return dataSource.rejectEmergency(emergencyId, responder)
    }

    override suspend fun markArrived(emergencyId: String, responder: User, distanceMeters: Double): Result<Emergency> {
        return dataSource.markArrived(emergencyId, responder, distanceMeters)
    }

    override suspend fun updateResponderDistance(
        emergencyId: String,
        responderId: String,
        distanceMeters: Double
    ): Result<Emergency> {
        return dataSource.updateResponderDistance(emergencyId, responderId, distanceMeters)
    }

    override suspend fun confirmResolution(emergencyId: String, user: User): Result<Emergency> {
        return dataSource.confirmResolution(emergencyId, user)
    }

    override suspend fun resolveEmergency(emergencyId: String, user: User): Result<Emergency> {
        return dataSource.resolveEmergency(emergencyId, user)
    }

    override suspend fun cancelEmergency(emergencyId: String, reason: String): Result<Emergency> {
        return dataSource.cancelEmergency(emergencyId = emergencyId, reason = reason)
    }

    override suspend fun cancelEmergency(emergencyId: String, emergencyPassword: String?, reason: String): Result<Emergency> {
        return dataSource.cancelEmergency(emergencyId = emergencyId, emergencyPassword = emergencyPassword, reason = reason)
    }

    override suspend fun selectEmergency(emergencyId: String) {
        dataSource.selectEmergency(emergencyId)
    }

    override suspend fun updateRequesterLocation(emergencyId: String, location: Location): Result<Emergency> {
        val result = dataSource.updateRequesterLocation(emergencyId, location)
        result.onSuccess { emg ->
            locationDataSource?.updateLocation(emg.requester.id, emg.requesterLocation)
        }
        return result
    }

    override suspend fun updateResponderLocation(
        emergencyId: String,
        responderId: String,
        location: Location
    ): Result<Emergency> {
        val result = dataSource.updateResponderLocation(emergencyId, responderId, location)
        result.onSuccess { emg ->
            emg.responderLocations[responderId]?.let { loc ->
                locationDataSource?.updateLocation(responderId, loc)
            }
        }
        return result
    }

    override suspend fun advanceRequesterLocation(emergencyId: String): Result<Emergency> {
        val result = dataSource.advanceRequesterLocation(emergencyId)
        result.onSuccess { emg ->
            locationDataSource?.updateLocation(emg.requester.id, emg.requesterLocation)
        }
        return result
    }

    override suspend fun advanceResponderLocation(emergencyId: String, responderId: String): Result<Emergency> {
        val result = dataSource.advanceResponderLocation(emergencyId, responderId)
        result.onSuccess { emg ->
            emg.responderLocations[responderId]?.let { loc ->
                locationDataSource?.updateLocation(responderId, loc)
            }
        }
        return result
    }

    override suspend fun stopLiveLocation(emergencyId: String): Result<Unit> {
        return dataSource.stopLiveLocation(emergencyId)
    }

    override fun getEmergencyParticipantLocation(
        emergencyId: String,
        participantId: String,
        viewerId: String
    ): Result<Location> {
        return dataSource.getEmergencyParticipantLocation(emergencyId, participantId, viewerId)
    }

    override fun getVisibleParticipantLocations(emergencyId: String, viewerId: String): Result<List<ParticipantLocation>> {
        return dataSource.getVisibleParticipantLocations(emergencyId, viewerId)
    }
}
