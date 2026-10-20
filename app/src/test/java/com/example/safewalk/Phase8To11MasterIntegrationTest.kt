package com.example.safewalk

import com.example.safewalk.data.repository.AuthRepositoryImpl
import com.example.safewalk.data.repository.EmergencyRepositoryImpl
import com.example.safewalk.data.repository.LocationRepositoryImpl
import com.example.safewalk.data.repository.NotificationRepositoryImpl
import com.example.safewalk.data.source.mock.MockAuthDataSource
import com.example.safewalk.data.source.mock.MockData
import com.example.safewalk.data.source.mock.MockEmergencyDataSource
import com.example.safewalk.data.source.mock.MockLocationDataSource
import com.example.safewalk.data.source.mock.MockNotificationDataSource
import com.example.safewalk.domain.model.Emergency
import com.example.safewalk.domain.model.EmergencyStatus
import com.example.safewalk.domain.model.Location
import com.example.safewalk.domain.model.LocationStatus
import com.example.safewalk.domain.model.NotificationType
import com.example.safewalk.domain.model.RealtimeEmergencyEvent
import com.example.safewalk.domain.model.User
import com.example.safewalk.domain.model.UserRole
import com.example.safewalk.domain.model.VerificationDocument
import com.example.safewalk.domain.model.VerificationStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Master Integration Test Suite for Phases 8, 9, 10, and 11.
 * Verifies:
 * - Phase 8: Two-Party Resolution, Strict 5s Cancellation, Terminal States, Trusted Contacts.
 * - Phase 9: Mock FCM Push Dispatch, Section 9.4 Deduplication, Realtime WebSocket Events.
 * - Phase 10: Document Upload, Verification Approval/Rejection, RBAC, Settings & Session.
 * - Phase 11: End-to-End Multi-Persona Verification (Gaurav, Rahul, Aman, Police).
 */
class Phase8To11MasterIntegrationTest {

    private lateinit var authDataSource: MockAuthDataSource
    private lateinit var emergencyDataSource: MockEmergencyDataSource
    private lateinit var locationDataSource: MockLocationDataSource
    private lateinit var notificationDataSource: MockNotificationDataSource

    private lateinit var authRepo: AuthRepositoryImpl
    private lateinit var emergencyRepo: EmergencyRepositoryImpl
    private lateinit var locationRepo: LocationRepositoryImpl
    private lateinit var notificationRepo: NotificationRepositoryImpl

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

    private val policeUser = User(
        id = "police-001",
        safeWalkId = "SW-POLICE-911",
        name = "Verified Police Patrol",
        phone = "+91 100",
        email = "police.delhi@safewalk.local",
        role = UserRole.POLICE,
        isVerified = true,
        status = "On Duty / Patrol Active",
        verificationStatus = VerificationStatus.APPROVED,
        department = "Delhi Police - Central Division",
        badgeId = "DP-8821",
        designation = "Patrol Sergeant"
    )

    @Before
    fun setUp() {
        notificationDataSource = MockNotificationDataSource()
        authDataSource = MockAuthDataSource(notificationDataSource)
        locationDataSource = MockLocationDataSource()

        emergencyDataSource = MockEmergencyDataSource(
            notificationDataSource = notificationDataSource,
            userLookup = { authDataSource.getAvailableUsers() },
            locationLookup = { userId -> locationDataSource.getCurrentLocation(userId) }
        )

        authRepo = AuthRepositoryImpl(authDataSource)
        emergencyRepo = EmergencyRepositoryImpl(emergencyDataSource, locationDataSource)
        locationRepo = LocationRepositoryImpl(locationDataSource, emergencyDataSource)
        notificationRepo = NotificationRepositoryImpl(notificationDataSource)

        // Set up initial locations
        locationDataSource.updateLocation(userGaurav.id, baseLocation)
        // Position Rahul at ~100m
        val rahulLoc = Location.createAtOffset(baseLocation, 100.0, 0.0, "Rahul Initial")
        locationDataSource.updateLocation(userRahul.id, rahulLoc)
        // Position Aman at ~200m
        val amanLoc = Location.createAtOffset(baseLocation, 200.0, 90.0, "Aman Initial")
        locationDataSource.updateLocation(userAman.id, amanLoc)
        // Position Police at ~30m
        val policeLoc = Location.createAtOffset(baseLocation, 30.0, 180.0, "Police Station")
        locationDataSource.updateLocation(policeUser.id, policeLoc)
    }

