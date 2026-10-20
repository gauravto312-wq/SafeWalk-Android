package com.example.safewalk

import com.example.safewalk.data.repository.EmergencyRepositoryImpl
import com.example.safewalk.data.source.mock.MockEmergencyDataSource
import com.example.safewalk.domain.model.Emergency
import com.example.safewalk.domain.model.EmergencyStatus
import com.example.safewalk.domain.model.Location
import com.example.safewalk.domain.model.User
import com.example.safewalk.domain.model.UserRole
import com.example.safewalk.domain.model.VerificationStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Regression test suite verifying requester cancellation window (strict 5 seconds)
 * and requester resolution availability (<= 25m of any accepted responder).
 */
class RequesterCancellationAndResolutionRegressionTest {

    private lateinit var dataSource: MockEmergencyDataSource
    private lateinit var emergencyRepo: EmergencyRepositoryImpl

    private val baseLocation = Location(28.6139, 77.2090, "Connaught Place, New Delhi")

    private val userGaurav = User(
        id = "user-001",
        safeWalkId = "SW-GAURAV-101",
        name = "Gaurav Mishra",
        phone = "+91 98765 43210",
        email = "gaurav@safewalk.local",
        role = UserRole.USER,
        isVerified = true,
        status = "Ready",
        verificationStatus = VerificationStatus.NONE,
        emergencyContacts = listOf("SW-RAHUL-102")
    )

    private val userRahul = User(
        id = "user-002",
        safeWalkId = "SW-RAHUL-102",
        name = "Rahul Sharma",
        phone = "+91 98765 43211",
        email = "rahul@safewalk.local",
        role = UserRole.USER,
        isVerified = true,
        status = "Ready",
        verificationStatus = VerificationStatus.NONE
    )

    private val userAman = User(
        id = "user-003",
        safeWalkId = "SW-AMAN-103",
        name = "Aman Verma",
        phone = "+91 98765 43212",
        email = "aman@safewalk.local",
        role = UserRole.USER,
        isVerified = false,
        status = "Standby",
        verificationStatus = VerificationStatus.NONE
    )

    @Before
    fun setUp() {
        dataSource = MockEmergencyDataSource()
        emergencyRepo = EmergencyRepositoryImpl(dataSource)
    }

    // =========================================================================
    // 1. CANCELLATION 5-SECOND WINDOW REGRESSION TESTS
    // =========================================================================

    @Test
    fun test01_t1s_cancelVisible() {
        val created = 1000000L
        val emg = Emergency(
            id = "EMG-REG-01",
            requester = userGaurav,
            createdAt = created,
            requesterLocation = baseLocation
        )
        // t = 1.000s (elapsed = 1000ms < 5000ms)
        val queryTime = created + 1000L
        assertTrue("At t=1s, Cancel SOS button must be visible", emg.canCancel(queryTime))
        assertEquals(4000L, emg.remainingCancellationTimeMillis(queryTime))
    }

    @Test
    fun test02_t4999ms_cancelVisible() {
        val created = 1000000L
        val emg = Emergency(
            id = "EMG-REG-02",
            requester = userGaurav,
            createdAt = created,
            requesterLocation = baseLocation
        )
        // t = 4.999s (elapsed = 4999ms < 5000ms)
        val queryTime = created + 4999L
        assertTrue("At t=4.999s, Cancel SOS button must still be visible", emg.canCancel(queryTime))
        assertEquals(1L, emg.remainingCancellationTimeMillis(queryTime))
    }

    @Test
    fun test03_tGte5s_cancelHidden() {
        val created = 1000000L
        val emg = Emergency(
            id = "EMG-REG-03",
            requester = userGaurav,
            createdAt = created,
            requesterLocation = baseLocation
        )
        // At exactly t = 5.000s
        assertFalse("At t=5.000s, Cancel SOS button must be hidden", emg.canCancel(created + 5000L))
        assertEquals(0L, emg.remainingCancellationTimeMillis(created + 5000L))

        // At t = 5.001s
        assertFalse("At t=5.001s, Cancel SOS button must remain hidden", emg.canCancel(created + 5001L))
        assertEquals(0L, emg.remainingCancellationTimeMillis(created + 5001L))

        // At t = 10s
        assertFalse("At t=10s, Cancel SOS button must remain hidden", emg.canCancel(created + 10000L))
        assertEquals(0L, emg.remainingCancellationTimeMillis(created + 10000L))
    }

    @Test
    fun test04_tGte5s_cancelRepositoryActionRejected() = runBlocking {
        // Create emergency with createdAt 6 seconds ago (elapsed >= 5000ms)
        val oldCreatedAt = System.currentTimeMillis() - 6000L
        val emg = Emergency(
            id = "EMG-REG-04",
            requester = userGaurav,
            createdAt = oldCreatedAt,
            requesterLocation = baseLocation
        )
        dataSource.addEmergencyForTesting(emg)

        val cancelResult = emergencyRepo.cancelEmergency(emg.id, "Late cancel attempt")
        assertTrue("Cancel action must be rejected by repository when elapsed >= 5000ms", cancelResult.isFailure)
        assertTrue(
            "Rejection error must state window has expired",
            cancelResult.exceptionOrNull()?.message?.contains("expired") == true
        )
    }

    // =========================================================================
    // 2. RESOLUTION DISTANCE THRESHOLD REGRESSION TESTS (<= 25m)
    // =========================================================================

