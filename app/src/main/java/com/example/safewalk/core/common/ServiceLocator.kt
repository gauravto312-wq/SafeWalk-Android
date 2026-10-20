package com.example.safewalk.core.common

import android.content.Context
import com.example.safewalk.core.location.DefaultLocationProvider
import com.example.safewalk.core.location.LocationProvider
import com.example.safewalk.data.remote.ApiConfig
import com.example.safewalk.data.remote.LocationApiService
import com.example.safewalk.data.remote.NotificationApiService
import com.example.safewalk.data.remote.NetworkClient
import com.example.safewalk.data.repository.AuthRepositoryImpl
import com.example.safewalk.data.repository.EmergencyRepositoryImpl
import com.example.safewalk.data.repository.LocationRepositoryImpl
import com.example.safewalk.data.repository.NotificationRepositoryImpl
import com.example.safewalk.data.repository.RealLocationRepository
import com.example.safewalk.data.repository.RemoteAuthRepository
import com.example.safewalk.data.session.SessionManager
import com.example.safewalk.data.source.mock.MockAuthDataSource
import com.example.safewalk.data.source.mock.MockEmergencyDataSource
import com.example.safewalk.data.source.mock.MockLocationDataSource
import com.example.safewalk.data.source.mock.MockNotificationDataSource
import com.example.safewalk.data.source.remote.RemoteAuthDataSource
import com.example.safewalk.data.repository.RemoteEmergencyRepository
import com.example.safewalk.data.source.remote.RemoteEmergencyDataSource
import com.example.safewalk.domain.repository.AuthRepository
import com.example.safewalk.domain.repository.EmergencyRepository
import com.example.safewalk.domain.repository.LocationRepository
import com.example.safewalk.domain.repository.NotificationRepository

/**
 * Service Locator providing repository singletons for MVVM architecture.
 * Manages both real RemoteAuthRepository (Spring Boot backend) and MockAuthRepository.
 */
object ServiceLocator {

    @Volatile
    var appContext: Context? = null
        private set

    @Volatile
    var useRemoteBackend: Boolean = true

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    val mockNotificationDataSource: MockNotificationDataSource by lazy {
        MockNotificationDataSource()
    }

    val mockAuthDataSource: MockAuthDataSource by lazy {
        MockAuthDataSource(notificationDataSource = mockNotificationDataSource)
    }

    val mockLocationDataSource: MockLocationDataSource by lazy {
        MockLocationDataSource()
    }

    val mockEmergencyDataSource: MockEmergencyDataSource by lazy {
        MockEmergencyDataSource(
            notificationDataSource = mockNotificationDataSource,
            userLookup = { mockAuthDataSource.getAvailableUsers() },
            locationLookup = { userId -> mockLocationDataSource.getCurrentLocation(userId) }
        )
    }

    val mockAuthRepository: AuthRepositoryImpl by lazy {
        AuthRepositoryImpl(mockAuthDataSource)
    }

    val sessionManager: SessionManager? by lazy {
        appContext?.let { SessionManager(it) }
    }

    val remoteAuthDataSource: RemoteAuthDataSource by lazy {
        RemoteAuthDataSource(
            apiServiceProvider = {
                NetworkClient.getAuthApiService(
                    sessionManager = sessionManager,
                    onUnauthorized = {
                        sessionManager?.clearSession()
                    }
                )
            }
        )
    }

    val remoteAuthRepository: RemoteAuthRepository? by lazy {
        sessionManager?.let { sm ->
            RemoteAuthRepository(
                remoteDataSource = remoteAuthDataSource,
                sessionManager = sm,
                mockFallbackRepository = mockAuthRepository
            )
        }
    }

    val authRepository: AuthRepository
        get() {
            return if (useRemoteBackend && remoteAuthRepository != null) {
                remoteAuthRepository!!
            } else {
                mockAuthRepository
            }
        }

    val mockEmergencyRepository: EmergencyRepositoryImpl by lazy {
        EmergencyRepositoryImpl(mockEmergencyDataSource, mockLocationDataSource)
    }

    val remoteEmergencyDataSource: RemoteEmergencyDataSource by lazy {
        val apiService = NetworkClient.getEmergencyApiService(
            sessionManager = sessionManager,
            onUnauthorized = {
                sessionManager?.clearSession()
            }
        )
        RemoteEmergencyDataSource(apiService)
    }

    val webSocketClient: com.example.safewalk.core.websocket.SafeWalkWebSocketClient? by lazy {
        sessionManager?.let { sm ->
            com.example.safewalk.core.websocket.SafeWalkWebSocketClient(
                okHttpClient = NetworkClient.getOkHttpClient(sm),
                tokenProvider = { sm.getToken() },
                baseUrlProvider = { com.example.safewalk.data.remote.ApiConfig.baseUrl }
            )
        }
    }

    val remoteEmergencyRepository: RemoteEmergencyRepository? by lazy {
        sessionManager?.let {
            RemoteEmergencyRepository(
                remoteDataSource = remoteEmergencyDataSource,
                mockFallbackDataSource = mockEmergencyDataSource,
                webSocketClient = webSocketClient
            )
        }
    }

    val emergencyRepository: EmergencyRepository
        get() {
            return if (useRemoteBackend && remoteEmergencyRepository != null) {
                remoteEmergencyRepository!!
            } else {
                mockEmergencyRepository
            }
        }

    val mockLocationRepository: LocationRepositoryImpl by lazy {
        LocationRepositoryImpl(mockLocationDataSource, mockEmergencyDataSource)
    }

    val realLocationProvider: LocationProvider? by lazy {
        appContext?.let { DefaultLocationProvider(it) }
    }

    val locationApiService: LocationApiService
        get() = NetworkClient.getLocationApiService(
            sessionManager = sessionManager,
            onUnauthorized = {
                sessionManager?.clearSession()
            }
        )

    val notificationApiService: NotificationApiService
        get() = NetworkClient.getNotificationApiService(
            sessionManager = sessionManager,
            onUnauthorized = {
                sessionManager?.clearSession()
            }
        )

    val realLocationRepository: RealLocationRepository? by lazy {
        realLocationProvider?.let { provider ->
            RealLocationRepository(
                locationProvider = provider,
                apiServiceProvider = {
                    NetworkClient.getLocationApiService(
                        sessionManager = sessionManager,
                        onUnauthorized = {
                            sessionManager?.clearSession()
                        }
                    )
                },
                mockFallback = mockLocationRepository
            )
        }
    }

    val locationRepository: LocationRepository
        get() {
            return if (useRemoteBackend && realLocationRepository != null) {
                realLocationRepository!!
            } else {
                mockLocationRepository
            }
        }

    val notificationRepository: NotificationRepository by lazy {
        NotificationRepositoryImpl(mockNotificationDataSource)
    }

    val liveLocationManager: com.example.safewalk.core.location.LiveLocationManager by lazy {
        com.example.safewalk.core.location.LiveLocationManager.getInstance()
    }

    fun clearUserStateOnLogout() {
        com.example.safewalk.core.notification.FcmTokenManager.unregisterTokenFromBackend()
        liveLocationManager.stopAllTracking(appContext)
        realLocationRepository?.clearLocationState()
        remoteEmergencyRepository?.clearEmergencyState()
        webSocketClient?.disconnect()
    }

    fun resetForTests() {
        appContext = null
        useRemoteBackend = false
    }
}
