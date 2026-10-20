package com.example.safewalk.data.source.mock

import com.example.safewalk.domain.model.Location
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Mock Data Source providing simulated GPS coordinates and reverse geocoded addresses.
 */
class MockLocationDataSource {

    private val userLocations = mutableMapOf<String, MutableStateFlow<Location>>()

    fun getLocationForUser(userId: String): Flow<Location> {
        val flow = userLocations.getOrPut(userId) {
            MutableStateFlow(MockData.defaultLocation)
        }
        return flow.asStateFlow()
    }

    fun updateLocation(userId: String, location: Location) {
        val flow = userLocations.getOrPut(userId) {
            MutableStateFlow(location)
        }
        flow.value = location
    }

    fun getCurrentLocation(userId: String): Location {
        return userLocations[userId]?.value ?: MockData.defaultLocation
    }

    fun getDefaultLocation(): Location = MockData.defaultLocation
}
