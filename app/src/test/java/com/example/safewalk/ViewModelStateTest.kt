package com.example.safewalk

import com.example.safewalk.data.repository.AuthRepositoryImpl
import com.example.safewalk.data.repository.EmergencyRepositoryImpl
import com.example.safewalk.data.repository.LocationRepositoryImpl
import com.example.safewalk.data.source.mock.MockAuthDataSource
import com.example.safewalk.data.source.mock.MockData
import com.example.safewalk.data.source.mock.MockEmergencyDataSource
import com.example.safewalk.data.source.mock.MockLocationDataSource
import com.example.safewalk.domain.model.EmergencyStatus
import com.example.safewalk.domain.model.UserRole
import com.example.safewalk.viewmodel.AuthViewModel
import com.example.safewalk.viewmodel.EmergencyViewModel
import com.example.safewalk.viewmodel.HomeViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ViewModelStateTest {

    private lateinit var authRepo: AuthRepositoryImpl
    private lateinit var emergencyRepo: EmergencyRepositoryImpl
    private lateinit var locationRepo: LocationRepositoryImpl

    @Before
    fun setUp() {
        val authDataSource = MockAuthDataSource()
        val emergencyDataSource = MockEmergencyDataSource()
        val locationDataSource = MockLocationDataSource()

        authRepo = AuthRepositoryImpl(authDataSource)
        emergencyRepo = EmergencyRepositoryImpl(emergencyDataSource)
        locationRepo = LocationRepositoryImpl(locationDataSource)
    }

    @Test
    fun authViewModel_emptyLogin_setsErrorMessage() = runBlocking {
        val viewModel = AuthViewModel(authRepo, externalScope = this)
        viewModel.login("", "pass") {}
        assertEquals("Please enter SafeWalk ID or Email", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun homeViewModel_triggerSosEmergency_createsActiveEmergency() = runBlocking {
        // Ensure user is logged in
        val user = MockData.mockUsers[0]
        authRepo.loginAsMockUser(user)

        val homeVm = HomeViewModel(authRepo, emergencyRepo, locationRepo, externalScope = this)
        var createdEmergencyId: String? = null

        homeVm.triggerSosEmergency { id ->
            createdEmergencyId = id
        }
        kotlinx.coroutines.yield()

        assertNotNull(createdEmergencyId)
        val created = emergencyRepo.activeEmergencies.value.find { it.id == createdEmergencyId }
        assertNotNull(created)
        assertEquals(EmergencyStatus.ACTIVE, created?.status)
        assertEquals(user.id, created?.requester?.id)
    }

    @Test
    fun emergencyViewModel_flowFromAcceptToResolution() = runBlocking {
        val requester = MockData.mockUsers[0]
        val responder = MockData.mockUsers[1]
        val emg = emergencyRepo.createEmergency(requester, locationRepo.getDefaultMockLocation())

        // Responder logs in
        authRepo.loginAsMockUser(responder)
        val emergencyVm = EmergencyViewModel(emergencyRepo, authRepo, externalScope = this)

        // Accept
        emergencyRepo.acceptEmergency(emg.id, responder)
        val acceptedEmg = emergencyRepo.currentEmergency.value
        assertEquals(EmergencyStatus.ACCEPTED, acceptedEmg?.status)

        // Arrive
        emergencyRepo.markArrived(emg.id, responder)
        val arrivedEmg = emergencyRepo.currentEmergency.value
        assertEquals(EmergencyStatus.ACCEPTED, arrivedEmg?.status)
        assertTrue(arrivedEmg?.isResponderArrived(responder.id) == true)

        // Confirm both
        emergencyRepo.confirmResolution(emg.id, responder)
        emergencyRepo.confirmResolution(emg.id, requester)
        val resolvedEmg = emergencyRepo.currentEmergency.value
        assertEquals(EmergencyStatus.RESOLVED, resolvedEmg?.status)
        assertTrue(resolvedEmg?.isFullyResolved == true)
    }
}
