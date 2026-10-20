package com.example.safewalk.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.safewalk.core.common.ServiceLocator
import com.example.safewalk.domain.model.Location
import com.example.safewalk.domain.repository.LocationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LocationUiState(
    val hasPermission: Boolean = false,
    val isGpsEnabled: Boolean = false,
    val currentLocation: Location? = null,
    val isAcquiring: Boolean = false,
    val isSyncing: Boolean = false,
    val lastSyncedTimestamp: Long? = null,
    val isSyncedWithCloud: Boolean = false,
    val errorMessage: String? = null
)

class LocationViewModel(
    private val locationRepository: LocationRepository = ServiceLocator.locationRepository,
    private val externalScope: CoroutineScope? = null
) : ViewModel() {

    private val scope: CoroutineScope
        get() = externalScope ?: viewModelScope

    private val _uiState = MutableStateFlow(LocationUiState())
    val uiState: StateFlow<LocationUiState> = _uiState.asStateFlow()

    init {
        checkStatus()
    }

    fun checkStatus() {
        val hasPerm = locationRepository.hasLocationPermission()
        val isEnabled = locationRepository.isLocationEnabled()
        _uiState.value = _uiState.value.copy(
            hasPermission = hasPerm,
            isGpsEnabled = isEnabled,
            errorMessage = when {
                !hasPerm -> "Location permission is required to acquire GPS coordinates."
                !isEnabled -> "Device location is off. Please turn on location."
                else -> null
            }
        )
    }

    fun onLocationProviderAvailabilityChanged(): kotlinx.coroutines.Job? {
        val wasGpsDisabled = !_uiState.value.isGpsEnabled
        checkStatus()
        val currentState = _uiState.value
        return if (currentState.hasPermission && currentState.isGpsEnabled) {
            refreshAndSyncLocation()
        } else null
    }

    fun onPermissionResult(granted: Boolean) {
        val isEnabled = locationRepository.isLocationEnabled()
        _uiState.value = _uiState.value.copy(
            hasPermission = granted,
            isGpsEnabled = isEnabled,
            errorMessage = when {
                !granted -> "Location permission was denied. SafeWalk needs location access."
                !isEnabled -> "Device location is off. Please turn on location."
                else -> null
            }
        )
        if (granted && isEnabled) {
            refreshAndSyncLocation()
        }
    }

    fun refreshAndSyncLocation(): kotlinx.coroutines.Job {
        checkStatus()
        val currentState = _uiState.value

        if (!currentState.hasPermission) {
            _uiState.value = currentState.copy(
                errorMessage = "Location permission is required to acquire GPS coordinates."
            )
            return kotlinx.coroutines.CompletableDeferred<Unit>().apply { complete(Unit) }
        }

        if (!currentState.isGpsEnabled) {
            _uiState.value = currentState.copy(
                errorMessage = "Device location is off. Please turn on location."
            )
            return kotlinx.coroutines.CompletableDeferred<Unit>().apply { complete(Unit) }
        }

        return scope.launch {
            _uiState.value = _uiState.value.copy(
                isAcquiring = true,
                isSyncing = true,
                errorMessage = null
            )

            val acquireResult = locationRepository.acquireCurrentLocation()
            if (acquireResult.isSuccess) {
                val loc = acquireResult.getOrThrow()
                _uiState.value = _uiState.value.copy(
                    currentLocation = loc,
                    isAcquiring = false
                )

                // Transmit to PUT /api/v1/location
                val transmitResult = locationRepository.sendLocationToBackend(loc)
                if (transmitResult.isSuccess) {
                    _uiState.value = _uiState.value.copy(
                        isSyncing = false,
                        isSyncedWithCloud = true,
                        lastSyncedTimestamp = System.currentTimeMillis(),
                        errorMessage = null
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isSyncing = false,
                        isSyncedWithCloud = false,
                        errorMessage = transmitResult.exceptionOrNull()?.localizedMessage ?: "Failed to sync location to backend"
                    )
                }
            } else {
                _uiState.value = _uiState.value.copy(
                    isAcquiring = false,
                    isSyncing = false,
                    isSyncedWithCloud = false,
                    errorMessage = acquireResult.exceptionOrNull()?.localizedMessage ?: "Failed to acquire device location"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
