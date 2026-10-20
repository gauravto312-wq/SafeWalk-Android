package com.example.safewalk.domain.model

/**
 * Lifecycle status of live-location telemetry in SafeWalk.
 */
enum class LocationStatus(val displayName: String) {
    ACTIVE("LIVE"),
    STALE("LOCATION STALE"),
    STOPPED("STOPPED")
}
