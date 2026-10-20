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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Phase 6 Unit Test Suite: Complete Responder & Multi-Responder Lifecycle (40 Scenarios).
 */
class Phase6ResponderLifecycleTest {

    private lateinit var dataSource: MockEmergencyDataSource
    private lateinit var emergencyRepo: EmergencyRepositoryImpl

    private val userGaurav = MockData.mockUsers[0] // USER, SW-GAURAV-101 (has SW-RAHUL-102 in emergencyContacts)
    private val userRahul = MockData.mockUsers[1]  // USER, SW-RAHUL-102
    private val userAman = MockData.mockUsers[2]   // USER, SW-AMAN-103 (regular civilian, not in contacts)
    private val userPriya = User(
        id = "user-007",
        safeWalkId = "SW-PRIYA-107",
        name = "Priya Nair",
        phone = "+91 98765 43217",
        email = "priya@safewalk.local",
        role = UserRole.USER,
        isVerified = true,
        status = "Ready",
        verificationStatus = VerificationStatus.NONE
    )
    private val policeOfficer = MockData.mockUsers[3] // POLICE, verified
    private val unverifiedPolice = User(
        id = "user-008",
        safeWalkId = "SW-POLICE-UNV",
        name = "Cadet Rajesh",
        phone = "+91 98765 43218",
        email = "rajesh@police.local",
        role = UserRole.POLICE,
        isVerified = false,
        status = "Verification Pending",
        verificationStatus = VerificationStatus.PENDING
    )

    private val baseLocation = Location(28.6139, 77.2090, "Connaught Place, New Delhi")

    @Before
    fun setUp() {
        dataSource = MockEmergencyDataSource()
        emergencyRepo = EmergencyRepositoryImpl(dataSource)
    }

    // =========================================================================
    // 1. DISCOVERY (Scenarios 1-7)
    // =========================================================================

