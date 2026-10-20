package com.example.safewalk.domain.model

/**
 * Domain model representing a SafeWalk user.
 */
data class User(
    val id: String,
    val safeWalkId: String,
    val name: String,
    val phone: String,
    val email: String,
    val role: UserRole,
    val isVerified: Boolean = false,
    val status: String = "Ready",
    val avatarUrl: String? = null,
    val verificationStatus: VerificationStatus = if (role == UserRole.USER) {
        VerificationStatus.NONE
    } else if (isVerified) {
        VerificationStatus.VERIFIED
    } else {
        VerificationStatus.PENDING
    },
    // Professional Information
    val department: String? = null,
    val badgeId: String? = null,
    val hospitalName: String? = null,
    val licenseId: String? = null,
    val designation: String? = null,
    // Verification documents
    val documents: List<VerificationDocument> = emptyList(),
    // Up to 2 trusted/emergency contacts (SafeWalk IDs)
    val emergencyContacts: List<String> = emptyList(),
    // Secret Emergency / SOS Cancellation Password (validated on cancellation)
    val emergencyPassword: String? = null,
    val rejectionReason: String? = null
) {
    val initials: String
        get() = name.split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .map { it.first().uppercaseChar() }
            .joinToString("")
            .ifEmpty { "SW" }

    val isAuthorityAndVerified: Boolean
        get() = role.isAuthority && isVerified && verificationStatus == VerificationStatus.VERIFIED
}