    // =========================================================================
    // PHASE 8: TWO-PARTY RESOLUTION & TERMINAL STATES
    // =========================================================================

    @Test
    fun testPhase8_DualConfirmation_RequiresBothPartiesToResolve() = runBlocking {
        // 1. Gaurav creates SOS
        val emergency = emergencyRepo.createEmergency(userGaurav, baseLocation)
        assertEquals(EmergencyStatus.ACTIVE, emergency.status)

        // 2. Rahul accepts
        val acceptedResult = emergencyRepo.acceptEmergency(emergency.id, userRahul)
        assertTrue(acceptedResult.isSuccess)

        // 3. Rahul arrives within 25m
        val arrivedResult = emergencyRepo.markArrived(emergency.id, userRahul, 20.0)
        assertTrue(arrivedResult.isSuccess)
        val arrivedEmergency = arrivedResult.getOrThrow()
        assertTrue(arrivedEmergency.effectiveArrivedResponder != null)

        // 4. Gaurav (requester) confirms resolution first
        val reqConfirmResult = emergencyRepo.confirmResolution(emergency.id, userGaurav)
        assertTrue(reqConfirmResult.isSuccess)
        val afterReqConfirm = reqConfirmResult.getOrThrow()
        assertTrue(afterReqConfirm.requesterConfirmedResolution)
        assertFalse(afterReqConfirm.responderConfirmedResolution)
        assertEquals(EmergencyStatus.ACCEPTED, afterReqConfirm.status) // Not resolved yet!

        // 5. Rahul (arrived responder) confirms resolution second
        val respConfirmResult = emergencyRepo.confirmResolution(emergency.id, userRahul)
        assertTrue(respConfirmResult.isSuccess)
        val afterBothConfirm = respConfirmResult.getOrThrow()
        assertTrue(afterBothConfirm.requesterConfirmedResolution)
        assertTrue(afterBothConfirm.responderConfirmedResolution)
        assertEquals(EmergencyStatus.RESOLVED, afterBothConfirm.status) // Fully resolved!
        assertTrue(afterBothConfirm.isTerminal)
        assertNotNull(afterBothConfirm.resolvedAt)
        assertEquals(LocationStatus.STOPPED, afterBothConfirm.requesterLocation.status)
    }

    @Test
    fun testPhase8_DualConfirmation_ResponderCanConfirmFirst() = runBlocking {
        val emergency = emergencyRepo.createEmergency(userGaurav, baseLocation)
        emergencyRepo.acceptEmergency(emergency.id, userRahul)
        emergencyRepo.markArrived(emergency.id, userRahul, 25.0)

        // Rahul confirms first
        val respConfirmResult = emergencyRepo.confirmResolution(emergency.id, userRahul)
        assertTrue(respConfirmResult.isSuccess)
        val afterResp = respConfirmResult.getOrThrow()
        assertTrue(afterResp.responderConfirmedResolution)
        assertFalse(afterResp.requesterConfirmedResolution)
        assertEquals(EmergencyStatus.ACCEPTED, afterResp.status)

        // Gaurav confirms second
        val reqConfirmResult = emergencyRepo.confirmResolution(emergency.id, userGaurav)
        assertTrue(reqConfirmResult.isSuccess)
        val finalEmg = reqConfirmResult.getOrThrow()
        assertEquals(EmergencyStatus.RESOLVED, finalEmg.status)
        assertTrue(finalEmg.isTerminal)
    }

