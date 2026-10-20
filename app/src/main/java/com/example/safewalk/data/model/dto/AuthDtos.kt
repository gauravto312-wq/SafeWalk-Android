package com.example.safewalk.data.model.dto

import com.google.gson.annotations.SerializedName

/**
 * Authentication and User DTOs matching backend AuthController & UserController contracts:
 * POST /api/v1/auth/register
 * POST /api/v1/auth/login
 * GET /api/v1/users/me
 */
data class RegisterRequestDto(
    @SerializedName("name") val name: String,
    @SerializedName("mobileNumber") val mobileNumber: String,
    @SerializedName("password") val password: String,
    @SerializedName("emergencyPassword") val emergencyPassword: String? = null,
    @SerializedName("role") val role: String? = null,
    @SerializedName("badgeNumber") val badgeNumber: String? = null,
    @SerializedName("department") val department: String? = null,
    @SerializedName("policeStation") val policeStation: String? = null,
    @SerializedName("email") val email: String? = null
)

data class LoginRequestDto(
    @SerializedName("mobileNumber") val mobileNumber: String,
    @SerializedName("password") val password: String
)

data class AuthResponseDto(
    @SerializedName("token") val token: String,
    @SerializedName("tokenType") val tokenType: String = "Bearer",
    @SerializedName("user") val user: UserResponseDto? = null
)

data class UserResponseDto(
    @SerializedName("id") val id: Long,
    @SerializedName("name") val name: String,
    @SerializedName("mobileNumber") val mobileNumber: String,
    @SerializedName("role") val role: String,
    @SerializedName("accountStatus") val accountStatus: String,
    @SerializedName("policeVerified") val policeVerified: Boolean = false,
    @SerializedName("verificationStatus") val verificationStatus: String? = null,
    @SerializedName("badgeNumber") val badgeNumber: String? = null,
    @SerializedName("department") val department: String? = null,
    @SerializedName("policeStation") val policeStation: String? = null,
    @SerializedName("rejectionReason") val rejectionReason: String? = null,
    @SerializedName("avatarUrl") val avatarUrl: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("createdAt") val createdAt: String? = null,
    @SerializedName("updatedAt") val updatedAt: String? = null
)

data class VerificationDocumentResponseDto(
    @SerializedName("id") val id: Long,
    @SerializedName("userId") val userId: Long? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("fileName") val fileName: String,
    @SerializedName("mimeType") val mimeType: String? = null,
    @SerializedName("fileSize") val fileSize: Long? = null,
    @SerializedName("uploadedAt") val uploadedAt: String? = null
)
