package com.example.safewalk.data

import com.example.safewalk.core.common.ServiceLocator
import com.example.safewalk.core.location.LocationProvider
import com.example.safewalk.core.location.LocationResult
import com.example.safewalk.data.model.dto.ApiResponseDto
import com.example.safewalk.data.model.dto.LocationUpdateRequestDto
import com.example.safewalk.data.remote.ApiConfig
import com.example.safewalk.data.remote.LocationApiService
import com.example.safewalk.data.remote.NetworkClient
import com.example.safewalk.data.repository.RealLocationRepository
import com.example.safewalk.domain.model.Location
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class LocationRoutingFixTest {

    private class TestLocationProvider : LocationProvider {
        override fun hasLocationPermission(): Boolean = true
        override fun isLocationEnabled(): Boolean = true
        override suspend fun getCurrentLocation(): LocationResult {
            return LocationResult.Success(Location(28.6139, 77.2090, "New Delhi"))
        }
    }

    private class CountingFakeLocationApiService(
        var id: String
    ) : LocationApiService {
        var callCount: Int = 0
        var lastUpdateRequest: LocationUpdateRequestDto? = null

        override suspend fun updateLocation(request: LocationUpdateRequestDto): Response<ApiResponseDto<String>> {
            callCount++
            lastUpdateRequest = request
            return Response.success(ApiResponseDto(success = true, message = "OK from $id", data = id))
        }

        override suspend fun getCurrentLocation() = TODO()
        override suspend fun removeLocation() = TODO()
        override suspend fun getNearbyResponders(latitude: Double, longitude: Double, radius: Double?) = TODO()
    }

    @Before
    fun setUp() {
        ApiConfig.resetToDefault()
    }

    @After
    fun tearDown() {
        ApiConfig.resetToDefault()
    }

    @Test
    fun realLocationRepository_dynamicallyResolvesLatestLocationApiService() = runBlocking {
        var currentService = CountingFakeLocationApiService(id = "service-v1")
        val repository = RealLocationRepository(
            locationProvider = TestLocationProvider(),
            apiServiceProvider = { currentService }
        )

        val testLocation = Location(28.6139, 77.2090, "New Delhi")

        // First transmit hits service-v1
        val result1 = repository.sendLocationToBackend(testLocation)
        assertTrue(result1.isSuccess)
        assertEquals(1, currentService.callCount)

        // Switch the provider to service-v2 without recreating repository
        val newService = CountingFakeLocationApiService(id = "service-v2")
        currentService = newService

        // Second transmit must dynamically hit service-v2, not stale service-v1
        val result2 = repository.sendLocationToBackend(testLocation)
        assertTrue(result2.isSuccess)
        assertEquals(1, currentService.callCount)
        assertEquals(1, newService.callCount)
    }

    @Test
    fun realLocationRepository_secondaryConstructor_preservesBackwardsCompatibility() = runBlocking {
        val staticService = CountingFakeLocationApiService(id = "static-service")
        val repository = RealLocationRepository(
            locationProvider = TestLocationProvider(),
            locationApiService = staticService
        )

        val testLocation = Location(28.6139, 77.2090, "New Delhi")
        val result = repository.sendLocationToBackend(testLocation)
        assertTrue(result.isSuccess)
        assertEquals(1, staticService.callCount)
    }

    @Test
    fun networkClient_invalidatesLocationAndNotificationCachesOnBaseUrlChange() {
        // Given initial custom base URL
        ApiConfig.baseUrl = "https://custom-test-host.example.com/"
        val initialRetrofit = NetworkClient.getRetrofit()
        val initialLocationService = NetworkClient.getLocationApiService()
        val initialNotificationService = NetworkClient.getNotificationApiService()

        assertEquals("https://custom-test-host.example.com/", initialRetrofit.baseUrl().toString())

        // When base URL switches to Railway production
        ApiConfig.baseUrl = ApiConfig.DEFAULT_BASE_URL
        val railwayRetrofit = NetworkClient.getRetrofit()
        val railwayLocationService = NetworkClient.getLocationApiService()
        val railwayNotificationService = NetworkClient.getNotificationApiService()

        // Then retrofits and services must be recreated and not retain stale instances
        assertEquals(ApiConfig.DEFAULT_BASE_URL, railwayRetrofit.baseUrl().toString())
        assertNotEquals(initialLocationService, railwayLocationService)
        assertNotEquals(initialNotificationService, railwayNotificationService)
    }

    @Test
    fun serviceLocator_dynamicGetters_returnActiveNetworkClientServices() {
        ApiConfig.baseUrl = "https://custom-test-host.example.com/"
        val service1 = ServiceLocator.locationApiService
        assertNotNull(service1)

        // Changing base URL resets NetworkClient and ServiceLocator getter resolves the new one
        ApiConfig.baseUrl = ApiConfig.DEFAULT_BASE_URL
        val service2 = ServiceLocator.locationApiService
        assertNotNull(service2)
        assertNotEquals(service1, service2)
    }

    @Test
    fun apiConfig_defaultAndCustomUrl_formatsAndResetsProperly() {
        // Default base URL is Railway production
        ApiConfig.resetToDefault()
        assertEquals(ApiConfig.DEFAULT_BASE_URL, ApiConfig.baseUrl)
        assertTrue(ApiConfig.baseUrl.endsWith("/"))
        assertEquals(ApiConfig.CLOUD_CONNECT_TIMEOUT_SECONDS, ApiConfig.connectTimeoutSeconds)

        // URL without trailing slash is normalized with trailing slash
        ApiConfig.baseUrl = "https://staging.safewalk.app"
        assertEquals("https://staging.safewalk.app/", ApiConfig.baseUrl)

        ApiConfig.resetToDefault()
        assertEquals(ApiConfig.defaultBaseUrl, ApiConfig.baseUrl)
    }

    @Test
    fun realLocationRepository_refreshAndTransmitLocation_dynamicallyUsesProvider() = runBlocking {
        val service = CountingFakeLocationApiService(id = "service-refresh")
        val repository = RealLocationRepository(
            locationProvider = TestLocationProvider(),
            apiServiceProvider = { service }
        )

        val result = repository.refreshAndTransmitLocation()
        assertTrue(result.isSuccess)
        assertEquals(1, service.callCount)
        assertEquals(28.6139, service.lastUpdateRequest?.latitude ?: 0.0, 0.0001)
        assertEquals(77.2090, service.lastUpdateRequest?.longitude ?: 0.0, 0.0001)
    }

    @Test
    fun realLocationRepository_backendFailure_returnsFailureResult() = runBlocking {
        val failingService = object : LocationApiService {
            override suspend fun updateLocation(request: LocationUpdateRequestDto): Response<ApiResponseDto<String>> {
                return Response.error(500, "Backend error".toResponseBody())
            }
            override suspend fun getCurrentLocation() = TODO()
            override suspend fun removeLocation() = TODO()
            override suspend fun getNearbyResponders(latitude: Double, longitude: Double, radius: Double?) = TODO()
        }

        val repository = RealLocationRepository(
            locationProvider = TestLocationProvider(),
            apiServiceProvider = { failingService }
        )

        val result = repository.sendLocationToBackend(Location(28.6139, 77.2090, "New Delhi"))
        assertTrue(result.isFailure)
    }
}
