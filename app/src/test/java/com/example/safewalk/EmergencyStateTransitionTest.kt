package com.example.safewalk

import com.example.safewalk.data.repository.EmergencyRepositoryImpl
import com.example.safewalk.data.source.mock.MockData
import com.example.safewalk.data.source.mock.MockEmergencyDataSource
import com.example.safewalk.domain.model.EmergencyStatus
import com.example.safewalk.domain.model.Location
import com.example.safewalk.domain.model.User
import com.example.safewalk.domain.model.UserRole
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EmergencyStateTransitionTest {

    private lateinit var repository: EmergencyRepositoryImpl
    private val requester = MockData.mockUsers[0] // Gaurav
    private val responder1 = MockData.mockUsers[1] // Rahul
    private val responder2 = MockData.mockUsers[3] // Police
    private val mockLocation = Location(28.6139, 77.2090, "Connaught Place")

    @Before
    fun setUp() {
        val dataSource = MockEmergencyDataSource()
        repository = EmergencyRepositoryImpl(dataSource)
    }

    @Test
    fun createEmergency_startsInActiveStatus() = runBlocking {
        val emergency = repository.createEmergency(requester, mockLocation)
        assertEquals(EmergencyStatus.ACTIVE, emergency.status)
        assertEquals(requester.id, emergency.requester.id)
        assertTrue(emergency.acceptedResponders.isEmpty())
        assertFalse(emergency.isTerminal)
    }

    @Test
    fun acceptEmergency_supportsMultipleResponders_transitionsToAccepted() = runBlocking {
        val emergency = repository.createEmergency(requester, mockLocation)

        // First responder accepts
        val accept1 = repository.acceptEmergency(emergency.id, responder1)
        assertTrue(accept1.isSuccess)
        val emg1 = accept1.getOrThrow()
        assertEquals(EmergencyStatus.ACCEPTED, emg1.status)
        assertEquals(1, emg1.acceptedResponders.size)
        assertTrue(emg1.isUserAcceptedResponder(responder1.id))

        // Second responder accepts (e.g. Police)
        val accept2 = repository.acceptEmergency(emergency.id, responder2)
        assertTrue(accept2.isSuccess)
        val emg2 = accept2.getOrThrow()
        assertEquals(2, emg2.acceptedResponders.size)
        assertTrue(emg2.isUserAcceptedResponder(responder2.id))
    }

    @Test
    fun markArrived_setsArrivedResponderAndStatus() = runBlocking {
        val emergency = repository.createEmergency(requester, mockLocation)
        repository.acceptEmergency(emergency.id, responder1)

        val arrivedResult = repository.markArrived(emergency.id, responder1)
        assertTrue(arrivedResult.isSuccess)
        val arrivedEmg = arrivedResult.getOrThrow()
        assertEquals(EmergencyStatus.ACCEPTED, arrivedEmg.status)
        assertTrue(arrivedEmg.isResponderArrived(responder1.id))
        assertEquals(responder1.id, arrivedEmg.arrivedResponder?.id)
    }

    @Test
    fun resolution_requiresBothRequesterAndResponderConfirmations() = runBlocking {
        val emergency = repository.createEmergency(requester, mockLocation)
        repository.acceptEmergency(emergency.id, responder1)
        repository.markArrived(emergency.id, responder1)

        // Only responder confirms first
        val respConfirm = repository.confirmResolution(emergency.id, responder1).getOrThrow()
        assertTrue(respConfirm.responderConfirmedResolution)
        assertFalse(respConfirm.requesterConfirmedResolution)
        assertEquals(EmergencyStatus.ACCEPTED, respConfirm.status) // Not yet resolved!

        // Now requester confirms
        val bothConfirm = repository.confirmResolution(emergency.id, requester).getOrThrow()
        assertTrue(bothConfirm.requesterConfirmedResolution)
        assertTrue(bothConfirm.responderConfirmedResolution)
        assertEquals(EmergencyStatus.RESOLVED, bothConfirm.status)
        assertTrue(bothConfirm.isTerminal)
        assertTrue(bothConfirm.isFullyResolved)
    }

    @Test
    fun cancelEmergency_transitionsToCancelledWithReason() = runBlocking {
        val emergency = repository.createEmergency(requester, mockLocation)
        val cancelResult = repository.cancelEmergency(emergency.id, "False alarm")
        assertTrue(cancelResult.isSuccess)
        val cancelled = cancelResult.getOrThrow()
        assertEquals(EmergencyStatus.CANCELLED, cancelled.status)
        assertEquals("False alarm", cancelled.cancellationReason)
        assertTrue(cancelled.isTerminal)
    }
}
