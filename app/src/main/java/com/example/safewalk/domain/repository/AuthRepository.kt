package com.example.safewalk.domain.repository

import com.example.safewalk.domain.model.User
import com.example.safewalk.domain.model.UserRole
import com.example.safewalk.domain.model.VerificationDocument
import kotlinx.coroutines.flow.StateFlow

/**
 * Domain repository contract for Authentication, Registration, Verification, and Session Management.
 */
interface AuthRepository {
    val currentUser: StateFlow<User?>

    suspend fun login(safeWalkIdOrEmail: String, password: String): Result<User>
    suspend fun loginAsMockUser(user: User): Result<User>
    suspend fun register(
        name: String,
        email: String,
        phone: String,
        password: String,
        confirmPassword: String,
        emergencyPassword: String? = null
    ): Result<User>
    suspend fun register(
        name: String,
        email: String,
        phone: String,
        password: String,
        confirmPassword: String,
        role: UserRole,
        emergencyPassword: String? = null,
        department: String? = null,
        badgeId: String? = null,
        hospitalName: String? = null,
        licenseId: String? = null,
        designation: String? = null
    ): Result<User>
    suspend fun register(user: User, password: String): Result<User>
    suspend fun uploadVerificationDocument(userId: String, document: VerificationDocument): Result<User>
    suspend fun approveVerification(userId: String): Result<User>
    suspend fun rejectVerification(userId: String): Result<User>
    suspend fun addEmergencyContact(userId: String, contactSafeWalkId: String): Result<User>
    suspend fun replaceEmergencyContact(userId: String, oldContactSafeWalkId: String, newContactSafeWalkId: String): Result<User>
    suspend fun removeEmergencyContact(userId: String, contactSafeWalkId: String): Result<User>
    suspend fun uploadAvatar(userId: String, fileBytes: ByteArray, fileName: String, mimeType: String): Result<User>
    suspend fun logout()
    suspend fun restoreSession(): Result<User?> = Result.success(currentUser.value)
    fun getAvailableMockUsers(): List<User>
    fun isAuthenticated(): Boolean
}

