package com.example.safewalk

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
import com.example.safewalk.domain.model.VerificationDocument
import com.example.safewalk.domain.model.VerificationStatus
import com.example.safewalk.viewmodel.EmergencyViewModel
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
 * Phase 4 Comprehensive Unit Test Suite covering all 43 test scenarios from Section 31.
 */
class Phase4VerificationAndDiscoveryTest {

    private lateinit var authDataSource: MockAuthDataSource
    private lateinit var emergencyDataSource: MockEmergencyDataSource
    private lateinit var locationDataSource: MockLocationDataSource

    private lateinit var authRepo: AuthRepositoryImpl
    private lateinit var emergencyRepo: EmergencyRepositoryImpl
    private lateinit var locationRepo: LocationRepositoryImpl

    private val baseLocation = Location(28.6139, 77.2090, "Connaught Place Center")

    @Before
    fun setUp() {
        authDataSource = MockAuthDataSource()
        emergencyDataSource = MockEmergencyDataSource()
        locationDataSource = MockLocationDataSource()

        authRepo = AuthRepositoryImpl(authDataSource)
        emergencyRepo = EmergencyRepositoryImpl(emergencyDataSource)
        locationRepo = LocationRepositoryImpl(locationDataSource)
    }

    // =========================================================================
    // REGISTRATION SCENARIOS (1 to 10)
    // =========================================================================

    @Test
    fun test01_userRegistrationSucceeds() = runBlocking {
        val result = authRepo.register(
            name = "Citizen Priya",
            email = "priya@safewalk.local",
            phone = "+91 91234 56780",
            password = "password123",
            confirmPassword = "password123",
            role = UserRole.USER
        )
        assertTrue("User registration should succeed", result.isSuccess)
        val user = result.getOrNull()
        assertNotNull(user)
        assertEquals(UserRole.USER, user?.role)
        assertEquals("Ready", user?.status)
        assertEquals(VerificationStatus.NONE, user?.verificationStatus)
        assertTrue(user?.safeWalkId?.startsWith("SW-USER-") == true)
    }

    @Test
    fun test02_policeRegistrationSucceeds() = runBlocking {
        val result = authRepo.register(
            name = "Officer Vikram",
            email = "vikram@delhipolice.local",
            phone = "+91 91234 56781",
            password = "password123",
            confirmPassword = "password123",
            role = UserRole.POLICE,
            department = "New Delhi Police Station",
            badgeId = "DP-7701",
            designation = "Sub-Inspector"
        )
        assertTrue("Police registration should succeed", result.isSuccess)
        val police = result.getOrNull()
        assertNotNull(police)
        assertEquals(UserRole.POLICE, police?.role)
        assertEquals("New Delhi Police Station", police?.department)
        assertEquals("DP-7701", police?.badgeId)
        assertTrue(police?.safeWalkId?.startsWith("SW-POLICE-") == true)
    }

    @Test
    fun test03_hospitalRegistrationSucceeds() = runBlocking {
        val result = authRepo.register(
            name = "Dr. Sameer",
            email = "sameer@aims-trauma.local",
            phone = "+91 91234 56782",
            password = "password123",
            confirmPassword = "password123",
            role = UserRole.HOSPITAL,
            hospitalName = "AIIMS Trauma Center",
            licenseId = "HOSP-MED-109",
            designation = "Emergency In-Charge"
        )
        assertTrue("Hospital registration should succeed", result.isSuccess)
        val hosp = result.getOrNull()
        assertNotNull(hosp)
        assertEquals(UserRole.HOSPITAL, hosp?.role)
        assertEquals("AIIMS Trauma Center", hosp?.hospitalName)
        assertEquals("HOSP-MED-109", hosp?.licenseId)
        assertTrue(hosp?.safeWalkId?.startsWith("SW-HOSP-") == true)
    }

