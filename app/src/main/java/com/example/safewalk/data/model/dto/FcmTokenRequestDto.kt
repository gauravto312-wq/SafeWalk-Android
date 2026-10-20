package com.example.safewalk.data.model.dto

/**
 * Data transfer object for FCM device token registration.
 */
data class FcmTokenRequestDto(
    val token: String,
    val platform: String = "ANDROID"
)