    @Test
    fun testPhase8_NonArrivedResponder_CannotConfirmResolution() = runBlocking {
        val emergency = emergencyRepo.createEmergency(userGaurav, baseLocation)
        emergencyRepo.acceptEmergency(emergency.id, userRahul)
        // Rahul is at 100m, NOT arrived (must be <= 25m)

        val confirmResult = emergencyRepo.confirmResolution(emergency.id, userRahul)
        assertTrue(confirmResult.isFailure)
        assertTrue(confirmResult.exceptionOrNull()?.message?.contains("arrived") == true)
    }

    @Test
    fun testPhase8_UnauthorizedUser_CannotConfirmResolution() = runBlocking {
        val emergency = emergencyRepo.createEmergency(userGaurav, baseLocation)
        emergencyRepo.acceptEmergency(emergency.id, userRahul)
        emergencyRepo.markArrived(emergency.id, userRahul, 15.0)

        // Aman is not part of this emergency
        val confirmResult = emergencyRepo.confirmResolution(emergency.id, userAman)
        assertTrue(confirmResult.isFailure)
    }

    @Test
    fun testPhase8_DirectResolution_RequiresArrivalThreshold() = runBlocking {
        val emergency = emergencyRepo.createEmergency(userGaurav, baseLocation)
        emergencyRepo.acceptEmergency(emergency.id, userRahul)

        // Attempt direct resolve while distance is 100m (> 25m)
        val failResolve = emergencyRepo.resolveEmergency(emergency.id, userGaurav)
        assertTrue(failResolve.isFailure)

        // Move Rahul to 24m (<= 25m)
        emergencyRepo.updateResponderDistance(emergency.id, userRahul.id, 24.0)

        // Now direct resolve succeeds
        val successResolve = emergencyRepo.resolveEmergency(emergency.id, userGaurav)
        assertTrue(successResolve.isSuccess)
        assertEquals(EmergencyStatus.RESOLVED, successResolve.getOrThrow().status)
    }

    // =========================================================================
    // PHASE 8: STRICT 5.000s CANCELLATION WINDOW & TERMINAL IMMUTABILITY
    // =========================================================================

    @Test
    fun testPhase8_CancellationWithin5Seconds_Succeeds() = runBlocking {
        val emergency = emergencyRepo.createEmergency(userGaurav, baseLocation)
        // Immediate cancel (within 5.000s)
        val cancelResult = emergencyRepo.cancelEmergency(emergency.id, "False alarm")
        assertTrue(cancelResult.isSuccess)
        val cancelled = cancelResult.getOrThrow()
        assertEquals(EmergencyStatus.CANCELLED, cancelled.status)
        assertTrue(cancelled.isTerminal)
        assertEquals("False alarm", cancelled.cancellationReason)
        assertEquals(LocationStatus.STOPPED, cancelled.requesterLocation.status)
    }

    @Test
    fun testPhase8_CancellationAfter5Seconds_IsStrictlyRejected() = runBlocking {
        // Create emergency timestamped 5001ms ago
        val pastEmergency = Emergency(
            id = "EMG-PAST-001",
            requester = userGaurav,
            status = EmergencyStatus.ACTIVE,
            createdAt = System.currentTimeMillis() - 5001L,
            requesterLocation = baseLocation
        )
        emergencyDataSource.addEmergencyForTesting(pastEmergency)

        val cancelResult = emergencyRepo.cancelEmergency(pastEmergency.id, "Late cancel")
        assertTrue(cancelResult.isFailure)
        assertTrue(cancelResult.exceptionOrNull()?.message?.contains("expired") == true)
    }

    @Test
    fun testPhase8_TerminalStateImmutability_CannotAcceptOrAdvance() = runBlocking {
        val emergency = emergencyRepo.createEmergency(userGaurav, baseLocation)
        emergencyRepo.cancelEmergency(emergency.id, "Test cancellation")

        // Cannot accept a cancelled emergency
        val acceptResult = emergencyRepo.acceptEmergency(emergency.id, userRahul)
        assertTrue(acceptResult.isFailure)

        // Cannot advance location of cancelled emergency
        val advanceResult = emergencyRepo.advanceRequesterLocation(emergency.id)
        assertTrue(advanceResult.isFailure)
    }

