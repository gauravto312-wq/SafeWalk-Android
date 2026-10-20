package com.example.safewalk.data.model.dto

/**
 * Emergency Request & Response DTOs matching EmergencyController contracts:
 * POST /api/v1/emergencies
 * GET /api/v1/emergencies/{id}
 * POST /api/v1/emergencies/{id}/accept
 * POST /api/v1/emergencies/{id}/arrive
 * POST /api/v1/emergencies/{id}/cancel
 * POST /api/v1/emergencies/{id}/resolve
 * POST /api/v1/emergencies/{id}/reject
 * GET /api/v1/emergencies/{id}/locations
 */
import com.google.gson.annotations.SerializedName

data class CreateEmergencyRequestDto(
    val type: String = "GENERAL",
    val latitude: Double,
    val longitude: Double,
    val address: String? = null,
    val description: String? = null,
    val isTest: Boolean = false
)

data class PageResponseDto<T>(
    val content: List<T> = emptyList(),
    val totalElements: Long = 0L,
    val totalPages: Int = 0,
    val number: Int = 0,
    val size: Int = 0
)

data class CancelEmergencyRequestDto(
    val emergencyPassword: String,
    val reason: String? = null
)

data class EmergencyResponseDto(
    val id: Long,
    val requesterId: Long,
    val requesterName: String? = null,
    @SerializedName("requesterPhone", alternate = ["requesterMobile"])
    val requesterPhone: String? = null,
    val responderId: Long? = null,
    val responderName: String? = null,
    @SerializedName("responderPhone", alternate = ["responderMobile"])
    val responderPhone: String? = null,
    val responders: List<UserResponseDto> = emptyList(),
    val type: String = "GENERAL",
    val status: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String? = null,
    val description: String? = null,
    val isTest: Boolean = false,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val acceptedAt: String? = null,
    val resolvedAt: String? = null,
    val cancelledAt: String? = null,
    val expiredAt: String? = null,
    val responderIds: List<Long> = emptyList(),
    val responderCount: Int = 0,
    val arrivedResponderId: Long? = null,
    val arrivedResponderName: String? = null,
    val arrivedAt: String? = null,
    val requesterResolveConfirmed: Boolean = false,
    val responderResolveConfirmed: Boolean = false,
    val distanceMeters: Double? = null,
    @SerializedName("trustedContactViewer") val trustedContactViewer: Boolean = false
)

data class EmergencyLocationResponseDto(
    val emergencyId: Long,
    val status: String,
    val requester: ParticipantLocationDto,
    val responders: List<ParticipantLocationDto> = emptyList()
)

data class ParticipantLocationDto(
    val userId: Long,
    val name: String,
    val mobileNumber: String,
    val latitude: Double?,
    val longitude: Double?,
    val distanceKm: Double? = null,
    val updatedAt: String? = null
)