    @Test
    fun test05_responder100m_resolveHidden() {
        val emg = Emergency(
            id = "EMG-REG-05",
            requester = userGaurav,
            status = EmergencyStatus.ACCEPTED,
            requesterLocation = baseLocation,
            acceptedResponders = listOf(userRahul),
            responderDistances = mapOf(userRahul.id to 100.0)
        )
        assertFalse("When responder is at 100m, Resolve button must be hidden", emg.canRequesterResolve())
    }

    @Test
    fun test06_responder26m_resolveHidden() {
        val emg = Emergency(
            id = "EMG-REG-06",
            requester = userGaurav,
            status = EmergencyStatus.ACCEPTED,
            requesterLocation = baseLocation,
            acceptedResponders = listOf(userRahul),
            responderDistances = mapOf(userRahul.id to 26.0)
        )
        assertFalse("When responder is at 26m (>25m), Resolve button must be hidden", emg.canRequesterResolve())
    }

    @Test
    fun test07_responderExactly25m_resolveVisible() {
        val emg = Emergency(
            id = "EMG-REG-07",
            requester = userGaurav,
            status = EmergencyStatus.ACCEPTED,
            requesterLocation = baseLocation,
            acceptedResponders = listOf(userRahul),
            responderDistances = mapOf(userRahul.id to 25.0)
        )
        assertTrue("When responder is exactly at 25m (<=25m), Resolve button must be visible", emg.canRequesterResolve())
    }

    @Test
    fun test08_anyOneOfMultipleRespondersLte25m_resolveVisible() {
        // Multi-responder: Rahul is at 100m, Aman reaches 25m
        val emg = Emergency(
            id = "EMG-REG-08",
            requester = userGaurav,
            status = EmergencyStatus.ACCEPTED,
            requesterLocation = baseLocation,
            acceptedResponders = listOf(userRahul, userAman),
            responderDistances = mapOf(
                userRahul.id to 100.0,
                userAman.id to 25.0
            )
        )
        assertTrue("When ANY one of multiple responders reaches <= 25m, Resolve button must be visible", emg.canRequesterResolve())

        // Also test where Aman is at 20m and Rahul is at 60m
        val emg2 = emg.copy(
            responderDistances = mapOf(
                userRahul.id to 60.0,
                userAman.id to 20.0
            )
        )
        assertTrue("When Aman is at 20m (<25m), Resolve button must be visible", emg2.canRequesterResolve())
    }

    @Test
    fun test09_resolvingChangesEmergencyToResolved() = runBlocking {
        // Setup: Gaurav creates SOS, Rahul accepts, Rahul reaches 25m
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        emergencyRepo.acceptEmergency(emg.id, userRahul)
        emergencyRepo.updateResponderDistance(emg.id, userRahul.id, 25.0)

        // Verify resolve is available
        val currentEmg = dataSource.currentEmergency.value ?: emg
        assertTrue("Resolve must be available when responder is at 25m", currentEmg.canRequesterResolve())

        // Requester resolves emergency
        val resolveResult = emergencyRepo.resolveEmergency(emg.id, userGaurav)
        assertTrue("Resolving emergency must succeed", resolveResult.isSuccess)

        val resolved = resolveResult.getOrThrow()
        assertEquals("Emergency status must become RESOLVED", EmergencyStatus.RESOLVED, resolved.status)
        assertTrue("Emergency must be in terminal state", resolved.isTerminal)
        assertNotNull("resolvedAt timestamp must be recorded", resolved.resolvedAt)
        assertFalse("Terminal emergency cannot be resolved again", resolved.canRequesterResolve())
        assertFalse("Terminal emergency cannot be cancelled", resolved.canCancel())
    }

    @Test
    fun test10_resolveRejectedIfNoResponderWithin25m() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        emergencyRepo.acceptEmergency(emg.id, userRahul)
        emergencyRepo.updateResponderDistance(emg.id, userRahul.id, 100.0)

        val resolveResult = emergencyRepo.resolveEmergency(emg.id, userGaurav)
        assertTrue("Resolve must be rejected if no responder is within 25m", resolveResult.isFailure)
        assertTrue(
            "Rejection error must mention <= 25m",
            resolveResult.exceptionOrNull()?.message?.contains("<= 25m") == true
        )
    }

    @Test
    fun test11_cancelAndResolveMutuallyExclusiveAfter5Seconds() {
        val created = 1000000L
        // At t = 6s (>5s), Aman is at 25m
        val emg = Emergency(
            id = "EMG-REG-11",
            requester = userGaurav,
            createdAt = created,
            status = EmergencyStatus.ACCEPTED,
            requesterLocation = baseLocation,
            acceptedResponders = listOf(userAman),
            responderDistances = mapOf(userAman.id to 25.0)
        )
        val queryTime = created + 6000L

        // Cancel must be FALSE
        assertFalse("After 5s, Cancel SOS is unavailable", emg.canCancel(queryTime))
        // Resolve must be TRUE
        assertTrue("After 5s when responder at 25m, Resolve is available", emg.canRequesterResolve())
        // Both buttons are never shown simultaneously after the 5-second window
        assertFalse("Both buttons must NOT be simultaneously available after 5s", emg.canCancel(queryTime) && emg.canRequesterResolve())
    }
}
