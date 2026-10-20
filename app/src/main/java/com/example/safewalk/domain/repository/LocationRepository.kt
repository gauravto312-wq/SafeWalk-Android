package com.example.safewalk.domain.repository

import com.example.safewalk.domain.model.Emergency
import com.example.safewalk.domain.model.Location
import com.example.safewalk.domain.model.ParticipantLocation
import kotlinx.coroutines.flow.Flow

/**
 * Domain repository contract for Location updates and Phase 7 bidirectional live-location telemetry.
 * Provides abstraction so MockLocationRepository can later be seamlessly replaced
 * by a RealLocationRepository (FusedLocationProvider / GPS) without modifying ViewModels or UI.
 */
interface LocationRepository {
    fun getUserLocation(userId: String): Flow<Location>
    suspend fun updateLocation(userId: String, location: Location)
    fun getDefaultMockLocation(): Location

    // Phase 1 Location Foundation operations
    fun hasLocationPermission(): Boolean = false
    fun isLocationEnabled(): Boolean = true
    suspend fun acquireCurrentLocation(): Result<Location> = Result.failure(UnsupportedOperationException("Not implemented"))
    suspend fun sendLocationToBackend(location: Location): Result<Unit> = Result.success(Unit)
    suspend fun refreshAndTransmitLocation(): Result<Location> = Result.failure(UnsupportedOperationException("Not implemented"))
    fun getLocationUpdates(intervalMs: Long = 5000L, minDistanceMeters: Float = 5f): Flow<com.example.safewalk.core.location.LocationResult> = kotlinx.coroutines.flow.emptyFlow()

    // Phase 7 operations
    fun getCurrentLocation(userId: String): Location
    fun getEmergencyParticipantLocation(emergencyId: String, participantId: String, viewerId: String): Result<Location>
    fun getVisibleParticipantLocations(emergencyId: String, viewerId: String): Result<List<ParticipantLocation>>
    suspend fun updateMockLocation(userId: String, location: Location)
    suspend fun advanceMockLocation(emergencyId: String, userId: String): Result<Location>
    suspend fun stopLiveLocation(emergencyId: String): Result<Unit>
    suspend fun updateRequesterLocation(emergencyId: String, location: Location): Result<Emergency>
    suspend fun updateResponderLocation(emergencyId: String, responderId: String, location: Location): Result<Emergency>
}
