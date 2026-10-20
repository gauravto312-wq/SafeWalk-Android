package com.example.safewalk.domain.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Domain model representing an Emergency lifecycle in SafeWalk.
 */
data class Emergency(
    val id: String,
    val requester: User,
    val status: EmergencyStatus = EmergencyStatus.ACTIVE,
    val createdAt: Long = System.currentTimeMillis(),
    val acceptedResponders: List<User> = emptyList(),
    val arrivedResponders: List<User> = emptyList(),
    val arrivedResponder: User? = null,
    val requesterLocation: Location,
    val responderLocations: Map<String, Location> = emptyMap(),
    val responderDistances: Map<String, Double> = emptyMap(),
    val distanceMeters: Double? = null,
    val requesterConfirmedResolution: Boolean = false,
    val responderConfirmedResolution: Boolean = false,
    val cancellationReason: String? = null,
    val resolvedAt: Long? = null,
    val expiredAt: Long? = null,
    val isTrustedContactViewer: Boolean = false
) {
    val formattedDistance: String?
        get() {
            val d = distanceMeters ?: return null
            return if (d >= 1000.0) {
                String.format(Locale.getDefault(), "%.1f km away", d / 1000.0)
            } else {
                String.format(Locale.getDefault(), "%.0f m away", d)
            }
        }

    val formattedTime: String
        get() {
            val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
            return sdf.format(Date(createdAt))
        }

    val formattedResolvedTime: String?
        get() {
            if (resolvedAt == null) return null
            val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
            return sdf.format(Date(resolvedAt))
        }

    val formattedExpiredTime: String?
        get() {
            if (expiredAt == null) return null
            val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
            return sdf.format(Date(expiredAt))
        }

    val isTerminal: Boolean
        get() = status.isTerminal

    val isFullyResolved: Boolean
        get() = requesterConfirmedResolution && responderConfirmedResolution

    val activeArrivedResponders: List<User>
        get() = if (arrivedResponders.isNotEmpty()) {
            arrivedResponders
        } else if (arrivedResponder != null) {
            listOf(arrivedResponder)
        } else {
            emptyList()
        }

    val effectiveArrivedResponder: User?
        get() = arrivedResponder ?: arrivedResponders.firstOrNull()

    val designatedArrivedResponder: User?
        get() = effectiveArrivedResponder

    val hasArrivedResponder: Boolean
        get() = effectiveArrivedResponder != null

    fun isDesignatedArrivedResponder(userId: String): Boolean {
        return effectiveArrivedResponder?.id == userId
    }

    fun isAnotherResponderArrived(userId: String): Boolean {
        val designated = effectiveArrivedResponder ?: return false
        return designated.id != userId
    }

    fun isUserAcceptedResponder(userId: String): Boolean {
        return acceptedResponders.any { it.id == userId }
    }

    fun isResponderArrived(userId: String): Boolean {
        return activeArrivedResponders.any { it.id == userId }
    }

    fun isUserRequester(userId: String): Boolean {
        return requester.id == userId
    }

    companion object {
        const val CANCELLATION_WINDOW_MILLIS = 5000L
    }

    /**
     * Calculates remaining time in milliseconds for the cancellation window.
     * remainingTime = 5000ms - (currentTime - emergency.createdAt)
     */
    fun remainingCancellationTimeMillis(currentTimeMillis: Long = System.currentTimeMillis()): Long {
        if (isTerminal) return 0L
        val elapsed = (currentTimeMillis - createdAt).coerceAtLeast(0L)
        val remaining = CANCELLATION_WINDOW_MILLIS - elapsed
        return if (remaining > 0L) remaining else 0L
    }

    /**
     * Returns true if the emergency is eligible for cancellation:
     * - Must not be terminal (not CANCELLED and not RESOLVED)
     * - Emergency age must be strictly less than 5000ms (5.000 seconds)
     */
    fun canCancel(currentTimeMillis: Long = System.currentTimeMillis()): Boolean {
        if (isTerminal) return false
        val elapsed = (currentTimeMillis - createdAt).coerceAtLeast(0L)
        return elapsed < CANCELLATION_WINDOW_MILLIS
    }

    val numericId: String
        get() = id.removePrefix("EMG-")

    val displayId: String
        get() = "Emergency ID: $numericId"

    /**
     * Returns true if the requester is eligible to resolve the emergency:
     * - Must not be terminal (not CANCELLED and not RESOLVED).
     * - A responder must have accepted the emergency and marked ARRIVED on scene
     *   (or reached distance <= 25.0 meters).
     * Does not require all responders to arrive.
     * Does not require a specific responder.
     * Does not require the requester to wait for every responder.
     */
    fun canRequesterResolve(): Boolean {
        if (isTerminal) return false
        if (activeArrivedResponders.isNotEmpty() || arrivedResponder != null) return true
        return acceptedResponders.any { responder ->
            isResponderArrived(responder.id) || (responderDistances[responder.id]?.let { it <= 25.0 } == true)
        }
    }
}