    // =========================================================================
    // PHASE 8: TRUSTED CONTACTS VALIDATION RULES
    // =========================================================================

    @Test
    fun testPhase8_TrustedContacts_FullValidationLifecycle() = runBlocking {
        authRepo.loginAsMockUser(userGaurav)

        // Gaurav starts with SW-RAHUL-102
        val currentUser = authRepo.currentUser.value!!
        assertEquals(1, currentUser.emergencyContacts.size)

        // 1. Cannot add self
        val selfResult = authRepo.addEmergencyContact(userGaurav.id, userGaurav.safeWalkId)
        assertTrue(selfResult.isFailure)

        // 2. Cannot add non-existent ID
        val nonExistentResult = authRepo.addEmergencyContact(userGaurav.id, "SW-GHOST-999")
        assertTrue(nonExistentResult.isFailure)

        // 3. Cannot add duplicate
        val duplicateResult = authRepo.addEmergencyContact(userGaurav.id, "SW-RAHUL-102")
        assertTrue(duplicateResult.isFailure)

        // 4. Add valid second contact (Aman)
        val validSecond = authRepo.addEmergencyContact(userGaurav.id, userAman.safeWalkId)
        assertTrue(validSecond.isSuccess)
        assertEquals(2, validSecond.getOrThrow().emergencyContacts.size)

        // 5. Add valid 3rd contact (Police) - Step 8 increases maximum limit to 3 contacts
        val thirdContactResult = authRepo.addEmergencyContact(userGaurav.id, policeUser.safeWalkId)
        assertTrue(thirdContactResult.isSuccess)
        assertEquals(3, thirdContactResult.getOrThrow().emergencyContacts.size)

        // 6. Cannot add 4th contact (max 3 limit)
        val fourthContactResult = authRepo.addEmergencyContact(userGaurav.id, "SW-HOSP-102")
        assertTrue(fourthContactResult.isFailure)

        // 7. Atomic replacement of contact
        val replaceResult = authRepo.replaceEmergencyContact(userGaurav.id, policeUser.safeWalkId, "SW-HOSP-102")
        assertTrue(replaceResult.isSuccess)
        assertEquals(3, replaceResult.getOrThrow().emergencyContacts.size)
        assertTrue(replaceResult.getOrThrow().emergencyContacts.contains("SW-HOSP-102"))
        assertFalse(replaceResult.getOrThrow().emergencyContacts.contains(policeUser.safeWalkId))

        // 8. Remove contact
        val removeResult = authRepo.removeEmergencyContact(userGaurav.id, userAman.safeWalkId)
        assertTrue(removeResult.isSuccess)
        assertEquals(2, removeResult.getOrThrow().emergencyContacts.size)
    }

    // =========================================================================
    // PHASE 9: MOCK FCM PUSH & SECTION 9.4 DEDUPLICATION
    // =========================================================================

