package com.example.safewalk

import com.example.safewalk.data.repository.EmergencyRepositoryImpl
import com.example.safewalk.data.source.mock.MockData
import com.example.safewalk.data.source.mock.MockEmergencyDataSource
import com.example.safewalk.domain.model.Emergency
import com.example.safewalk.domain.model.EmergencyStatus
import com.example.safewalk.domain.model.Location
import com.example.safewalk.domain.model.User
import com.example.safewalk.domain.model.UserRole
import com.example.safewalk.domain.model.VerificationStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Phase 5 Unit Test Suite: Complete Requester SOS & Emergency Lifecycle (46 Scenarios).
 */
class Phase5RequesterLifecycleTest {

    private lateinit var dataSource: MockEmergencyDataSource
    private lateinit var emergencyRepo: EmergencyRepositoryImpl

    private val userGaurav = MockData.mockUsers[0] // USER, SW-GAURAV-101
    private val userRahul = MockData.mockUsers[1]  // USER, SW-RAHUL-102
    private val userAman = MockData.mockUsers[2]   // USER, SW-AMAN-103
    private val policeOfficer = MockData.mockUsers[3] // POLICE, verified
    private val hospitalUnit = MockData.mockUsers[4]  // HOSPITAL, verified

    private val baseLocation = Location(28.6139, 77.2090, "Connaught Place, New Delhi")

    @Before
    fun setUp() {
        dataSource = MockEmergencyDataSource()
        emergencyRepo = EmergencyRepositoryImpl(dataSource)
    }

    // =========================================================================
    // 1. SOS CREATION (Scenarios 1-5)
    // =========================================================================

