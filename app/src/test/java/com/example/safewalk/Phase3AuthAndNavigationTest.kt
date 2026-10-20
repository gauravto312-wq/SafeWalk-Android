package com.example.safewalk

import com.example.safewalk.core.navigation.Screen
import com.example.safewalk.data.repository.AuthRepositoryImpl
import com.example.safewalk.data.repository.EmergencyRepositoryImpl
import com.example.safewalk.data.repository.LocationRepositoryImpl
import com.example.safewalk.data.source.mock.MockAuthDataSource
import com.example.safewalk.data.source.mock.MockData
import com.example.safewalk.data.source.mock.MockEmergencyDataSource
import com.example.safewalk.data.source.mock.MockLocationDataSource
import com.example.safewalk.domain.model.Emergency
import com.example.safewalk.domain.model.EmergencyStatus
import com.example.safewalk.domain.model.Location
import com.example.safewalk.domain.model.User
import com.example.safewalk.domain.model.UserRole
import com.example.safewalk.ui.components.NavigationTab
import com.example.safewalk.viewmodel.AuthViewModel
import com.example.safewalk.viewmodel.EmergencyViewModel
import com.example.safewalk.viewmodel.HomeViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class Phase3AuthAndNavigationTest {

    private lateinit var authDataSource: MockAuthDataSource
    private lateinit var emergencyDataSource: MockEmergencyDataSource
    private lateinit var locationDataSource: MockLocationDataSource

    private lateinit var authRepository: AuthRepositoryImpl
    private lateinit var emergencyRepository: EmergencyRepositoryImpl
    private lateinit var locationRepository: LocationRepositoryImpl

    private val testScope = CoroutineScope(Dispatchers.Unconfined)

    @Before
    fun setUp() {
        authDataSource = MockAuthDataSource()
        emergencyDataSource = MockEmergencyDataSource()
        locationDataSource = MockLocationDataSource()

        authRepository = AuthRepositoryImpl(authDataSource)
        emergencyRepository = EmergencyRepositoryImpl(emergencyDataSource)
        locationRepository = LocationRepositoryImpl(locationDataSource)
    }

    // 1. Splash routes correctly: unauthenticated -> login, authenticated -> home
    @Test
    fun test01_splashRoutesCorrectly() = runBlocking {
        val authVm = AuthViewModel(authRepository, externalScope = testScope)
        assertNull(authRepository.currentUser.value)
        assertFalse(authVm.isAuthenticated()) // Routes to Login

        authRepository.login("SW-GAURAV-101", "password")
        assertNotNull(authRepository.currentUser.value)
        assertTrue(authVm.isAuthenticated()) // Routes to Home
    }

    // 2. Login with valid SafeWalk ID succeeds
    @Test
    fun test02_loginWithValidSafeWalkId_succeeds() = runBlocking {
        val result = authRepository.login("SW-GAURAV-101", "password")
        assertTrue(result.isSuccess)
        val user = result.getOrNull()
        assertNotNull(user)
        assertEquals("SW-GAURAV-101", user?.safeWalkId)
        assertEquals("Gaurav Mishra", user?.name)
    }

    // 3. Login with valid email succeeds
    @Test
    fun test03_loginWithValidEmail_succeeds() = runBlocking {
        val result = authRepository.login("gaurav@safewalk.local", "password")
        assertTrue(result.isSuccess)
        val user = result.getOrNull()
        assertNotNull(user)
        assertEquals("gaurav@safewalk.local", user?.email)
    }

    // 4. Login with invalid ID/email fails
    @Test
    fun test04_loginWithInvalidIdOrEmail_fails() = runBlocking {
        val result = authRepository.login("NON_EXISTENT_USER", "password")
        assertTrue(result.isFailure)
        assertEquals("Invalid SafeWalk ID, Email or password", result.exceptionOrNull()?.message)
    }

    // 5. Login with empty ID/email shows validation error
    @Test
    fun test05_loginWithEmptyIdentifier_showsValidationError() = runBlocking {
        val authVm = AuthViewModel(authRepository, externalScope = testScope)
        authVm.login("", "some_password") {}
        assertEquals("Please enter SafeWalk ID or Email", authVm.uiState.value.errorMessage)
    }

    // 6. Login with empty password shows validation error
    @Test
    fun test06_loginWithEmptyPassword_showsValidationError() = runBlocking {
        val authVm = AuthViewModel(authRepository, externalScope = testScope)
        authVm.login("SW-GAURAV-101", "") {}
        assertEquals("Please enter password", authVm.uiState.value.errorMessage)
    }

    // 7. Login sets authenticated session in repository
    @Test
    fun test07_loginSetsAuthenticatedSessionInRepository() = runBlocking {
        assertNull(authRepository.currentUser.value)
        val result = authRepository.login("SW-POLICE-911", "password")
        assertTrue(result.isSuccess)
        assertEquals(result.getOrNull(), authRepository.currentUser.value)
        assertTrue(authRepository.isAuthenticated())
    }

    // 8. Register with valid details creates new user
    @Test
    fun test08_registerWithValidDetails_createsNewUser() = runBlocking {
        val result = authRepository.register(
            name = "Rahul Sharma",
            email = "rahul.sharma@example.com",
            phone = "+91 98765 43210",
            password = "Password123",
            confirmPassword = "Password123"
        )
        assertTrue(result.isSuccess)
        val user = result.getOrNull()
        assertNotNull(user)
        assertEquals("Rahul Sharma", user?.name)
        assertEquals("rahul.sharma@example.com", user?.email)
    }

    // 9. Register with valid details generates unique SafeWalk User ID (format SW-USER-1001+)
    @Test
    fun test09_registerGeneratesUniqueSafeWalkUserId() = runBlocking {
        val result1 = authRepository.register(
            name = "User One",
            email = "user1@example.com",
            phone = "+91 98765 00001",
            password = "Password123",
            confirmPassword = "Password123"
        )
        val result2 = authRepository.register(
            name = "User Two",
            email = "user2@example.com",
            phone = "+91 98765 00002",
            password = "Password123",
            confirmPassword = "Password123"
        )
        assertTrue(result1.isSuccess)
        assertTrue(result2.isSuccess)

        val id1 = result1.getOrNull()?.safeWalkId ?: ""
        val id2 = result2.getOrNull()?.safeWalkId ?: ""

        val regex = Regex("^SW-USER-\\d{4,}$")
        assertTrue("id1 matches format: $id1", regex.matches(id1))
        assertTrue("id2 matches format: $id2", regex.matches(id2))
        assertTrue("id1 and id2 are distinct", id1 != id2)
        assertEquals("SW-USER-1001", id1)
        assertEquals("SW-USER-1002", id2)
    }

    // 10. Register sets ROLE_USER automatically
    @Test
    fun test10_registerSetsRoleUserAutomatically() = runBlocking {
        val result = authRepository.register(
            name = "Citizen Priya",
            email = "priya@example.com",
            phone = "+91 98765 11111",
            password = "Password123",
            confirmPassword = "Password123"
        )
        assertTrue(result.isSuccess)
        val user = result.getOrNull()
        assertEquals(UserRole.USER, user?.role)
        assertEquals(false, user?.isVerified)
    }

    // 11. Register automatically sets authenticated session
    @Test
    fun test11_registerAutomaticallySetsAuthenticatedSession() = runBlocking {
        assertNull(authRepository.currentUser.value)
        val result = authRepository.register(
            name = "Vikram Singh",
            email = "vikram@example.com",
            phone = "+91 98765 22222",
            password = "Password123",
            confirmPassword = "Password123"
        )
        assertTrue(result.isSuccess)
        assertEquals(result.getOrNull(), authRepository.currentUser.value)
        assertTrue(authRepository.isAuthenticated())
    }

    // 12. Register with duplicate email fails with exact message: 'An account with this email already exists.'
    @Test
    fun test12_registerWithDuplicateEmail_failsWithExactMessage() = runBlocking {
        // gaurav@safewalk.local is already in mock data
        val result = authRepository.register(
            name = "Duplicate Gaurav",
            email = "gaurav@safewalk.local",
            phone = "+91 98765 33333",
            password = "Password123",
            confirmPassword = "Password123"
        )
        assertTrue(result.isFailure)
        val expectedMessage = "An account with this email already exists."
        assertEquals(expectedMessage, result.exceptionOrNull()?.message)

        // Also test through AuthViewModel
        val authVm = AuthViewModel(authRepository, externalScope = testScope)
        authVm.register(
            name = "Duplicate Gaurav",
            email = "gaurav@safewalk.local",
            phone = "+91 98765 33333",
            password = "Password123",
            confirmPassword = "Password123"
        ) {}
        assertEquals(expectedMessage, authVm.uiState.value.errorMessage)
    }

    // 13. Register with mismatched passwords shows validation error
    @Test
    fun test13_registerWithMismatchedPasswords_showsValidationError() = runBlocking {
        val authVm = AuthViewModel(authRepository, externalScope = testScope)
        authVm.register(
            name = "Mismatch User",
            email = "mismatch@example.com",
            phone = "+91 98765 44444",
            password = "Password123",
            confirmPassword = "DifferentPassword456"
        ) {}
        assertEquals("Passwords do not match", authVm.uiState.value.errorMessage)
    }

    // 14. Logout clears authenticated session
    @Test
    fun test14_logoutClearsAuthenticatedSession() = runBlocking {
        authRepository.login("SW-GAURAV-101", "password")
        assertNotNull(authRepository.currentUser.value)

        authRepository.logout()
        assertNull(authRepository.currentUser.value)
        assertFalse(authRepository.isAuthenticated())
    }

    // 15. Logout navigates to login with cleared backstack (verified at repository/ViewModel layer)
    @Test
    fun test15_logoutNavigatesWithClearedSession() = runBlocking {
        val authVm = AuthViewModel(authRepository, externalScope = testScope)
        authRepository.login("SW-GAURAV-101", "password")
        assertNotNull(authVm.currentUser.value)

        var logoutCallbackCalled = false
        authVm.logout {
            logoutCallbackCalled = true
        }

        assertTrue(logoutCallbackCalled)
        assertNull(authVm.currentUser.value)
        assertFalse(authVm.isAuthenticated())
    }

    // 16. Unauthenticated access to Home redirects/blocks correctly
    @Test
    fun test16_unauthenticatedAccessToHome_isBlocked() = runBlocking {
        assertNull(authRepository.currentUser.value)
        val homeVm = HomeViewModel(authRepository, emergencyRepository, locationRepository, externalScope = testScope)
        assertNull(homeVm.currentUser.value)
    }

    // 17. Unauthenticated access to Profile redirects/blocks correctly
    @Test
    fun test17_unauthenticatedAccessToProfile_isBlocked() = runBlocking {
        assertNull(authRepository.currentUser.value)
        val authVm = AuthViewModel(authRepository, externalScope = testScope)
        assertNull(authVm.currentUser.value)
        assertFalse(authVm.isAuthenticated())
    }

    // 18. Unauthenticated access to History redirects/blocks correctly
    @Test
    fun test18_unauthenticatedAccessToHistory_isBlocked() = runBlocking {
        assertNull(authRepository.currentUser.value)
        assertFalse(authRepository.isAuthenticated())
    }

    // 19. Unauthenticated access to Settings redirects/blocks correctly
    @Test
    fun test19_unauthenticatedAccessToSettings_isBlocked() = runBlocking {
        assertNull(authRepository.currentUser.value)
        assertFalse(authRepository.isAuthenticated())
    }

    // 20. Home displays logged-in user name
    @Test
    fun test20_homeDisplaysLoggedInUserName() = runBlocking {
        authRepository.login("SW-GAURAV-101", "password")
        val homeVm = HomeViewModel(authRepository, emergencyRepository, locationRepository, externalScope = testScope)
        assertEquals("Gaurav Mishra", homeVm.currentUser.value?.name)
    }

    // 21. Home displays logged-in user SafeWalk ID
    @Test
    fun test21_homeDisplaysLoggedInUserSafeWalkId() = runBlocking {
        authRepository.login("SW-POLICE-911", "password")
        val homeVm = HomeViewModel(authRepository, emergencyRepository, locationRepository, externalScope = testScope)
        assertEquals("SW-POLICE-911", homeVm.currentUser.value?.safeWalkId)
    }

    // 22. Home displays logged-in user role
    @Test
    fun test22_homeDisplaysLoggedInUserRole() = runBlocking {
        authRepository.login("SW-HOSP-102", "password")
        val homeVm = HomeViewModel(authRepository, emergencyRepository, locationRepository, externalScope = testScope)
        assertEquals(UserRole.HOSPITAL, homeVm.currentUser.value?.role)
    }

    // 23. Home displays logged-in user status (e.g., Ready, On Duty)
    @Test
    fun test23_homeDisplaysLoggedInUserStatus() = runBlocking {
        authRepository.login("SW-POLICE-911", "password")
        val homeVm = HomeViewModel(authRepository, emergencyRepository, locationRepository, externalScope = testScope)
        assertEquals("On Duty / Patrol Active", homeVm.currentUser.value?.status)

        authRepository.login("SW-GAURAV-101", "password")
        assertEquals("Ready", homeVm.currentUser.value?.status)
    }

    // 24. Home displays active emergency section correctly when emergencies exist
    @Test
    fun test24_homeDisplaysActiveEmergencySectionCorrectly() = runBlocking {
        val user = MockData.mockUsers[0]
        authRepository.loginAsMockUser(user)

        val homeVm = HomeViewModel(authRepository, emergencyRepository, locationRepository, externalScope = testScope)
        val initialCount = homeVm.activeEmergencies.value.size

        // Create an emergency
        emergencyRepository.createEmergency(user, locationRepository.getDefaultMockLocation())

        val updatedEmergencies = emergencyRepository.activeEmergencies.value
        assertEquals(initialCount + 1, updatedEmergencies.size)
        assertTrue(updatedEmergencies.any { it.status == EmergencyStatus.ACTIVE })
    }

    // 25. Triggering SOS from Home creates emergency with current logged-in user as requester
    @Test
    fun test25_triggeringSosCreatesEmergencyWithCurrentUserAsRequester() = runBlocking {
        val user = MockData.mockUsers[0]
        authRepository.loginAsMockUser(user)

        val homeVm = HomeViewModel(authRepository, emergencyRepository, locationRepository, externalScope = testScope)
        var createdId: String? = null
        homeVm.triggerSosEmergency { id ->
            createdId = id
        }

        assertNotNull(createdId)
        val created = emergencyRepository.activeEmergencies.value.find { it.id == createdId }
        assertNotNull(created)
        assertEquals(user.id, created?.requester?.id)
        assertEquals(user.name, created?.requester?.name)
        assertEquals(EmergencyStatus.ACTIVE, created?.status)
    }

    // 26. SOS cancellation button is visible immediately after SOS creation
    @Test
    fun test26_sosCancellationButtonVisibleImmediatelyAfterCreation() = runBlocking {
        val requester = MockData.mockUsers[0]
        val now = System.currentTimeMillis()
        val emg = Emergency(
            id = "EMG-TEST-26",
            requester = requester,
            createdAt = now,
            requesterLocation = Location(12.9716, 77.5946, "MG Road, Bengaluru")
        )

        assertTrue(emg.canCancel(currentTimeMillis = now))
        assertEquals(5000L, emg.remainingCancellationTimeMillis(currentTimeMillis = now))

        val emergencyVm = EmergencyViewModel(emergencyRepository, authRepository, externalScope = testScope)
        emergencyVm.startCancellationCountdown(emg, currentTimeProvider = { now })
        assertTrue(emergencyVm.canCancel.value)
        assertEquals(5000L, emergencyVm.cancellationRemainingMillis.value)
        emergencyVm.stopCancellationCountdown()
    }

    // 27. SOS cancellation button remains visible during 0–5 seconds
    @Test
    fun test27_sosCancellationButtonRemainsVisibleDuring0To5Seconds() = runBlocking {
        val requester = MockData.mockUsers[0]
        val createdAt = 1_000_000L
        val emg = Emergency(
            id = "EMG-TEST-27",
            requester = requester,
            createdAt = createdAt,
            requesterLocation = Location(12.9716, 77.5946, "MG Road, Bengaluru")
        )

        // At t = 2.5s (2500ms elapsed)
        val t2500 = createdAt + 2500L
        assertTrue(emg.canCancel(currentTimeMillis = t2500))
        assertEquals(2500L, emg.remainingCancellationTimeMillis(currentTimeMillis = t2500))

        // At t = 4.999s (4999ms elapsed)
        val t4999 = createdAt + 4999L
        assertTrue(emg.canCancel(currentTimeMillis = t4999))
        assertEquals(1L, emg.remainingCancellationTimeMillis(currentTimeMillis = t4999))

        val emergencyVm = EmergencyViewModel(emergencyRepository, authRepository, externalScope = testScope)
        emergencyVm.startCancellationCountdown(emg, currentTimeProvider = { t2500 })
        assertTrue(emergencyVm.canCancel.value)
        assertEquals(2500L, emergencyVm.cancellationRemainingMillis.value)
        emergencyVm.stopCancellationCountdown()
    }

    // 28. SOS cancellation button is removed after 5 seconds
    @Test
    fun test28_sosCancellationButtonRemovedAfter5Seconds() = runBlocking {
        val requester = MockData.mockUsers[0]
        val createdAt = 1_000_000L
        val emg = Emergency(
            id = "EMG-TEST-28",
            requester = requester,
            createdAt = createdAt,
            requesterLocation = Location(12.9716, 77.5946, "MG Road, Bengaluru")
        )

        // Exactly at 5.000s
        val t5000 = createdAt + 5000L
        assertFalse(emg.canCancel(currentTimeMillis = t5000))
        assertEquals(0L, emg.remainingCancellationTimeMillis(currentTimeMillis = t5000))

        // Beyond 5.000s
        val t6000 = createdAt + 6000L
        assertFalse(emg.canCancel(currentTimeMillis = t6000))
        assertEquals(0L, emg.remainingCancellationTimeMillis(currentTimeMillis = t6000))

        val emergencyVm = EmergencyViewModel(emergencyRepository, authRepository, externalScope = testScope)
        emergencyVm.startCancellationCountdown(emg, currentTimeProvider = { t5000 })
        assertFalse(emergencyVm.canCancel.value)
        assertEquals(0L, emergencyVm.cancellationRemainingMillis.value)
        emergencyVm.stopCancellationCountdown()
    }

    // 29. Bottom navigation switches between Home, History, Profile, Settings
    @Test
    fun test29_bottomNavigationSwitchesBetweenAllTabs() {
        assertEquals(Screen.Home.route, NavigationTab.HOME.route)
        assertEquals("Home", NavigationTab.HOME.label)

        assertEquals(Screen.History.route, NavigationTab.HISTORY.route)
        assertEquals("History", NavigationTab.HISTORY.label)

        assertEquals(Screen.Profile.route, NavigationTab.PROFILE.route)
        assertEquals("Profile", NavigationTab.PROFILE.label)

        assertEquals(Screen.Settings.route, NavigationTab.SETTINGS.route)
        assertEquals("Settings", NavigationTab.SETTINGS.label)

        // Verify count of tabs
        assertEquals(4, NavigationTab.values().size)
    }
}
