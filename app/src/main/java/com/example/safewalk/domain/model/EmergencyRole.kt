package com.example.safewalk.domain.model

/**
 * Contextual emergency roles.
 * These are NOT permanent account roles.
 * A single USER can be a REQUESTER in one emergency and a RESPONDER in another.
 */
enum class EmergencyRole(val displayName: String) {
    REQUESTER("Requester"),
    RESPONDER("Responder")
}
