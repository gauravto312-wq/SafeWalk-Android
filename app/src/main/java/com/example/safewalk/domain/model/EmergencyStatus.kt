package com.example.safewalk.domain.model

/**
 * Emergency state transitions strictly adhering to the SafeWalk Spring Boot backend FSM:
 * ACTIVE -> ACCEPTED -> RESOLVED
 * ACTIVE -> CANCELLED
 * ACCEPTED -> ACTIVE (when last responder opts out)
 */
enum class EmergencyStatus(val displayName: String) {
    ACTIVE("Active SOS"),
    ACCEPTED("Responder Assigned"),
    RESOLVED("Emergency Resolved"),
    CANCELLED("Emergency Cancelled"),
    EXPIRED("Emergency Expired");

    val isTerminal: Boolean
        get() = this == RESOLVED || this == CANCELLED || this == EXPIRED
}
