package com.example.safewalk.domain.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Mock representation of geographic location and reverse-geocoded address.
 * Ready for future replacement with real GPS/location providers without architectural rework.
 */
data class Location(
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: LocationStatus = LocationStatus.ACTIVE
) {
    val formattedCoordinates: String
        get() = String.format(Locale.US, "%.5f, %.5f", latitude, longitude)

    val formattedTimestamp: String
        get() {
            val sdf = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }

    /**
     * Calculates distance to another Location using the Haversine formula.
     * Returns distance in meters.
     */
    fun distanceTo(other: Location): Double {
        val earthRadiusMeters = 6371000.0
        val lat1Rad = Math.toRadians(latitude)
        val lat2Rad = Math.toRadians(other.latitude)
        val dLat = Math.toRadians(other.latitude - latitude)
        val dLon = Math.toRadians(other.longitude - longitude)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(lat1Rad) * cos(lat2Rad) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusMeters * c
    }

    /**
     * Checks if this location reading is considered stale.
     * Mock staleness threshold is 30,000ms (30 seconds).
     */
    fun isStale(
        currentTimeMillis: Long = System.currentTimeMillis(),
        thresholdMillis: Long = DEFAULT_STALE_THRESHOLD_MILLIS
    ): Boolean {
        if (status == LocationStatus.STOPPED) return false
        val elapsed = (currentTimeMillis - timestamp).coerceAtLeast(0L)
        return elapsed > thresholdMillis
    }

    /**
     * Returns effective LocationStatus considering timestamps and stopped state.
     */
    fun effectiveStatus(
        currentTimeMillis: Long = System.currentTimeMillis(),
        thresholdMillis: Long = DEFAULT_STALE_THRESHOLD_MILLIS
    ): LocationStatus {
        return when {
            status == LocationStatus.STOPPED -> LocationStatus.STOPPED
            isStale(currentTimeMillis, thresholdMillis) -> LocationStatus.STALE
            else -> LocationStatus.ACTIVE
        }
    }

    /**
     * Returns approximate / masked coordinates for privacy before acceptance.
     * Truncates precision to protect exact requester GPS.
     */
    fun toApproximate(approximateAddress: String = "General Sector (~750m away)"): Location {
        val approxLat = Math.round(latitude * 100.0) / 100.0
        val approxLon = Math.round(longitude * 100.0) / 100.0
        return Location(
            latitude = approxLat,
            longitude = approxLon,
            address = approximateAddress,
            timestamp = timestamp,
            status = status
        )
    }

    companion object {
        const val DEFAULT_STALE_THRESHOLD_MILLIS = 30_000L

        /**
         * Creates a deterministic mock location at a given distance (meters) and bearing (degrees)
         * from a base location. Uses exact spherical trigonometry so distanceTo() yields the target distance.
         */
        fun createAtOffset(
            base: Location,
            distanceMeters: Double,
            bearingDegrees: Double = 0.0,
            address: String = "Simulated Location",
            timestamp: Long = System.currentTimeMillis(),
            status: LocationStatus = LocationStatus.ACTIVE
        ): Location {
            val earthRadiusMeters = 6371000.0
            val bearingRad = Math.toRadians(bearingDegrees)
            val baseLatRad = Math.toRadians(base.latitude)

            val dLatDeg = Math.toDegrees((distanceMeters * cos(bearingRad)) / earthRadiusMeters)
            val dLonDeg = Math.toDegrees((distanceMeters * sin(bearingRad)) / (earthRadiusMeters * cos(baseLatRad)))

            return Location(
                latitude = base.latitude + dLatDeg,
                longitude = base.longitude + dLonDeg,
                address = address,
                timestamp = timestamp,
                status = status
            )
        }
    }
}