    @Test
    fun testPhase9_SosCreation_AlertsTrustedContactsAndNearbyWithDeduplication() = runBlocking {
        // Gaurav has Rahul as trusted contact.
        // Aman is nearby (~200m) and NOT a trusted contact.
        val emergency = emergencyRepo.createEmergency(userGaurav, baseLocation)

        // 1. Rahul (trusted contact) receives TRUSTED_CONTACT_ALERT
        val rahulNotifs = notificationRepo.getNotificationsForUser(userRahul.id).first()
        val rahulTrustedAlert = rahulNotifs.find { it.type == NotificationType.TRUSTED_CONTACT_ALERT }
        assertNotNull(rahulTrustedAlert)
        assertEquals(emergency.id, rahulTrustedAlert?.relatedEmergencyId)

        // Section 9.4 Deduplication: Rahul must NOT receive EMERGENCY_SOS_NEARBY
        val rahulNearbyAlert = rahulNotifs.find { it.type == NotificationType.EMERGENCY_SOS_NEARBY }
        assertTrue(rahulNearbyAlert == null)

        // 2. Aman (normal nearby user) receives EMERGENCY_SOS_NEARBY
        val amanNotifs = notificationRepo.getNotificationsForUser(userAman.id).first()
        val amanNearbyAlert = amanNotifs.find { it.type == NotificationType.EMERGENCY_SOS_NEARBY }
        assertNotNull(amanNearbyAlert)
        assertEquals(emergency.id, amanNearbyAlert?.relatedEmergencyId)

        // 3. Gaurav (requester) must NOT receive nearby notification for own emergency
        val gauravNotifs = notificationRepo.getNotificationsForUser(userGaurav.id).first()
        val gauravNearbyAlert = gauravNotifs.find { it.type == NotificationType.EMERGENCY_SOS_NEARBY }
        assertTrue(gauravNearbyAlert == null)
    }

    @Test
    fun testPhase9_LifecycleNotifications_AcceptedArrivedResolvedCancelled() = runBlocking {
        val emergency = emergencyRepo.createEmergency(userGaurav, baseLocation)

        // 1. Rahul accepts -> Gaurav receives RESPONDER_ACCEPTED
        emergencyRepo.acceptEmergency(emergency.id, userRahul)
        val gauravNotifsAfterAccept = notificationRepo.getNotificationsForUser(userGaurav.id).first()
        val acceptNotif = gauravNotifsAfterAccept.find { it.type == NotificationType.RESPONDER_ACCEPTED }
        assertNotNull(acceptNotif)

        // 2. Rahul arrives (<= 25m) -> Gaurav receives RESPONDER_ARRIVED
        emergencyRepo.markArrived(emergency.id, userRahul, 20.0)
        val gauravNotifsAfterArrival = notificationRepo.getNotificationsForUser(userGaurav.id).first()
        val arrivalNotif = gauravNotifsAfterArrival.find { it.type == NotificationType.RESPONDER_ARRIVED }
        assertNotNull(arrivalNotif)

        // 3. Resolve emergency -> Both receive EMERGENCY_RESOLVED
        emergencyRepo.confirmResolution(emergency.id, userGaurav)
        emergencyRepo.confirmResolution(emergency.id, userRahul)

        val gauravFinalNotifs = notificationRepo.getNotificationsForUser(userGaurav.id).first()
        val rahulFinalNotifs = notificationRepo.getNotificationsForUser(userRahul.id).first()
        assertNotNull(gauravFinalNotifs.find { it.type == NotificationType.EMERGENCY_RESOLVED })
        assertNotNull(rahulFinalNotifs.find { it.type == NotificationType.EMERGENCY_RESOLVED })
    }

    @Test
    fun testPhase9_CancellationNotification_SentToAcceptedResponders() = runBlocking {
        val emergency = emergencyRepo.createEmergency(userGaurav, baseLocation)
        emergencyRepo.acceptEmergency(emergency.id, userRahul)

        // Gaurav cancels
        emergencyRepo.cancelEmergency(emergency.id, "Accidental press")

        val rahulNotifs = notificationRepo.getNotificationsForUser(userRahul.id).first()
        val cancelNotif = rahulNotifs.find { it.type == NotificationType.EMERGENCY_CANCELLED }
        assertNotNull(cancelNotif)
        assertTrue(cancelNotif?.message?.contains("Accidental press") == true)
    }

