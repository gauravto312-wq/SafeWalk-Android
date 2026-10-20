package com.example.safewalk

import com.example.safewalk.data.repository.EmergencyRepositoryImpl
import com.example.safewalk.data.repository.LocationRepositoryImpl
import com.example.safewalk.data.source.mock.MockEmergencyDataSource
import com.example.safewalk.data.source.mock.MockLocationDataSource
import com.example.safewalk.domain.model.EmergencyRole
import com.example.safewalk.domain.model.EmergencyStatus
import com.example.safewalk.domain.model.Location
import com.example.safewalk.domain.model.LocationStatus
import com.example.safewalk.domain.model.User
import com.example.safewalk.domain.model.UserRole
import com.example.safewalk.domain.model.VerificationStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Phase 7 Test Suite:
 * Comprehensive testing of Mock GPS, Bidirectional Multi-Participant Live Location,
 * Haversine Distance Recalculation, Mock Movement, Stale Location, Terminal Stops,
 * and IDOR-style Authorization & Privacy.
 */
class Phase7MockLiveLocationPrivacyTest {

    private lateinit var emergencyDataSource: MockEmergencyDataSource
    private lateinit var locationDataSource: MockLocationDataSource
    private lateinit var emergencyRepo: EmergencyRepositoryImpl
    private lateinit var locationRepo: LocationRepositoryImpl

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
        verificationStatus = VerificationStatus.NONE
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
        isVerified = true,
        status = "Standby",
        verificationStatus = VerificationStatus.NONE
    )

    private val userRohit = User(
        id = "user-004",
        safeWalkId = "SW-ROHIT-104",
        name = "Rohit Kumar",
        phone = "+91 98765 43213",
        email = "rohit@safewalk.local",
        role = UserRole.USER,
        isVerified = true,
        status = "Active Nearby",
        verificationStatus = VerificationStatus.NONE
    )

    private val userStranger = User(
        id = "user-999",
        safeWalkId = "SW-STRANGER-999",
        name = "Unknown Bystander",
        phone = "+91 99999 00000",
        email = "stranger@safewalk.local",
        role = UserRole.USER,
        isVerified = false,
        status = "Unrelated",
        verificationStatus = VerificationStatus.NONE
    )

    @Before
    fun setUp() {
        emergencyDataSource = MockEmergencyDataSource()
        locationDataSource = MockLocationDataSource()
        emergencyRepo = EmergencyRepositoryImpl(emergencyDataSource, locationDataSource)
        locationRepo = LocationRepositoryImpl(locationDataSource, emergencyDataSource)
    }

    // =========================================================================
    // 1 & 2. BIDIRECTIONAL MUTUAL VISIBILITY AFTER ACCEPTANCE
    // =========================================================================

    @Test
    fun test01_requesterSeesAcceptedResponder() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        emergencyRepo.acceptEmergency(emg.id, userRahul)

        val result = locationRepo.getEmergencyParticipantLocation(emg.id, userRahul.id, viewerId = userGaurav.id)
        assertTrue("Requester must see accepted responder location", result.isSuccess)
        val responderLoc = result.getOrThrow()
        assertNotNull("Responder location must not be null", responderLoc)
        assertEquals(LocationStatus.ACTIVE, responderLoc.status)
    }

    @Test
    fun test02_responderSeesRequesterAfterAcceptance() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        emergencyRepo.acceptEmergency(emg.id, userRahul)

        val result = locationRepo.getEmergencyParticipantLocation(emg.id, userGaurav.id, viewerId = userRahul.id)
        assertTrue("Accepted responder must see requester live location", result.isSuccess)
        val reqLoc = result.getOrThrow()
        assertEquals(baseLocation.latitude, reqLoc.latitude, 0.00001)
        assertEquals(baseLocation.longitude, reqLoc.longitude, 0.00001)
    }

    // =========================================================================
    // 3 & 4. PRE-ACCEPTANCE LOCATION PRIVACY
    // =========================================================================

    @Test
    fun test03_exactRequesterLocationHiddenBeforeAcceptance() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)

        // Unaccepted nearby user (userRahul hasn't accepted yet)
        val accessResult = locationRepo.getEmergencyParticipantLocation(emg.id, userGaurav.id, viewerId = userRahul.id)
        assertTrue("Pre-acceptance access to exact requester GPS must be DENIED", accessResult.isFailure)
        assertTrue(accessResult.exceptionOrNull() is SecurityException)

        // Pre-acceptance discovery only exposes approximate masked coordinates
        val approx = baseLocation.toApproximate()
        assertNotEquals(baseLocation.formattedCoordinates, approx.formattedCoordinates)
        assertTrue(approx.address.contains("Sector"))
    }

    @Test
    fun test04_exactResponderLocationHiddenBeforeAcceptance() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        emergencyRepo.acceptEmergency(emg.id, userRahul)

        // Stranger attempts to view Rahul's responder location
        val accessResult = locationRepo.getEmergencyParticipantLocation(emg.id, userRahul.id, viewerId = userStranger.id)
        assertTrue("Unauthorized access to responder GPS must be DENIED", accessResult.isFailure)
        assertTrue(accessResult.exceptionOrNull() is SecurityException)
    }

    // =========================================================================
    // 5, 6, 7. MULTI-RESPONDER INDEPENDENT LOCATION STATE
    // =========================================================================

    @Test
    fun test05_multipleRespondersVisible() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        emergencyRepo.acceptEmergency(emg.id, userRahul)
        emergencyRepo.acceptEmergency(emg.id, userAman)
        emergencyRepo.acceptEmergency(emg.id, userRohit)

        val participantsRes = locationRepo.getVisibleParticipantLocations(emg.id, viewerId = userGaurav.id)
        assertTrue(participantsRes.isSuccess)
        val participants = participantsRes.getOrThrow()

        // 1 requester + 3 responders = 4 participants
        assertEquals(4, participants.size)
        assertTrue(participants.any { it.userId == userGaurav.id && it.role == EmergencyRole.REQUESTER })
        assertTrue(participants.any { it.userId == userRahul.id && it.role == EmergencyRole.RESPONDER })
        assertTrue(participants.any { it.userId == userAman.id && it.role == EmergencyRole.RESPONDER })
        assertTrue(participants.any { it.userId == userRohit.id && it.role == EmergencyRole.RESPONDER })
    }

    @Test
    fun test06_responderALocationIndependentFromResponderB() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        emergencyRepo.acceptEmergency(emg.id, userRahul)
        emergencyRepo.acceptEmergency(emg.id, userAman)
        emergencyRepo.acceptEmergency(emg.id, userRohit)

        val currentEmg = emergencyDataSource.currentEmergency.value!!
        val rahulLoc = currentEmg.responderLocations[userRahul.id]!!
        val amanLoc = currentEmg.responderLocations[userAman.id]!!
        val rohitLoc = currentEmg.responderLocations[userRohit.id]!!

        // Each responder must have distinct coordinates
        assertFalse("Rahul and Aman must have distinct coordinates",
            rahulLoc.latitude == amanLoc.latitude && rahulLoc.longitude == amanLoc.longitude)
        assertFalse("Aman and Rohit must have distinct coordinates",
            amanLoc.latitude == rohitLoc.latitude && amanLoc.longitude == rohitLoc.longitude)
        assertFalse("Rahul and Rohit must have distinct coordinates",
            rahulLoc.latitude == rohitLoc.latitude && rahulLoc.longitude == rohitLoc.longitude)
    }

    @Test
    fun test07_requesterLocationIndependentFromResponderLocations() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        emergencyRepo.acceptEmergency(emg.id, userRahul)

        val currentEmg = emergencyDataSource.currentEmergency.value!!
        val reqLoc = currentEmg.requesterLocation
        val rahulLoc = currentEmg.responderLocations[userRahul.id]!!

        assertFalse("Requester and responder must not share identical coordinates",
            reqLoc.latitude == rahulLoc.latitude && reqLoc.longitude == rahulLoc.longitude)
    }

    // =========================================================================
    // 8, 9, 10. REQUESTER MOVEMENT PROPAGATION TO ALL RESPONDERS
    // =========================================================================

    @Test
    fun test08_requesterMovementUpdatesResponderA() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        emergencyRepo.acceptEmergency(emg.id, userRahul)

        val newLocation = Location(28.6180, 77.2090, "Connaught Place North Radial")
        emergencyRepo.updateRequesterLocation(emg.id, newLocation)

        val rahulView = locationRepo.getEmergencyParticipantLocation(emg.id, userGaurav.id, viewerId = userRahul.id).getOrThrow()
        assertEquals(28.6180, rahulView.latitude, 0.00001)
        assertEquals(77.2090, rahulView.longitude, 0.00001)
    }

    @Test
    fun test09_requesterMovementUpdatesResponderB() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        emergencyRepo.acceptEmergency(emg.id, userRahul)
        emergencyRepo.acceptEmergency(emg.id, userAman)

        val newLocation = Location(28.6190, 77.2100, "Barakhamba Crossing")
        emergencyRepo.updateRequesterLocation(emg.id, newLocation)

        val amanView = locationRepo.getEmergencyParticipantLocation(emg.id, userGaurav.id, viewerId = userAman.id).getOrThrow()
        assertEquals(28.6190, amanView.latitude, 0.00001)
        assertEquals(77.2100, amanView.longitude, 0.00001)
    }

    @Test
    fun test10_requesterMovementUpdatesResponderC() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        emergencyRepo.acceptEmergency(emg.id, userRahul)
        emergencyRepo.acceptEmergency(emg.id, userAman)
        emergencyRepo.acceptEmergency(emg.id, userRohit)

        val newLocation = Location(28.6200, 77.2110, "Mandi House Junction")
        emergencyRepo.updateRequesterLocation(emg.id, newLocation)

        val rohitView = locationRepo.getEmergencyParticipantLocation(emg.id, userGaurav.id, viewerId = userRohit.id).getOrThrow()
        assertEquals(28.6200, rohitView.latitude, 0.00001)
        assertEquals(77.2110, rohitView.longitude, 0.00001)
    }

    // =========================================================================
    // 11, 12, 13. RESPONDER INDEPENDENT MOVEMENT PROPAGATION
    // =========================================================================

    @Test
    fun test11_responderAMovementUpdatesRequester() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        emergencyRepo.acceptEmergency(emg.id, userRahul)
        emergencyRepo.acceptEmergency(emg.id, userAman)

        val initialAmanLoc = emergencyDataSource.currentEmergency.value!!.responderLocations[userAman.id]!!

        val rahulNewLoc = Location(28.6145, 77.2090, "Rahul moved closer")
        emergencyRepo.updateResponderLocation(emg.id, userRahul.id, rahulNewLoc)

        val gauravViewOfRahul = locationRepo.getEmergencyParticipantLocation(emg.id, userRahul.id, viewerId = userGaurav.id).getOrThrow()
        assertEquals(rahulNewLoc.latitude, gauravViewOfRahul.latitude, 0.00001)

        // Aman's location must NOT change
        val currentAmanLoc = emergencyDataSource.currentEmergency.value!!.responderLocations[userAman.id]!!
        assertEquals(initialAmanLoc.latitude, currentAmanLoc.latitude, 0.00001)
    }

    @Test
    fun test12_responderBMovementUpdatesRequester() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        emergencyRepo.acceptEmergency(emg.id, userRahul)
        emergencyRepo.acceptEmergency(emg.id, userAman)

        val initialRahulLoc = emergencyDataSource.currentEmergency.value!!.responderLocations[userRahul.id]!!

        val amanNewLoc = Location(28.6120, 77.2090, "Aman approaching from South")
        emergencyRepo.updateResponderLocation(emg.id, userAman.id, amanNewLoc)

        val gauravViewOfAman = locationRepo.getEmergencyParticipantLocation(emg.id, userAman.id, viewerId = userGaurav.id).getOrThrow()
        assertEquals(amanNewLoc.latitude, gauravViewOfAman.latitude, 0.00001)

        // Rahul's location must NOT change
        val currentRahulLoc = emergencyDataSource.currentEmergency.value!!.responderLocations[userRahul.id]!!
        assertEquals(initialRahulLoc.latitude, currentRahulLoc.latitude, 0.00001)
    }

    @Test
    fun test13_responderCMovementUpdatesRequester() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        emergencyRepo.acceptEmergency(emg.id, userRahul)
        emergencyRepo.acceptEmergency(emg.id, userAman)
        emergencyRepo.acceptEmergency(emg.id, userRohit)

        val rohitNewLoc = Location(28.6139, 77.2150, "Rohit approaching from East")
        emergencyRepo.updateResponderLocation(emg.id, userRohit.id, rohitNewLoc)

        val gauravViewOfRohit = locationRepo.getEmergencyParticipantLocation(emg.id, userRohit.id, viewerId = userGaurav.id).getOrThrow()
        assertEquals(rohitNewLoc.latitude, gauravViewOfRohit.latitude, 0.00001)
    }

    // =========================================================================
    // 14 & 15. ISOLATION OF COORDINATES
    // =========================================================================

    @Test
    fun test14_requesterMovementDoesNotModifyResponderCoordinates() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        emergencyRepo.acceptEmergency(emg.id, userRahul)

        val originalRahulLoc = emergencyDataSource.currentEmergency.value!!.responderLocations[userRahul.id]!!

        emergencyRepo.updateRequesterLocation(emg.id, Location(28.6200, 77.2090, "Moved North"))

        val postMoveRahulLoc = emergencyDataSource.currentEmergency.value!!.responderLocations[userRahul.id]!!
        assertEquals("Rahul coordinates must remain completely unchanged when requester moves",
            originalRahulLoc.latitude, postMoveRahulLoc.latitude, 0.000001)
        assertEquals(originalRahulLoc.longitude, postMoveRahulLoc.longitude, 0.000001)
    }

    @Test
    fun test15_responderMovementDoesNotModifyRequesterCoordinates() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        emergencyRepo.acceptEmergency(emg.id, userRahul)

        val originalRequesterLoc = emergencyDataSource.currentEmergency.value!!.requesterLocation

        emergencyRepo.updateResponderLocation(emg.id, userRahul.id, Location(28.6140, 77.2095, "Rahul step"))

        val postMoveRequesterLoc = emergencyDataSource.currentEmergency.value!!.requesterLocation
        assertEquals("Requester coordinates must remain completely unchanged when responder moves",
            originalRequesterLoc.latitude, postMoveRequesterLoc.latitude, 0.000001)
        assertEquals(originalRequesterLoc.longitude, postMoveRequesterLoc.longitude, 0.000001)
    }

    // =========================================================================
    // 16. DISTANCE RECALCULATION FROM COORDINATES
    // =========================================================================

    @Test
    fun test16_distanceRecalculatesFromCoordinates() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        emergencyRepo.acceptEmergency(emg.id, userRahul)

        // Set explicit coordinates for Rahul at ~100m North
        val targetDistance = 100.0
        val targetLocation = Location.createAtOffset(baseLocation, targetDistance, bearingDegrees = 0.0)
        emergencyRepo.updateResponderLocation(emg.id, userRahul.id, targetLocation)

        val currentEmg = emergencyDataSource.currentEmergency.value!!
        val calculatedDist = currentEmg.responderDistances[userRahul.id]!!
        assertEquals("Calculated distance must derive from Haversine coordinates", targetDistance, calculatedDist, 0.5)

        // Now move requester 50m North (closer to Rahul)
        val newReqLoc = Location.createAtOffset(baseLocation, 50.0, bearingDegrees = 0.0)
        emergencyRepo.updateRequesterLocation(emg.id, newReqLoc)

        val updatedEmg = emergencyDataSource.currentEmergency.value!!
        val updatedDist = updatedEmg.responderDistances[userRahul.id]!!
        assertEquals("Distance must automatically decrease to ~50m after requester moves closer", 50.0, updatedDist, 0.5)
    }

    // =========================================================================
    // 17. TIMESTAMP DYNAMICS
    // =========================================================================

    @Test
    fun test17_timestampChangesAfterLocationUpdate() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        val t1 = emg.requesterLocation.timestamp

        // Simulate small delay
        Thread.sleep(10)
        val t2 = System.currentTimeMillis()
        emergencyRepo.updateRequesterLocation(emg.id, baseLocation.copy(timestamp = t2))

        val updatedLoc = emergencyDataSource.currentEmergency.value!!.requesterLocation
        assertTrue("Location timestamp must update after position update", updatedLoc.timestamp >= t2)
        assertTrue(updatedLoc.timestamp > t1)
    }

    // =========================================================================
    // 18. STALE LOCATION BEHAVIOR
    // =========================================================================

    @Test
    fun test18_staleLocationBehavior() {
        val now = 1000000L
        val freshLocation = Location(28.6139, 77.2090, "Fresh Location", timestamp = now - 5000L) // 5s old
        assertFalse("Location <= 30s old must not be stale", freshLocation.isStale(currentTimeMillis = now))
        assertEquals(LocationStatus.ACTIVE, freshLocation.effectiveStatus(currentTimeMillis = now))

        val staleLocation = Location(28.6139, 77.2090, "Stale Location", timestamp = now - 35000L) // 35s old (>30s)
        assertTrue("Location > 30s old must be detected as stale", staleLocation.isStale(currentTimeMillis = now))
        assertEquals(LocationStatus.STALE, staleLocation.effectiveStatus(currentTimeMillis = now))
    }

    // =========================================================================
    // 19 & 20. TERMINAL STATE STOPS AND MUTATION BLOCKING
    // =========================================================================

    @Test
    fun test19_terminalStateStopsLiveLocation() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        emergencyRepo.acceptEmergency(emg.id, userRahul)
        emergencyRepo.updateResponderDistance(emg.id, userRahul.id, 25.0)

        // Resolve emergency
        emergencyRepo.resolveEmergency(emg.id, userGaurav)

        val terminalEmg = emergencyDataSource.currentEmergency.value!!
        assertTrue(terminalEmg.isTerminal)
        assertEquals(EmergencyStatus.RESOLVED, terminalEmg.status)
        assertEquals(LocationStatus.STOPPED, terminalEmg.requesterLocation.status)
        assertEquals(LocationStatus.STOPPED, terminalEmg.responderLocations[userRahul.id]!!.status)

        // Queries on terminal emergency return STOPPED status
        val viewerResult = locationRepo.getEmergencyParticipantLocation(emg.id, userGaurav.id, viewerId = userRahul.id)
        assertTrue(viewerResult.isSuccess)
        assertEquals(LocationStatus.STOPPED, viewerResult.getOrThrow().status)
    }

    @Test
    fun test20_terminalStateBlocksFurtherMovement() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        emergencyRepo.acceptEmergency(emg.id, userRahul)
        emergencyRepo.updateResponderDistance(emg.id, userRahul.id, 25.0)
        emergencyRepo.resolveEmergency(emg.id, userGaurav)

        // Attempting to move requester on terminal emergency
        val reqMoveRes = emergencyRepo.updateRequesterLocation(emg.id, Location(28.62, 77.21, "New place"))
        assertTrue("Requester movement must be rejected on terminal emergency", reqMoveRes.isFailure)

        // Attempting to advance responder on terminal emergency
        val respMoveRes = emergencyRepo.advanceResponderLocation(emg.id, userRahul.id)
        assertTrue("Responder advance must be rejected on terminal emergency", respMoveRes.isFailure)
    }

    // =========================================================================
    // 21, 22, 23. IDOR / AUTHORIZATION TESTS
    // =========================================================================

    @Test
    fun test21_unauthorizedAccessDenied() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        emergencyRepo.acceptEmergency(emg.id, userRahul)

        // Stranger attempts to view participants
        val res = locationRepo.getVisibleParticipantLocations(emg.id, viewerId = userStranger.id)
        assertTrue("Stranger must receive 403-style authorization denial", res.isFailure)
        assertTrue(res.exceptionOrNull() is SecurityException)
    }

    @Test
    fun test22_idorStyleArbitraryParticipantAccessDenied() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        emergencyRepo.acceptEmergency(emg.id, userRahul)

        // Requester attempts to query a user ID that is NOT in the emergency
        val arbitraryQuery = locationRepo.getEmergencyParticipantLocation(emg.id, participantId = "unknown-user-666", viewerId = userGaurav.id)
        assertTrue("Arbitrary participant ID must be rejected", arbitraryQuery.isFailure)
        assertTrue(arbitraryQuery.exceptionOrNull() is SecurityException)
    }

    @Test
    fun test23_acceptedParticipantAccessAllowed() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        emergencyRepo.acceptEmergency(emg.id, userRahul)
        emergencyRepo.acceptEmergency(emg.id, userAman)

        // Rahul viewing Aman
        val rahulViewsAman = locationRepo.getEmergencyParticipantLocation(emg.id, participantId = userAman.id, viewerId = userRahul.id)
        assertTrue("Accepted responder can view peer accepted responder in emergency", rahulViewsAman.isSuccess)

        // Aman viewing Rahul
        val amanViewsRahul = locationRepo.getEmergencyParticipantLocation(emg.id, participantId = userRahul.id, viewerId = userAman.id)
        assertTrue("Accepted responder can view peer accepted responder in emergency", amanViewsRahul.isSuccess)
    }

    // =========================================================================
    // 24. ALL ACCEPTED RESPONDERS RECEIVE CURRENT REQUESTER LOCATION
    // =========================================================================

    @Test
    fun test24_allAcceptedRespondersReceiveCurrentRequesterLocation() = runBlocking {
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        emergencyRepo.acceptEmergency(emg.id, userRahul)
        emergencyRepo.acceptEmergency(emg.id, userAman)
        emergencyRepo.acceptEmergency(emg.id, userRohit)

        // Move 1
        val waypoint1 = Location(28.6150, 77.2090, "Waypoint 1")
        emergencyRepo.updateRequesterLocation(emg.id, waypoint1)

        // Move 2
        val waypoint2 = Location(28.6175, 77.2105, "Waypoint 2 (Final)")
        emergencyRepo.updateRequesterLocation(emg.id, waypoint2)

        // Verify Rahul, Aman, and Rohit all receive waypoint 2
        val rahulQuery = locationRepo.getEmergencyParticipantLocation(emg.id, userGaurav.id, viewerId = userRahul.id).getOrThrow()
        val amanQuery = locationRepo.getEmergencyParticipantLocation(emg.id, userGaurav.id, viewerId = userAman.id).getOrThrow()
        val rohitQuery = locationRepo.getEmergencyParticipantLocation(emg.id, userGaurav.id, viewerId = userRohit.id).getOrThrow()

        assertEquals("Waypoint 2 (Final)", rahulQuery.address)
        assertEquals("Waypoint 2 (Final)", amanQuery.address)
        assertEquals("Waypoint 2 (Final)", rohitQuery.address)

        assertEquals(28.6175, rahulQuery.latitude, 0.00001)
        assertEquals(28.6175, amanQuery.latitude, 0.00001)
        assertEquals(28.6175, rohitQuery.latitude, 0.00001)
    }
}
