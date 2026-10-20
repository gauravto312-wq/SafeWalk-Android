package com.example.safewalk.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.safewalk.core.common.ServiceLocator
import com.example.safewalk.domain.model.User
import com.example.safewalk.domain.model.UserRole
import com.example.safewalk.domain.model.VerificationDocument
import com.example.safewalk.domain.repository.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class AuthUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val isLoginSuccess: Boolean = false
)

class AuthViewModel(
    private val authRepository: AuthRepository = ServiceLocator.authRepository,
    private val externalScope: CoroutineScope? = null
) : ViewModel() {

    private val scope: CoroutineScope
        get() = externalScope ?: viewModelScope

    val currentUser: StateFlow<User?> = authRepository.currentUser
    val availableMockUsers: List<User>
        get() = authRepository.getAvailableMockUsers()

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun isAuthenticated(): Boolean = authRepository.isAuthenticated()

    fun restoreSession(onResult: (Boolean) -> Unit) {
        scope.launch {
            val result = authRepository.restoreSession()
            val authenticated = result.isSuccess && result.getOrNull() != null
            onResult(authenticated)
        }
    }

    /**
     * Refreshes current user profile and verification status from backend.
     */
    fun refreshProfile(onComplete: (() -> Unit)? = null) {
        scope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = authRepository.restoreSession()
            result.onSuccess {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    successMessage = "Profile refreshed"
                )
                onComplete?.invoke()
            }.onFailure { ex ->
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = ex.message)
                onComplete?.invoke()
            }
        }
    }

    fun loginWithMockUser(user: User, onSuccess: () -> Unit) {
        scope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = authRepository.loginAsMockUser(user)
            result.onSuccess {
                _uiState.value = _uiState.value.copy(isLoading = false, isLoginSuccess = true)
                onSuccess()
            }.onFailure { ex ->
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = ex.message)
            }
        }
    }

    fun login(identifier: String, pass: String, onSuccess: () -> Unit) {
        if (identifier.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Please enter SafeWalk ID or Email")
            return
        }
        if (pass.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Please enter password")
            return
        }
        scope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = authRepository.login(identifier, pass)
            result.onSuccess {
                _uiState.value = _uiState.value.copy(isLoading = false, isLoginSuccess = true)
                onSuccess()
            }.onFailure { ex ->
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = ex.message)
            }
        }
    }

    /**
     * Standard citizen registration (defaulting to Citizen / ROLE_USER).
     */
    fun register(
        name: String,
        email: String,
        phone: String,
        password: String,
        confirmPassword: String,
        onSuccess: () -> Unit
    ) {
        register(
            name = name,
            email = email,
            phone = phone,
            password = password,
            confirmPassword = confirmPassword,
            role = UserRole.USER,
            onSuccess = onSuccess
        )
    }

    /**
     * Phase 4 Role-aware registration for Citizen, Police, and Hospital.
     * Rejects ADMIN role explicitly.
     */
    fun register(
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
        designation: String? = null,
        onSuccess: () -> Unit
    ) {
        if (role == UserRole.ADMIN) {
            _uiState.value = _uiState.value.copy(errorMessage = "ADMIN registration is not permitted via mobile app")
            return
        }
        if (name.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Full Name is required")
            return
        }
        if (email.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Email is required")
            return
        }
        if (!email.trim().matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".toRegex())) {
            _uiState.value = _uiState.value.copy(errorMessage = "Please enter a valid email address")
            return
        }
        if (phone.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Phone Number is required")
            return
        }
        if (!phone.trim().matches("^[+]?[0-9\\s-]{7,15}$".toRegex())) {
            _uiState.value = _uiState.value.copy(errorMessage = "Please enter a valid phone number")
            return
        }
        if (password.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Password is required")
            return
        }
        if (password.length < 6) {
            _uiState.value = _uiState.value.copy(errorMessage = "Password must be at least 6 characters")
            return
        }
        if (password != confirmPassword) {
            _uiState.value = _uiState.value.copy(errorMessage = "Passwords do not match")
            return
        }

        scope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = authRepository.register(
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
            result.onSuccess {
                _uiState.value = _uiState.value.copy(isLoading = false, isLoginSuccess = true)
                onSuccess()
            }.onFailure { ex ->
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = ex.message)
            }
        }
    }

    // Overloaded register for backward compatibility with Phase 2 tests
    fun register(
        name: String,
        safeWalkId: String,
        phone: String,
        email: String,
        role: UserRole,
        onSuccess: () -> Unit
    ) {
        if (name.isBlank() || email.isBlank() || phone.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "All fields are required")
            return
        }
        val newUser = User(
            id = "user-${System.currentTimeMillis() % 10000}",
            safeWalkId = if (safeWalkId.isNotBlank()) safeWalkId else "SW-${(100..999).random()}",
            name = name,
            phone = phone,
            email = email,
            role = role,
            isVerified = role.isAuthority
        )
        scope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = authRepository.register(newUser, "dummy")
            result.onSuccess {
                _uiState.value = _uiState.value.copy(isLoading = false, isLoginSuccess = true)
                onSuccess()
            }.onFailure { ex ->
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = ex.message)
            }
        }
    }

    /**
     * Upload verification document (default quick upload).
     */
    fun uploadVerificationDocument(title: String, fileName: String, onSuccess: (() -> Unit)? = null) {
        val user = currentUser.value ?: return
        val doc = VerificationDocument(
            id = "DOC-${UUID.randomUUID().toString().take(6).uppercase()}",
            title = title,
            fileName = fileName
        )
        scope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = authRepository.uploadVerificationDocument(user.id, doc)
            result.onSuccess {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    successMessage = "Document '$fileName' uploaded successfully"
                )
                onSuccess?.invoke()
            }.onFailure { ex ->
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = ex.message)
            }
        }
    }

    /**
     * Upload custom verification document with binary file bytes.
     */
    fun uploadVerificationDocumentFile(
        title: String,
        fileName: String,
        fileBytes: ByteArray,
        mimeType: String,
        onSuccess: (() -> Unit)? = null
    ) {
        val user = currentUser.value ?: return
        if (fileBytes.size > 10 * 1024 * 1024) {
            _uiState.value = _uiState.value.copy(errorMessage = "File size exceeds 10 MB limit.")
            return
        }
        val lowerName = fileName.lowercase()
        if (!lowerName.endsWith(".pdf") && !lowerName.endsWith(".jpg") && !lowerName.endsWith(".jpeg") && !lowerName.endsWith(".png")) {
            _uiState.value = _uiState.value.copy(errorMessage = "Only PDF, JPG, JPEG, and PNG files are allowed.")
            return
        }

        scope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val repo = authRepository
            val result = if (repo is com.example.safewalk.data.repository.RemoteAuthRepository) {
                repo.uploadVerificationDocument(
                    title = title,
                    fileName = fileName,
                    fileBytes = fileBytes,
                    mimeType = mimeType
                )
            } else {
                val doc = VerificationDocument(
                    id = "DOC-${UUID.randomUUID().toString().take(6).uppercase()}",
                    title = title,
                    fileName = fileName
                )
                repo.uploadVerificationDocument(user.id, doc)
            }

            result.onSuccess {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    successMessage = "Document '$fileName' uploaded successfully"
                )
                onSuccess?.invoke()
            }.onFailure { ex ->
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = ex.message)
            }
        }
    }

    /**
     * Controlled mock verification approval mechanism for testing.
     */
    fun approveCurrentUserVerification(onSuccess: (() -> Unit)? = null) {
        val user = currentUser.value ?: return
        scope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = authRepository.approveVerification(user.id)
            result.onSuccess {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    successMessage = "Account verified successfully!"
                )
                onSuccess?.invoke()
            }.onFailure { ex ->
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = ex.message)
            }
        }
    }

    /**
     * Controlled mock verification rejection mechanism for testing.
     */
    fun rejectCurrentUserVerification(onSuccess: (() -> Unit)? = null) {
        val user = currentUser.value ?: return
        scope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = authRepository.rejectVerification(user.id)
            result.onSuccess {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    successMessage = "Account verification rejected"
                )
                onSuccess?.invoke()
            }.onFailure { ex ->
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = ex.message)
            }
        }
    }

    /**
     * Adds an emergency contact by SafeWalk User ID (up to 3 contacts max).
     */
    fun addEmergencyContact(contactSafeWalkId: String, onSuccess: (() -> Unit)? = null) {
        val user = currentUser.value ?: return
        scope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = authRepository.addEmergencyContact(user.id, contactSafeWalkId)
            result.onSuccess {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    successMessage = "Emergency contact $contactSafeWalkId added"
                )
                onSuccess?.invoke()
            }.onFailure { ex ->
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = ex.message)
            }
        }
    }

    /**
     * Atomically replaces an emergency contact.
     */
    fun replaceEmergencyContact(
        oldContactSafeWalkId: String,
        newContactSafeWalkId: String,
        onSuccess: (() -> Unit)? = null
    ) {
        val user = currentUser.value ?: return
        scope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = authRepository.replaceEmergencyContact(user.id, oldContactSafeWalkId, newContactSafeWalkId)
            result.onSuccess {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    successMessage = "Replaced $oldContactSafeWalkId with $newContactSafeWalkId"
                )
                onSuccess?.invoke()
            }.onFailure { ex ->
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = ex.message)
            }
        }
    }

    /**
     * Removes an emergency contact.
     */
    fun removeEmergencyContact(contactSafeWalkId: String, onSuccess: (() -> Unit)? = null) {
        val user = currentUser.value ?: return
        scope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = authRepository.removeEmergencyContact(user.id, contactSafeWalkId)
            result.onSuccess {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    successMessage = "Emergency contact removed"
                )
                onSuccess?.invoke()
            }.onFailure { ex ->
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = ex.message)
            }
        }
    }

    /**
     * Uploads and updates current user's profile avatar.
     */
    fun uploadAvatar(
        fileBytes: ByteArray,
        fileName: String,
        mimeType: String = "image/jpeg",
        onSuccess: (() -> Unit)? = null
    ) {
        val user = currentUser.value ?: return
        scope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = authRepository.uploadAvatar(user.id, fileBytes, fileName, mimeType)
            result.onSuccess {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    successMessage = "Profile picture updated successfully!"
                )
                onSuccess?.invoke()
            }.onFailure { ex ->
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = ex.message)
            }
        }
    }

    fun logout(onLoggedOut: () -> Unit) {
        scope.launch {
            authRepository.logout()
            _uiState.value = AuthUiState()
            onLoggedOut()
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun clearFeedback() {
        _uiState.value = _uiState.value.copy(errorMessage = null, successMessage = null)
    }
}