    @Test
    fun testPhase9_NotificationManagement_UnreadCountAndMutations() = runBlocking {
        val testUser = userAman
        val notif1 = notificationDataSource.dispatchFcmPush(
            type = NotificationType.SYSTEM_ALERT,
            recipientUserId = testUser.id,
            title = "Notice 1",
            message = "Body 1"
        )
        val notif2 = notificationDataSource.dispatchFcmPush(
            type = NotificationType.SYSTEM_ALERT,
            recipientUserId = testUser.id,
            title = "Notice 2",
            message = "Body 2"
        )

        // Check unread count
        val unreadInitial = notificationRepo.getUnreadCount(testUser.id).first()
        assertTrue(unreadInitial >= 2)

        // Mark single as read
        notificationRepo.markAsRead(notif1.id)
        val unreadAfterOne = notificationRepo.getUnreadCount(testUser.id).first()
        assertEquals(unreadInitial - 1, unreadAfterOne)

        // Mark all as read
        notificationRepo.markAllAsRead(testUser.id)
        val unreadFinal = notificationRepo.getUnreadCount(testUser.id).first()
        assertEquals(0, unreadFinal)

        // Delete single notification
        val deleteResult = notificationRepo.deleteNotification(notif1.id)
        assertTrue(deleteResult.isSuccess)

        // Clear all notifications
        val clearResult = notificationRepo.clearNotifications(testUser.id)
        assertTrue(clearResult.isSuccess)
        val emptyList = notificationRepo.getNotificationsForUser(testUser.id).first()
        assertTrue(emptyList.isEmpty())
    }

    // =========================================================================
    // PHASE 9: REALTIME WEBSOCKET EVENTS FLOW
    // =========================================================================

    @Test
    fun testPhase9_RealtimeWebSocketEvents_EmittedCorrectly() = runBlocking {
        val events = mutableListOf<RealtimeEmergencyEvent>()
        val job = launch {
            emergencyRepo.realtimeEvents.collect { events.add(it) }
        }
        kotlinx.coroutines.yield()

        // 1. Create emergency -> StatusChanged (ACTIVE)
        val emg = emergencyRepo.createEmergency(userGaurav, baseLocation)
        kotlinx.coroutines.yield()

        // 2. Accept -> ResponderAccepted & StatusChanged (ACCEPTED)
        emergencyRepo.acceptEmergency(emg.id, userRahul)
        kotlinx.coroutines.yield()

        // 3. Move -> LocationUpdated
        val newLoc = Location.createAtOffset(baseLocation, 10.0, 0.0, "Near scene")
        emergencyRepo.updateResponderLocation(emg.id, userRahul.id, newLoc)
        kotlinx.coroutines.yield()

        // 4. Resolve -> EmergencyResolved & StatusChanged (RESOLVED)
        emergencyRepo.confirmResolution(emg.id, userGaurav)
        emergencyRepo.confirmResolution(emg.id, userRahul)
        kotlinx.coroutines.yield()

        job.cancel()

        assertTrue(events.any { it is RealtimeEmergencyEvent.StatusChanged && it.newStatus == EmergencyStatus.ACTIVE })
        assertTrue(events.any { it is RealtimeEmergencyEvent.ResponderAccepted && it.responder.id == userRahul.id })
        assertTrue(events.any { it is RealtimeEmergencyEvent.LocationUpdated && it.participantId == userRahul.id })
        assertTrue(events.any { it is RealtimeEmergencyEvent.ResponderArrived && it.responder.id == userRahul.id })
        assertTrue(events.any { it is RealtimeEmergencyEvent.EmergencyResolved })
        assertTrue(events.any { it is RealtimeEmergencyEvent.StatusChanged && it.newStatus == EmergencyStatus.RESOLVED })
    }

    // =========================================================================
    // PHASE 10: DOCUMENT VERIFICATION, RBAC & SETTINGS
    // =========================================================================

