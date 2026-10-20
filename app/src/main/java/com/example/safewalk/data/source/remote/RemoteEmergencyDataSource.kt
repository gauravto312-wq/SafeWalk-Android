package com.example.safewalk.data.source.remote

import com.example.safewalk.core.error.SafeWalkErrorMapper
import com.example.safewalk.core.error.SafeWalkException
import com.example.safewalk.data.model.dto.CancelEmergencyRequestDto
import com.example.safewalk.data.model.dto.CreateEmergencyRequestDto
import com.example.safewalk.data.model.dto.EmergencyLocationResponseDto
import com.example.safewalk.data.model.dto.EmergencyResponseDto
import com.example.safewalk.data.remote.EmergencyApiService

/**
 * Remote Data Source handling HTTP network calls for emergency operations
 * to the SafeWalk Spring Boot backend.
 */
class RemoteEmergencyDataSource(
    private val apiService: EmergencyApiService
) {

    suspend fun createEmergency(
        type: String = "GENERAL",
        latitude: Double,
        longitude: Double,
        address: String? = null,
        description: String? = null
    ): Result<EmergencyResponseDto> {
        return try {
            val request = CreateEmergencyRequestDto(
                type = type,
                latitude = latitude,
                longitude = longitude,
                address = address,
                description = description
            )
            val response = apiService.createEmergency(request)
            if (response.isSuccessful) {
                val data = response.body()?.data
                if (data != null) {
                    Result.success(data)
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

    suspend fun getActiveEmergencies(status: String? = null): Result<List<EmergencyResponseDto>> {
        return try {
            val response = apiService.getActiveEmergencies(status)
            if (response.isSuccessful) {
                val data = response.body()?.data ?: emptyList()
                Result.success(data)
            } else {
                val error = SafeWalkErrorMapper.mapHttpError(response.code(), response.errorBody())
                Result.failure(error)
            }
        } catch (t: Throwable) {
            Result.failure(SafeWalkErrorMapper.mapThrowable(t))
        }
    }

    suspend fun getEmergencyById(id: Long): Result<EmergencyResponseDto> {
        return try {
            val response = apiService.getEmergencyById(id)
            if (response.isSuccessful) {
                val data = response.body()?.data
                if (data != null) {
                    Result.success(data)
                } else {
                    Result.failure(SafeWalkException.NotFoundError("Emergency request not found"))
                }
            } else {
                val error = SafeWalkErrorMapper.mapHttpError(response.code(), response.errorBody())
                Result.failure(error)
            }
        } catch (t: Throwable) {
            Result.failure(SafeWalkErrorMapper.mapThrowable(t))
        }
    }

    suspend fun getMyActiveEmergency(): Result<EmergencyResponseDto> {
        return try {
            val response = apiService.getMyActiveEmergency()
            if (response.isSuccessful) {
                val data = response.body()?.data
                if (data != null) {
                    Result.success(data)
                } else {
                    Result.failure(SafeWalkException.NotFoundError("No active emergency found"))
                }
            } else {
                val error = SafeWalkErrorMapper.mapHttpError(response.code(), response.errorBody())
                Result.failure(error)
            }
        } catch (t: Throwable) {
            Result.failure(SafeWalkErrorMapper.mapThrowable(t))
        }
    }

    suspend fun getMyEmergencyHistory(page: Int = 0, size: Int = 50): Result<List<EmergencyResponseDto>> {
        return try {
            val response = apiService.getMyEmergencyHistory(page, size)
            if (response.isSuccessful) {
                val data = response.body()?.data?.content ?: emptyList()
                Result.success(data)
            } else {
                val error = SafeWalkErrorMapper.mapHttpError(response.code(), response.errorBody())
                Result.failure(error)
            }
        } catch (t: Throwable) {
            Result.failure(SafeWalkErrorMapper.mapThrowable(t))
        }
    }

    suspend fun acceptEmergency(id: Long): Result<EmergencyResponseDto> {
        return try {
            val response = apiService.acceptEmergency(id)
            if (response.isSuccessful) {
                val data = response.body()?.data
                if (data != null) {
                    Result.success(data)
                } else {
                    Result.failure(SafeWalkException.ServerError("Empty emergency response from server"))
                }
            } else {
                val error = SafeWalkErrorMapper.mapHttpError(response.code(), response.errorBody())
                Result.failure(error)
            }
        } catch (t: Throwable) {
            Result.failure(SafeWalkErrorMapper.mapThrowable(t))
        }
    }

    suspend fun arriveEmergency(id: Long): Result<EmergencyResponseDto> {
        return try {
            val response = apiService.arriveEmergency(id)
            if (response.isSuccessful) {
                val data = response.body()?.data
                if (data != null) {
                    Result.success(data)
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

    suspend fun rejectEmergency(id: Long): Result<EmergencyResponseDto> {
        return try {
            val response = apiService.rejectEmergency(id)
            if (response.isSuccessful) {
                val data = response.body()?.data
                if (data != null) {
                    Result.success(data)
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

    suspend fun cancelEmergency(id: Long, password: String, reason: String? = null): Result<EmergencyResponseDto> {
        return try {
            val request = CancelEmergencyRequestDto(
                emergencyPassword = password,
                reason = reason
            )
            val response = apiService.cancelEmergency(id, request)
            if (response.isSuccessful) {
                val data = response.body()?.data
                if (data != null) {
                    Result.success(data)
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

    suspend fun resolveEmergency(id: Long): Result<EmergencyResponseDto> {
        return try {
            val response = apiService.resolveEmergency(id)
            if (response.isSuccessful) {
                val data = response.body()?.data
                if (data != null) {
                    Result.success(data)
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

    suspend fun getEmergencyLocations(id: Long): Result<EmergencyLocationResponseDto> {
        return try {
            val response = apiService.getEmergencyLocations(id)
            if (response.isSuccessful) {
                val data = response.body()?.data
                if (data != null) {
                    Result.success(data)
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
}
