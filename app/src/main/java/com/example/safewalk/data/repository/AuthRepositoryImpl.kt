package com.example.safewalk.data.repository

import com.example.safewalk.data.source.mock.MockAuthDataSource
import com.example.safewalk.domain.model.User
import com.example.safewalk.domain.model.UserRole
import com.example.safewalk.domain.model.VerificationDocument
import com.example.safewalk.domain.repository.AuthRepository
import kotlinx.coroutines.flow.StateFlow

/**
 * Implementation of AuthRepository delegating to MockAuthDataSource.
 */
class AuthRepositoryImpl(
    private val dataSource: MockAuthDataSource
) : AuthRepository {

    override val currentUser: StateFlow<User?> = dataSource.currentUser

    override suspend fun login(safeWalkIdOrEmail: String, password: String): Result<User> {
        return dataSource.login(safeWalkIdOrEmail, password)
    }

    override suspend fun loginAsMockUser(user: User): Result<User> {
        return dataSource.loginAsUser(user)
    }

    override suspend fun register(
        name: String,
        email: String,
        phone: String,
        password: String,
        confirmPassword: String,
        emergencyPassword: String?
    ): Result<User> {
        return dataSource.register(name, email, phone, password, confirmPassword, emergencyPassword)
    }

    override suspend fun register(
        name: String,
        email: String,
        phone: String,
        password: String,
        confirmPassword: String,
        role: UserRole,
        emergencyPassword: String?,
        department: String?,
        badgeId: String?,
        hospitalName: String?,
        licenseId: String?,
        designation: String?
    ): Result<User> {
        return dataSource.register(
            name = name,
            email = email,
            phone = phone,
            password = password,
            confirmPassword = confirmPassword,
            role = role,
            emergencyPassword = emergencyPassword,
            department = department,
            badgeId = badgeId,
            hospitalName = hospitalName,
            licenseId = licenseId,
            designation = designation
        )
    }

    override suspend fun register(user: User, password: String): Result<User> {
        return dataSource.register(user, password)
    }

    override suspend fun uploadVerificationDocument(userId: String, document: VerificationDocument): Result<User> {
        return dataSource.uploadVerificationDocument(userId, document)
    }

    override suspend fun approveVerification(userId: String): Result<User> {
        return dataSource.approveVerification(userId)
    }

    override suspend fun rejectVerification(userId: String): Result<User> {
        return dataSource.rejectVerification(userId)
    }

    override suspend fun addEmergencyContact(userId: String, contactSafeWalkId: String): Result<User> {
        return dataSource.addEmergencyContact(userId, contactSafeWalkId)
    }

    override suspend fun replaceEmergencyContact(
        userId: String,
        oldContactSafeWalkId: String,
        newContactSafeWalkId: String
    ): Result<User> {
        return dataSource.replaceEmergencyContact(userId, oldContactSafeWalkId, newContactSafeWalkId)
    }

    override suspend fun removeEmergencyContact(userId: String, contactSafeWalkId: String): Result<User> {
        return dataSource.removeEmergencyContact(userId, contactSafeWalkId)
    }

    override suspend fun uploadAvatar(
        userId: String,
        fileBytes: ByteArray,
        fileName: String,
        mimeType: String
    ): Result<User> {
        return dataSource.uploadAvatar(userId, "mock_avatar_${System.currentTimeMillis()}.jpg")
    }

    override suspend fun logout() {
        dataSource.logout()
    }

    override fun getAvailableMockUsers(): List<User> {
        return dataSource.getAvailableUsers()
    }

    override fun isAuthenticated(): Boolean {
        return dataSource.isAuthenticated()
    }
}

typealias MockAuthRepository = AuthRepositoryImpl