    @Test
    fun test01_authenticatedUserCanCreateSos() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        assertNotNull("Emergency must be successfully instantiated", emg)
    }

    @Test
    fun test02_newEmergencyReceivesUniqueId() = runBlocking {
        val emg1 = emergencyRepo.createEmergency(userGaurav, baseLocation)
        val emg2 = emergencyRepo.createEmergency(userRahul, baseLocation)
        assertNotNull(emg1.id)
        assertNotNull(emg2.id)
        assertTrue(emg1.id.startsWith("EMG-2026-"))
        assertTrue(emg2.id.startsWith("EMG-2026-"))
        assertNotEquals("Each emergency must have a unique ID", emg1.id, emg2.id)
    }

    @Test
    fun test03_currentUserBecomesRequester() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        assertEquals("Current user must be requester", userGaurav.id, emg.requester.id)
        assertTrue(emg.isUserRequester(userGaurav.id))
        assertFalse(emg.isUserRequester(userRahul.id))
    }

    @Test
    fun test04_createdAtIsCaptured() = runBlocking {
        val before = System.currentTimeMillis()
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        val after = System.currentTimeMillis()
        assertTrue("createdAt timestamp must be recorded", emg.createdAt in before..after)
    }

    @Test
    fun test05_initialStateIsActive() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        assertEquals("Initial state must be ACTIVE", EmergencyStatus.ACTIVE, emg.status)
        assertFalse("Active emergency is not terminal", emg.isTerminal)
        assertTrue("Accepted responders initially empty", emg.acceptedResponders.isEmpty())
    }

    // =========================================================================
    // 2. DISCOVERY (Scenarios 6-14)
    // =========================================================================

    @Test
    fun test06_eligibleNormalUsersAreDiscovered() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        // 500m away normal user (Aman is not in Gaurav's emergency contacts)
        val normalUserLocation = Location(baseLocation.latitude + 0.004, baseLocation.longitude + 0.003, "500m Away")
        val eligible = emergencyRepo.isUserEligibleForEmergency(userAman, emg, normalUserLocation)
        assertTrue("Normal user at ~500m must be eligible", eligible)
    }

    @Test
    fun test07_userLessThanOrEqualTo50mIsExcluded() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        // ~20m away (<= 50m exclusion zone)
        val closeLocation = Location(baseLocation.latitude + 0.0001, baseLocation.longitude + 0.0001, "Close")
        val eligible = emergencyRepo.isUserEligibleForEmergency(userAman, emg, closeLocation)
        assertFalse("Normal user <=50m must be excluded", eligible)
    }

    @Test
    fun test08_userGreaterThan2kmIsExcluded() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        // ~4km away (> 2km)
        val farLocation = Location(baseLocation.latitude + 0.035, baseLocation.longitude + 0.035, "Far")
        val eligible = emergencyRepo.isUserEligibleForEmergency(userAman, emg, farLocation)
        assertFalse("Normal user >2km must be excluded", eligible)
    }

    @Test
    fun test09_verifiedPoliceWithin2kmIsEligible() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        val policeLoc = Location(baseLocation.latitude + 0.005, baseLocation.longitude + 0.005, "1km Away")
        val eligible = emergencyRepo.isUserEligibleForEmergency(policeOfficer, emg, policeLoc)
        assertTrue("Verified police within 2km must be eligible", eligible)
    }

    @Test
    fun test10_verifiedPoliceLessThanOrEqualTo50mRemainsEligible() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        // 10m away
        val closeLoc = Location(baseLocation.latitude + 0.00005, baseLocation.longitude + 0.00005, "10m Away")
        val eligible = emergencyRepo.isUserEligibleForEmergency(policeOfficer, emg, closeLoc)
        assertTrue("Verified police must bypass 50m exclusion zone", eligible)
    }

    @Test
    fun test11_unverifiedPoliceExcluded() = runBlocking {
        val unverifiedPolice = policeOfficer.copy(id = "unverified-cop", isVerified = false, verificationStatus = VerificationStatus.PENDING)
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        val loc = Location(baseLocation.latitude + 0.005, baseLocation.longitude + 0.005, "1km Away")
        val eligible = emergencyRepo.isUserEligibleForEmergency(unverifiedPolice, emg, loc)
        assertFalse("Unverified police officer must be excluded from police broadcast", eligible)
    }

    @Test
    fun test12_requesterExcludedFromOwnEmergency() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        val loc = Location(baseLocation.latitude + 0.005, baseLocation.longitude + 0.005, "1km Away")
        val eligible = emergencyRepo.isUserEligibleForEmergency(userGaurav, emg, loc)
        assertFalse("Requester must never be eligible for own emergency", eligible)
    }

    @Test
    fun test13_trustedContactsCanQualifyRegardlessOfDistance() = runBlocking {
        // Gaurav has Rahul as emergency contact
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        val veryFarLocation = Location(baseLocation.latitude + 1.0, baseLocation.longitude + 1.0, "100km away")
        val eligible = emergencyRepo.isUserEligibleForEmergency(userRahul, emg, veryFarLocation)
        assertTrue("Designated emergency contact must discover emergency regardless of distance", eligible)
    }

    @Test
    fun test14_duplicateEligibilityIsDeduplicated() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        val closeLocation = Location(baseLocation.latitude + 0.004, baseLocation.longitude + 0.003, "500m Away")
        val list = emergencyRepo.getEligibleEmergenciesForUser(userRahul, closeLocation)
        val duplicates = list.count { it.id == emg.id }
        assertEquals("Emergency must appear exactly once in eligible list", 1, duplicates)
    }

    // =========================================================================
    // 3. ACCEPTANCE (Scenarios 15-19)
    // =========================================================================

    @Test
    fun test15_responderAcceptanceChangesEmergencyActiveToAccepted() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        assertEquals(EmergencyStatus.ACTIVE, emg.status)
        val accepted = emergencyRepo.acceptEmergency(emg.id, userRahul).getOrThrow()
        assertEquals("Status must transition from ACTIVE to ACCEPTED", EmergencyStatus.ACCEPTED, accepted.status)
    }

    @Test
    fun test16_acceptedResponderIsStored() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        val accepted = emergencyRepo.acceptEmergency(emg.id, userRahul).getOrThrow()
        assertEquals(1, accepted.acceptedResponders.size)
        assertEquals(userRahul.id, accepted.acceptedResponders[0].id)
        assertTrue(accepted.isUserAcceptedResponder(userRahul.id))
    }

    @Test
    fun test17_duplicateResponderIsRejected() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        emergencyRepo.acceptEmergency(emg.id, userRahul)
        val dupResult = emergencyRepo.acceptEmergency(emg.id, userRahul)
        assertTrue("Duplicate responder acceptance must fail", dupResult.isFailure)
        assertTrue(dupResult.exceptionOrNull()?.message?.contains("already accepted", ignoreCase = true) == true)
    }

    @Test
    fun test18_requesterCannotAcceptOwnEmergency() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        val selfAccept = emergencyRepo.acceptEmergency(emg.id, userGaurav)
        assertTrue("Requester cannot accept own SOS", selfAccept.isFailure)
        assertTrue(selfAccept.exceptionOrNull()?.message?.contains("Requester cannot", ignoreCase = true) == true)
    }

    @Test
    fun test19_multipleRespondersAreSupportedWithoutCap() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        val r1 = emergencyRepo.acceptEmergency(emg.id, userRahul).getOrThrow()
        val r2 = emergencyRepo.acceptEmergency(emg.id, userAman).getOrThrow()
        val r3 = emergencyRepo.acceptEmergency(emg.id, policeOfficer).getOrThrow()

        assertEquals("Multiple responders must be stored", 3, r3.acceptedResponders.size)
        assertEquals("Emergency remains ACCEPTED with multiple responders", EmergencyStatus.ACCEPTED, r3.status)
    }

    // =========================================================================
    // 4. ARRIVAL (Scenarios 20-24)
    // =========================================================================

    @Test
    fun test20_30mDistanceCannotArrive() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        emergencyRepo.acceptEmergency(emg.id, userRahul)
        val arrival = emergencyRepo.markArrived(emg.id, userRahul, distanceMeters = 30.0)
        assertTrue("30m must be rejected (<=25m rule)", arrival.isFailure)
        assertTrue(arrival.exceptionOrNull()?.message?.contains("25 meters or less") == true)
    }

    @Test
    fun test21_25mDistanceCanArrive() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        emergencyRepo.acceptEmergency(emg.id, userRahul)
        val arrival = emergencyRepo.markArrived(emg.id, userRahul, distanceMeters = 25.0)
        assertTrue("25.0m must be allowed", arrival.isSuccess)
        val updated = arrival.getOrThrow()
        assertTrue(updated.isResponderArrived(userRahul.id))
        assertEquals(userRahul.id, updated.designatedArrivedResponder?.id)
    }

    @Test
    fun test22_24mDistanceCanArrive() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        emergencyRepo.acceptEmergency(emg.id, userRahul)
        val arrival = emergencyRepo.markArrived(emg.id, userRahul, distanceMeters = 24.0)
        assertTrue("24.0m must be allowed", arrival.isSuccess)
        val updated = arrival.getOrThrow()
        assertTrue(updated.isResponderArrived(userRahul.id))
    }

    @Test
    fun test23_arrivalBelongsToSpecificResponder() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        emergencyRepo.acceptEmergency(emg.id, userRahul)
        emergencyRepo.acceptEmergency(emg.id, userAman)

        // Rahul arrives at 20m
        val updated = emergencyRepo.markArrived(emg.id, userRahul, distanceMeters = 20.0).getOrThrow()

        assertTrue("Rahul must be marked arrived", updated.isResponderArrived(userRahul.id))
        assertFalse("Aman must NOT be marked arrived", updated.isResponderArrived(userAman.id))
    }

    @Test
    fun test24_arrivalDoesNotAutomaticallyResolveEmergency() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        emergencyRepo.acceptEmergency(emg.id, userRahul)
        val updated = emergencyRepo.markArrived(emg.id, userRahul, distanceMeters = 20.0).getOrThrow()

        assertFalse("Arrival must not resolve emergency", updated.isFullyResolved)
        assertFalse("Emergency remains non-terminal after arrival", updated.isTerminal)
        assertEquals("Emergency status remains ACCEPTED", EmergencyStatus.ACCEPTED, updated.status)
    }

    // =========================================================================
    // 5. PRIVACY (Scenarios 25-28)
    // =========================================================================

    @Test
    fun test25_requesterSeesAcceptedResponders() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        emergencyRepo.acceptEmergency(emg.id, userRahul)
        val canView = emergencyRepo.canViewDetailedLocation(emg, userGaurav)
        assertTrue("Requester can view authorized location telemetry", canView)
    }

    @Test
    fun test26_acceptedResponderSeesRequester() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        val accepted = emergencyRepo.acceptEmergency(emg.id, userRahul).getOrThrow()
        val canView = emergencyRepo.canViewDetailedLocation(accepted, userRahul)
        assertTrue("Accepted responder can view requester location", canView)
    }

    @Test
    fun test27_unacceptedNearbyUserCannotSeeDetailedParticipantLocation() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        val canView = emergencyRepo.canViewDetailedLocation(emg, userAman)
        assertFalse("Unaccepted user cannot view detailed participant location", canView)
    }

    @Test
    fun test28_unrelatedUserCannotSeeParticipantLocation() = runBlocking {
        val unrelatedUser = User(id = "stranger-99", safeWalkId = "SW-STRANGER", name = "Stranger", phone = "0", email = "s@s.com", role = UserRole.USER)
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        val canView = emergencyRepo.canViewDetailedLocation(emg, unrelatedUser)
        assertFalse("Unrelated user cannot view detailed participant location", canView)
    }

    // =========================================================================
    // 6. CANCELLATION (Scenarios 29-35)
    // =========================================================================

    @Test
    fun test29_cancelVisibleImmediately() {
        val now = 1000000L
        val emg = Emergency(id = "EMG-1", requester = userGaurav, createdAt = now, requesterLocation = baseLocation)
        assertTrue("Cancel button must be visible at t=0", emg.canCancel(now))
        assertEquals(5000L, emg.remainingCancellationTimeMillis(now))
    }

    @Test
    fun test30_cancelVisibleAt4999ms() {
        val created = 1000000L
        val emg = Emergency(id = "EMG-1", requester = userGaurav, createdAt = created, requesterLocation = baseLocation)
        assertTrue("Cancel button must be visible at 4.999s", emg.canCancel(created + 4999L))
        assertEquals(1L, emg.remainingCancellationTimeMillis(created + 4999L))
    }

    @Test
    fun test31_cancelHiddenAt5000ms() {
        val created = 1000000L
        val emg = Emergency(id = "EMG-1", requester = userGaurav, createdAt = created, requesterLocation = baseLocation)
        assertFalse("Cancel button must be hidden at exactly 5.000s", emg.canCancel(created + 5000L))
        assertEquals(0L, emg.remainingCancellationTimeMillis(created + 5000L))
    }

    @Test
    fun test32_cancellationSucceedsBefore5Seconds() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        val result = emergencyRepo.cancelEmergency(emg.id, "False alarm")
        assertTrue("Cancellation within 5s must succeed", result.isSuccess)
        val cancelled = result.getOrThrow()
        assertEquals(EmergencyStatus.CANCELLED, cancelled.status)
        assertTrue(cancelled.isTerminal)
    }

    @Test
    fun test33_cancellationRejectedAfter5Seconds() = runBlocking {
        // Create emergency with createdAt 6 seconds in the past
        val oldCreatedAt = System.currentTimeMillis() - 6000L
        val emg = Emergency(id = "EMG-OLD", requester = userGaurav, createdAt = oldCreatedAt, requesterLocation = baseLocation)
        dataSource.addEmergencyForTesting(emg)

        val result = emergencyRepo.cancelEmergency(emg.id, "Late cancel")
        assertTrue("Cancellation after 5s must be rejected by repository", result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("expired") == true)
    }

    @Test
    fun test34_recompositionDoesNotResetTimer() {
        val created = 1000000L
        val emg = Emergency(id = "EMG-1", requester = userGaurav, createdAt = created, requesterLocation = baseLocation)
        // Simulate recomposition 3 seconds later
        val remaining1 = emg.remainingCancellationTimeMillis(created + 3000L)
        // Simulate second recomposition at same timestamp
        val remaining2 = emg.remainingCancellationTimeMillis(created + 3000L)
        assertEquals("Recomposition must not alter remaining time", 2000L, remaining1)
        assertEquals("Recomposition must not alter remaining time", remaining1, remaining2)
    }

    @Test
    fun test35_navigationDoesNotResetTimer() {
        val created = 1000000L
        val emg = Emergency(id = "EMG-1", requester = userGaurav, createdAt = created, requesterLocation = baseLocation)
        val remainingAt4s = emg.remainingCancellationTimeMillis(created + 4000L)
        assertEquals("After 4 seconds, exactly 1 second remains", 1000L, remainingAt4s)
        val remainingAt5s = emg.remainingCancellationTimeMillis(created + 5000L)
        assertEquals("After 5 seconds, 0 seconds remain", 0L, remainingAt5s)
    }

    // =========================================================================
    // 7. RESOLUTION (Scenarios 36-40)
    // =========================================================================

    @Test
    fun test36_requesterConfirmationAloneDoesNotResolve() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        emergencyRepo.acceptEmergency(emg.id, userRahul)
        emergencyRepo.markArrived(emg.id, userRahul, 20.0)

        val confirmed = emergencyRepo.confirmResolution(emg.id, userGaurav).getOrThrow()
        assertTrue(confirmed.requesterConfirmedResolution)
        assertFalse(confirmed.responderConfirmedResolution)
        assertFalse("Requester alone must not resolve emergency", confirmed.isFullyResolved)
        assertNotEquals(EmergencyStatus.RESOLVED, confirmed.status)
    }

    @Test
    fun test37_responderConfirmationAloneDoesNotResolve() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        emergencyRepo.acceptEmergency(emg.id, userRahul)
        emergencyRepo.markArrived(emg.id, userRahul, 20.0)

        val confirmed = emergencyRepo.confirmResolution(emg.id, userRahul).getOrThrow()
        assertFalse(confirmed.requesterConfirmedResolution)
        assertTrue(confirmed.responderConfirmedResolution)
        assertFalse("Responder alone must not resolve emergency", confirmed.isFullyResolved)
        assertNotEquals(EmergencyStatus.RESOLVED, confirmed.status)
    }

    @Test
    fun test38_correctRequesterAndDesignatedArrivedResponderResolves() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        emergencyRepo.acceptEmergency(emg.id, userRahul)
        emergencyRepo.markArrived(emg.id, userRahul, 20.0)

        // Both confirm
        emergencyRepo.confirmResolution(emg.id, userGaurav)
        val fullyResolved = emergencyRepo.confirmResolution(emg.id, userRahul).getOrThrow()

        assertTrue(fullyResolved.requesterConfirmedResolution)
        assertTrue(fullyResolved.responderConfirmedResolution)
        assertTrue("Dual confirmation must mark emergency fully resolved", fullyResolved.isFullyResolved)
        assertEquals(EmergencyStatus.RESOLVED, fullyResolved.status)
        assertTrue(fullyResolved.isTerminal)
        assertNotNull(fullyResolved.resolvedAt)
    }

    @Test
    fun test39_otherResponderCannotReplaceDesignatedArrivedResponder() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        emergencyRepo.acceptEmergency(emg.id, userRahul)
        emergencyRepo.acceptEmergency(emg.id, userAman)

        // Only Rahul arrives (designated arrived responder)
        emergencyRepo.markArrived(emg.id, userRahul, 20.0)

        // Aman (accepted, but NOT arrived) attempts to confirm resolution
        val amanConfirm = emergencyRepo.confirmResolution(emg.id, userAman)
        assertTrue("Non-arrived responder cannot confirm resolution", amanConfirm.isFailure)
        assertTrue(amanConfirm.exceptionOrNull()?.message?.contains("designated arrived responder") == true)
    }

    @Test
    fun test40_resolvedDisablesEmergencyActions() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        emergencyRepo.acceptEmergency(emg.id, userRahul)
        emergencyRepo.markArrived(emg.id, userRahul, 20.0)
        emergencyRepo.confirmResolution(emg.id, userGaurav)
        val resolved = emergencyRepo.confirmResolution(emg.id, userRahul).getOrThrow()

        // Further actions must be rejected
        val furtherAccept = emergencyRepo.acceptEmergency(resolved.id, userAman)
        assertTrue("Cannot accept resolved emergency", furtherAccept.isFailure)

        val furtherArrive = emergencyRepo.markArrived(resolved.id, userAman, 20.0)
        assertTrue("Cannot arrive at resolved emergency", furtherArrive.isFailure)

        val furtherConfirm = emergencyRepo.confirmResolution(resolved.id, userGaurav)
        assertTrue("Cannot confirm resolved emergency", furtherConfirm.isFailure)
    }

    // =========================================================================
    // 8. TERMINAL STATES (Scenarios 41-43)
    // =========================================================================

    @Test
    fun test41_cancelledCannotBeAccepted() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        emergencyRepo.cancelEmergency(emg.id, "Cancelled")

        val result = emergencyRepo.acceptEmergency(emg.id, userRahul)
        assertTrue("Cannot accept cancelled emergency", result.isFailure)
    }

    @Test
    fun test42_resolvedCannotBeAccepted() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        emergencyRepo.acceptEmergency(emg.id, userRahul)
        emergencyRepo.markArrived(emg.id, userRahul, 20.0)
        emergencyRepo.confirmResolution(emg.id, userGaurav)
        emergencyRepo.confirmResolution(emg.id, userRahul)

        val result = emergencyRepo.acceptEmergency(emg.id, userAman)
        assertTrue("Cannot accept resolved emergency", result.isFailure)
    }

    @Test
    fun test43_terminalEmergencyRemainsInHistory() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        emergencyRepo.cancelEmergency(emg.id, "False alarm")

        val history = emergencyRepo.getEmergencyHistory(userGaurav.id).first()
        assertTrue("Cancelled emergency must be preserved in history", history.any { it.id == emg.id })
    }

    // =========================================================================
    // 9. MULTIPLE EMERGENCIES (Scenarios 44-46)
    // =========================================================================

    @Test
    fun test44_multipleActiveEmergenciesCoexist() = runBlocking {
        val emg1 = emergencyRepo.createEmergency(userGaurav, baseLocation)
        val emg2 = emergencyRepo.createEmergency(userRahul, baseLocation)
        val emg3 = emergencyRepo.createEmergency(userAman, baseLocation)

        val activeList = emergencyRepo.activeEmergencies.value
        assertTrue(activeList.any { it.id == emg1.id })
        assertTrue(activeList.any { it.id == emg2.id })
        assertTrue(activeList.any { it.id == emg3.id })
    }

    @Test
    fun test45_oneEmergencyDoesNotOverwriteAnother() = runBlocking {
        val emg1 = emergencyRepo.createEmergency(userGaurav, baseLocation)
        val emg2 = emergencyRepo.createEmergency(userRahul, baseLocation)

        // Cancel emg1
        emergencyRepo.cancelEmergency(emg1.id, "Test Cancel")

        val fetchedEmg1 = emergencyRepo.getEmergencyById(emg1.id).first()
        val fetchedEmg2 = emergencyRepo.getEmergencyById(emg2.id).first()

        assertEquals(EmergencyStatus.CANCELLED, fetchedEmg1?.status)
        assertEquals("Emg2 must remain ACTIVE when emg1 is cancelled", EmergencyStatus.ACTIVE, fetchedEmg2?.status)
    }

    @Test
    fun test46_userCanBeRequesterInOneEmergencyAndResponderInAnother() = runBlocking {
        // Emergency A: Gaurav is Requester, Rahul is Responder
        val emgA = emergencyRepo.createEmergency(userGaurav, baseLocation)
        val acceptA = emergencyRepo.acceptEmergency(emgA.id, userRahul).getOrThrow()

        // Emergency B: Rahul is Requester, Gaurav is Responder
        val emgB = emergencyRepo.createEmergency(userRahul, baseLocation)
        val acceptB = emergencyRepo.acceptEmergency(emgB.id, userGaurav).getOrThrow()

        // Assert Emergency A roles
        assertTrue(acceptA.isUserRequester(userGaurav.id))
        assertTrue(acceptA.isUserAcceptedResponder(userRahul.id))
        assertFalse(acceptA.isUserRequester(userRahul.id))

        // Assert Emergency B roles
        assertTrue(acceptB.isUserRequester(userRahul.id))
        assertTrue(acceptB.isUserAcceptedResponder(userGaurav.id))
        assertFalse(acceptB.isUserRequester(userGaurav.id))
    }
}
