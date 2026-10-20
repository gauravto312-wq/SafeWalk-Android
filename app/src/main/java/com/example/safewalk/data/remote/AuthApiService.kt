package com.example.safewalk.data.remote

import com.example.safewalk.data.model.dto.ApiResponseDto
import com.example.safewalk.data.model.dto.AuthResponseDto
import com.example.safewalk.data.model.dto.LoginRequestDto
import com.example.safewalk.data.model.dto.RegisterRequestDto
import com.example.safewalk.data.model.dto.UserResponseDto
import com.example.safewalk.data.model.dto.VerificationDocumentResponseDto
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

/**
 * Retrofit interface representing the Spring Boot SafeWalk Authentication, Current User, and Verification Document APIs.
 */
interface AuthApiService {

    @POST("api/v1/auth/register")
    suspend fun register(
        @Body request: RegisterRequestDto
    ): Response<ApiResponseDto<AuthResponseDto>>

    @POST("api/v1/auth/login")
    suspend fun login(
        @Body request: LoginRequestDto
    ): Response<ApiResponseDto<AuthResponseDto>>

    @GET("api/v1/users/me")
    suspend fun getCurrentUser(): Response<ApiResponseDto<UserResponseDto>>

    @Multipart
    @POST("api/v1/users/me/verification-documents")
    suspend fun uploadVerificationDocument(
        @Part file: MultipartBody.Part,
        @Part("title") title: RequestBody? = null
    ): Response<ApiResponseDto<VerificationDocumentResponseDto>>

    @GET("api/v1/users/me/verification-documents")
    suspend fun getVerificationDocuments(): Response<ApiResponseDto<List<VerificationDocumentResponseDto>>>

    @GET("api/v1/users/me/contacts")
    suspend fun getEmergencyContacts(): Response<ApiResponseDto<List<com.example.safewalk.data.model.dto.EmergencyContactResponseDto>>>

    @POST("api/v1/users/me/contacts")
    suspend fun addEmergencyContact(
        @Body request: com.example.safewalk.data.model.dto.EmergencyContactRequestDto
    ): Response<ApiResponseDto<com.example.safewalk.data.model.dto.EmergencyContactResponseDto>>

    @retrofit2.http.PUT("api/v1/users/me/contacts/{contactIdentifier}")
    suspend fun replaceEmergencyContact(
        @retrofit2.http.Path("contactIdentifier") contactIdentifier: String,
        @Body request: com.example.safewalk.data.model.dto.EmergencyContactRequestDto
    ): Response<ApiResponseDto<com.example.safewalk.data.model.dto.EmergencyContactResponseDto>>

    @retrofit2.http.DELETE("api/v1/users/me/contacts/{contactIdentifier}")
    suspend fun removeEmergencyContact(
        @retrofit2.http.Path("contactIdentifier") contactIdentifier: String
    ): Response<ApiResponseDto<Unit>>

    @Multipart
    @POST("api/v1/users/me/avatar")
    suspend fun uploadAvatar(
        @Part file: MultipartBody.Part
    ): Response<ApiResponseDto<UserResponseDto>>
}
