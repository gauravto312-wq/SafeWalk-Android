package com.example.safewalk.data.source.remote

import com.example.safewalk.core.error.SafeWalkErrorMapper
import com.example.safewalk.core.error.SafeWalkException
import com.example.safewalk.data.model.dto.AuthResponseDto
import com.example.safewalk.data.model.dto.LoginRequestDto
import com.example.safewalk.data.model.dto.RegisterRequestDto
import com.example.safewalk.data.model.dto.UserResponseDto
import com.example.safewalk.data.model.dto.VerificationDocumentResponseDto
import com.example.safewalk.data.remote.AuthApiService
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Remote Data Source handling HTTP network calls to the SafeWalk Spring Boot backend.
 */
class RemoteAuthDataSource(
    private val apiServiceProvider: () -> AuthApiService
) {

    constructor(apiService: AuthApiService) : this({ apiService })

    private val apiService: AuthApiService
        get() = apiServiceProvider()

    suspend fun register(
        name: String,
        mobileNumber: String,
        password: String,
        email: String? = null,
        emergencyPassword: String? = null,
        role: String? = null,
        badgeNumber: String? = null,
        department: String? = null,
        policeStation: String? = null
    ): Result<AuthResponseDto> {
        return try {
            val request = RegisterRequestDto(
                name = name,
                mobileNumber = mobileNumber,
                password = password,
                email = email?.trim()?.ifBlank { null },
                emergencyPassword = emergencyPassword?.ifBlank { null },
                role = role,
                badgeNumber = badgeNumber?.ifBlank { null },
                department = department?.ifBlank { null },
                policeStation = policeStation?.ifBlank { null }
            )
            val response = apiService.register(request)
            if (response.isSuccessful) {
                val authData = response.body()?.data
                if (authData != null) {
                    Result.success(authData)
                } else {
                    Result.failure(SafeWalkException.ServerError("Empty response payload from server"))
                }
            } else {
                val error = SafeWalkErrorMapper.mapHttpError(response.code(), response.errorBody())
                Result.failure(error)
            }
        } catch (t: Throwable) {
            Result.failure(SafeWalkErrorMapper.mapThrowable(t))
        }
    }

    suspend fun login(
        mobileNumber: String,
        password: String
    ): Result<AuthResponseDto> {
        return try {
            val request = LoginRequestDto(
                mobileNumber = mobileNumber,
                password = password
            )
            val response = apiService.login(request)
            if (response.isSuccessful) {
                val authData = response.body()?.data
                if (authData != null) {
                    Result.success(authData)
                } else {
                    Result.failure(SafeWalkException.ServerError("Empty response payload from server"))
                }
            } else {
                val error = SafeWalkErrorMapper.mapHttpError(response.code(), response.errorBody())
                Result.failure(error)
            }
        } catch (t: Throwable) {
            Result.failure(SafeWalkErrorMapper.mapThrowable(t))
        }
    }

    suspend fun getCurrentUser(): Result<UserResponseDto> {
        return try {
            val response = apiService.getCurrentUser()
            if (response.isSuccessful) {
                val userData = response.body()?.data
                if (userData != null) {
                    Result.success(userData)
                } else {
                    Result.failure(SafeWalkException.ServerError("Empty user profile in server response"))
                }
            } else {
                val error = SafeWalkErrorMapper.mapHttpError(response.code(), response.errorBody())
                Result.failure(error)
            }
        } catch (t: Throwable) {
            Result.failure(SafeWalkErrorMapper.mapThrowable(t))
        }
    }

    suspend fun uploadVerificationDocument(
        fileBytes: ByteArray,
        fileName: String,
        mimeType: String = "application/pdf",
        title: String? = null
    ): Result<VerificationDocumentResponseDto> {
        return try {
            val mediaType = mimeType.toMediaTypeOrNull() ?: "application/octet-stream".toMediaTypeOrNull()
            val requestBody = fileBytes.toRequestBody(mediaType)
            val filePart = MultipartBody.Part.createFormData("file", fileName, requestBody)
            val titlePart = title?.toRequestBody("text/plain".toMediaTypeOrNull())

            val response = apiService.uploadVerificationDocument(filePart, titlePart)
            if (response.isSuccessful) {
                val doc = response.body()?.data
                if (doc != null) {
                    Result.success(doc)
                } else {
                    Result.failure(SafeWalkException.ServerError("Empty document payload in server response"))
                }
            } else {
                val error = SafeWalkErrorMapper.mapHttpError(response.code(), response.errorBody())
                Result.failure(error)
            }
        } catch (t: Throwable) {
            Result.failure(SafeWalkErrorMapper.mapThrowable(t))
        }
    }

    suspend fun getVerificationDocuments(): Result<List<VerificationDocumentResponseDto>> {
        return try {
            val response = apiService.getVerificationDocuments()
            if (response.isSuccessful) {
                val docs = response.body()?.data ?: emptyList()
                Result.success(docs)
            } else {
                val error = SafeWalkErrorMapper.mapHttpError(response.code(), response.errorBody())
                Result.failure(error)
            }
        } catch (t: Throwable) {
            Result.failure(SafeWalkErrorMapper.mapThrowable(t))
        }
    }

    suspend fun uploadAvatar(
        fileBytes: ByteArray,
        fileName: String,
        mimeType: String = "image/jpeg"
    ): Result<UserResponseDto> {
        return try {
            val mediaType = mimeType.toMediaTypeOrNull() ?: "image/jpeg".toMediaTypeOrNull()
            val requestBody = fileBytes.toRequestBody(mediaType)
            val filePart = MultipartBody.Part.createFormData("file", fileName, requestBody)

            val response = apiService.uploadAvatar(filePart)
            if (response.isSuccessful) {
                val user = response.body()?.data
                if (user != null) {
                    Result.success(user)
                } else {
                    Result.failure(SafeWalkException.ServerError("Empty user response upon avatar upload"))
                }
            } else {
                val error = SafeWalkErrorMapper.mapHttpError(response.code(), response.errorBody())
                Result.failure(error)
            }
        } catch (t: Throwable) {
            Result.failure(SafeWalkErrorMapper.mapThrowable(t))
        }
    }

    suspend fun getEmergencyContacts(): Result<List<com.example.safewalk.data.model.dto.EmergencyContactResponseDto>> {
        return try {
            val response = apiService.getEmergencyContacts()
            if (response.isSuccessful) {
                val contacts = response.body()?.data ?: emptyList()
                Result.success(contacts)
            } else {
                val error = SafeWalkErrorMapper.mapHttpError(response.code(), response.errorBody())
                Result.failure(error)
            }
        } catch (t: Throwable) {
            Result.failure(SafeWalkErrorMapper.mapThrowable(t))
        }
    }

    suspend fun addEmergencyContact(contactIdentifier: String): Result<com.example.safewalk.data.model.dto.EmergencyContactResponseDto> {
        return try {
            val request = com.example.safewalk.data.model.dto.EmergencyContactRequestDto(
                contactIdentifier = contactIdentifier,
                contactUserId = contactIdentifier
            )
            val response = apiService.addEmergencyContact(request)
            if (response.isSuccessful) {
                val contact = response.body()?.data
                if (contact != null) {
                    Result.success(contact)
                } else {
                    Result.failure(SafeWalkException.ServerError("Empty contact payload in server response"))
                }
            } else {
                val error = SafeWalkErrorMapper.mapHttpError(response.code(), response.errorBody())
                Result.failure(error)
            }
        } catch (t: Throwable) {
            Result.failure(SafeWalkErrorMapper.mapThrowable(t))
        }
    }

    suspend fun replaceEmergencyContact(
        contactIdentifier: String,
        newContactIdentifier: String
    ): Result<com.example.safewalk.data.model.dto.EmergencyContactResponseDto> {
        return try {
            val request = com.example.safewalk.data.model.dto.EmergencyContactRequestDto(
                contactIdentifier = newContactIdentifier,
                contactUserId = newContactIdentifier
            )
            val response = apiService.replaceEmergencyContact(contactIdentifier, request)
            if (response.isSuccessful) {
                val contact = response.body()?.data
                if (contact != null) {
                    Result.success(contact)
                } else {
                    Result.failure(SafeWalkException.ServerError("Empty contact payload in server response"))
                }
            } else {
                val error = SafeWalkErrorMapper.mapHttpError(response.code(), response.errorBody())
                Result.failure(error)
            }
        } catch (t: Throwable) {
            Result.failure(SafeWalkErrorMapper.mapThrowable(t))
        }
    }

    suspend fun removeEmergencyContact(contactIdentifier: String): Result<Unit> {
        return try {
            val response = apiService.removeEmergencyContact(contactIdentifier)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                val error = SafeWalkErrorMapper.mapHttpError(response.code(), response.errorBody())
                Result.failure(error)
            }
        } catch (t: Throwable) {
            Result.failure(SafeWalkErrorMapper.mapThrowable(t))
        }
    }
}
