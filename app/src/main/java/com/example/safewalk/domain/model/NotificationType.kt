package com.example.safewalk.domain.model

/**
 * Types of notifications supported by the SafeWalk Notification System / Mock FCM.
 */
enum class NotificationType(val displayName: String, val iconText: String) {
    EMERGENCY_SOS_NEARBY("Nearby Emergency SOS", "🚨"),
    RESPONDER_ACCEPTED("Responder En Route", "🏃"),
    RESPONDER_ARRIVED("Responder Arrived", "📍"),
    EMERGENCY_RESOLVED("Emergency Resolved", "✅"),
    EMERGENCY_CANCELLED("Emergency Cancelled", "ℹ️"),
    TRUSTED_CONTACT_ALERT("Trusted Contact Alert", "🛡️"),
    VERIFICATION_APPROVED("Account Verified", "🎉"),
    VERIFICATION_REJECTED("Verification Update", "⚠️"),
    SYSTEM_ALERT("System Alert", "📢")
}
