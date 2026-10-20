package com.example.safewalk.data.model.dto

/**
 * Emergency Contact & Location DTOs matching UserController and LocationController contracts:
 * GET /api/v1/users/me/contacts
 * POST /api/v1/users/me/contacts
 * DELETE /api/v1/users/me/contacts/{contactIdentifier}
 * PUT /api/v1/location (Note: Singular endpoint)
 * GET /api/v1/location/nearby
 */
data class EmergencyContactRequestDto(
    val contactUserId: String? = null,
    val contactIdentifier: String? = null,
    val relationship: String? = null
)

data class EmergencyContactResponseDto(
    val id: Long,
    val userId: Long,
    val contactUserId: Long,
    val contactUniqueId: String,
    val name: String,
    val phoneNumber: String,
    val relationship: String? = null,
    val createdAt: String
)

data class LocationUpdateRequestDto(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double? = null,
    val speed: Double? = null,
    val heading: Double? = null,
    val timestamp: Long? = null
)

data class UserLocationResponseDto(
    val userId: Long,
    val latitude: Double?,
    val longitude: Double?,
    val timestamp: Long?,
    val fresh: Boolean?
)

data class NearbyUserResponseDto(
    val userId: Long,
    val name: String,
    val mobileNumber: String,
    val latitude: Double?,
    val longitude: Double?,
    val distanceKm: Double,
    val role: String,
    val accountStatus: String,
    val policeVerified: Boolean
)
