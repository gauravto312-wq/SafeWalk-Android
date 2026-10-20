package com.example.safewalk.data.source.mock

import com.example.safewalk.domain.model.NotificationType
import com.example.safewalk.domain.model.User
import com.example.safewalk.domain.model.UserRole
import com.example.safewalk.domain.model.VerificationDocument
import com.example.safewalk.domain.model.VerificationStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger

/**
 * In-memory Mock Data Source for Authentication, Registration, Verification, and Session Management.
 */
class MockAuthDataSource(
    private var notificationDataSource: MockNotificationDataSource? = null
) {

    private val users = MockData.mockUsers.toMutableList()
    // Initial state: Unauthenticated (null)
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val nextCitizenSequence = AtomicInteger(1001)
    private val nextPoliceSequence = AtomicInteger(1001)
    private val nextHospitalSequence = AtomicInteger(1001)

    private val EMAIL_REGEX = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".toRegex()
    private val PHONE_REGEX = "^[+]?[0-9\\s-]{7,15}$".toRegex()

    fun getAvailableUsers(): List<User> = users.toList()

    fun isAuthenticated(): Boolean = _currentUser.value != null

    fun login(identifier: String, pass: String): Result<User> {
        val trimmed = identifier.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Please enter SafeWalk ID or Email"))
        }
        if (pass.isEmpty()) {
            return Result.failure(IllegalArgumentException("Please enter password"))
        }

        val user = users.find {
            it.safeWalkId.equals(trimmed, ignoreCase = true) ||
            it.email.equals(trimmed, ignoreCase = true) ||
            it.phone == trimmed
        }

        return if (user != null) {
            _currentUser.value = user
            Result.success(user)
        } else {
            Result.failure(IllegalArgumentException("Invalid SafeWalk ID, Email or password"))
        }
    }

    fun loginAsUser(user: User): Result<User> {
        val found = users.find { it.id == user.id } ?: user
        _currentUser.value = found
        return Result.success(found)
    }

    fun register(
        name: String,
        email: String,
        phone: String,
        password: String,
        confirmPassword: String,
        emergencyPassword: String? = null
    ): Result<User> {
        return register(
            name = name,
            email = email,
            phone = phone,
            password = password,
            confirmPassword = confirmPassword,
            emergencyPassword = emergencyPassword,
            role = UserRole.USER
        )
    }

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
        designation: String? = null
    ): Result<User> {
        // Business Rule: ADMIN accounts are NOT registered via the Android app!
        if (role == UserRole.ADMIN) {
            return Result.failure(IllegalArgumentException("ADMIN registration is not permitted via the mobile app"))
        }

        val trimmedName = name.trim()
        val trimmedEmail = email.trim()
        val trimmedPhone = phone.trim()

        if (trimmedName.isEmpty()) {
            return Result.failure(IllegalArgumentException("Full Name is required"))
        }
        if (trimmedEmail.isEmpty()) {
            return Result.failure(IllegalArgumentException("Email is required"))
        }
        if (!EMAIL_REGEX.matches(trimmedEmail)) {
            return Result.failure(IllegalArgumentException("Please enter a valid email address"))
        }
        if (trimmedPhone.isEmpty()) {
            return Result.failure(IllegalArgumentException("Phone Number is required"))
        }
        if (!PHONE_REGEX.matches(trimmedPhone)) {
            return Result.failure(IllegalArgumentException("Please enter a valid phone number"))
        }
        if (password.isEmpty()) {
            return Result.failure(IllegalArgumentException("Password is required"))
        }
        if (password.length < 6) {
            return Result.failure(IllegalArgumentException("Password must be at least 6 characters"))
        }
        if (password != confirmPassword) {
            return Result.failure(IllegalArgumentException("Passwords do not match"))
        }

        // Duplicate email check
        val emailExists = users.any { it.email.equals(trimmedEmail, ignoreCase = true) }
        if (emailExists) {
            return Result.failure(IllegalStateException("An account with this email already exists."))
        }

        val (uniqueSafeWalkId, userId, initialVerification, initialStatus) = when (role) {
            UserRole.USER -> {
                val seq = nextCitizenSequence.getAndIncrement()
                Quadruple("SW-USER-$seq", "user-$seq", VerificationStatus.NONE, "Ready")
            }
            UserRole.POLICE -> {
                val seq = nextPoliceSequence.getAndIncrement()
                Quadruple("SW-POLICE-$seq", "police-$seq", VerificationStatus.PENDING, "Verification Pending")
            }
            UserRole.HOSPITAL -> {
                val seq = nextHospitalSequence.getAndIncrement()
                Quadruple("SW-HOSP-$seq", "hosp-$seq", VerificationStatus.PENDING, "Verification Pending")
            }
            UserRole.ADMIN -> error("Admin registration disallowed")
        }

        val newUser = User(
            id = userId,
            safeWalkId = uniqueSafeWalkId,
            name = trimmedName,
            phone = trimmedPhone,
            email = trimmedEmail,
            role = role,
            isVerified = false, // Always starts unverified for new registrations
            status = initialStatus,
            verificationStatus = initialVerification,
            department = department?.trim()?.ifEmpty { null },
            badgeId = badgeId?.trim()?.ifEmpty { null },
            hospitalName = hospitalName?.trim()?.ifEmpty { null },
            licenseId = licenseId?.trim()?.ifEmpty { null },
            designation = designation?.trim()?.ifEmpty { null },
            emergencyPassword = emergencyPassword?.trim()?.ifEmpty { null } ?: "1234"
        )

        users.add(newUser)
        _currentUser.value = newUser
        return Result.success(newUser)
    }

    fun register(newUser: User, pass: String): Result<User> {
        val existing = users.find {
            it.email.equals(newUser.email, ignoreCase = true) || it.safeWalkId == newUser.safeWalkId
        }
        if (existing != null) {
            return Result.failure(IllegalStateException("An account with this email already exists."))
        }
        users.add(newUser)
        _currentUser.value = newUser
        return Result.success(newUser)
    }

    /**
     * Uploads mock document for Police or Hospital verification.
     */
    fun uploadVerificationDocument(userId: String, document: VerificationDocument): Result<User> {
        val index = users.indexOfFirst { it.id == userId }
        if (index == -1) {
            return Result.failure(IllegalArgumentException("User not found"))
        }
        val user = users[index]
        val updatedUser = user.copy(documents = user.documents + document)
        users[index] = updatedUser
        if (_currentUser.value?.id == userId) {
            _currentUser.value = updatedUser
        }
        return Result.success(updatedUser)
    }

    fun setNotificationDataSource(dataSource: MockNotificationDataSource) {
        this.notificationDataSource = dataSource
    }

    /**
     * Approves verification for a Police or Hospital user (Mock review mechanism).
     */
    fun approveVerification(userId: String): Result<User> {
        val index = users.indexOfFirst { it.id == userId }
        if (index == -1) {
            return Result.failure(IllegalArgumentException("User not found"))
        }
        val user = users[index]
        val statusText = if (user.role == UserRole.POLICE) "On Duty / Patrol Active" else "Medical Unit Ready"
        val updatedUser = user.copy(
            isVerified = true,
            verificationStatus = VerificationStatus.APPROVED,
            status = statusText
        )
        users[index] = updatedUser
        if (_currentUser.value?.id == userId) {
            _currentUser.value = updatedUser
        }

        // Phase 9 Mock FCM Push Dispatch
        notificationDataSource?.dispatchFcmPush(
            type = NotificationType.VERIFICATION_APPROVED,
            recipientUserId = userId,
            title = "Account Verified",
            message = "Your professional credentials have been approved. Official responder dispatch is now active.",
            navigationTarget = "profile"
        )

        return Result.success(updatedUser)
    }

    /**
     * Rejects verification for a Police or Hospital user (Mock review mechanism).
     */
    fun rejectVerification(userId: String): Result<User> {
        val index = users.indexOfFirst { it.id == userId }
        if (index == -1) {
            return Result.failure(IllegalArgumentException("User not found"))
        }
        val user = users[index]
        val updatedUser = user.copy(
            isVerified = false,
            verificationStatus = VerificationStatus.REJECTED,
            status = "Verification Rejected"
        )
        users[index] = updatedUser
        if (_currentUser.value?.id == userId) {
            _currentUser.value = updatedUser
        }

        // Phase 9 Mock FCM Push Dispatch
        notificationDataSource?.dispatchFcmPush(
            type = NotificationType.VERIFICATION_REJECTED,
            recipientUserId = userId,
            title = "Verification Needs Action",
            message = "Your credentials could not be verified. Please review requirements and upload revised documentation.",
            navigationTarget = "profile"
        )

        return Result.success(updatedUser)
    }

    /**
     * Adds an emergency contact by SafeWalk User ID (up to 2 contacts allowed).
     */
    fun addEmergencyContact(userId: String, contactSafeWalkId: String): Result<User> {
        val trimmedContact = contactSafeWalkId.trim().uppercase()
        if (trimmedContact.isEmpty()) {
            return Result.failure(IllegalArgumentException("SafeWalk ID is required"))
        }
        val index = users.indexOfFirst { it.id == userId }
        if (index == -1) {
            return Result.failure(IllegalArgumentException("User not found"))
        }
        val user = users[index]
        if (user.safeWalkId.equals(trimmedContact, ignoreCase = true)) {
            return Result.failure(IllegalArgumentException("Cannot add yourself as an emergency contact"))
        }
        if (user.emergencyContacts.size >= 3) {
            return Result.failure(IllegalStateException("Maximum of 3 emergency contacts allowed"))
        }
        if (user.emergencyContacts.any { it.equals(trimmedContact, ignoreCase = true) }) {
            return Result.failure(IllegalArgumentException("This contact is already in your emergency contacts list"))
        }

        val contactExists = users.any { it.safeWalkId.equals(trimmedContact, ignoreCase = true) }
        if (!contactExists) {
            return Result.failure(IllegalArgumentException("SafeWalk ID '$contactSafeWalkId' does not exist"))
        }

        val updatedUser = user.copy(emergencyContacts = user.emergencyContacts + trimmedContact)
        users[index] = updatedUser
        if (_currentUser.value?.id == userId) {
            _currentUser.value = updatedUser
        }
        return Result.success(updatedUser)
    }

    /**
     * Atomically replaces an emergency contact by SafeWalk User ID.
     */
    fun replaceEmergencyContact(userId: String, oldContactSafeWalkId: String, newContactSafeWalkId: String): Result<User> {
        val trimmedOld = oldContactSafeWalkId.trim().uppercase()
        val trimmedNew = newContactSafeWalkId.trim().uppercase()
        if (trimmedNew.isEmpty()) {
            return Result.failure(IllegalArgumentException("SafeWalk ID is required"))
        }
        val index = users.indexOfFirst { it.id == userId }
        if (index == -1) {
            return Result.failure(IllegalArgumentException("User not found"))
        }
        val user = users[index]
        if (user.safeWalkId.equals(trimmedNew, ignoreCase = true)) {
            return Result.failure(IllegalArgumentException("Cannot add yourself as an emergency contact"))
        }
        if (user.emergencyContacts.any { it.equals(trimmedNew, ignoreCase = true) }) {
            return Result.failure(IllegalArgumentException("This contact is already in your emergency contacts list"))
        }
        val contactExists = users.any { it.safeWalkId.equals(trimmedNew, ignoreCase = true) }
        if (!contactExists) {
            return Result.failure(IllegalArgumentException("SafeWalk ID '$newContactSafeWalkId' does not exist"))
        }

        val updatedContacts = user.emergencyContacts.map {
            if (it.equals(trimmedOld, ignoreCase = true)) trimmedNew else it
        }
        val updatedUser = user.copy(emergencyContacts = updatedContacts)
        users[index] = updatedUser
        if (_currentUser.value?.id == userId) {
            _currentUser.value = updatedUser
        }
        return Result.success(updatedUser)
    }

    /**
     * Removes an emergency contact by SafeWalk User ID.
     */
    fun removeEmergencyContact(userId: String, contactSafeWalkId: String): Result<User> {
        val trimmedContact = contactSafeWalkId.trim().uppercase()
        val index = users.indexOfFirst { it.id == userId }
        if (index == -1) {
            return Result.failure(IllegalArgumentException("User not found"))
        }
        val user = users[index]
        val updatedContacts = user.emergencyContacts.filter { !it.equals(trimmedContact, ignoreCase = true) }
        val updatedUser = user.copy(emergencyContacts = updatedContacts)
        users[index] = updatedUser
        if (_currentUser.value?.id == userId) {
            _currentUser.value = updatedUser
        }
        return Result.success(updatedUser)
    }

    /**
     * Updates user avatar URL in mock data source.
     */
    fun uploadAvatar(userId: String, avatarUrl: String): Result<User> {
        val index = users.indexOfFirst { it.id == userId }
        if (index == -1) {
            return Result.failure(IllegalArgumentException("User not found"))
        }
        val user = users[index]
        val updatedUser = user.copy(avatarUrl = avatarUrl)
        users[index] = updatedUser
        if (_currentUser.value?.id == userId) {
            _currentUser.value = updatedUser
        }
        return Result.success(updatedUser)
    }

    fun logout() {
        _currentUser.value = null
    }

    fun reset() {
        users.clear()
        users.addAll(MockData.mockUsers)
        _currentUser.value = null
        nextCitizenSequence.set(1001)
        nextPoliceSequence.set(1001)
        nextHospitalSequence.set(1001)
    }

    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
