package com.example.safewalk.domain.model

/**
 * Domain model representing real-time WebSocket / STOMP messages.
 * Maps directly to backend STOMP broadcast destinations:
 * - `/topic/sos/{emergencyId}` (Status, Acceptance, Arrival, Resolution, Cancellation, Rejection)
 * - `/topic/sos/{emergencyId}/location` (Location updates & Stopped events)
 */
sealed class RealtimeEmergencyEvent {
    abstract val emergencyId: String
    abstract val eventType: String
    val timestamp: Long = System.currentTimeMillis()

    data class SosCreated(
        override val emergencyId: String,
        val emergency: Emergency,
        override val eventType: String = "SOS_CREATED"
    ) : RealtimeEmergencyEvent()

    data class LocationUpdated(
        override val emergencyId: String,
        val participantId: String,
        val location: Location,
        val distanceToCounterpartMeters: Double? = null,
        override val eventType: String = "LOCATION_UPDATED"
    ) : RealtimeEmergencyEvent()

    data class LocationSharingStopped(
        override val emergencyId: String,
        override val eventType: String = "LOCATION_SHARING_STOPPED"
    ) : RealtimeEmergencyEvent()

    data class ResponderAccepted(
        override val emergencyId: String,
        val responder: User,
        override val eventType: String = "SOS_ACCEPTED"
    ) : RealtimeEmergencyEvent()

    data class ResponderArrived(
        override val emergencyId: String,
        val responder: User,
        override val eventType: String = "RESPONDER_ARRIVED"
    ) : RealtimeEmergencyEvent()

    data class StatusChanged(
        override val emergencyId: String,
        val oldStatus: EmergencyStatus,
        val newStatus: EmergencyStatus,
        override val eventType: String = "STATUS_CHANGED"
    ) : RealtimeEmergencyEvent()

    data class ResolutionConfirmed(
        override val emergencyId: String,
        val participantId: String,
        val isRequester: Boolean,
        override val eventType: String = "RESOLUTION_CONFIRMATION_PENDING"
    ) : RealtimeEmergencyEvent()

    data class ResolutionConfirmationPending(
        override val emergencyId: String,
        val requesterResolveConfirmed: Boolean,
        val responderResolveConfirmed: Boolean,
        val message: String,
        override val eventType: String = "RESOLUTION_CONFIRMATION_PENDING"
    ) : RealtimeEmergencyEvent()

    data class EmergencyResolved(
        override val emergencyId: String,
        val resolvedAt: Long,
        override val eventType: String = "SOS_RESOLVED"
    ) : RealtimeEmergencyEvent()

    data class EmergencyCancelled(
        override val emergencyId: String,
        val reason: String?,
        override val eventType: String = "SOS_CANCELLED"
    ) : RealtimeEmergencyEvent()

    data class EmergencyExpired(
        override val emergencyId: String,
        val expiredAt: Long = System.currentTimeMillis(),
        override val eventType: String = "SOS_EXPIRED"
    ) : RealtimeEmergencyEvent()

    data class EmergencyRejected(
        override val emergencyId: String,
        val responderId: String,
        val remainingRespondersCount: Int,
        override val eventType: String = "SOS_REJECTED"
    ) : RealtimeEmergencyEvent()
}
