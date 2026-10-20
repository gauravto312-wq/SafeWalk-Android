package com.example.safewalk.data.model.dto

import com.google.gson.annotations.SerializedName

/**
 * Standard Envelope and Error Response matching backend contracts:
 * Section 6.1 ApiResponse<T> and Section 16.1 ErrorResponse.
 */
data class ApiResponseDto<T>(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: T? = null,
    @SerializedName("timestamp") val timestamp: String? = null
)

data class ErrorResponseDto(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String? = null,
    @SerializedName("errors") val errors: Map<String, String>? = null,
    @SerializedName("path") val path: String? = null,
    @SerializedName("timestamp") val timestamp: String? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("status") val status: Int? = null
)
