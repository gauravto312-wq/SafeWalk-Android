package com.example.safewalk

import com.example.safewalk.core.error.SafeWalkException
import com.example.safewalk.data.model.dto.EmergencyResponseDto
import com.example.safewalk.data.model.dto.toDomain
import com.example.safewalk.data.model.dto.toResponseDto
import com.example.safewalk.data.repository.EmergencyRepositoryImpl
import com.example.safewalk.data.source.mock.MockData
import com.example.safewalk.data.source.mock.MockEmergencyDataSource
import com.example.safewalk.domain.model.EmergencyStatus
import com.example.safewalk.domain.model.Location
import com.example.safewalk.domain.model.RealtimeEmergencyEvent
import com.example.safewalk.domain.model.UserRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Formal verification test suite for SafeWalk Android - Spring Boot Backend Reconciliation.
 * Tests strict compliance with backend business invariants:
 * - Rule 13: Emergency Password on SOS Cancellation
 * - Rule 14: Single Arrival Guard (HTTP 409 Conflict)
 * - Rule 15: Two-Party Dual-Confirmation Resolution Protocol
 * - Rule 16: Responder Withdrawal / Opt-Out Reversion to ACTIVE
 * - Exact 4-State FSM enum transitions (ACTIVE, ACCEPTED, RESOLVED, CANCELLED)
 * - DTO Round-Trip Mappings
 */
class BackendReconciliationVerificationTest {

    private lateinit var dataSource: MockEmergencyDataSource
    private lateinit var repository: EmergencyRepositoryImpl

    private val userGaurav = MockData.mockUsers[0].copy(emergencyPassword = "1234") // Requester
    private val userRahul = MockData.mockUsers[1] // First responder
    private val userAman = MockData.mockUsers[2] // Second responder
    private val mockLocation = Location(28.6139, 77.2090, "Connaught Place, New Delhi")

    @Before
    fun setUp() {
        dataSource = MockEmergencyDataSource()
        repository = EmergencyRepositoryImpl(dataSource)
    }

    // =========================================================================
    // 1. RULE 13: EMERGENCY PASSWORD ON SOS CANCELLATION
    // =========================================================================

    @Test
    fun testCancellation_validPassword_within5Seconds_succeeds() = runBlocking {
        val emg = repository.createEmergency(userGaurav, mockLocation)
        val result = repository.cancelEmergency(emg.id, emergencyPassword = "1234", reason = "False alarm")
        assertTrue("Cancellation with valid emergency password must succeed", result.isSuccess)
        val cancelled = result.getOrThrow()
        assertEquals(EmergencyStatus.CANCELLED, cancelled.status)
        assertTrue(cancelled.isTerminal)
    }

