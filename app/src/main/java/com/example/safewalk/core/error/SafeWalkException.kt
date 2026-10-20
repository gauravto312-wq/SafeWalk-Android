package com.example.safewalk.core.error

import com.example.safewalk.data.model.dto.ErrorResponseDto
import com.google.gson.Gson
import okhttp3.ResponseBody
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Centralized Domain Exception hierarchy for SafeWalk.
 * Strictly maps to SafeWalk Spring Boot backend HTTP Status Codes and Error Responses:
 * - 400 Bad Request (Validation / Invalid Operation)
 * - 401 Unauthorized (Bad Credentials / Invalid Emergency Password / Expired JWT)
 * - 403 Forbidden (IDOR / Access Denied / Account Restricted / Suspended)
 * - 404 Not Found (User / Emergency / Contact Not Found)
 * - 409 Conflict (5s Window Expired / Arrival Distance >25m / Single Arrival Guard / Duplicate Resource)
 * - 500 Server Error
 * - 0 Network / Connection Error
 */
sealed class SafeWalkException(
    message: String,
    val httpStatus: Int,
    cause: Throwable? = null
) : Exception(message, cause) {

    class ValidationError(message: String) : SafeWalkException(message, 400)
    class InvalidOperationError(message: String) : SafeWalkException(message, 400)

    class AuthError(message: String = "Invalid credentials or session expired.") : SafeWalkException(message, 401)
    class InvalidEmergencyPasswordError(message: String = "Invalid emergency password. Cancellation rejected.") : SafeWalkException(message, 401)

    class ForbiddenError(message: String = "Access denied. You are not authorized to perform this operation.") : SafeWalkException(message, 403)
    class AccountStatusError(message: String) : SafeWalkException(message, 403)

    class NotFoundError(message: String = "Requested resource not found.") : SafeWalkException(message, 404)

    class ConflictError(message: String) : SafeWalkException(message, 409)
    class CancellationExpiredError(
        message: String = "Cancellation window has expired. SOS can only be cancelled within 5 seconds of creation."
    ) : SafeWalkException(message, 409)
    class ArrivalDistanceExceededError(
        val distanceMeters: Double,
        message: String = "Responder is too far to mark arrival. Must be within 25 meters or less (current distance: ${distanceMeters.toInt()} meters)."
    ) : SafeWalkException(message, 409)
    class SingleArrivalConflictError(
        val arrivedResponderName: String,
        message: String = "Another responder ($arrivedResponderName) has already arrived at the scene."
    ) : SafeWalkException(message, 409)
    class DuplicateResourceError(message: String) : SafeWalkException(message, 409)
    class ActiveEmergencyExistsError(message: String = "You already have an active emergency request.") : SafeWalkException(message, 409)

    class ServerError(message: String = "Internal server error. Please try again.") : SafeWalkException(message, 500)
    class NetworkError(message: String = "Unable to connect to SafeWalk network. Check your connection.") : SafeWalkException(message, 0)
}

object SafeWalkErrorMapper {

    private val gson = Gson()

    fun parseErrorBody(errorBody: ResponseBody?): ErrorResponseDto? {
        if (errorBody == null) return null
        return try {
            val json = errorBody.string()
            gson.fromJson(json, ErrorResponseDto::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun mapHttpError(statusCode: Int, errorBody: ResponseBody?): SafeWalkException {
        val parsed = parseErrorBody(errorBody)

        val message = when (statusCode) {
            400 -> {
                val fieldErrors = parsed?.errors
                if (!fieldErrors.isNullOrEmpty()) {
                    fieldErrors.values.firstOrNull() ?: parsed.message ?: "Invalid request parameters"
                } else {
                    parsed?.message ?: "Invalid request parameters"
                }
            }
            401 -> parsed?.message ?: "Invalid mobile number or password."
            403 -> parsed?.message ?: "Access denied. Your account may be restricted or suspended."
            404 -> parsed?.message ?: "Requested user resource was not found."
            409 -> {
                val backendMsg = parsed?.message
                if (backendMsg != null && (backendMsg.contains("already registered", ignoreCase = true) ||
                        backendMsg.contains("duplicate", ignoreCase = true) ||
                        backendMsg.contains("mobile", ignoreCase = true))) {
                    "This mobile number is already registered across SafeWalk accounts"
                } else {
                    backendMsg ?: "This mobile number is already registered across SafeWalk accounts"
                }
            }
            500 -> "SafeWalk server error occurred. Please try again later."
            else -> parsed?.message ?: "Unexpected server error (HTTP $statusCode)."
        }

        return when (statusCode) {
            400 -> SafeWalkException.ValidationError(message)
            401 -> SafeWalkException.AuthError(message)
            403 -> SafeWalkException.ForbiddenError(message)
            404 -> SafeWalkException.NotFoundError(message)
            409 -> SafeWalkException.ConflictError(message)
            500 -> SafeWalkException.ServerError(message)
            else -> SafeWalkException.ServerError(message)
        }
    }

    fun mapThrowable(throwable: Throwable): SafeWalkException {
        return when (throwable) {
            is SafeWalkException -> throwable
            is ConnectException -> SafeWalkException.NetworkError(
                "Unable to connect to SafeWalk server. Please check backend connection on port 8080."
            )
            is SocketTimeoutException -> SafeWalkException.NetworkError(
                "Connection to SafeWalk server timed out. Please check your network connection."
            )
            is UnknownHostException -> SafeWalkException.NetworkError(
                "Cannot resolve SafeWalk server address. Please verify your network."
            )
            is IOException -> SafeWalkException.NetworkError(
                "Network communication error: ${throwable.message ?: "Connection failed"}"
            )
            else -> SafeWalkException.ServerError(
                throwable.message ?: "An unexpected error occurred"
            )
        }
    }

    /**
     * Translates an arbitrary Throwable into a clean, emergency-safe, user-friendly message.
     */
    fun toUserFriendlyMessage(throwable: Throwable): String {
        return when (throwable) {
            is SafeWalkException -> throwable.message ?: "An unexpected error occurred."
            is IllegalArgumentException -> throwable.message ?: "Invalid request."
            is IllegalStateException -> throwable.message ?: "Action conflict. Please refresh."
            is SecurityException -> "Access denied. Action not authorized."
            is NoSuchElementException -> "Resource not found."
            else -> throwable.message ?: "An unexpected error occurred. Please try again."
        }
    }
}

