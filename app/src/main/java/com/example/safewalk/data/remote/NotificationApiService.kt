package com.example.safewalk.data.remote

import com.example.safewalk.data.model.dto.ApiResponseDto
import com.example.safewalk.data.model.dto.FcmTokenRequestDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Retrofit interface for SafeWalk Spring Boot Notification / FCM REST APIs:
 * - POST /api/v1/notifications/device-token
 * - DELETE /api/v1/notifications/device-token?token={token}
 */
interface NotificationApiService {

    @POST("api/v1/notifications/device-token")
    suspend fun registerDeviceToken(
        @Body request: FcmTokenRequestDto
    ): Response<ApiResponseDto<String>>

    @DELETE("api/v1/notifications/device-token")
    suspend fun removeDeviceToken(
        @Query("token") token: String
    ): Response<ApiResponseDto<String>>
}