    @Test
    fun test04_adminIsNotAvailableInPublicRegistration() = runBlocking {
        val result = authRepo.register(
            name = "Admin Attempt",
            email = "admin.attempt@safewalk.local",
            phone = "+91 99999 00000",
            password = "password123",
            confirmPassword = "password123",
            role = UserRole.ADMIN
        )
        assertTrue("Admin public registration must be disallowed", result.isFailure)
        val ex = result.exceptionOrNull()
        assertNotNull(ex)
        assertTrue(ex?.message?.contains("ADMIN", ignoreCase = true) == true)
    }

    @Test
    fun test05_policeStartsUnverified() = runBlocking {
        val result = authRepo.register(
            name = "Officer Ajay",
            email = "ajay@safewalk.local",
            phone = "+91 91234 56783",
            password = "password123",
            confirmPassword = "password123",
            role = UserRole.POLICE
        )
        val police = result.getOrNull()
        assertNotNull(police)
        assertFalse("Newly registered police must NOT be verified", police!!.isVerified)
        assertEquals(VerificationStatus.PENDING, police.verificationStatus)
        assertEquals("Verification Pending", police.status)
    }

    @Test
    fun test06_hospitalStartsUnverified() = runBlocking {
        val result = authRepo.register(
            name = "St. Jude ER",
            email = "er@stjude.local",
            phone = "+91 91234 56784",
            password = "password123",
            confirmPassword = "password123",
            role = UserRole.HOSPITAL
        )
        val hosp = result.getOrNull()
        assertNotNull(hosp)
        assertFalse("Newly registered hospital must NOT be verified", hosp!!.isVerified)
        assertEquals(VerificationStatus.PENDING, hosp.verificationStatus)
        assertEquals("Verification Pending", hosp.status)
    }

    @Test
    fun test07_policeDocumentUploadStateWorks() = runBlocking {
        val reg = authRepo.register(
            name = "Officer Sunita",
            email = "sunita@safewalk.local",
            phone = "+91 91234 56785",
            password = "password123",
            confirmPassword = "password123",
            role = UserRole.POLICE
        ).getOrThrow()

        val doc = VerificationDocument(
            id = "DOC-POLICE-01",
            title = "Police Identification Card",
            fileName = "Officer_ID_Card.pdf"
        )
        val uploadResult = authRepo.uploadVerificationDocument(reg.id, doc)
        assertTrue(uploadResult.isSuccess)
        val updated = uploadResult.getOrNull()
        assertNotNull(updated)
        assertEquals(1, updated?.documents?.size)
        assertEquals("Officer_ID_Card.pdf", updated?.documents?.first()?.fileName)
    }

    @Test
    fun test08_hospitalDocumentUploadStateWorks() = runBlocking {
        val reg = authRepo.register(
            name = "Apollo Emergency",
            email = "apollo@safewalk.local",
            phone = "+91 91234 56786",
            password = "password123",
            confirmPassword = "password123",
            role = UserRole.HOSPITAL
        ).getOrThrow()

        val doc = VerificationDocument(
            id = "DOC-HOSP-01",
            title = "Hospital Medical License",
            fileName = "Hospital_License_2026.pdf"
        )
        val uploadResult = authRepo.uploadVerificationDocument(reg.id, doc)
        assertTrue(uploadResult.isSuccess)
        val updated = uploadResult.getOrNull()
        assertNotNull(updated)
        assertEquals(1, updated?.documents?.size)
        assertEquals("Hospital_License_2026.pdf", updated?.documents?.first()?.fileName)
    }

