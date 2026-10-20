package com.example.safewalk.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.example.safewalk.domain.model.Location
import com.example.safewalk.domain.model.LocationStatus
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Concrete implementation of LocationProvider using Google Play Services FusedLocationProviderClient
 * with a high-accuracy configuration and fallback to Android LocationManager.
 */
class DefaultLocationProvider(
    private val context: Context,
    private val customFusedClient: FusedLocationProviderClient? = null,
    private val customLocationManager: LocationManager? = null
) : LocationProvider {

    private val fusedClient: FusedLocationProviderClient by lazy {
        customFusedClient ?: LocationServices.getFusedLocationProviderClient(context)
    }

    private val locationManager: LocationManager? by lazy {
        customLocationManager ?: (context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager)
    }

    override fun hasLocationPermission(): Boolean {
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fineGranted || coarseGranted
    }

    override fun isLocationEnabled(): Boolean {
        val lm = locationManager ?: return false
        val gpsEnabled = try {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
        } catch (_: Exception) {
            false
        }
        val networkEnabled = try {
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (_: Exception) {
            false
        }
        return gpsEnabled || networkEnabled
    }

    @SuppressLint("MissingPermission")
    override suspend fun getCurrentLocation(): LocationResult = withContext(Dispatchers.IO) {
        try {
            if (!hasLocationPermission()) {
                return@withContext LocationResult.Failure.PermissionDenied
            }

            if (!isLocationEnabled()) {
                return@withContext LocationResult.Failure.LocationDisabled
            }

            // 1. Try FusedLocationProviderClient (High Accuracy)
            var rawLocation = try {
                getFusedLocation()
            } catch (e: Exception) {
                null
            }

            // 2. Fallback to LocationManager if Fused returns null
            if (rawLocation == null) {
                rawLocation = getLocationManagerFallback()
            }

            if (rawLocation != null) {
                val address = resolveAddress(rawLocation.latitude, rawLocation.longitude)
                val domainLocation = Location(
                    latitude = rawLocation.latitude,
                    longitude = rawLocation.longitude,
                    address = address,
                    timestamp = if (rawLocation.time > 0) rawLocation.time else System.currentTimeMillis(),
                    status = LocationStatus.ACTIVE
                )
                LocationResult.Success(domainLocation)
            } else {
                LocationResult.Failure.Error(
                    IllegalStateException("No location signal available from GPS or network provider")
                )
            }
        } catch (e: SecurityException) {
            LocationResult.Failure.PermissionDenied
        } catch (e: Throwable) {
            LocationResult.Failure.Error(e)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun getFusedLocation(): android.location.Location? =
        suspendCancellableCoroutine { continuation ->
            val cancellationTokenSource = CancellationTokenSource()

            val request = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setMaxUpdateAgeMillis(30_000L)
                .build()

            fusedClient.getCurrentLocation(request, cancellationTokenSource.token)
                .addOnSuccessListener { loc ->
                    if (loc != null) {
                        if (continuation.isActive) continuation.resume(loc)
                    } else {
                        // Fallback to lastLocation on Fused
                        fusedClient.lastLocation
                            .addOnSuccessListener { lastLoc ->
                                if (continuation.isActive) continuation.resume(lastLoc)
                            }
                            .addOnFailureListener {
                                if (continuation.isActive) continuation.resume(null)
                            }
                    }
                }
                .addOnFailureListener { ex ->
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }

            continuation.invokeOnCancellation {
                cancellationTokenSource.cancel()
            }
        }

    @SuppressLint("MissingPermission")
    private fun getLocationManagerFallback(): android.location.Location? {
        val lm = locationManager ?: return null
        return try {
            val gpsLoc = if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            } else null

            val netLoc = if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            } else null

            when {
                gpsLoc != null && netLoc != null -> if (gpsLoc.time >= netLoc.time) gpsLoc else netLoc
                gpsLoc != null -> gpsLoc
                else -> netLoc
            }
        } catch (_: Exception) {
            null
        }
    }

    @SuppressLint("MissingPermission")
    override fun getLocationUpdates(
        intervalMs: Long,
        minDistanceMeters: Float
    ): Flow<LocationResult> = callbackFlow {
        if (!hasLocationPermission()) {
            trySend(LocationResult.Failure.PermissionDenied)
            close()
            return@callbackFlow
        }

        if (!isLocationEnabled()) {
            trySend(LocationResult.Failure.LocationDisabled)
        }

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            intervalMs
        )
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .setMinUpdateDistanceMeters(minDistanceMeters)
            .build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
                val loc = result.lastLocation ?: return
                val address = resolveAddress(loc.latitude, loc.longitude)
                val domainLocation = Location(
                    latitude = loc.latitude,
                    longitude = loc.longitude,
                    address = address,
                    timestamp = if (loc.time > 0) loc.time else System.currentTimeMillis(),
                    status = LocationStatus.ACTIVE
                )
                trySend(LocationResult.Success(domainLocation))
            }

            override fun onLocationAvailability(availability: LocationAvailability) {
                if (!availability.isLocationAvailable && !isLocationEnabled()) {
                    trySend(LocationResult.Failure.LocationDisabled)
                }
            }
        }

        var isFusedRegistered = false
        try {
            fusedClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            isFusedRegistered = true
        } catch (_: Exception) {}

        var lmListener: android.location.LocationListener? = null
        if (!isFusedRegistered) {
            val lm = locationManager
            if (lm != null) {
                lmListener = android.location.LocationListener { loc ->
                    val address = resolveAddress(loc.latitude, loc.longitude)
                    val domainLocation = Location(
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        address = address,
                        timestamp = if (loc.time > 0) loc.time else System.currentTimeMillis(),
                        status = LocationStatus.ACTIVE
                    )
                    trySend(LocationResult.Success(domainLocation))
                }
                try {
                    if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                        lm.requestLocationUpdates(
                            LocationManager.GPS_PROVIDER,
                            intervalMs,
                            minDistanceMeters,
                            lmListener,
                            Looper.getMainLooper()
                        )
                    } else if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                        lm.requestLocationUpdates(
                            LocationManager.NETWORK_PROVIDER,
                            intervalMs,
                            minDistanceMeters,
                            lmListener,
                            Looper.getMainLooper()
                        )
                    }
                } catch (e: Exception) {
                    trySend(LocationResult.Failure.Error(e))
                }
            } else {
                trySend(LocationResult.Failure.Error(IllegalStateException("No location provider available")))
            }
        }

        awaitClose {
            if (isFusedRegistered) {
                fusedClient.removeLocationUpdates(locationCallback)
            }
            if (lmListener != null) {
                try {
                    locationManager?.removeUpdates(lmListener)
                } catch (_: Exception) {}
            }
        }
    }

    private fun resolveAddress(latitude: Double, longitude: Double): String {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                addr.getAddressLine(0) ?: "${addr.locality ?: "Location"}, ${addr.countryName ?: ""}".trim(',', ' ')
            } else {
                String.format(Locale.US, "GPS (%.4f, %.4f)", latitude, longitude)
            }
        } catch (_: Exception) {
            String.format(Locale.US, "GPS (%.4f, %.4f)", latitude, longitude)
        }
    }
}
