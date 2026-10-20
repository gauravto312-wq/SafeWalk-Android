package com.example.safewalk.data.repository

import android.util.Log
import com.example.safewalk.core.error.SafeWalkException
import com.example.safewalk.data.model.dto.toDomain
import com.example.safewalk.data.session.SessionManager
import com.example.safewalk.data.source.remote.RemoteAuthDataSource
import com.example.safewalk.domain.model.User
import com.example.safewalk.domain.model.UserRole
import com.example.safewalk.domain.model.VerificationDocument
import com.example.safewalk.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Remote implementation of AuthRepository backed by the real Spring Boot backend.
 * Handles user registration, login, JWT token persistence, session restoration, and logout.
 * Optionally supports mockFallbackRepository for offline personas and unit testing.
 */
class RemoteAuthRepository(
    private val remoteDataSource: RemoteAuthDataSource,
    private val sessionManager: SessionManager,
    private val mockFallbackRepository: AuthRepository? = null
) : AuthRepository {

    companion object {
        private const val TAG = "RemoteAuthRepo"
    }

    private val _currentUser = MutableStateFlow<User?>(sessionManager.getUser())
    override val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    init {
        // Sync session state on initialization
        val cached = sessionManager.getUser()
        if (cached != null && sessionManager.hasActiveSession()) {
            _currentUser.value = cached
        }
    }

    /**
     * Cleans phone numbers by stripping whitespace, dashes, and parentheses while preserving country code prefix.
     */
    private fun cleanMobileNumber(raw: String): String {
        return raw.trim().replace(" ", "").replace("-", "").replace("(", "").replace(")", "")
    }

    override suspend fun login(safeWalkIdOrEmail: String, password: String): Result<User> {
        val input = safeWalkIdOrEmail.trim()
        val cleaned = cleanMobileNumber(input)

        // Attempt remote login with the cleaned mobile number
        var remoteResult = remoteDataSource.login(
            mobileNumber = cleaned,
            password = password
        )

        // If remote login failed with 401 AuthError, check alternate E.164 / 10-digit formats
        if (remoteResult.isFailure && remoteResult.exceptionOrNull() is SafeWalkException.AuthError) {
            val alternateMobile = when {
                !cleaned.startsWith("+") && cleaned.matches("^\\d{10}$".toRegex()) -> "+91$cleaned"
                cleaned.startsWith("+91") && cleaned.length == 13 -> cleaned.substring(3)
                else -> null
            }
            if (alternateMobile != null) {
                val retryResult = remoteDataSource.login(
                    mobileNumber = alternateMobile,
                    password = password
                )
                if (retryResult.isSuccess) {
                    remoteResult = retryResult
                }
            }
        }

        return if (remoteResult.isSuccess) {
            val authResponse = remoteResult.getOrThrow()
            var domainUser = authResponse.user?.toDomain() ?: User(
                id = "1",
                safeWalkId = "SW-$cleaned",
                name = cleaned,
                phone = cleaned,
                email = "",
                role = UserRole.USER
            )

            sessionManager.saveSession(authResponse.token, domainUser)

            if (domainUser.role.isAuthority) {
                val docsResult = remoteDataSource.getVerificationDocuments()
                if (docsResult.isSuccess) {
                    val docs = docsResult.getOrThrow().map { it.toDomain() }
                    domainUser = domainUser.copy(documents = docs)
                    sessionManager.updateCurrentUser(domainUser)
                }
            }

            _currentUser.value = domainUser
            try {
                com.example.safewalk.core.common.ServiceLocator.webSocketClient?.connect()
            } catch (_: Exception) {}
            try {
                com.example.safewalk.core.notification.FcmTokenManager.fetchAndSyncToken()
            } catch (_: Exception) {}
            Result.success(domainUser)
        } else {
            // Real authentication must NEVER silently fall back to mock authentication on failures
            val exception = remoteResult.exceptionOrNull()
            Result.failure(exception ?: SafeWalkException.AuthError())
        }
    }

    override suspend fun loginAsMockUser(user: User): Result<User> {
        if (mockFallbackRepository != null) {
            val result = mockFallbackRepository.loginAsMockUser(user)
            result.onSuccess {
                _currentUser.value = it
                sessionManager.saveSession("mock_persona_token_${user.id}", it)
            }
            return result
        }
        _currentUser.value = user
        sessionManager.saveSession("mock_token_${user.id}", user)
        return Result.success(user)
    }

    override suspend fun register(
        name: String,
        email: String,
        phone: String,
        password: String,
        confirmPassword: String,
        emergencyPassword: String?
    ): Result<User> {
        return register(
            name = name,
            email = email,
            phone = phone,
            password = password,
            confirmPassword = confirmPassword,
            role = UserRole.USER,
            emergencyPassword = emergencyPassword
        )
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
        if (password != confirmPassword) {
            return Result.failure(SafeWalkException.ValidationError("Passwords do not match."))
        }
        if (password.length < 6) {
            return Result.failure(SafeWalkException.ValidationError("Password must be at least 6 characters long."))
        }

        if (role == UserRole.ADMIN) {
            return Result.failure(
                SafeWalkException.ValidationError("ADMIN registration is not permitted via mobile app.")
            )
        }

        val cleanedMobile = cleanMobileNumber(phone)

        val remoteResult = remoteDataSource.register(
            name = name.trim(),
            mobileNumber = cleanedMobile,
            password = password,
            email = email.trim().ifBlank { null },
            emergencyPassword = emergencyPassword?.trim()?.ifBlank { null },
            role = role.name,
            badgeNumber = badgeId?.trim()?.ifBlank { null },
            department = (department ?: hospitalName)?.trim()?.ifBlank { null },
            policeStation = department?.trim()?.ifBlank { null }
        )

        return if (remoteResult.isSuccess) {
            val authResponse = remoteResult.getOrThrow()
            val domainUser = authResponse.user?.toDomain()?.copy(
                email = authResponse.user.email?.ifBlank { email.trim() } ?: email.trim(),
                department = department,
                badgeId = badgeId,
                hospitalName = hospitalName,
                licenseId = licenseId,
                designation = designation,
                emergencyPassword = emergencyPassword
            ) ?: User(
                id = "1",
                safeWalkId = "SW-$cleanedMobile",
                name = name.trim(),
                phone = cleanedMobile,
                email = email.trim(),
                role = role,
                department = department,
                badgeId = badgeId,
                hospitalName = hospitalName,
                licenseId = licenseId,
                designation = designation,
                emergencyPassword = emergencyPassword
            )

            sessionManager.saveSession(authResponse.token, domainUser)
            _currentUser.value = domainUser
            try {
                com.example.safewalk.core.common.ServiceLocator.webSocketClient?.connect()
            } catch (_: Exception) {}
            try {
                com.example.safewalk.core.notification.FcmTokenManager.fetchAndSyncToken()
            } catch (_: Exception) {}
            Result.success(domainUser)
        } else {
            val exception = remoteResult.exceptionOrNull()
            Result.failure(exception ?: SafeWalkException.ValidationError("Registration failed."))
        }
    }

    override suspend fun register(user: User, password: String): Result<User> {
        return register(
            name = user.name,
            email = user.email,
            phone = user.phone,
            password = password,
            confirmPassword = password,
            role = user.role,
            emergencyPassword = user.emergencyPassword,
            department = user.department,
            badgeId = user.badgeId,
            hospitalName = user.hospitalName,
            licenseId = user.licenseId,
            designation = user.designation
        )
    }

    /**
     * Restores session on app startup. Validates token against backend /api/v1/users/me.
     * Clears session if 401 Unauthorized is returned.
     */
    override suspend fun restoreSession(): Result<User?> {
        if (!sessionManager.hasActiveSession()) {
            _currentUser.value = null
            return Result.success(null)
        }

        val cachedUser = sessionManager.getUser()
        _currentUser.value = cachedUser

        // Verify token with backend
        val token = sessionManager.getToken()
        if (token != null && !token.startsWith("mock_")) {
            val result = remoteDataSource.getCurrentUser()
            if (result.isSuccess) {
                val freshUserDto = result.getOrThrow()
                var freshUser = freshUserDto.toDomain().let { domain ->
                    domain.copy(
                        email = domain.email.ifBlank { cachedUser?.email ?: "" },
                        emergencyPassword = cachedUser?.emergencyPassword
                    )
                }
                if (freshUser.role.isAuthority) {
                    val docsResult = remoteDataSource.getVerificationDocuments()
                    if (docsResult.isSuccess) {
                        val docs = docsResult.getOrThrow().map { it.toDomain() }
                        freshUser = freshUser.copy(documents = docs)
                    }
                }
                val contactsResult = remoteDataSource.getEmergencyContacts()
                if (contactsResult.isSuccess) {
                    val contactIds = contactsResult.getOrThrow().map { it.contactUniqueId }
                    if (contactIds.isNotEmpty()) {
                        freshUser = freshUser.copy(emergencyContacts = contactIds)
                    }
                }
                sessionManager.updateCurrentUser(freshUser)
                _currentUser.value = freshUser
                try {
                    com.example.safewalk.core.common.ServiceLocator.webSocketClient?.connect()
                } catch (_: Exception) {}
                try {
                    com.example.safewalk.core.notification.FcmTokenManager.fetchAndSyncToken()
                } catch (_: Exception) {}
                return Result.success(freshUser)
            } else {
                val ex = result.exceptionOrNull()
                if (ex is SafeWalkException.AuthError) {
                    Log.w(TAG, "Stored session token is invalid or expired. Logging out.")
                    logout()
                    return Result.failure(ex)
                }
            }
        }

        if (cachedUser != null) {
            try {
                com.example.safewalk.core.common.ServiceLocator.webSocketClient?.connect()
            } catch (_: Exception) {}
            try {
                com.example.safewalk.core.notification.FcmTokenManager.fetchAndSyncToken()
            } catch (_: Exception) {}
        }
        return Result.success(cachedUser)
    }

    override suspend fun uploadVerificationDocument(userId: String, document: VerificationDocument): Result<User> {
        val current = _currentUser.value ?: return Result.failure(SafeWalkException.AuthError())
        val token = sessionManager.getToken()

        val finalDoc = if (token != null && !token.startsWith("mock_")) {
            val pdfContent = "%PDF-1.4\nSafeWalk Verification Document: ${document.title}\n%%EOF".toByteArray()
            val fileName = if (document.fileName.endsWith(".pdf", ignoreCase = true) ||
                document.fileName.endsWith(".jpg", ignoreCase = true) ||
                document.fileName.endsWith(".jpeg", ignoreCase = true) ||
                document.fileName.endsWith(".png", ignoreCase = true)
            ) {
                document.fileName
            } else {
                "${document.fileName}.pdf"
            }
            val uploadResult = remoteDataSource.uploadVerificationDocument(
                fileBytes = pdfContent,
                fileName = fileName,
                mimeType = "application/pdf",
                title = document.title
            )
            if (uploadResult.isSuccess) {
                uploadResult.getOrThrow().toDomain()
            } else {
                return Result.failure(uploadResult.exceptionOrNull() ?: SafeWalkException.ServerError("Document upload failed"))
            }
        } else {
            document
        }

        val updatedDocs = current.documents + finalDoc
        val newStatus = if (current.role.isAuthority && (current.verificationStatus == com.example.safewalk.domain.model.VerificationStatus.REJECTED || current.verificationStatus == com.example.safewalk.domain.model.VerificationStatus.NONE)) {
            com.example.safewalk.domain.model.VerificationStatus.PENDING
        } else {
            current.verificationStatus
        }
        val updatedUser = current.copy(
            documents = updatedDocs,
            verificationStatus = newStatus,
            isVerified = if (newStatus == com.example.safewalk.domain.model.VerificationStatus.PENDING) false else current.isVerified,
            rejectionReason = if (newStatus == com.example.safewalk.domain.model.VerificationStatus.PENDING) null else current.rejectionReason
        )
        sessionManager.updateCurrentUser(updatedUser)
        _currentUser.value = updatedUser
        return Result.success(updatedUser)
    }

    suspend fun uploadVerificationDocument(
        title: String,
        fileName: String,
        fileBytes: ByteArray,
        mimeType: String
    ): Result<User> {
        val current = _currentUser.value ?: return Result.failure(SafeWalkException.AuthError())
        val token = sessionManager.getToken()

        val finalDoc = if (token != null && !token.startsWith("mock_")) {
            val uploadResult = remoteDataSource.uploadVerificationDocument(
                fileBytes = fileBytes,
                fileName = fileName,
                mimeType = mimeType,
                title = title
            )
            if (uploadResult.isSuccess) {
                uploadResult.getOrThrow().toDomain()
            } else {
                return Result.failure(uploadResult.exceptionOrNull() ?: SafeWalkException.ServerError("Document upload failed"))
            }
        } else {
            VerificationDocument(
                id = "DOC-${java.util.UUID.randomUUID().toString().take(6).uppercase()}",
                title = title,
                fileName = fileName
            )
        }

        val updatedDocs = current.documents + finalDoc
        val newStatus = if (current.role.isAuthority && (current.verificationStatus == com.example.safewalk.domain.model.VerificationStatus.REJECTED || current.verificationStatus == com.example.safewalk.domain.model.VerificationStatus.NONE)) {
            com.example.safewalk.domain.model.VerificationStatus.PENDING
        } else {
            current.verificationStatus
        }
        val updatedUser = current.copy(
            documents = updatedDocs,
            verificationStatus = newStatus,
            isVerified = if (newStatus == com.example.safewalk.domain.model.VerificationStatus.PENDING) false else current.isVerified,
            rejectionReason = if (newStatus == com.example.safewalk.domain.model.VerificationStatus.PENDING) null else current.rejectionReason
        )
        sessionManager.updateCurrentUser(updatedUser)
        _currentUser.value = updatedUser
        return Result.success(updatedUser)
    }

    override suspend fun approveVerification(userId: String): Result<User> {
        val token = sessionManager.getToken()
        if (token != null && !token.startsWith("mock_")) {
            return Result.failure(
                SafeWalkException.ValidationError("Direct client approval is not permitted for live accounts. Admin approval is required via the backend.")
            )
        }
        val current = _currentUser.value ?: return Result.failure(SafeWalkException.AuthError())
        val updated = current.copy(
            isVerified = true,
            verificationStatus = com.example.safewalk.domain.model.VerificationStatus.VERIFIED
        )
        sessionManager.updateCurrentUser(updated)
        _currentUser.value = updated
        return Result.success(updated)
    }

    override suspend fun rejectVerification(userId: String): Result<User> {
        val token = sessionManager.getToken()
        if (token != null && !token.startsWith("mock_")) {
            return Result.failure(
                SafeWalkException.ValidationError("Direct client rejection is not permitted for live accounts. Admin action is required via the backend.")
            )
        }
        val current = _currentUser.value ?: return Result.failure(SafeWalkException.AuthError())
        val updated = current.copy(
            isVerified = false,
            verificationStatus = com.example.safewalk.domain.model.VerificationStatus.REJECTED
        )
        sessionManager.updateCurrentUser(updated)
        _currentUser.value = updated
        return Result.success(updated)
    }

    override suspend fun addEmergencyContact(userId: String, contactSafeWalkId: String): Result<User> {
        val current = _currentUser.value ?: return Result.failure(SafeWalkException.AuthError())
        if (current.emergencyContacts.size >= 3) {
            return Result.failure(SafeWalkException.ValidationError("Maximum 3 emergency contacts allowed."))
        }
        val token = sessionManager.getToken()
        if (token != null && !token.startsWith("mock_")) {
            val remoteRes = remoteDataSource.addEmergencyContact(contactSafeWalkId)
            if (remoteRes.isFailure) {
                return Result.failure(remoteRes.exceptionOrNull() ?: SafeWalkException.ServerError("Failed to add contact"))
            }
        }
        val updated = current.copy(emergencyContacts = current.emergencyContacts + contactSafeWalkId)
        sessionManager.updateCurrentUser(updated)
        _currentUser.value = updated
        return Result.success(updated)
    }

    override suspend fun replaceEmergencyContact(
        userId: String,
        oldContactSafeWalkId: String,
        newContactSafeWalkId: String
    ): Result<User> {
        val current = _currentUser.value ?: return Result.failure(SafeWalkException.AuthError())
        val token = sessionManager.getToken()
        if (token != null && !token.startsWith("mock_")) {
            val remoteRes = remoteDataSource.replaceEmergencyContact(oldContactSafeWalkId, newContactSafeWalkId)
            if (remoteRes.isFailure) {
                return Result.failure(remoteRes.exceptionOrNull() ?: SafeWalkException.ServerError("Failed to replace contact"))
            }
        }
        val updatedContacts = current.emergencyContacts.map {
            if (it.equals(oldContactSafeWalkId, ignoreCase = true)) newContactSafeWalkId else it
        }
        val updated = current.copy(emergencyContacts = updatedContacts)
        sessionManager.updateCurrentUser(updated)
        _currentUser.value = updated
        return Result.success(updated)
    }

    override suspend fun removeEmergencyContact(userId: String, contactSafeWalkId: String): Result<User> {
        val current = _currentUser.value ?: return Result.failure(SafeWalkException.AuthError())
        val token = sessionManager.getToken()
        if (token != null && !token.startsWith("mock_")) {
            val remoteRes = remoteDataSource.removeEmergencyContact(contactSafeWalkId)
            if (remoteRes.isFailure) {
                return Result.failure(remoteRes.exceptionOrNull() ?: SafeWalkException.ServerError("Failed to remove contact"))
            }
        }
        val updated = current.copy(emergencyContacts = current.emergencyContacts.filter { !it.equals(contactSafeWalkId, ignoreCase = true) })
        sessionManager.updateCurrentUser(updated)
        _currentUser.value = updated
        return Result.success(updated)
    }

    override suspend fun uploadAvatar(
        userId: String,
        fileBytes: ByteArray,
        fileName: String,
        mimeType: String
    ): Result<User> {
        val current = _currentUser.value ?: return Result.failure(SafeWalkException.AuthError())
        val token = sessionManager.getToken()
        val avatarUrl = if (token != null && !token.startsWith("mock_")) {
            val uploadRes = remoteDataSource.uploadAvatar(fileBytes, fileName, mimeType)
            if (uploadRes.isFailure) {
                return Result.failure(uploadRes.exceptionOrNull() ?: SafeWalkException.ServerError("Failed to upload avatar"))
            }
            uploadRes.getOrNull()?.avatarUrl ?: "/api/v1/users/me/avatar"
        } else {
            "mock_avatar_${System.currentTimeMillis()}.jpg"
        }
        val updated = current.copy(avatarUrl = avatarUrl)
        sessionManager.updateCurrentUser(updated)
        _currentUser.value = updated
        return Result.success(updated)
    }

    override suspend fun logout() {
        try {
            com.example.safewalk.core.notification.FcmTokenManager.unregisterTokenFromBackendSuspend()
        } catch (_: Exception) {}
        sessionManager.clearSession()
        _currentUser.value = null
        try {
            com.example.safewalk.core.common.ServiceLocator.clearUserStateOnLogout()
        } catch (_: Exception) {}
        mockFallbackRepository?.logout()
    }

    override fun getAvailableMockUsers(): List<User> {
        return mockFallbackRepository?.getAvailableMockUsers() ?: emptyList()
    }

    override fun isAuthenticated(): Boolean {
        return sessionManager.hasActiveSession()
    }
}
