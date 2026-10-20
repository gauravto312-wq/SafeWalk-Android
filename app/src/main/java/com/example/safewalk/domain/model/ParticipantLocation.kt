package com.example.safewalk.domain.model

/**
 * Domain model representing a participant's live location stream during an emergency.
 * Provides independent coordinates, calculated distances, and authorization-aware states.
 */
data class ParticipantLocation(
    val userId: String,
    val userName: String = "",
    val safeWalkId: String = "",
    val role: EmergencyRole,
    val location: Location,
    val distanceToCounterpartMeters: Double? = null,
    val status: LocationStatus = location.status,
    val isArrived: Boolean = false
) {
    val isStale: Boolean
        get() = status == LocationStatus.STALE || location.isStale()

    val formattedDistance: String
        get() {
            return if (distanceToCounterpartMeters != null) {
                "${distanceToCounterpartMeters.toInt()}m"
            } else {
                "Unknown"
            }
        }
}
