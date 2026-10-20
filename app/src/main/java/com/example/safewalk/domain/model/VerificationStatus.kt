package com.example.safewalk.domain.model

/**
 * Verification state for SafeWalk professional/authority accounts (Police & Hospital).
 */
enum class VerificationStatus(val displayName: String) {
    NONE("None"),
    PENDING("Verification Pending"),
    VERIFIED("Verified"),
    REJECTED("Verification Rejected");

    val isPending: Boolean get() = this == PENDING
    val isVerified: Boolean get() = this == VERIFIED
    @Deprecated("Use isVerified instead", ReplaceWith("isVerified"))
    val isApproved: Boolean get() = this == VERIFIED
    val isRejected: Boolean get() = this == REJECTED

    companion object {
        @Deprecated("Use VERIFIED instead", ReplaceWith("VERIFIED"))
        val APPROVED: VerificationStatus = VERIFIED

        fun fromString(value: String?): VerificationStatus {
            return when (value?.trim()?.uppercase()) {
                "VERIFIED", "APPROVED" -> VERIFIED
                "PENDING" -> PENDING
                "REJECTED" -> REJECTED
                else -> NONE
            }
        }
    }
}