    @Test
    fun test01_eligibleUserSeesEmergency() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        // User Aman is ~500m away, normal civilian, not requester, not in contacts
        val amanLocation = Location(baseLocation.latitude + 0.004, baseLocation.longitude + 0.003, "500m away")
        val eligible = emergencyRepo.isUserEligibleForEmergency(userAman, emg, amanLocation)
        assertTrue("Civilian between 50m and 2km must be eligible", eligible)
    }

    @Test
    fun test02_userLessThanOrEqualTo50mIsExcluded() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        // ~20m away (<= 50m civilian exclusion zone)
        val closeLocation = Location(baseLocation.latitude + 0.0001, baseLocation.longitude + 0.0001, "20m away")
        val eligible = emergencyRepo.isUserEligibleForEmergency(userAman, emg, closeLocation)
        assertFalse("Civilian <=50m must be excluded to prevent crowding", eligible)
    }

    @Test
    fun test03_userGreaterThan2kmIsExcluded() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        // ~4km away (> 2km civilian discovery radius)
        val farLocation = Location(baseLocation.latitude + 0.035, baseLocation.longitude + 0.035, "4km away")
        val eligible = emergencyRepo.isUserEligibleForEmergency(userAman, emg, farLocation)
        assertFalse("Civilian >2km must be excluded from discovery feed", eligible)
    }

    @Test
    fun test04_verifiedPoliceLessThanOrEqualTo2kmIsEligible() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        val policeLocation = Location(baseLocation.latitude + 0.005, baseLocation.longitude + 0.005, "1km away")
        val eligible = emergencyRepo.isUserEligibleForEmergency(policeOfficer, emg, policeLocation)
        assertTrue("Verified police within 2km must be eligible", eligible)
    }

    @Test
    fun test05_verifiedPoliceLessThanOrEqualTo50mIsEligible() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        // Verified police at 15m (<= 50m) - 50m exclusion does NOT apply to verified police
        val closeLocation = Location(baseLocation.latitude + 0.0001, baseLocation.longitude + 0.0001, "15m away")
        val eligible = emergencyRepo.isUserEligibleForEmergency(policeOfficer, emg, closeLocation)
        assertTrue("Verified police within 50m must be eligible (no exclusion zone)", eligible)
    }

    @Test
    fun test06_unverifiedPoliceIsExcluded() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        val loc = Location(baseLocation.latitude + 0.005, baseLocation.longitude + 0.005, "1km away")
        val eligible = emergencyRepo.isUserEligibleForEmergency(unverifiedPolice, emg, loc)
        assertFalse("Unverified police must NOT be eligible for emergency response", eligible)
    }

    @Test
    fun test07_requesterCannotSeeOwnEmergencyAsResponder() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        val eligible = emergencyRepo.isUserEligibleForEmergency(userGaurav, emg, baseLocation)
        assertFalse("Requester can NEVER discover or respond to their own emergency", eligible)
    }

    // =========================================================================
    // 2. ACCEPTANCE (Scenarios 8-14)
    // =========================================================================

    @Test
    fun test08_eligibleUserCanAccept() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        val result = emergencyRepo.acceptEmergency(emg.id, userRahul)
        assertTrue("Eligible user must be able to accept emergency", result.isSuccess)
    }

    @Test
    fun test09_acceptanceChangesActiveToAccepted() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        assertEquals(EmergencyStatus.ACTIVE, emg.status)
        val accepted = emergencyRepo.acceptEmergency(emg.id, userRahul).getOrThrow()
        assertEquals("First responder acceptance transitions ACTIVE to ACCEPTED", EmergencyStatus.ACCEPTED, accepted.status)
    }

    @Test
    fun test10_responderBecomesContextualResponder() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        val accepted = emergencyRepo.acceptEmergency(emg.id, userRahul).getOrThrow()
        assertTrue("Accepted user becomes contextual RESPONDER", accepted.isUserAcceptedResponder(userRahul.id))
        assertFalse("Responder is NOT the requester", accepted.isUserRequester(userRahul.id))
        assertEquals("Permanent account role remains ROLE_USER", UserRole.USER, userRahul.role)
    }

    @Test
    fun test11_duplicateAcceptanceRejected() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        emergencyRepo.acceptEmergency(emg.id, userRahul).getOrThrow()
        val dupResult = emergencyRepo.acceptEmergency(emg.id, userRahul)
        assertTrue("Duplicate acceptance by same user must be rejected", dupResult.isFailure)
        assertTrue(dupResult.exceptionOrNull()?.message?.contains("already accepted") == true)
    }

    @Test
    fun test12_requesterSelfAcceptanceRejected() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        val result = emergencyRepo.acceptEmergency(emg.id, userGaurav)
        assertTrue("Requester cannot accept own emergency", result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Requester cannot accept") == true)
    }

    @Test
    fun test13_multipleRespondersSupported() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        emergencyRepo.acceptEmergency(emg.id, userRahul).getOrThrow()
        val withTwo = emergencyRepo.acceptEmergency(emg.id, userAman).getOrThrow()
        assertEquals("Must support multiple responders concurrently", 2, withTwo.acceptedResponders.size)
        assertTrue(withTwo.isUserAcceptedResponder(userRahul.id))
        assertTrue(withTwo.isUserAcceptedResponder(userAman.id))
    }

    @Test
    fun test14_noFixedResponderCap() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        emergencyRepo.acceptEmergency(emg.id, userRahul).getOrThrow()
        emergencyRepo.acceptEmergency(emg.id, userAman).getOrThrow()
        emergencyRepo.acceptEmergency(emg.id, policeOfficer).getOrThrow()
        val withFour = emergencyRepo.acceptEmergency(emg.id, userPriya).getOrThrow()
        assertEquals("No fixed cap on accepted responders", 4, withFour.acceptedResponders.size)
    }

    // =========================================================================
    // 3. PRIVACY (Scenarios 15-19)
    // =========================================================================

    @Test
    fun test15_preAcceptanceExactLocationHidden() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        val canView = emergencyRepo.canViewDetailedLocation(emg, userAman)
        assertFalse("Unaccepted user MUST NOT see exact GPS location", canView)
    }

    @Test
    fun test16_acceptedResponderSeesRequester() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        val accepted = emergencyRepo.acceptEmergency(emg.id, userRahul).getOrThrow()
        val canView = emergencyRepo.canViewDetailedLocation(accepted, userRahul)
        assertTrue("Accepted responder CAN see requester's location", canView)
    }

    @Test
    fun test17_requesterSeesAcceptedResponder() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        val accepted = emergencyRepo.acceptEmergency(emg.id, userRahul).getOrThrow()
        val canView = emergencyRepo.canViewDetailedLocation(accepted, userGaurav)
        assertTrue("Requester CAN see accepted responder's live location", canView)
    }

    @Test
    fun test18_unacceptedUserCannotSeeDetailedParticipantLocation() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        emergencyRepo.acceptEmergency(emg.id, userRahul).getOrThrow()
        val unaccepted = emergencyRepo.getEmergencyById(emg.id).first()!!
        val canView = emergencyRepo.canViewDetailedLocation(unaccepted, userAman)
        assertFalse("Unaccepted nearby user CANNOT see detailed location", canView)
    }

    @Test
    fun test19_unrelatedUserCannotSeeParticipantLocation() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        emergencyRepo.acceptEmergency(emg.id, userRahul).getOrThrow()
        val unaccepted = emergencyRepo.getEmergencyById(emg.id).first()!!
        val canView = emergencyRepo.canViewDetailedLocation(unaccepted, userPriya)
        assertFalse("Unrelated user CANNOT see participant location", canView)
    }

    // =========================================================================
    // 4. MOVEMENT (Scenarios 20-21)
    // =========================================================================

    @Test
    fun test20_mockDistanceDecreases() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        emergencyRepo.acceptEmergency(emg.id, userRahul).getOrThrow()
        emergencyRepo.updateResponderDistance(emg.id, userRahul.id, 420.0).getOrThrow()

        val step1 = emergencyRepo.updateResponderDistance(emg.id, userRahul.id, 250.0).getOrThrow()
        assertEquals(250.0, step1.responderDistances[userRahul.id]!!, 0.01)

        val step2 = emergencyRepo.updateResponderDistance(emg.id, userRahul.id, 50.0).getOrThrow()
        assertEquals(50.0, step2.responderDistances[userRahul.id]!!, 0.01)
        assertTrue(step2.responderDistances[userRahul.id]!! < step1.responderDistances[userRahul.id]!!)
    }

    @Test
    fun test21_distanceUpdatesAreReflectedInState() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        emergencyRepo.acceptEmergency(emg.id, userRahul).getOrThrow()
        emergencyRepo.updateResponderDistance(emg.id, userRahul.id, 25.0).getOrThrow()

        val fetched = emergencyRepo.getEmergencyById(emg.id).first()!!
        assertEquals("Distance update must be reactive in flow", 25.0, fetched.responderDistances[userRahul.id]!!, 0.01)
    }

    // =========================================================================
    // 5. ARRIVAL (Scenarios 22-28)
    // =========================================================================

    @Test
    fun test22_thirtyMetersCannotArrive() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        emergencyRepo.acceptEmergency(emg.id, userRahul).getOrThrow()

        val result = emergencyRepo.markArrived(emg.id, userRahul, distanceMeters = 30.0)
        assertTrue("Arrival at 30m must be rejected (exceeds 25m)", result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("25 meters or less") == true)
    }

    @Test
    fun test23_twentyFiveMetersCanArrive() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        emergencyRepo.acceptEmergency(emg.id, userRahul).getOrThrow()

        val result = emergencyRepo.markArrived(emg.id, userRahul, distanceMeters = 25.0)
        assertTrue("Arrival at exactly 25.0m must succeed (<= 25m rule)", result.isSuccess)
        val arrived = result.getOrThrow()
        assertTrue(arrived.isResponderArrived(userRahul.id))
    }

    @Test
    fun test24_twentyFourMetersCanArrive() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        emergencyRepo.acceptEmergency(emg.id, userRahul).getOrThrow()

        val result = emergencyRepo.markArrived(emg.id, userRahul, distanceMeters = 24.0)
        assertTrue("Arrival at 24.0m must succeed (<= 25m rule)", result.isSuccess)
        val arrived = result.getOrThrow()
        assertTrue(arrived.isResponderArrived(userRahul.id))
    }

    @Test
    fun test25_arrivalBelongsToResponder() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        emergencyRepo.acceptEmergency(emg.id, userRahul).getOrThrow()
        emergencyRepo.acceptEmergency(emg.id, userAman).getOrThrow()

        // Rahul arrives, Aman does not
        val updated = emergencyRepo.markArrived(emg.id, userRahul, distanceMeters = 20.0).getOrThrow()
        assertTrue("Rahul must be marked arrived", updated.isResponderArrived(userRahul.id))
        assertFalse("Aman must remain en-route (not arrived)", updated.isResponderArrived(userAman.id))
    }

    @Test
    fun test26_firstValidArrivalBecomesDesignatedArrivedResponder() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        emergencyRepo.acceptEmergency(emg.id, userRahul).getOrThrow()
        emergencyRepo.acceptEmergency(emg.id, userAman).getOrThrow()

        // Rahul arrives first
        val updated = emergencyRepo.markArrived(emg.id, userRahul, distanceMeters = 25.0).getOrThrow()
        assertEquals("First arrived responder is designated", userRahul.id, updated.designatedArrivedResponder?.id)
    }

    @Test
    fun test27_otherResponderCannotReplaceDesignatedArrival() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        emergencyRepo.acceptEmergency(emg.id, userRahul).getOrThrow()
        emergencyRepo.acceptEmergency(emg.id, userAman).getOrThrow()

        // Rahul arrives first
        emergencyRepo.markArrived(emg.id, userRahul, distanceMeters = 25.0).getOrThrow()

        // Aman arrives second - rejected by Single Arrival Guard (HTTP 409 Conflict)
        val secondArrivalResult = emergencyRepo.markArrived(emg.id, userAman, distanceMeters = 15.0)
        assertTrue("Second arrival must be rejected by Single Arrival Guard", secondArrivalResult.isFailure)
        assertTrue(
            "Conflict error mentions another responder has already arrived",
            secondArrivalResult.exceptionOrNull()?.message?.contains("already arrived") == true
        )

        val current = emergencyRepo.currentEmergency.value!!
        assertEquals("Designated responder must NOT be overwritten", userRahul.id, current.designatedArrivedResponder?.id)
    }

    @Test
    fun test28_arrivalDoesNotResolveEmergency() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        emergencyRepo.acceptEmergency(emg.id, userRahul).getOrThrow()

        val updated = emergencyRepo.markArrived(emg.id, userRahul, distanceMeters = 25.0).getOrThrow()
        assertEquals("Arrival does NOT change emergency to RESOLVED; status remains ACCEPTED", EmergencyStatus.ACCEPTED, updated.status)
        assertFalse(updated.isTerminal)
    }

    // =========================================================================
    // 6. RESOLUTION (Scenarios 29-33)
    // =========================================================================

    @Test
    fun test29_requesterConfirmsFirstRemainsActiveOrAccepted() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        emergencyRepo.acceptEmergency(emg.id, userRahul).getOrThrow()
        emergencyRepo.markArrived(emg.id, userRahul, distanceMeters = 25.0).getOrThrow()

        val updated = emergencyRepo.confirmResolution(emg.id, userGaurav).getOrThrow()
        assertTrue("Requester is confirmed", updated.requesterConfirmedResolution)
        assertFalse("Responder confirmation is pending", updated.responderConfirmedResolution)
        assertEquals("Status remains ACCEPTED when only requester confirmed", EmergencyStatus.ACCEPTED, updated.status)
        assertFalse("Emergency must not be terminal yet", updated.isTerminal)
    }

    @Test
    fun test30_responderConfirmsFirstRemainsActiveOrAccepted() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        emergencyRepo.acceptEmergency(emg.id, userRahul).getOrThrow()
        emergencyRepo.markArrived(emg.id, userRahul, distanceMeters = 25.0).getOrThrow()

        val updated = emergencyRepo.confirmResolution(emg.id, userRahul).getOrThrow()
        assertFalse("Requester confirmation is pending", updated.requesterConfirmedResolution)
        assertTrue("Responder is confirmed", updated.responderConfirmedResolution)
        assertEquals("Status remains ACCEPTED when only responder confirmed", EmergencyStatus.ACCEPTED, updated.status)
        assertFalse("Emergency must not be terminal yet", updated.isTerminal)
    }

    @Test
    fun test31_requesterAndDesignatedArrivedResponderResolves() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        emergencyRepo.acceptEmergency(emg.id, userRahul).getOrThrow()
        emergencyRepo.markArrived(emg.id, userRahul, distanceMeters = 25.0).getOrThrow()

        // Requester confirms
        emergencyRepo.confirmResolution(emg.id, userGaurav).getOrThrow()
        // Designated arrived responder confirms
        val resolved = emergencyRepo.confirmResolution(emg.id, userRahul).getOrThrow()

        assertEquals("Status becomes RESOLVED when both parties confirm", EmergencyStatus.RESOLVED, resolved.status)
        assertTrue(resolved.isTerminal)
        assertTrue(resolved.isFullyResolved)
        assertNotNull(resolved.resolvedAt)
    }

    @Test
    fun test32_nonDesignatedResponderCannotCompleteResolution() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        emergencyRepo.acceptEmergency(emg.id, userRahul).getOrThrow()
        emergencyRepo.acceptEmergency(emg.id, userAman).getOrThrow()
        emergencyRepo.markArrived(emg.id, userRahul, distanceMeters = 25.0).getOrThrow()

        // Requester confirms
        emergencyRepo.confirmResolution(emg.id, userGaurav).getOrThrow()

        // Aman (non-designated responder) attempts to confirm
        val result = emergencyRepo.confirmResolution(emg.id, userAman)
        assertTrue("Non-designated responder confirmation must be rejected", result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("designated arrived responder") == true)

        // Emergency remains un-resolved
        val fetched = emergencyRepo.getEmergencyById(emg.id).first()!!
        assertNotEquals(EmergencyStatus.RESOLVED, fetched.status)
    }

    @Test
    fun test33_resolvedDisablesActiveResponderActions() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        emergencyRepo.acceptEmergency(emg.id, userRahul).getOrThrow()
        emergencyRepo.markArrived(emg.id, userRahul, distanceMeters = 25.0).getOrThrow()
        emergencyRepo.confirmResolution(emg.id, userGaurav).getOrThrow()
        emergencyRepo.confirmResolution(emg.id, userRahul).getOrThrow()

        // Mutations on resolved emergency must be rejected
        val moveResult = emergencyRepo.updateResponderDistance(emg.id, userRahul.id, 10.0)
        assertTrue("Distance update rejected on resolved emergency", moveResult.isFailure)

        val arriveResult = emergencyRepo.markArrived(emg.id, userRahul, 20.0)
        assertTrue("Arrival rejected on resolved emergency", arriveResult.isFailure)

        val confirmAgain = emergencyRepo.confirmResolution(emg.id, userRahul)
        assertTrue("Confirmation rejected on resolved emergency", confirmAgain.isFailure)
    }

    // =========================================================================
    // 7. TERMINAL (Scenarios 34-37)
    // =========================================================================

    @Test
    fun test34_cancelledEmergencyCannotBeAccepted() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        emergencyRepo.cancelEmergency(emg.id, "False alarm").getOrThrow()

        val result = emergencyRepo.acceptEmergency(emg.id, userRahul)
        assertTrue("Cannot accept a cancelled emergency", result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("terminal") == true)
    }

    @Test
    fun test35_resolvedEmergencyCannotBeAccepted() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        emergencyRepo.acceptEmergency(emg.id, userRahul).getOrThrow()
        emergencyRepo.markArrived(emg.id, userRahul, 25.0).getOrThrow()
        emergencyRepo.confirmResolution(emg.id, userGaurav).getOrThrow()
        emergencyRepo.confirmResolution(emg.id, userRahul).getOrThrow()

        val result = emergencyRepo.acceptEmergency(emg.id, userAman)
        assertTrue("Cannot accept a resolved emergency", result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("terminal") == true)
    }

    @Test
    fun test36_resolvedEmergencyCannotMarkArrived() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        emergencyRepo.acceptEmergency(emg.id, userRahul).getOrThrow()
        emergencyRepo.acceptEmergency(emg.id, userAman).getOrThrow()
        emergencyRepo.markArrived(emg.id, userRahul, 25.0).getOrThrow()
        emergencyRepo.confirmResolution(emg.id, userGaurav).getOrThrow()
        emergencyRepo.confirmResolution(emg.id, userRahul).getOrThrow()

        val result = emergencyRepo.markArrived(emg.id, userAman, 20.0)
        assertTrue("Cannot mark arrived on a resolved emergency", result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("terminal") == true)
    }

    @Test
    fun test37_cancelledEmergencyCannotMarkArrived() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        emergencyRepo.acceptEmergency(emg.id, userRahul).getOrThrow()
        emergencyRepo.cancelEmergency(emg.id, "Emergency cancelled").getOrThrow()

        val result = emergencyRepo.markArrived(emg.id, userRahul, 20.0)
        assertTrue("Cannot mark arrived on a cancelled emergency", result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("terminal") == true)
    }

    // =========================================================================
    // 8. MULTI-EMERGENCY (Scenarios 38-40)
    // =========================================================================

    @Test
    fun test38_multipleEmergenciesCoexist() = runBlocking {
        val emg1 = emergencyRepo.createEmergency(userGaurav, baseLocation)
        val emg2 = emergencyRepo.createEmergency(userAman, baseLocation)

        emergencyRepo.acceptEmergency(emg1.id, userRahul).getOrThrow()
        emergencyRepo.acceptEmergency(emg2.id, userRahul).getOrThrow()

        val activeList = emergencyRepo.activeEmergencies.value
        assertTrue(activeList.any { it.id == emg1.id })
        assertTrue(activeList.any { it.id == emg2.id })
    }

    @Test
    fun test39_oneEmergencyDoesNotOverwriteAnother() = runBlocking {
        val emg1 = emergencyRepo.createEmergency(userGaurav, baseLocation)
        val emg2 = emergencyRepo.createEmergency(userAman, baseLocation)

        emergencyRepo.acceptEmergency(emg1.id, userRahul).getOrThrow()
        emergencyRepo.markArrived(emg1.id, userRahul, 25.0).getOrThrow()
        emergencyRepo.confirmResolution(emg1.id, userGaurav).getOrThrow()
        emergencyRepo.confirmResolution(emg1.id, userRahul).getOrThrow()

        val fetched1 = emergencyRepo.getEmergencyById(emg1.id).first()!!
        val fetched2 = emergencyRepo.getEmergencyById(emg2.id).first()!!

        assertEquals(EmergencyStatus.RESOLVED, fetched1.status)
        assertEquals("Emg2 must remain ACTIVE when emg1 is resolved", EmergencyStatus.ACTIVE, fetched2.status)
    }

    @Test
    fun test40_userCanBeRequesterInOneAndResponderInAnother() = runBlocking {
        // Emergency A: Rahul is RESPONDER to Gaurav's SOS
        val emgA = emergencyRepo.createEmergency(userGaurav, baseLocation)
        val acceptedA = emergencyRepo.acceptEmergency(emgA.id, userRahul).getOrThrow()

        // Emergency B: Rahul is REQUESTER
        val emgB = emergencyRepo.createEmergency(userRahul, baseLocation)

        assertTrue("Rahul is accepted responder in Emergency A", acceptedA.isUserAcceptedResponder(userRahul.id))
        assertFalse("Rahul is NOT requester in Emergency A", acceptedA.isUserRequester(userRahul.id))

        assertTrue("Rahul is requester in Emergency B", emgB.isUserRequester(userRahul.id))
        assertFalse("Rahul is NOT responder in Emergency B", emgB.isUserAcceptedResponder(userRahul.id))
    }
}