    @Test
    fun test09_duplicateEmailRejected() = runBlocking {
        val first = authRepo.register(
            name = "Kavita",
            email = "kavita@safewalk.local",
            phone = "+91 91234 56787",
            password = "password123",
            confirmPassword = "password123",
            role = UserRole.USER
        )
        assertTrue(first.isSuccess)

        val duplicate = authRepo.register(
            name = "Kavita Duplicate",
            email = "kavita@safewalk.local",
            phone = "+91 91234 56788",
            password = "password123",
            confirmPassword = "password123",
            role = UserRole.USER
        )
        assertTrue("Duplicate email must be rejected", duplicate.isFailure)
        assertTrue(duplicate.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun test10_safeWalkIdGeneratedUniquely() = runBlocking {
        val u1 = authRepo.register("User One", "u1@safewalk.local", "+91 91000 00001", "pass123", "pass123", UserRole.USER).getOrThrow()
        val u2 = authRepo.register("User Two", "u2@safewalk.local", "+91 91000 00002", "pass123", "pass123", UserRole.USER).getOrThrow()
        val p1 = authRepo.register("Officer One", "p1@safewalk.local", "+91 91000 00003", "pass123", "pass123", UserRole.POLICE).getOrThrow()
        val h1 = authRepo.register("Hosp One", "h1@safewalk.local", "+91 91000 00004", "pass123", "pass123", UserRole.HOSPITAL).getOrThrow()

        assertNotEquals(u1.safeWalkId, u2.safeWalkId)
        assertTrue(u1.safeWalkId.startsWith("SW-USER-"))
        assertTrue(u2.safeWalkId.startsWith("SW-USER-"))
        assertTrue(p1.safeWalkId.startsWith("SW-POLICE-"))
        assertTrue(h1.safeWalkId.startsWith("SW-HOSP-"))
    }

    // =========================================================================
    // VERIFICATION SCENARIOS (11 to 16)
    // =========================================================================

    @Test
    fun test11_policePendingState() = runBlocking {
        val reg = authRepo.register("Officer Deep", "deep@safewalk.local", "+91 91000 00005", "pass123", "pass123", UserRole.POLICE).getOrThrow()
        assertEquals(VerificationStatus.PENDING, reg.verificationStatus)
        assertFalse(reg.isVerified)
    }

    @Test
    fun test12_hospitalPendingState() = runBlocking {
        val reg = authRepo.register("Care Clinic", "care@safewalk.local", "+91 91000 00006", "pass123", "pass123", UserRole.HOSPITAL).getOrThrow()
        assertEquals(VerificationStatus.PENDING, reg.verificationStatus)
        assertFalse(reg.isVerified)
    }

    @Test
    fun test13_mockPoliceApprovalToVerified() = runBlocking {
        val reg = authRepo.register("Officer Tara", "tara@safewalk.local", "+91 91000 00007", "pass123", "pass123", UserRole.POLICE).getOrThrow()
        val approveResult = authRepo.approveVerification(reg.id)
        assertTrue(approveResult.isSuccess)
        val approved = approveResult.getOrThrow()
        assertTrue(approved.isVerified)
        assertEquals(VerificationStatus.APPROVED, approved.verificationStatus)
        assertEquals("On Duty / Patrol Active", approved.status)
    }

    @Test
    fun test14_mockHospitalApprovalToVerified() = runBlocking {
        val reg = authRepo.register("MedCity ER", "medcity@safewalk.local", "+91 91000 00008", "pass123", "pass123", UserRole.HOSPITAL).getOrThrow()
        val approveResult = authRepo.approveVerification(reg.id)
        assertTrue(approveResult.isSuccess)
        val approved = approveResult.getOrThrow()
        assertTrue(approved.isVerified)
        assertEquals(VerificationStatus.APPROVED, approved.verificationStatus)
        assertEquals("Medical Unit Ready", approved.status)
    }

    @Test
    fun test15_mockRejectionToRejected() = runBlocking {
        val reg = authRepo.register("Unverified Entity", "fake@safewalk.local", "+91 91000 00009", "pass123", "pass123", UserRole.POLICE).getOrThrow()
        val rejectResult = authRepo.rejectVerification(reg.id)
        assertTrue(rejectResult.isSuccess)
        val rejected = rejectResult.getOrThrow()
        assertFalse(rejected.isVerified)
        assertEquals(VerificationStatus.REJECTED, rejected.verificationStatus)
        assertEquals("Verification Rejected", rejected.status)
    }

    @Test
    fun test16_unverifiedPoliceExcludedFromVerifiedPoliceDiscovery() = runBlocking {
        val unverifiedPolice = authRepo.register(
            "Officer Pending", "pending.pol@safewalk.local", "+91 91000 00010", "pass123", "pass123", UserRole.POLICE
        ).getOrThrow()

        val requester = MockData.mockUsers[0]
        val emg = emergencyRepo.createEmergency(requester, baseLocation)

        // Located 30 meters away (within 50m exclusion zone)
        val nearLocation = Location(baseLocation.latitude + 0.0002, baseLocation.longitude, "Near 30m")
        val isEligible = emergencyRepo.isUserEligibleForEmergency(unverifiedPolice, emg, nearLocation)
        assertFalse("Unverified police must NOT be eligible as verified police responder", isEligible)
    }

    // =========================================================================
    // DISCOVERY SCENARIOS (17 to 26)
    // =========================================================================

    // Helper to compute offset coordinates roughly by meters
    // 1 deg lat ~ 111,000 meters. 0.001 deg ~ 111 meters.
    private fun createLocationAtDistanceMeters(base: Location, distanceMeters: Double): Location {
        val metersPerLatDegree = 6371000.0 * (Math.PI / 180.0)
        val deltaLat = distanceMeters / metersPerLatDegree
        return Location(base.latitude + deltaLat, base.longitude, "Offset ${distanceMeters}m")
    }

    @Test
    fun test17_normalUserBetween50mAnd2kmIsEligible() = runBlocking {
        val citizen = MockData.mockUsers[1] // Rahul (USER)
        val requester = MockData.mockUsers[0].copy(emergencyContacts = emptyList())
        val emg = emergencyRepo.createEmergency(requester, baseLocation)

        // 500 meters away
        val loc500m = createLocationAtDistanceMeters(baseLocation, 500.0)
        assertTrue(emergencyRepo.isUserEligibleForEmergency(citizen, emg, loc500m))
    }

    @Test
    fun test18_normalUserUnderOrEqual50mIsExcluded() = runBlocking {
        val citizen = MockData.mockUsers[1]
        val requester = MockData.mockUsers[0].copy(emergencyContacts = emptyList())
        val emg = emergencyRepo.createEmergency(requester, baseLocation)

        // Exactly 30m away (< 50m)
        val loc30m = createLocationAtDistanceMeters(baseLocation, 30.0)
        assertFalse("Normal user at 30m (<50m) must be excluded", emergencyRepo.isUserEligibleForEmergency(citizen, emg, loc30m))

        // 45m away (< 50m)
        val loc45m = createLocationAtDistanceMeters(baseLocation, 45.0)
        assertFalse("Normal user at 45m (<=50m) must be excluded", emergencyRepo.isUserEligibleForEmergency(citizen, emg, loc45m))
    }

    @Test
    fun test19_normalUserOver2kmIsExcluded() = runBlocking {
        val citizen = MockData.mockUsers[1]
        val requester = MockData.mockUsers[0].copy(emergencyContacts = emptyList())
        val emg = emergencyRepo.createEmergency(requester, baseLocation)

        // 2500m away (> 2km)
        val loc2500m = createLocationAtDistanceMeters(baseLocation, 2500.0)
        assertFalse("Normal user >2km must be excluded", emergencyRepo.isUserEligibleForEmergency(citizen, emg, loc2500m))
    }

    @Test
    fun test20_verifiedPoliceUnder2kmIsEligible() = runBlocking {
        val police = MockData.mockUsers[3] // Verified Police Patrol
        val requester = MockData.mockUsers[0]
        val emg = emergencyRepo.createEmergency(requester, baseLocation)

        val loc1200m = createLocationAtDistanceMeters(baseLocation, 1200.0)
        assertTrue("Verified police <=2km is eligible", emergencyRepo.isUserEligibleForEmergency(police, emg, loc1200m))
    }

    @Test
    fun test21_verifiedPoliceWithin50mRemainsEligible() = runBlocking {
        val police = MockData.mockUsers[3] // Verified Police Patrol
        val requester = MockData.mockUsers[0]
        val emg = emergencyRepo.createEmergency(requester, baseLocation)

        // 20m away (Bypasses 50m exclusion zone)
        val loc20m = createLocationAtDistanceMeters(baseLocation, 20.0)
        assertTrue("Verified police within 50m must bypass exclusion", emergencyRepo.isUserEligibleForEmergency(police, emg, loc20m))
    }

    @Test
    fun test22_unverifiedPoliceExcluded() = runBlocking {
        val unverifiedPolice = User(
            id = "pol-unverified",
            safeWalkId = "SW-POLICE-UNV",
            name = "Unverified Cop",
            phone = "100",
            email = "unv@police.local",
            role = UserRole.POLICE,
            isVerified = false,
            verificationStatus = VerificationStatus.PENDING
        )
        val requester = MockData.mockUsers[0]
        val emg = emergencyRepo.createEmergency(requester, baseLocation)

        val loc500m = createLocationAtDistanceMeters(baseLocation, 500.0)
        assertFalse("Unverified police must not qualify as verified police", emergencyRepo.isUserEligibleForEmergency(unverifiedPolice, emg, loc500m))
    }

    @Test
    fun test23_requesterExcludedFromOwnEmergency() = runBlocking {
        val requester = MockData.mockUsers[0]
        val emg = emergencyRepo.createEmergency(requester, baseLocation)

        val loc500m = createLocationAtDistanceMeters(baseLocation, 500.0)
        assertFalse("Requester cannot discover own emergency as a responder", emergencyRepo.isUserEligibleForEmergency(requester, emg, loc500m))
    }

    @Test
    fun test24_trustedContactsQualifyRegardlessOfDistance() = runBlocking {
        val contactUser = MockData.mockUsers[1] // Rahul: SW-RAHUL-102
        val requesterWithContact = MockData.mockUsers[0].copy(
            emergencyContacts = listOf(contactUser.safeWalkId)
        )
        val emg = emergencyRepo.createEmergency(requesterWithContact, baseLocation)

        // 10 kilometers away (> 2km limit)
        val loc10km = createLocationAtDistanceMeters(baseLocation, 10000.0)
        assertTrue("Emergency contact qualifies regardless of distance", emergencyRepo.isUserEligibleForEmergency(contactUser, emg, loc10km))

        // 10 meters away (< 50m limit)
        val loc10m = createLocationAtDistanceMeters(baseLocation, 10.0)
        assertTrue("Emergency contact qualifies regardless of 50m exclusion", emergencyRepo.isUserEligibleForEmergency(contactUser, emg, loc10m))
    }

    @Test
    fun test25_duplicateNotificationEligibilityIsDeduplicated() = runBlocking {
        val contactAndNearbyUser = MockData.mockUsers[1] // SW-RAHUL-102
        val requester = MockData.mockUsers[0].copy(
            emergencyContacts = listOf(contactAndNearbyUser.safeWalkId)
        )
        val emg = emergencyRepo.createEmergency(requester, baseLocation)

        // User qualifies BOTH by distance (500m) AND by emergency contact
        val loc500m = createLocationAtDistanceMeters(baseLocation, 500.0)
        val eligibleList = emergencyRepo.getEligibleEmergenciesForUser(contactAndNearbyUser, loc500m)
        assertEquals("User should appear only once despite multiple qualifications", 1, eligibleList.count { it.id == emg.id })
    }

    @Test
    fun test26_noFixedResponderCap() = runBlocking {
        val requester = MockData.mockUsers[0]
        val emg = emergencyRepo.createEmergency(requester, baseLocation)

        // Accept 5 distinct responders
        for (i in 1..5) {
            val responder = User(
                id = "resp-$i",
                safeWalkId = "SW-RESP-$i",
                name = "Responder $i",
                phone = "98765432$i",
                email = "resp$i@safewalk.local",
                role = UserRole.USER
            )
            val acceptResult = emergencyRepo.acceptEmergency(emg.id, responder)
            assertTrue("Accepting responder $i should succeed without cap", acceptResult.isSuccess)
        }

        val updated = emergencyRepo.currentEmergency.value
        assertEquals(5, updated?.acceptedResponders?.size)
    }

    // =========================================================================
    // RESPONDER SCENARIOS (27 to 31)
    // =========================================================================

    @Test
    fun test27_userCanAcceptAnotherUsersEmergency() = runBlocking {
        val requester = MockData.mockUsers[0]
        val responder = MockData.mockUsers[1]
        val emg = emergencyRepo.createEmergency(requester, baseLocation)

        val result = emergencyRepo.acceptEmergency(emg.id, responder)
        assertTrue(result.isSuccess)
        val updated = result.getOrThrow()
        assertEquals(EmergencyStatus.ACCEPTED, updated.status)
        assertTrue(updated.isUserAcceptedResponder(responder.id))
    }

    @Test
    fun test28_userCannotAcceptOwnEmergency() = runBlocking {
        val requester = MockData.mockUsers[0]
        val emg = emergencyRepo.createEmergency(requester, baseLocation)

        val result = emergencyRepo.acceptEmergency(emg.id, requester)
        assertTrue("Requester accepting own emergency must fail", result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun test29_sameResponderCannotBeAddedTwice() = runBlocking {
        val requester = MockData.mockUsers[0]
        val responder = MockData.mockUsers[1]
        val emg = emergencyRepo.createEmergency(requester, baseLocation)

        val firstAccept = emergencyRepo.acceptEmergency(emg.id, responder)
        assertTrue(firstAccept.isSuccess)

        val secondAccept = emergencyRepo.acceptEmergency(emg.id, responder)
        assertTrue("Duplicate acceptance must be rejected", secondAccept.isFailure)
    }

    @Test
    fun test30_multipleRespondersCanAcceptSameEmergency() = runBlocking {
        val requester = MockData.mockUsers[0]
        val resp1 = MockData.mockUsers[1]
        val resp2 = MockData.mockUsers[2]
        val emg = emergencyRepo.createEmergency(requester, baseLocation)

        emergencyRepo.acceptEmergency(emg.id, resp1)
        val secondResult = emergencyRepo.acceptEmergency(emg.id, resp2)
        assertTrue(secondResult.isSuccess)

        val updated = secondResult.getOrThrow()
        assertEquals(2, updated.acceptedResponders.size)
        assertTrue(updated.acceptedResponders.any { it.id == resp1.id })
        assertTrue(updated.acceptedResponders.any { it.id == resp2.id })
    }

    @Test
    fun test31_responderBecomesContextualResponder() = runBlocking {
        val requester = MockData.mockUsers[0] // Permanent role: ROLE_USER
        val responder = MockData.mockUsers[1] // Permanent role: ROLE_USER
        val emg = emergencyRepo.createEmergency(requester, baseLocation)

        emergencyRepo.acceptEmergency(emg.id, responder)

        // Permanent account role remains USER
        assertEquals(UserRole.USER, responder.role)

        // Contextual emergency role check
        assertTrue("User is contextual responder", emg.isUserRequester(requester.id))
        assertFalse("User is not requester", emg.isUserRequester(responder.id))
        val updated = emergencyRepo.currentEmergency.value!!
        assertTrue("User is accepted responder", updated.isUserAcceptedResponder(responder.id))
    }

    // =========================================================================
    // ARRIVAL SCENARIOS (32 to 35)
    // =========================================================================

    @Test
    fun test32_30mDistanceCannotMarkArrived() = runBlocking {
        val requester = MockData.mockUsers[0]
        val responder = MockData.mockUsers[1]
        val emg = emergencyRepo.createEmergency(requester, baseLocation)
        emergencyRepo.acceptEmergency(emg.id, responder)

        val result = emergencyRepo.markArrived(emg.id, responder, distanceMeters = 30.0)
        assertTrue("Distance of 30m must NOT allow arrival", result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("25 meters or less") == true)
    }

    @Test
    fun test33_25mDistanceCanMarkArrived() = runBlocking {
        val requester = MockData.mockUsers[0]
        val responder = MockData.mockUsers[1]
        val emg = emergencyRepo.createEmergency(requester, baseLocation)
        emergencyRepo.acceptEmergency(emg.id, responder)

        val result = emergencyRepo.markArrived(emg.id, responder, distanceMeters = 25.0)
        assertTrue("Distance of exactly 25.0m MUST allow arrival (<= 25m)", result.isSuccess)
        val updated = result.getOrThrow()
        assertEquals(EmergencyStatus.ACCEPTED, updated.status)
        assertTrue(updated.isResponderArrived(responder.id))
    }

    @Test
    fun test34_24mDistanceCanMarkArrived() = runBlocking {
        val requester = MockData.mockUsers[0]
        val responder = MockData.mockUsers[1]
        val emg = emergencyRepo.createEmergency(requester, baseLocation)
        emergencyRepo.acceptEmergency(emg.id, responder)

        val result = emergencyRepo.markArrived(emg.id, responder, distanceMeters = 24.0)
        assertTrue("Distance of 24.0m MUST allow arrival (<= 25m)", result.isSuccess)
        val updated = result.getOrThrow()
        assertEquals(EmergencyStatus.ACCEPTED, updated.status)
        assertTrue(updated.isResponderArrived(responder.id))
    }

    @Test
    fun test35_arrivalBelongsToResponderNotWholeEmergency() = runBlocking {
        val requester = MockData.mockUsers[0]
        val resp1 = MockData.mockUsers[1]
        val resp2 = MockData.mockUsers[2]
        val emg = emergencyRepo.createEmergency(requester, baseLocation)
        emergencyRepo.acceptEmergency(emg.id, resp1)
        emergencyRepo.acceptEmergency(emg.id, resp2)

        // resp1 arrives at scene
        val arrivalResult = emergencyRepo.markArrived(emg.id, resp1, distanceMeters = 20.0)
        assertTrue(arrivalResult.isSuccess)
        val updated = arrivalResult.getOrThrow()

        // Verify resp1 has arrived, but resp2 has NOT arrived
        assertTrue("Resp1 is arrived", updated.isResponderArrived(resp1.id))
        assertFalse("Resp2 is not yet arrived", updated.isResponderArrived(resp2.id))
        assertFalse("Emergency must NOT be terminal purely due to arrival", updated.isTerminal)
        assertFalse("Emergency must not be resolved without dual confirmation", updated.isFullyResolved)
    }

    // =========================================================================
    // LOCATION PRIVACY SCENARIOS (36 to 39)
    // =========================================================================

    @Test
    fun test36_requesterCanSeeAcceptedResponders() = runBlocking {
        val requester = MockData.mockUsers[0]
        val responder = MockData.mockUsers[1]
        val emg = emergencyRepo.createEmergency(requester, baseLocation)
        emergencyRepo.acceptEmergency(emg.id, responder)
        val updated = emergencyRepo.currentEmergency.value!!

        assertTrue("Requester has location clearance", emergencyRepo.canViewDetailedLocation(updated, requester))
    }

    @Test
    fun test37_acceptedResponderCanSeeRequester() = runBlocking {
        val requester = MockData.mockUsers[0]
        val responder = MockData.mockUsers[1]
        val emg = emergencyRepo.createEmergency(requester, baseLocation)
        emergencyRepo.acceptEmergency(emg.id, responder)
        val updated = emergencyRepo.currentEmergency.value!!

        assertTrue("Accepted responder has location clearance", emergencyRepo.canViewDetailedLocation(updated, responder))
    }

    @Test
    fun test38_nonAcceptedNearbyUserCannotSeeParticipantLocation() = runBlocking {
        val requester = MockData.mockUsers[0]
        val nearbyUser = MockData.mockUsers[1]
        val emg = emergencyRepo.createEmergency(requester, baseLocation)

        assertFalse("Nearby user without acceptance cannot view detailed coordinates", emergencyRepo.canViewDetailedLocation(emg, nearbyUser))
    }

    @Test
    fun test39_unrelatedUserCannotSeeParticipantLocation() = runBlocking {
        val requester = MockData.mockUsers[0]
        val unrelatedUser = User(
            id = "stranger-99",
            safeWalkId = "SW-STRANGER",
            name = "Stranger",
            phone = "111",
            email = "stranger@external.local",
            role = UserRole.USER
        )
        val emg = emergencyRepo.createEmergency(requester, baseLocation)

        assertFalse("Unrelated user cannot view detailed coordinates", emergencyRepo.canViewDetailedLocation(emg, unrelatedUser))
    }

    // =========================================================================
    // CANCELLATION SCENARIOS (40 to 43)
    // =========================================================================

    @Test
    fun test40_cancelVisibleAtT0() {
        val requester = MockData.mockUsers[0]
        val now = 1000000000000L
        val emg = Emergency(
            id = "EMG-TEST-40",
            requester = requester,
            requesterLocation = baseLocation,
            createdAt = now
        )
        assertTrue("Cancel button must be visible at t=0", emg.canCancel(now))
        assertEquals(5000L, emg.remainingCancellationTimeMillis(now))
    }

    @Test
    fun test41_cancelVisibleAtT4999() {
        val requester = MockData.mockUsers[0]
        val created = 1000000000000L
        val emg = Emergency(
            id = "EMG-TEST-41",
            requester = requester,
            requesterLocation = baseLocation,
            createdAt = created
        )
        val t4999 = created + 4999L
        assertTrue("Cancel button must be visible at t=4.999s", emg.canCancel(t4999))
        assertEquals(1L, emg.remainingCancellationTimeMillis(t4999))
    }

    @Test
    fun test42_cancelHiddenAtT5000OrGreater() {
        val requester = MockData.mockUsers[0]
        val created = 1000000000000L
        val emg = Emergency(
            id = "EMG-TEST-42",
            requester = requester,
            requesterLocation = baseLocation,
            createdAt = created
        )
        val t5000 = created + 5000L
        val t6000 = created + 6000L

        assertFalse("Cancel button must be hidden at exactly t=5.000s", emg.canCancel(t5000))
        assertEquals(0L, emg.remainingCancellationTimeMillis(t5000))

        assertFalse("Cancel button must be hidden at t=6.000s", emg.canCancel(t6000))
        assertEquals(0L, emg.remainingCancellationTimeMillis(t6000))
    }

    @Test
    fun test43_recompositionDoesNotResetTimer() = runBlocking {
        val requester = MockData.mockUsers[0]
        val created = System.currentTimeMillis() - 3000L // Created 3 seconds ago
        val emg = Emergency(
            id = "EMG-TEST-43",
            requester = requester,
            requesterLocation = baseLocation,
            createdAt = created
        )

        // Opening / recomposing screen 3s after creation
        val viewModel = EmergencyViewModel(emergencyRepo, authRepo, externalScope = this)
        viewModel.startCancellationCountdown(emg)

        val remaining = viewModel.cancellationRemainingMillis.value
        // Remaining should be approximately 2000ms, definitely <= 2100ms and > 0ms
        assertTrue("Remaining time must be <= 2100ms (elapsed: 3000ms)", remaining in 1L..2100L)
        assertTrue(viewModel.canCancel.value)
    }
}