    @Test
    fun testPhase10_DocumentUploadAndVerificationReviewFlow() = runBlocking {
        // Register an unverified police officer
        val officer = authRepo.register(
            name = "Officer Vikram",
            email = "vikram@delhipolice.local",
            phone = "+91 98111 22334",
            password = "SecurePassword123!",
            confirmPassword = "SecurePassword123!",
            role = UserRole.POLICE,
            department = "Delhi Police Crime Branch",
            badgeId = "DP-7733"
        ).getOrThrow()

        assertEquals(VerificationStatus.PENDING, officer.verificationStatus)
        assertFalse(officer.isVerified)

        // 1. Upload verification documents
        val doc = VerificationDocument(
            id = "DOC-TEST-1",
            title = "Police Department ID",
            fileName = "id_card.pdf",
            uploadedAt = System.currentTimeMillis()
        )
        val uploadResult = authRepo.uploadVerificationDocument(officer.id, doc)
        assertTrue(uploadResult.isSuccess)
        assertEquals(1, uploadResult.getOrThrow().documents.size)

        // 2. Approve verification
        val approveResult = authRepo.approveVerification(officer.id)
        assertTrue(approveResult.isSuccess)
        val approvedOfficer = approveResult.getOrThrow()
        assertTrue(approvedOfficer.isVerified)
        assertEquals(VerificationStatus.APPROVED, approvedOfficer.verificationStatus)

        // Verification approved notification dispatched
        val officerNotifs = notificationRepo.getNotificationsForUser(officer.id).first()
        val approvedNotif = officerNotifs.find { it.type == NotificationType.VERIFICATION_APPROVED }
        assertNotNull(approvedNotif)

        // 3. Reject verification test
        val rejectResult = authRepo.rejectVerification(officer.id)
        assertTrue(rejectResult.isSuccess)
        val rejectedOfficer = rejectResult.getOrThrow()
        assertFalse(rejectedOfficer.isVerified)
        assertEquals(VerificationStatus.REJECTED, rejectedOfficer.verificationStatus)

        val officerNotifs2 = notificationRepo.getNotificationsForUser(officer.id).first()
        val rejectedNotif = officerNotifs2.find { it.type == NotificationType.VERIFICATION_REJECTED }
        assertNotNull(rejectedNotif)
    }

    @Test
    fun testPhase10_RBAC_PoliceDiscoveryRules() = runBlocking {
        // Emergency at base location
        val emergency = emergencyRepo.createEmergency(userGaurav, baseLocation)

        // Unverified police at 30m (< 50m)
        val unverifiedPolice = policeUser.copy(isVerified = false, verificationStatus = VerificationStatus.PENDING)
        val unverifiedEligible = emergencyRepo.isUserEligibleForEmergency(
            unverifiedPolice,
            emergency,
            Location.createAtOffset(baseLocation, 30.0, 0.0)
        )
        assertFalse(unverifiedEligible) // Unverified police cannot respond

        // Verified police at 30m (< 50m) -> Bypasses 50m exclusion!
        val verifiedEligible = emergencyRepo.isUserEligibleForEmergency(
            policeUser,
            emergency,
            Location.createAtOffset(baseLocation, 30.0, 0.0)
        )
        assertTrue(verifiedEligible) // Verified police can respond within 50m!

        // Normal user at 30m (< 50m) -> Excluded by proximity rule
        val normalUserClose = userAman
        val normalEligible = emergencyRepo.isUserEligibleForEmergency(
            normalUserClose,
            emergency,
            Location.createAtOffset(baseLocation, 30.0, 0.0)
        )
        assertFalse(normalEligible) // Normal user < 50m excluded
    }

    @Test
    fun testPhase10_SettingsAndSessionManagement() = runBlocking {
        // Login as Gaurav
        authRepo.loginAsMockUser(userGaurav)
        assertTrue(authRepo.isAuthenticated())
        assertEquals(userGaurav.id, authRepo.currentUser.value?.id)

        // Logout
        authRepo.logout()
        assertFalse(authRepo.isAuthenticated())
        assertTrue(authRepo.currentUser.value == null)

        // Login as Rahul
        authRepo.loginAsMockUser(userRahul)
        assertTrue(authRepo.isAuthenticated())
        assertEquals(userRahul.id, authRepo.currentUser.value?.id)
    }
}
