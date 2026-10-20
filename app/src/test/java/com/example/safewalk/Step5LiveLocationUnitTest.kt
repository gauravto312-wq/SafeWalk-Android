package com.example.safewalk

import com.example.safewalk.core.location.LiveLocationManager
import com.example.safewalk.core.location.LiveTrackingSession
import com.example.safewalk.core.location.LiveTrackingState
import com.example.safewalk.domain.model.Location
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class Step5LiveLocationUnitTest {

    private lateinit var liveLocationManager: LiveLocationManager

    @Before
    fun setUp() {
        liveLocationManager = LiveLocationManager.getInstance()
        liveLocationManager.resetForTests()
    }

    @After
    fun tearDown() {
        liveLocationManager.resetForTests()
    }

    @Test
    fun test_initial_state_is_idle() {
        assertEquals(LiveTrackingState.IDLE, liveLocationManager.trackingState.value)
        assertNull(liveLocationManager.currentSession.value)
        assertNull(liveLocationManager.lastLocation.value)
        assertNull(liveLocationManager.lastSyncedTimestamp.value)
    }

    @Test
    fun test_live_tracking_session_matches_with_or_without_prefix() {
        val session = LiveTrackingSession(
            userId = "101",
            emergencyId = "EMG-500",
            role = "REQUESTER"
        )

        // Exact match
        assertTrue(session.matches("101", "EMG-500"))
        // Match without prefix
        assertTrue(session.matches("101", "500"))
        // Mismatch user ID
        assertFalse(session.matches("102", "EMG-500"))
        // Mismatch emergency ID
        assertFalse(session.matches("101", "EMG-501"))
    }

    @Test
    fun test_start_tracking_transitions_to_tracking_state() {
        val started = liveLocationManager.startTracking(
            userId = "1",
            emergencyId = "EMG-200",
            role = "REQUESTER",
            context = null
        )

        assertTrue("First start should succeed", started)
        assertEquals(LiveTrackingState.TRACKING, liveLocationManager.trackingState.value)
        assertNotNull(liveLocationManager.currentSession.value)
        assertEquals("1", liveLocationManager.currentSession.value?.userId)
        assertEquals("EMG-200", liveLocationManager.currentSession.value?.emergencyId)
        assertEquals("REQUESTER", liveLocationManager.currentSession.value?.role)
    }

    @Test
    fun test_duplicate_session_prevention_suppresses_redundant_start() {
        val firstStart = liveLocationManager.startTracking(
            userId = "1",
            emergencyId = "EMG-200",
            role = "REQUESTER",
            context = null
        )
        assertTrue("First start should succeed", firstStart)

        // Attempting to start again with same user and emergency
        val secondStart = liveLocationManager.startTracking(
            userId = "1",
            emergencyId = "EMG-200",
            role = "REQUESTER",
            context = null
        )
        assertFalse("Second start should be suppressed as duplicate", secondStart)
        assertEquals(LiveTrackingState.TRACKING, liveLocationManager.trackingState.value)

        // Starting with normalized ID without prefix should also be suppressed
        val thirdStart = liveLocationManager.startTracking(
            userId = "1",
            emergencyId = "200",
            role = "REQUESTER",
            context = null
        )
        assertFalse("Normalized emergencyId duplicate should also be suppressed", thirdStart)
    }

    @Test
    fun test_stop_tracking_transitions_to_stopped_and_clears_session() {
        liveLocationManager.startTracking(
            userId = "1",
            emergencyId = "EMG-200",
            role = "REQUESTER",
            context = null
        )

        liveLocationManager.stopTracking("EMG-200", context = null)

        assertEquals(LiveTrackingState.STOPPED, liveLocationManager.trackingState.value)
        assertNull(liveLocationManager.currentSession.value)
    }

    @Test
    fun test_stop_tracking_with_mismatched_emergency_id_is_ignored() {
        liveLocationManager.startTracking(
            userId = "1",
            emergencyId = "EMG-200",
            role = "REQUESTER",
            context = null
        )

        liveLocationManager.stopTracking("EMG-999", context = null)

        // Session must remain intact and TRACKING
        assertEquals(LiveTrackingState.TRACKING, liveLocationManager.trackingState.value)
        assertNotNull(liveLocationManager.currentSession.value)
        assertEquals("EMG-200", liveLocationManager.currentSession.value?.emergencyId)
    }

    @Test
    fun test_stop_all_tracking_on_logout_resets_everything_to_idle() {
        liveLocationManager.startTracking(
            userId = "1",
            emergencyId = "EMG-200",
            role = "REQUESTER",
            context = null
        )
        liveLocationManager.onLocationSynced(Location(28.6139, 77.2090, "New Delhi"))

        liveLocationManager.stopAllTracking(context = null)

        assertEquals(LiveTrackingState.IDLE, liveLocationManager.trackingState.value)
        assertNull(liveLocationManager.currentSession.value)
        assertNull(liveLocationManager.lastLocation.value)
        assertNull(liveLocationManager.lastSyncedTimestamp.value)
        assertNull(liveLocationManager.lastError.value)
    }

    @Test
    fun test_gps_unavailable_transitions_to_paused_no_gps_and_auto_recovers() {
        liveLocationManager.startTracking(
            userId = "1",
            emergencyId = "EMG-200",
            role = "REQUESTER",
            context = null
        )
        assertEquals(LiveTrackingState.TRACKING, liveLocationManager.trackingState.value)

        // Signal lost
        liveLocationManager.onGpsUnavailable()
        assertEquals(LiveTrackingState.PAUSED_NO_GPS, liveLocationManager.trackingState.value)

        // Signal restored via onGpsRecovered
        liveLocationManager.onGpsRecovered()
        assertEquals(LiveTrackingState.TRACKING, liveLocationManager.trackingState.value)

        // Signal lost again
        liveLocationManager.onGpsUnavailable()
        assertEquals(LiveTrackingState.PAUSED_NO_GPS, liveLocationManager.trackingState.value)

        // Signal auto-restores when new location is acquired
        liveLocationManager.onLocationAcquired(Location(28.6139, 77.2090, "Connaught Place"))
        assertEquals(LiveTrackingState.TRACKING, liveLocationManager.trackingState.value)
    }

    @Test
    fun test_location_synced_updates_last_location_and_timestamp() {
        liveLocationManager.startTracking(
            userId = "1",
            emergencyId = "EMG-200",
            role = "REQUESTER",
            context = null
        )

        val loc = Location(28.6139, 77.2090, "Connaught Place")
        val beforeSync = System.currentTimeMillis()
        liveLocationManager.onLocationSynced(loc)
        val afterSync = System.currentTimeMillis()

        assertNotNull(liveLocationManager.lastLocation.value)
        assertEquals(28.6139, liveLocationManager.lastLocation.value?.latitude ?: 0.0, 0.0001)
        assertEquals(77.2090, liveLocationManager.lastLocation.value?.longitude ?: 0.0, 0.0001)
        assertNotNull(liveLocationManager.lastSyncedTimestamp.value)
        val timestamp = liveLocationManager.lastSyncedTimestamp.value!!
        assertTrue(timestamp in beforeSync..afterSync)
    }

    @Test
    fun test_error_recording() {
        liveLocationManager.startTracking(
            userId = "1",
            emergencyId = "EMG-200",
            role = "REQUESTER",
            context = null
        )

        liveLocationManager.onError("Network timeout while syncing live coordinates")
        assertEquals("Network timeout while syncing live coordinates", liveLocationManager.lastError.value)
    }
}
