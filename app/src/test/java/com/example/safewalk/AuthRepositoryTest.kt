package com.example.safewalk

import com.example.safewalk.data.repository.AuthRepositoryImpl
import com.example.safewalk.data.source.mock.MockAuthDataSource
import com.example.safewalk.domain.model.User
import com.example.safewalk.domain.model.UserRole
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AuthRepositoryTest {

    private lateinit var authRepository: AuthRepositoryImpl

    @Before
    fun setUp() {
        val dataSource = MockAuthDataSource()
        authRepository = AuthRepositoryImpl(dataSource)
    }

    @Test
    fun login_withValidSafeWalkId_succeeds() = runBlocking {
        val result = authRepository.login("SW-GAURAV-101", "dummy")
        assertTrue(result.isSuccess)
        val user = result.getOrNull()
        assertNotNull(user)
        assertEquals("Gaurav Mishra", user?.name)
        assertEquals(UserRole.USER, user?.role)
        assertEquals(user, authRepository.currentUser.value)
    }

    @Test
    fun login_withInvalidCredentials_fails() = runBlocking {
        val result = authRepository.login("INVALID-ID", "dummy")
        assertTrue(result.isFailure)
    }

    @Test
    fun loginAsMockUser_switchesCurrentUser() = runBlocking {
        val policeUser = authRepository.getAvailableMockUsers().first { it.role == UserRole.POLICE }
        val result = authRepository.loginAsMockUser(policeUser)
        assertTrue(result.isSuccess)
        assertEquals(UserRole.POLICE, authRepository.currentUser.value?.role)
        assertTrue(authRepository.currentUser.value?.isVerified == true)
    }

    @Test
    fun register_newValidUser_succeedsAndLogsIn() = runBlocking {
        val newUser = User(
            id = "test-user-999",
            safeWalkId = "SW-TEST-999",
            name = "Test Pilot",
            phone = "+91 99999 11111",
            email = "testpilot@safewalk.local",
            role = UserRole.USER
        )
        val result = authRepository.register(newUser, "secret")
        assertTrue(result.isSuccess)
        assertEquals("Test Pilot", authRepository.currentUser.value?.name)
    }

    @Test
    fun logout_clearsCurrentUser() = runBlocking {
        authRepository.login("SW-GAURAV-101", "dummy")
        assertNotNull(authRepository.currentUser.value)

        authRepository.logout()
        assertNull(authRepository.currentUser.value)
    }
}
