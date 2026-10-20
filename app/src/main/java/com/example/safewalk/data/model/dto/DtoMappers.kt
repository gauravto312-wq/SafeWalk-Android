package com.example.safewalk.data.model.dto

import com.example.safewalk.domain.model.Emergency
import com.example.safewalk.domain.model.EmergencyStatus
import com.example.safewalk.domain.model.Location
import com.example.safewalk.domain.model.LocationStatus
import com.example.safewalk.domain.model.User
import com.example.safewalk.domain.model.UserRole
import com.example.safewalk.domain.model.VerificationDocument
import com.example.safewalk.domain.model.VerificationStatus
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Pure mappers converting between Backend REST DTOs and Android Domain Models.
 * Ensures the Presentation and ViewModel layers remain completely decoupled from network schemas.
 */

fun UserResponseDto.toDomain(): User {
    val mappedRole = try {
        UserRole.valueOf(role)
    } catch (e: Exception) {
        UserRole.USER
    }

    val isAccVerified = verificationStatus?.equals("VERIFIED", ignoreCase = true) == true ||
            (verificationStatus.isNullOrBlank() && policeVerified)
    val mappedVerificationStatus = when {
        mappedRole == UserRole.USER -> VerificationStatus.NONE
        !verificationStatus.isNullOrBlank() -> VerificationStatus.fromString(verificationStatus)
        isAccVerified -> VerificationStatus.VERIFIED
        else -> VerificationStatus.PENDING
    }

    return User(
        id = id.toString(),
        safeWalkId = "SW-$id",
        name = name,
        phone = mobileNumber,
        email = email?.trim() ?: "",
        role = mappedRole,
        isVerified = isAccVerified,
        status = accountStatus,
        avatarUrl = avatarUrl,
        verificationStatus = mappedVerificationStatus,
        badgeId = badgeNumber,
        department = department ?: policeStation,
        rejectionReason = rejectionReason
    )
}

fun VerificationDocumentResponseDto.toDomain(): VerificationDocument {
    return VerificationDocument(
        id = id.toString(),
        title = title ?: fileName,
        fileName = fileName,
        uploadedAt = try {
            uploadedAt?.let {
                LocalDateTime.parse(it).atZone(ZoneOffset.UTC).toInstant().toEpochMilli()
            } ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    )
}

fun EmergencyResponseDto.toDomain(
    requester: User? = null,
    acceptedResponders: List<User> = emptyList(),
    arrivedResponder: User? = null
): Emergency {
    val mappedStatus = try {
        EmergencyStatus.valueOf(status)
    } catch (e: Exception) {
        EmergencyStatus.ACTIVE
    }

    val originLocation = Location(
        latitude = latitude ?: 0.0,
        longitude = longitude ?: 0.0,
        address = address ?: "Emergency Location",
        status = if (mappedStatus.isTerminal) LocationStatus.STOPPED else LocationStatus.ACTIVE
    )

    val reqUser = requester ?: User(
        id = requesterId.toString(),
        safeWalkId = "SW-$requesterId",
        name = requesterName ?: "Citizen User",
        phone = requesterPhone ?: "",
        email = "",
        role = UserRole.USER
    )

    val allResponders = if (acceptedResponders.isNotEmpty()) {
        acceptedResponders
    } else {
        responders.map { it.toDomain() }
    }

    val parsedCreatedAt = try {
        createdAt?.let {
            if (it.all { c -> c.isDigit() }) {
                it.toLong()
            } else {
                LocalDateTime.parse(it).atZone(ZoneOffset.UTC).toInstant().toEpochMilli()
            }
        } ?: System.currentTimeMillis()
    } catch (e: Exception) {
        System.currentTimeMillis()
    }

    val parsedResolvedAt = try {
        resolvedAt?.let {
            if (it.all { c -> c.isDigit() }) {
                it.toLong()
            } else {
                LocalDateTime.parse(it).atZone(ZoneOffset.UTC).toInstant().toEpochMilli()
            }
        }
    } catch (e: Exception) {
        null
    }

    val parsedExpiredAt = try {
        expiredAt?.let {
            if (it.all { c -> c.isDigit() }) {
                it.toLong()
            } else {
                LocalDateTime.parse(it).atZone(ZoneOffset.UTC).toInstant().toEpochMilli()
            }
        }
    } catch (e: Exception) {
        null
    }

    val resolvedArrivedResponder = arrivedResponder ?: (
        if (arrivedResponderId != null) {
            allResponders.firstOrNull { it.id == arrivedResponderId.toString() } ?: User(
                id = arrivedResponderId.toString(),
                safeWalkId = "SW-$arrivedResponderId",
                name = arrivedResponderName ?: "Responder",
                phone = "",
                email = "",
                role = UserRole.USER
            )
        } else null
    )

    return Emergency(
        id = "EMG-$id",
        requester = reqUser,
        status = mappedStatus,
        createdAt = parsedCreatedAt,
        resolvedAt = parsedResolvedAt,
        expiredAt = parsedExpiredAt,
        requesterLocation = originLocation,
        acceptedResponders = allResponders,
        arrivedResponder = resolvedArrivedResponder,
        arrivedResponders = if (resolvedArrivedResponder != null) listOf(resolvedArrivedResponder) else emptyList(),
        requesterConfirmedResolution = requesterResolveConfirmed,
        responderConfirmedResolution = responderResolveConfirmed,
        distanceMeters = distanceMeters,
        isTrustedContactViewer = trustedContactViewer
    )
}

fun NearbyUserResponseDto.toDomainUser(): User {
    val mappedRole = try {
        UserRole.valueOf(role)
    } catch (e: Exception) {
        UserRole.USER
    }

    return User(
        id = userId.toString(),
        safeWalkId = "SW-$userId",
        name = name,
        phone = mobileNumber,
        email = "",
        role = mappedRole,
        isVerified = policeVerified,
        status = accountStatus,
        verificationStatus = if (policeVerified) VerificationStatus.APPROVED else VerificationStatus.NONE
    )
}

fun Emergency.toResponseDto(): EmergencyResponseDto {
    return EmergencyResponseDto(
        id = id.removePrefix("EMG-").toLongOrNull() ?: 1L,
        requesterId = requester.id.removePrefix("user-").toLongOrNull() ?: 1L,
        requesterName = requester.name,
        requesterPhone = requester.phone,
        status = status.name,
        latitude = requesterLocation.latitude,
        longitude = requesterLocation.longitude,
        address = requesterLocation.address,
        createdAt = formattedTime,
        requesterResolveConfirmed = requesterConfirmedResolution,
        responderResolveConfirmed = responderConfirmedResolution
    )
}
