package com.example.safewalk.data.repository

import com.example.safewalk.data.source.mock.MockEmergencyDataSource
import com.example.safewalk.data.source.mock.MockLocationDataSource
import com.example.safewalk.domain.model.Emergency
import com.example.safewalk.domain.model.Location
import com.example.safewalk.domain.model.ParticipantLocation
import com.example.safewalk.domain.repository.LocationRepository
import kotlinx.coroutines.flow.Flow

/**
 * Implementation of LocationRepository delegating to MockLocationDataSource and MockEmergencyDataSource.
 */
class LocationRepositoryImpl(
    private val dataSource: MockLocationDataSource,
    private val emergencyDataSource: MockEmergencyDataSource? = null
) : LocationRepository {

    override fun getUserLocation(userId: String): Flow<Location> {
        return dataSource.getLocationForUser(userId)
    }

    override suspend fun updateLocation(userId: String, location: Location) {
        dataSource.updateLocation(userId, location)
    }

    override fun getDefaultMockLocation(): Location {
        return dataSource.getDefaultLocation()
    }

    override fun hasLocationPermission(): Boolean = true
    override fun isLocationEnabled(): Boolean = true
    override suspend fun acquireCurrentLocation(): Result<Location> = Result.success(getDefaultMockLocation())
    override suspend fun sendLocationToBackend(location: Location): Result<Unit> = Result.success(Unit)
    override suspend fun refreshAndTransmitLocation(): Result<Location> = Result.success(getDefaultMockLocation())

    override fun getCurrentLocation(userId: String): Location {
        return dataSource.getCurrentLocation(userId)
    }

    override fun getEmergencyParticipantLocation(
        emergencyId: String,
        participantId: String,
        viewerId: String
    ): Result<Location> {
        return emergencyDataSource?.getEmergencyParticipantLocation(emergencyId, participantId, viewerId)
            ?: Result.failure(IllegalStateException("Emergency data source not attached"))
    }

    override fun getVisibleParticipantLocations(
        emergencyId: String,
        viewerId: String
    ): Result<List<ParticipantLocation>> {
        return emergencyDataSource?.getVisibleParticipantLocations(emergencyId, viewerId)
            ?: Result.failure(IllegalStateException("Emergency data source not attached"))
    }

    override suspend fun updateMockLocation(userId: String, location: Location) {
        dataSource.updateLocation(userId, location)
    }

    override suspend fun advanceMockLocation(emergencyId: String, userId: String): Result<Location> {
        val emgSource = emergencyDataSource ?: return Result.failure(IllegalStateException("Emergency data source not attached"))
        val currentEmg = emgSource.currentEmergency.value ?: emgSource.activeEmergencies.value.find { it.id == emergencyId }
            ?: return Result.failure(IllegalArgumentException("Emergency not found"))

        return if (currentEmg.isUserRequester(userId)) {
            val res = emgSource.advanceRequesterLocation(emergencyId)
            res.map {
                dataSource.updateLocation(userId, it.requesterLocation)
                it.requesterLocation
            }
        } else {
            val res = emgSource.advanceResponderLocation(emergencyId, userId)
            res.map {
                val loc = it.responderLocations[userId] ?: it.requesterLocation
                dataSource.updateLocation(userId, loc)
                loc
            }
        }
    }

    override suspend fun stopLiveLocation(emergencyId: String): Result<Unit> {
        return emergencyDataSource?.stopLiveLocation(emergencyId) ?: Result.success(Unit)
    }

    override suspend fun updateRequesterLocation(emergencyId: String, location: Location): Result<Emergency> {
        val res = emergencyDataSource?.updateRequesterLocation(emergencyId, location)
            ?: Result.failure(IllegalStateException("Emergency data source not attached"))
        res.onSuccess {
            dataSource.updateLocation(it.requester.id, it.requesterLocation)
        }
        return res
    }

    override suspend fun updateResponderLocation(
        emergencyId: String,
        responderId: String,
        location: Location
    ): Result<Emergency> {
        val res = emergencyDataSource?.updateResponderLocation(emergencyId, responderId, location)
            ?: Result.failure(IllegalStateException("Emergency data source not attached"))
        res.onSuccess {
            it.responderLocations[responderId]?.let { loc ->
                dataSource.updateLocation(responderId, loc)
            }
        }
        return res
    }
}
