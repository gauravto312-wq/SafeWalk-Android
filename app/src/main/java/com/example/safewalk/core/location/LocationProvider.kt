package com.example.safewalk.core.location

import com.example.safewalk.domain.model.Location

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Result abstraction for location acquisition.
 */
sealed class LocationResult {
    data class Success(val location: Location) : LocationResult()

    sealed class Failure(val message: String) : LocationResult() {
        object PermissionDenied : Failure("Location permission is denied. Please grant location access.")
        object LocationDisabled : Failure("Location services / GPS is disabled. Please turn on device location.")
        data class Error(val cause: Throwable, val detailMessage: String = cause.localizedMessage ?: "Unknown location error") : Failure(detailMessage)
    }
}

/**
 * Abstraction for acquiring device location.
 */
interface LocationProvider {
    fun hasLocationPermission(): Boolean
    fun isLocationEnabled(): Boolean
    suspend fun getCurrentLocation(): LocationResult
    fun getLocationUpdates(intervalMs: Long = 5000L, minDistanceMeters: Float = 5f): Flow<LocationResult> = emptyFlow()
}
