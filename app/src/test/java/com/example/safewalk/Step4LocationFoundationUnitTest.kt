package com.example.safewalk

import com.example.safewalk.core.location.LocationProvider
import com.example.safewalk.core.location.LocationResult
import com.example.safewalk.data.model.dto.ApiResponseDto
import com.example.safewalk.data.model.dto.LocationUpdateRequestDto
import com.example.safewalk.data.remote.LocationApiService
import com.example.safewalk.data.repository.RealLocationRepository
import com.example.safewalk.domain.model.Location
import com.example.safewalk.domain.model.LocationStatus
import com.example.safewalk.viewmodel.LocationViewModel
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class Step4LocationFoundationUnitTest {

    class FakeLocationProvider(
        var permissionGranted: Boolean = true,
        var gpsEnabled: Boolean = true,
        var mockLocationToReturn: Location? = Location(28.6139, 77.2090, "New Delhi, Delhi, India")
    ) : LocationProvider {
        override fun hasLocationPermission(): Boolean = permissionGranted
        override fun isLocationEnabled(): Boolean = gpsEnabled
        override suspend fun getCurrentLocation(): LocationResult {
            if (!permissionGranted) return LocationResult.Failure.PermissionDenied
            if (!gpsEnabled) return LocationResult.Failure.LocationDisabled
            return if (mockLocationToReturn != null) {
                LocationResult.Success(mockLocationToReturn!!)
            } else {
                LocationResult.Failure.Error(IllegalStateException("No GPS signal"))
            }
        }
    }

    class FakeLocationApiService(
        var shouldSucceed: Boolean = true
    ) : LocationApiService {
        var lastUpdateRequest: LocationUpdateRequestDto? = null

        override suspend fun updateLocation(request: LocationUpdateRequestDto): Response<ApiResponseDto<String>> {
            lastUpdateRequest = request
            return if (shouldSucceed) {
                Response.success(ApiResponseDto(success = true, message = "Location updated successfully", data = null))
            } else {
                Response.error(500, "Internal Server Error".toResponseBody())
            }
        }

        override suspend fun getCurrentLocation() = TODO()
        override suspend fun removeLocation() = TODO()
        override suspend fun getNearbyResponders(latitude: Double, longitude: Double, radius: Double?) = TODO()
    }

    @Test
    fun test_location_permission_denied_returns_clean_failure() = runBlocking {
        val provider = FakeLocationProvider(permissionGranted = false, gpsEnabled = true)
        val result = provider.getCurrentLocation()

        assertTrue(result is LocationResult.Failure.PermissionDenied)
        val failure = result as LocationResult.Failure.PermissionDenied
        assertTrue(failure.message.contains("Location permission is denied"))
    }

    @Test
    fun test_gps_disabled_returns_clean_failure() = runBlocking {
        val provider = FakeLocationProvider(permissionGranted = true, gpsEnabled = false)
        val result = provider.getCurrentLocation()

        assertTrue(result is LocationResult.Failure.LocationDisabled)
        val failure = result as LocationResult.Failure.LocationDisabled
        assertTrue(failure.message.contains("Location services / GPS is disabled"))
    }

    @Test
    fun test_location_acquisition_success_when_permission_and_gps_enabled() = runBlocking {
        val expectedLoc = Location(28.6139, 77.2090, "Connaught Place, New Delhi")
        val provider = FakeLocationProvider(permissionGranted = true, gpsEnabled = true, mockLocationToReturn = expectedLoc)

        val result = provider.getCurrentLocation()
        assertTrue(result is LocationResult.Success)
        val success = result as LocationResult.Success
        assertEquals(28.6139, success.location.latitude, 0.0001)
        assertEquals(77.2090, success.location.longitude, 0.0001)
        assertEquals("Connaught Place, New Delhi", success.location.address)
    }

    @Test
    fun test_real_location_repository_transmits_coordinates_and_timestamp_to_backend() = runBlocking {
        val provider = FakeLocationProvider()
        val apiService = FakeLocationApiService(shouldSucceed = true)
        val repository = RealLocationRepository(provider, apiService)

        val now = System.currentTimeMillis()
        val testLocation = Location(12.9716, 77.5946, "Bengaluru, Karnataka", timestamp = now)

        val result = repository.sendLocationToBackend(testLocation)
        assertTrue(result.isSuccess)

        assertNotNull(apiService.lastUpdateRequest)
        assertEquals(12.9716, apiService.lastUpdateRequest!!.latitude, 0.0001)
        assertEquals(77.5946, apiService.lastUpdateRequest!!.longitude, 0.0001)
        assertEquals(now, apiService.lastUpdateRequest!!.timestamp)
    }

    @Test
    fun test_real_location_repository_handles_api_failure_gracefully() = runBlocking {
        val provider = FakeLocationProvider()
        val apiService = FakeLocationApiService(shouldSucceed = false)
        val repository = RealLocationRepository(provider, apiService)

        val testLocation = Location(12.9716, 77.5946, "Bengaluru, Karnataka")
        val result = repository.sendLocationToBackend(testLocation)

        assertFalse(result.isSuccess)
        assertNotNull(result.exceptionOrNull())
    }

    @Test
    fun test_location_view_model_sync_flow() = runBlocking {
        val provider = FakeLocationProvider()
        val apiService = FakeLocationApiService(shouldSucceed = true)
        val repository = RealLocationRepository(provider, apiService)
        val viewModel = LocationViewModel(repository, externalScope = this)

        assertTrue(viewModel.uiState.value.hasPermission)
        assertTrue(viewModel.uiState.value.isGpsEnabled)

        val job = viewModel.refreshAndSyncLocation()
        job.join()

        val state = viewModel.uiState.value
        assertNotNull(state.currentLocation)
        assertEquals(28.6139, state.currentLocation!!.latitude, 0.0001)
        assertTrue(state.isSyncedWithCloud)
        assertNotNull(state.lastSyncedTimestamp)
    }

    @Test
    fun test_location_freshness_and_staleness_identification() {
        val now = System.currentTimeMillis()
        val freshLocation = Location(28.6139, 77.2090, "New Delhi", timestamp = now - 5_000L) // 5s ago
        val staleLocation = Location(28.6139, 77.2090, "New Delhi", timestamp = now - 60_000L) // 60s ago (> 30s threshold)

        assertFalse(freshLocation.isStale(now, thresholdMillis = 30_000L))
        assertEquals(LocationStatus.ACTIVE, freshLocation.effectiveStatus(now, thresholdMillis = 30_000L))

        assertTrue(staleLocation.isStale(now, thresholdMillis = 30_000L))
        assertEquals(LocationStatus.STALE, staleLocation.effectiveStatus(now, thresholdMillis = 30_000L))
    }

    @Test
    fun test_location_provider_off_state_shows_clear_message() {
        val provider = FakeLocationProvider(permissionGranted = true, gpsEnabled = false)
        val apiService = FakeLocationApiService(shouldSucceed = true)
        val repository = RealLocationRepository(provider, apiService)
        val viewModel = LocationViewModel(repository)

        assertFalse(viewModel.uiState.value.isGpsEnabled)
        assertEquals("Device location is off. Please turn on location.", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun test_location_provider_on_recovery_and_auto_acquisition_and_sync() = runBlocking<Unit> {
        val provider = FakeLocationProvider(permissionGranted = true, gpsEnabled = false)
        val apiService = FakeLocationApiService(shouldSucceed = true)
        val repository = RealLocationRepository(provider, apiService)
        val viewModel = LocationViewModel(repository, externalScope = this)

        assertFalse(viewModel.uiState.value.isGpsEnabled)
        assertEquals("Device location is off. Please turn on location.", viewModel.uiState.value.errorMessage)

        // User turns location ON
        provider.gpsEnabled = true
        val job = viewModel.onLocationProviderAvailabilityChanged()
        job?.join()

        val state = viewModel.uiState.value
        assertTrue(state.isGpsEnabled)
        assertNotNull(state.currentLocation)
        assertEquals(28.6139, state.currentLocation!!.latitude, 0.0001)
        assertEquals(77.2090, state.currentLocation!!.longitude, 0.0001)
        assertTrue(state.isSyncedWithCloud)
        assertNotNull(state.lastSyncedTimestamp)
        assertNull(state.errorMessage)
    }
}
