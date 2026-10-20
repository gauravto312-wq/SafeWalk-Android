package com.example.safewalk

import com.example.safewalk.data.repository.AuthRepositoryImpl
import com.example.safewalk.data.repository.EmergencyRepositoryImpl
import com.example.safewalk.data.source.mock.MockAuthDataSource
import com.example.safewalk.data.source.mock.MockData
import com.example.safewalk.data.source.mock.MockEmergencyDataSource
import com.example.safewalk.domain.model.Emergency
import com.example.safewalk.domain.model.EmergencyStatus
import com.example.safewalk.domain.model.Location
import com.example.safewalk.viewmodel.EmergencyViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SosCancellationWindowTest {

    private lateinit var emergencyRepo: EmergencyRepositoryImpl
    private lateinit var authRepo: AuthRepositoryImpl
    private val testRequester = MockData.mockUsers[0]
    private val testLocation = Location(28.6139, 77.2090, "Connaught Place")

    @Before
    fun setUp() {
        val authDataSource = MockAuthDataSource()
        val emergencyDataSource = MockEmergencyDataSource()
        authRepo = AuthRepositoryImpl(authDataSource)
        emergencyRepo = EmergencyRepositoryImpl(emergencyDataSource)
    }

    @Test
    fun test1_emergencyCreatedAtCurrentTime_cancelButtonVisible() {
        val now = 10000000L
        val emergency = Emergency(
            id = "EMG-TEST-1",
            requester = testRequester,
            status = EmergencyStatus.ACTIVE,
            createdAt = now,
            requesterLocation = testLocation
        )

        // Age = 0s -> Visible
        assertTrue(emergency.canCancel(now))
        assertEquals(5000L, emergency.remainingCancellationTimeMillis(now))
    }

    @Test
    fun test2_emergencyAge4Seconds_cancelButtonVisible() {
        val now = 10000000L
        val emergency = Emergency(
            id = "EMG-TEST-2",
            requester = testRequester,
            status = EmergencyStatus.ACTIVE,
            createdAt = now - 4000L,
            requesterLocation = testLocation
        )

        // Age = 4.000s -> Visible
        assertTrue(emergency.canCancel(now))
        assertEquals(1000L, emergency.remainingCancellationTimeMillis(now))
    }

    @Test
    fun test3_emergencyAge4999Millis_cancelButtonVisible() {
        val now = 10000000L
        val emergency = Emergency(
            id = "EMG-TEST-3",
            requester = testRequester,
            status = EmergencyStatus.ACTIVE,
            createdAt = now - 4999L,
            requesterLocation = testLocation
        )

        // Age = 4.999s -> Visible
        assertTrue(emergency.canCancel(now))
        assertEquals(1L, emergency.remainingCancellationTimeMillis(now))
    }

    @Test
    fun test4_emergencyAge5000Millis_cancelButtonHidden() {
        val now = 10000000L
        val emergency = Emergency(
            id = "EMG-TEST-4",
            requester = testRequester,
            status = EmergencyStatus.ACTIVE,
            createdAt = now - 5000L,
            requesterLocation = testLocation
        )

        // Age = 5.000s -> Hidden
        assertFalse(emergency.canCancel(now))
        assertEquals(0L, emergency.remainingCancellationTimeMillis(now))
    }

    @Test
    fun test5_emergencyAgeGreaterThan5Seconds_cancelButtonHidden() {
        val now = 10000000L
        val emergency = Emergency(
            id = "EMG-TEST-5",
            requester = testRequester,
            status = EmergencyStatus.ACTIVE,
            createdAt = now - 5001L,
            requesterLocation = testLocation
        )

        // Age = 5.001s -> Hidden
        assertFalse(emergency.canCancel(now))
        assertEquals(0L, emergency.remainingCancellationTimeMillis(now))

        val emergencyMuchOlder = Emergency(
            id = "EMG-TEST-5B",
            requester = testRequester,
            status = EmergencyStatus.ACTIVE,
            createdAt = now - 15000L,
            requesterLocation = testLocation
        )
        assertFalse(emergencyMuchOlder.canCancel(now))
        assertEquals(0L, emergencyMuchOlder.remainingCancellationTimeMillis(now))
    }

    @Test
    fun test6_cancelledEmergency_cancelButtonHidden() {
        val now = 10000000L
        val emergency = Emergency(
            id = "EMG-TEST-6",
            requester = testRequester,
            status = EmergencyStatus.CANCELLED,
            createdAt = now - 1000L,
            requesterLocation = testLocation
        )

        // Status is CANCELLED -> Hidden even within 5 seconds
        assertFalse(emergency.canCancel(now))
        assertEquals(0L, emergency.remainingCancellationTimeMillis(now))
    }

    @Test
    fun test7_resolvedEmergency_cancelButtonHidden() {
        val now = 10000000L
        val emergency = Emergency(
            id = "EMG-TEST-7",
            requester = testRequester,
            status = EmergencyStatus.RESOLVED,
            createdAt = now - 1000L,
            requesterLocation = testLocation
        )

        // Status is RESOLVED -> Hidden even within 5 seconds
        assertFalse(emergency.canCancel(now))
        assertEquals(0L, emergency.remainingCancellationTimeMillis(now))
    }

    @Test
    fun test8_recomposition_timerDoesNotResetWindow() = runBlocking {
        val job = Job()
        val testScope = CoroutineScope(coroutineContext + job)
        var simulatedNow = 10000000L
        val emergency = Emergency(
            id = "EMG-TEST-8",
            requester = testRequester,
            status = EmergencyStatus.ACTIVE,
            createdAt = simulatedNow,
            requesterLocation = testLocation
        )

        val viewModel = EmergencyViewModel(emergencyRepo, authRepo, externalScope = testScope)
        viewModel.startCancellationCountdown(emergency) { simulatedNow }

        assertTrue(viewModel.canCancel.value)
        assertEquals(5000L, viewModel.cancellationRemainingMillis.value)

        // Simulate 2 seconds pass
        simulatedNow += 2000L

        // Trigger recomposition / re-evaluation
        viewModel.startCancellationCountdown(emergency) { simulatedNow }

        // Must NOT reset to 5000ms; must be exactly 3000ms remaining
        assertTrue(viewModel.canCancel.value)
        assertEquals(3000L, viewModel.cancellationRemainingMillis.value)

        job.cancel()
    }

    @Test
    fun test9_navigateAwayAndReturn_remainingTimeCalculatedFromCreatedAt() = runBlocking {
        val job = Job()
        val testScope = CoroutineScope(coroutineContext + job)
        var simulatedNow = 10000000L
        val emergency = Emergency(
            id = "EMG-TEST-9",
            requester = testRequester,
            status = EmergencyStatus.ACTIVE,
            createdAt = simulatedNow,
            requesterLocation = testLocation
        )

        val viewModel = EmergencyViewModel(emergencyRepo, authRepo, externalScope = testScope)
        viewModel.startCancellationCountdown(emergency) { simulatedNow }
        assertTrue(viewModel.canCancel.value)

        // User navigates away -> timer stopped
        viewModel.stopCancellationCountdown()

        // 3.5 seconds elapse while away
        simulatedNow += 3500L

        // User returns to screen -> screen restarts countdown with emergency
        viewModel.startCancellationCountdown(emergency) { simulatedNow }

        // Remaining time must be 1500ms, NOT reset to 5000ms!
        assertTrue(viewModel.canCancel.value)
        assertEquals(1500L, viewModel.cancellationRemainingMillis.value)

        // User navigates away again and returns after 6.0s total elapsed
        viewModel.stopCancellationCountdown()
        simulatedNow += 2500L // Total 6000ms elapsed since createdAt
        viewModel.startCancellationCountdown(emergency) { simulatedNow }

        // Must be hidden immediately
        assertFalse(viewModel.canCancel.value)
        assertEquals(0L, viewModel.cancellationRemainingMillis.value)

        job.cancel()
    }

    @Test
    fun test10_cancellationWithinWindow_successfullyCancelsEmergency() = runBlocking {
        val job = Job()
        val testScope = CoroutineScope(coroutineContext + job)
        val emg = emergencyRepo.createEmergency(testRequester, testLocation)
        val viewModel = EmergencyViewModel(emergencyRepo, authRepo, externalScope = testScope)
        viewModel.loadEmergency(emg.id)
        kotlinx.coroutines.yield()

        // Within allowed window: cancel
        var cancelledCallbackInvoked = false
        viewModel.cancelEmergency("Test cancellation") {
            cancelledCallbackInvoked = true
        }
        kotlinx.coroutines.yield()

        assertTrue(cancelledCallbackInvoked)
        assertFalse(viewModel.canCancel.value)
        assertEquals(EmergencyStatus.CANCELLED, viewModel.emergency.value?.status)
        assertEquals("Test cancellation", viewModel.emergency.value?.cancellationReason)

        job.cancel()
    }
}
