package com.example.safewalk.data.remote

import com.example.safewalk.data.model.dto.ApiResponseDto
import com.example.safewalk.data.model.dto.LocationUpdateRequestDto
import com.example.safewalk.data.model.dto.NearbyUserResponseDto
import com.example.safewalk.data.model.dto.UserLocationResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Query

/**
 * Retrofit interface for SafeWalk Spring Boot Location REST APIs:
 * - PUT /api/v1/location
 * - GET /api/v1/location
 * - DELETE /api/v1/location
 * - GET /api/v1/location/nearby
 */
interface LocationApiService {

    @PUT("api/v1/location")
    suspend fun updateLocation(
        @Body request: LocationUpdateRequestDto
    ): Response<ApiResponseDto<String>>

    @GET("api/v1/location")
    suspend fun getCurrentLocation(): Response<ApiResponseDto<UserLocationResponseDto>>

    @DELETE("api/v1/location")
    suspend fun removeLocation(): Response<ApiResponseDto<String>>

    @GET("api/v1/location/nearby")
    suspend fun getNearbyResponders(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("radius") radius: Double? = null
    ): Response<ApiResponseDto<List<NearbyUserResponseDto>>>
}
