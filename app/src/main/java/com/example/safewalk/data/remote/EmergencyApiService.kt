package com.example.safewalk.data.remote

import com.example.safewalk.data.model.dto.ApiResponseDto
import com.example.safewalk.data.model.dto.CancelEmergencyRequestDto
import com.example.safewalk.data.model.dto.CreateEmergencyRequestDto
import com.example.safewalk.data.model.dto.EmergencyLocationResponseDto
import com.example.safewalk.data.model.dto.EmergencyResponseDto
import com.example.safewalk.data.model.dto.PageResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface representing the Spring Boot SafeWalk Emergency REST APIs.
 */
interface EmergencyApiService {

    @POST("api/v1/emergencies")
    suspend fun createEmergency(
        @Body request: CreateEmergencyRequestDto
    ): Response<ApiResponseDto<EmergencyResponseDto>>

    @GET("api/v1/emergencies")
    suspend fun getActiveEmergencies(
        @Query("status") status: String? = null
    ): Response<ApiResponseDto<List<EmergencyResponseDto>>>

    @GET("api/v1/emergencies/{id}")
    suspend fun getEmergencyById(
        @Path("id") id: Long
    ): Response<ApiResponseDto<EmergencyResponseDto>>

    @GET("api/v1/emergencies/my-active")
    suspend fun getMyActiveEmergency(): Response<ApiResponseDto<EmergencyResponseDto>>

    @GET("api/v1/emergencies/my-history")
    suspend fun getMyEmergencyHistory(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 50
    ): Response<ApiResponseDto<PageResponseDto<EmergencyResponseDto>>>

    @POST("api/v1/emergencies/{id}/accept")
    suspend fun acceptEmergency(
        @Path("id") id: Long
    ): Response<ApiResponseDto<EmergencyResponseDto>>

    @POST("api/v1/emergencies/{id}/arrive")
    suspend fun arriveEmergency(
        @Path("id") id: Long
    ): Response<ApiResponseDto<EmergencyResponseDto>>

    @POST("api/v1/emergencies/{id}/reject")
    suspend fun rejectEmergency(
        @Path("id") id: Long
    ): Response<ApiResponseDto<EmergencyResponseDto>>

    @POST("api/v1/emergencies/{id}/cancel")
    suspend fun cancelEmergency(
        @Path("id") id: Long,
        @Body request: CancelEmergencyRequestDto
    ): Response<ApiResponseDto<EmergencyResponseDto>>

    @POST("api/v1/emergencies/{id}/resolve")
    suspend fun resolveEmergency(
        @Path("id") id: Long
    ): Response<ApiResponseDto<EmergencyResponseDto>>

    @GET("api/v1/emergencies/{id}/locations")
    suspend fun getEmergencyLocations(
        @Path("id") id: Long
    ): Response<ApiResponseDto<EmergencyLocationResponseDto>>
}
