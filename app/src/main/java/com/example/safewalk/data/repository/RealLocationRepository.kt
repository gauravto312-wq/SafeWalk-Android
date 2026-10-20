package com.example.safewalk.data.repository

import com.example.safewalk.core.location.LocationProvider
import com.example.safewalk.core.location.LocationResult
import com.example.safewalk.data.model.dto.LocationUpdateRequestDto
import com.example.safewalk.data.remote.LocationApiService
import com.example.safewalk.domain.model.Emergency
import com.example.safewalk.domain.model.Location
import com.example.safewalk.domain.model.ParticipantLocation
import com.example.safewalk.domain.repository.LocationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import java.io.IOException

/**
 * Real production implementation of LocationRepository connecting:
 * Android GPS / FusedLocationProvider -> LocationProvider -> RealLocationRepository -> LocationApiService (PUT /api/v1/location) -> Spring Boot -> Redis GEO.
 */
class RealLocationRepository(
    private val locationProvider: LocationProvider,
    private val apiServiceProvider: () -> LocationApiService,
    private val mockFallback: LocationRepositoryImpl? = null
) : LocationRepository {

    constructor(
        locationProvider: LocationProvider,
        locationApiService: LocationApiService,
        mockFallback: LocationRepositoryImpl? = null
    ) : this(locationProvider, { locationApiService }, mockFallback)

    private val locationApiService: LocationApiService
        get() = apiServiceProvider()

    private val _lastKnownLocation = MutableStateFlow<Location?>(null)
    val lastKnownLocation = _lastKnownLocation.asStateFlow()

    fun clearLocationState() {
        _lastKnownLocation.value = null
    }

    override fun hasLocationPermission(): Boolean {
        return locationProvider.hasLocationPermission()
    }

    override fun isLocationEnabled(): Boolean {
        return locationProvider.isLocationEnabled()
    }

    override suspend fun acquireCurrentLocation(): Result<Location> {
        return when (val result = locationProvider.getCurrentLocation()) {
            is LocationResult.Success -> {
                _lastKnownLocation.value = result.location
                Result.success(result.location)
            }
            is LocationResult.Failure -> {
                Result.failure(IllegalStateException(result.message))
            }
        }
    }

    override suspend fun sendLocationToBackend(location: Location): Result<Unit> {
        return try {
            val requestDto = LocationUpdateRequestDto(
                latitude = location.latitude,
                longitude = location.longitude,
                accuracy = null,
                speed = null,
                heading = null,
                timestamp = location.timestamp
            )
            val response = locationApiService.updateLocation(requestDto)
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(Unit)
            } else {
                val errorMsg = response.body()?.message ?: "Backend failed to update location (HTTP ${response.code()})"
                Result.failure(IOException(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun refreshAndTransmitLocation(): Result<Location> {
        val locResult = acquireCurrentLocation()
        if (locResult.isFailure) {
            return locResult
        }
        val location = locResult.getOrThrow()
        val transmitResult = sendLocationToBackend(location)
        return if (transmitResult.isSuccess) {
            Result.success(location)
        } else {
            Result.failure(transmitResult.exceptionOrNull() ?: IOException("Failed to transmit location to backend"))
        }
    }

    override fun getLocationUpdates(
        intervalMs: Long,
        minDistanceMeters: Float
    ): Flow<LocationResult> {
        return locationProvider.getLocationUpdates(intervalMs, minDistanceMeters)
    }

    override fun getUserLocation(userId: String): Flow<Location> {
        return mockFallback?.getUserLocation(userId) ?: lastKnownLocation.filterNotNull()
    }

    override suspend fun updateLocation(userId: String, location: Location) {
        _lastKnownLocation.value = location
        sendLocationToBackend(location)
        mockFallback?.updateLocation(userId, location)
    }

    override fun getDefaultMockLocation(): Location {
        return _lastKnownLocation.value ?: (mockFallback?.getDefaultMockLocation() ?: Location(28.6139, 77.2090, "New Delhi, Delhi, India"))
    }

    override fun getCurrentLocation(userId: String): Location {
        return _lastKnownLocation.value ?: (mockFallback?.getCurrentLocation(userId) ?: getDefaultMockLocation())
    }

    override fun getEmergencyParticipantLocation(
        emergencyId: String,
        participantId: String,
        viewerId: String
    ): Result<Location> {
        return mockFallback?.getEmergencyParticipantLocation(emergencyId, participantId, viewerId)
            ?: Result.failure(IllegalStateException("Participant tracking not available"))
    }

    override fun getVisibleParticipantLocations(
        emergencyId: String,
        viewerId: String
    ): Result<List<ParticipantLocation>> {
        return mockFallback?.getVisibleParticipantLocations(emergencyId, viewerId)
            ?: Result.failure(IllegalStateException("Participant tracking not available"))
    }

    override suspend fun updateMockLocation(userId: String, location: Location) {
        mockFallback?.updateMockLocation(userId, location)
    }

    override suspend fun advanceMockLocation(emergencyId: String, userId: String): Result<Location> {
        return mockFallback?.advanceMockLocation(emergencyId, userId)
            ?: Result.failure(IllegalStateException("Advance mock location not available"))
    }

    override suspend fun stopLiveLocation(emergencyId: String): Result<Unit> {
        return mockFallback?.stopLiveLocation(emergencyId) ?: Result.success(Unit)
    }

    override suspend fun updateRequesterLocation(emergencyId: String, location: Location): Result<Emergency> {
        return mockFallback?.updateRequesterLocation(emergencyId, location)
            ?: Result.failure(IllegalStateException("Fallback data source not attached"))
    }

    override suspend fun updateResponderLocation(
        emergencyId: String,
        responderId: String,
        location: Location
    ): Result<Emergency> {
        return mockFallback?.updateResponderLocation(emergencyId, responderId, location)
            ?: Result.failure(IllegalStateException("Fallback data source not attached"))
    }
}