    @Test
    fun testCancellation_invalidPassword_within5Seconds_rejectedWithUnauthorized() = runBlocking {
        val emg = repository.createEmergency(userGaurav, mockLocation)
        val result = repository.cancelEmergency(emg.id, emergencyPassword = "WRONG_PASSWORD", reason = "False alarm")
        assertTrue("Cancellation with invalid emergency password must fail", result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue("Exception must be InvalidEmergencyPasswordError", ex is SafeWalkException.InvalidEmergencyPasswordError)
        assertEquals(401, (ex as SafeWalkException).httpStatus)
    }

    @Test
    fun testCancellation_expiredWindow_rejectedWithConflict() = runBlocking {
        // Emergency created >5s ago
        val pastEmg = repository.createEmergency(userGaurav, mockLocation).copy(
            createdAt = System.currentTimeMillis() - 6000L
        )
        dataSource.addEmergencyForTesting(pastEmg)

        val result = repository.cancelEmergency(pastEmg.id, emergencyPassword = "1234", reason = "Too late")
        assertTrue("Cancellation after 5 seconds must fail", result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue("Exception must be CancellationExpiredError", ex is SafeWalkException.CancellationExpiredError)
        assertEquals(409, (ex as SafeWalkException).httpStatus)
    }

    // =========================================================================
    // 2. RULE 14: SINGLE ARRIVAL GUARD (BACKEND PESSIMISTIC LOCK CONTRACT)
    // =========================================================================

    @Test
    fun testSingleArrivalGuard_firstArrivalDesignated_secondArrivalRejectedWithConflict() = runBlocking {
        val emg = repository.createEmergency(userGaurav, mockLocation)
        repository.acceptEmergency(emg.id, userRahul).getOrThrow()
        repository.acceptEmergency(emg.id, userAman).getOrThrow()

        // 1. Rahul arrives first (<= 25m)
        val rahulArrival = repository.markArrived(emg.id, userRahul, distanceMeters = 22.0)
        assertTrue("First arrival must succeed", rahulArrival.isSuccess)
        val withRahul = rahulArrival.getOrThrow()
        assertEquals(userRahul.id, withRahul.arrivedResponder?.id)
        assertEquals(userRahul.id, withRahul.designatedArrivedResponder?.id)

        // 2. Aman attempts to arrive second (<= 25m)
        val amanArrival = repository.markArrived(emg.id, userAman, distanceMeters = 18.0)
        assertTrue("Second arrival must be rejected by Single Arrival Guard", amanArrival.isFailure)
        val ex = amanArrival.exceptionOrNull()
        assertTrue("Exception must be SingleArrivalConflictError", ex is SafeWalkException.SingleArrivalConflictError)
        assertEquals(409, (ex as SafeWalkException).httpStatus)
        assertTrue(ex.message!!.contains(userRahul.name))

        // Designated arrived responder remains unchanged
        val current = repository.currentEmergency.value!!
        assertEquals("Rahul must remain the designated arrived responder", userRahul.id, current.designatedArrivedResponder?.id)
    }

    @Test
    fun testArrivalDistance_greaterThan25m_rejectedWithConflict() = runBlocking {
        val emg = repository.createEmergency(userGaurav, mockLocation)
        repository.acceptEmergency(emg.id, userRahul).getOrThrow()

        val arrivalResult = repository.markArrived(emg.id, userRahul, distanceMeters = 26.0)
        assertTrue("Arrival at >25m must fail", arrivalResult.isFailure)
        val ex = arrivalResult.exceptionOrNull()
        assertTrue("Exception must be ArrivalDistanceExceededError", ex is SafeWalkException.ArrivalDistanceExceededError)
        assertEquals(409, (ex as SafeWalkException).httpStatus)
    }

    // =========================================================================
    // 3. RULE 15: TWO-PARTY DUAL-CONFIRMATION RESOLUTION PROTOCOL
    // =========================================================================

    @Test
    fun testTwoPartyResolution_requesterFirst_responderSecond_succeeds() = runBlocking {
        val emg = repository.createEmergency(userGaurav, mockLocation)
        repository.acceptEmergency(emg.id, userRahul).getOrThrow()
        repository.markArrived(emg.id, userRahul, distanceMeters = 20.0).getOrThrow()

        // 1. Requester confirms resolution first
        val step1 = repository.confirmResolution(emg.id, userGaurav).getOrThrow()
        assertTrue("Requester flag set", step1.requesterConfirmedResolution)
        assertFalse("Responder flag not yet set", step1.responderConfirmedResolution)
        assertEquals("Status remains ACCEPTED until both confirm", EmergencyStatus.ACCEPTED, step1.status)
        assertFalse(step1.isTerminal)

        // 2. Responder confirms resolution second
        val step2 = repository.confirmResolution(emg.id, userRahul).getOrThrow()
        assertTrue("Requester flag set", step2.requesterConfirmedResolution)
        assertTrue("Responder flag set", step2.responderConfirmedResolution)
        assertEquals("Status becomes RESOLVED when both confirm", EmergencyStatus.RESOLVED, step2.status)
        assertTrue(step2.isTerminal)
        assertNotNull(step2.resolvedAt)
    }

    @Test
    fun testTwoPartyResolution_responderFirst_requesterSecond_succeeds() = runBlocking {
        val emg = repository.createEmergency(userGaurav, mockLocation)
        repository.acceptEmergency(emg.id, userRahul).getOrThrow()
        repository.markArrived(emg.id, userRahul, distanceMeters = 20.0).getOrThrow()

        // 1. Responder confirms resolution first
        val step1 = repository.confirmResolution(emg.id, userRahul).getOrThrow()
        assertFalse("Requester flag not yet set", step1.requesterConfirmedResolution)
        assertTrue("Responder flag set", step1.responderConfirmedResolution)
        assertEquals("Status remains ACCEPTED", EmergencyStatus.ACCEPTED, step1.status)

        // 2. Requester confirms resolution second
        val step2 = repository.confirmResolution(emg.id, userGaurav).getOrThrow()
        assertEquals("Status becomes RESOLVED", EmergencyStatus.RESOLVED, step2.status)
        assertTrue(step2.isTerminal)
    }

    @Test
    fun testTwoPartyResolution_nonDesignatedResponderCannotConfirm() = runBlocking {
        val emg = repository.createEmergency(userGaurav, mockLocation)
        repository.acceptEmergency(emg.id, userRahul).getOrThrow()
        repository.acceptEmergency(emg.id, userAman).getOrThrow()
        repository.markArrived(emg.id, userRahul, distanceMeters = 20.0).getOrThrow()

        // Aman was not the arrived responder -> forbidden from confirming
        val result = repository.confirmResolution(emg.id, userAman)
        assertTrue("Non-designated responder cannot confirm resolution", result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is SafeWalkException.ConflictError)
    }

    // =========================================================================
    // 4. RULE 16: RESPONDER REJECT / OPT-OUT REVERSION TO ACTIVE
    // =========================================================================

    @Test
    fun testResponderOptOut_lastResponderLeaves_revertsToActive() = runBlocking {
        val emg = repository.createEmergency(userGaurav, mockLocation)
        val accepted = repository.acceptEmergency(emg.id, userRahul).getOrThrow()
        assertEquals(EmergencyStatus.ACCEPTED, accepted.status)
        assertEquals(1, accepted.acceptedResponders.size)

        // Rahul opts out / withdraws
        val afterOptOut = repository.rejectEmergency(emg.id, userRahul).getOrThrow()
        assertTrue("Accepted responders is now empty", afterOptOut.acceptedResponders.isEmpty())
        assertEquals("Emergency must revert from ACCEPTED back to ACTIVE when last responder leaves", EmergencyStatus.ACTIVE, afterOptOut.status)
    }

    @Test
    fun testResponderOptOut_otherRespondersRemain_staysAccepted() = runBlocking {
        val emg = repository.createEmergency(userGaurav, mockLocation)
        repository.acceptEmergency(emg.id, userRahul).getOrThrow()
        repository.acceptEmergency(emg.id, userAman).getOrThrow()

        // Rahul leaves, Aman remains
        val afterOptOut = repository.rejectEmergency(emg.id, userRahul).getOrThrow()
        assertEquals(1, afterOptOut.acceptedResponders.size)
        assertEquals(userAman.id, afterOptOut.acceptedResponders.first().id)
        assertEquals("Status remains ACCEPTED while other responders remain", EmergencyStatus.ACCEPTED, afterOptOut.status)
    }

    // =========================================================================
    // 5. EXACT 4-STATE FSM ENUM & DTO ROUND-TRIP INTEGRITY
    // =========================================================================

    @Test
    fun testExactFiveStateFsmValues() {
        val names = EmergencyStatus.values().map { it.name }
        assertEquals("FSM must have exactly 5 states", 5, names.size)
        assertTrue(names.contains("ACTIVE"))
        assertTrue(names.contains("ACCEPTED"))
        assertTrue(names.contains("RESOLVED"))
        assertTrue(names.contains("CANCELLED"))
        assertTrue(names.contains("EXPIRED"))
        assertFalse("ARRIVED must NOT be an enum state", names.contains("ARRIVED"))
    }

    @Test
    fun testDtoRoundTripMapping() = runBlocking {
        val emg = repository.createEmergency(userGaurav, mockLocation)
        repository.acceptEmergency(emg.id, userRahul)
        val domain = repository.currentEmergency.value!!

        val dto = domain.toResponseDto()
        assertEquals(domain.status.name, dto.status)
        assertEquals(domain.requester.name, dto.requesterName)

        val reconstructed = dto.toDomain(requester = userGaurav)
        assertEquals(domain.status, reconstructed.status)
        assertEquals(domain.requester.name, reconstructed.requester.name)
    }
}
