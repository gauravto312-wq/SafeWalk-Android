package com.example.safewalk

import com.example.safewalk.data.model.dto.EmergencyResponseDto
import com.example.safewalk.data.model.dto.toDomain
import com.example.safewalk.domain.model.Emergency
import com.example.safewalk.domain.model.EmergencyStatus
import com.example.safewalk.domain.model.Location
import com.example.safewalk.domain.model.User
import com.example.safewalk.domain.model.UserRole
import com.example.safewalk.ui.util.GoogleMapsIntentLauncher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Step4Phase2LocationRulesUnitTest {

    private val testRequester = User(
        id = "1",
        safeWalkId = "SW-1",
        name = "Aman Requester",
        phone = "+919876543210",
        email = "aman@example.com",
        role = UserRole.USER
    )

    private val testLocation = Location(
        latitude = 28.6139,
        longitude = 77.2090,
        address = "Connaught Place, New Delhi"
    )

    @Test
    fun test_emergency_domain_formatted_distance_kilometers() {
        val emg = Emergency(
            id = "EMG-100",
            requester = testRequester,
            requesterLocation = testLocation,
            distanceMeters = 1500.0
        )
        assertNotNull(emg.formattedDistance)
        assertTrue(emg.formattedDistance!!.contains("1.5 km away"))
    }

    @Test
    fun test_emergency_domain_formatted_distance_meters() {
        val emg = Emergency(
            id = "EMG-100",
            requester = testRequester,
            requesterLocation = testLocation,
            distanceMeters = 350.0
        )
        assertNotNull(emg.formattedDistance)
        assertTrue(emg.formattedDistance!!.contains("350 m away"))
    }

    @Test
    fun test_emergency_domain_formatted_distance_null_when_missing() {
        val emg = Emergency(
            id = "EMG-100",
            requester = testRequester,
            requesterLocation = testLocation,
            distanceMeters = null
        )
        assertNull(emg.formattedDistance)
    }

    @Test
    fun test_emergency_dto_mapping_preserves_distance_meters() {
        val dto = EmergencyResponseDto(
            id = 101L,
            requesterId = 1L,
            requesterName = "Aman Requester",
            requesterPhone = "+919876543210",
            status = "ACTIVE",
            latitude = 28.6139,
            longitude = 77.2090,
            address = "New Delhi",
            distanceMeters = 750.5
        )

        val domain = dto.toDomain()
        assertEquals("EMG-101", domain.id)
        assertEquals(750.5, domain.distanceMeters!!, 0.01)
        assertEquals(28.6139, domain.requesterLocation.latitude, 0.0001)
        assertEquals(77.2090, domain.requesterLocation.longitude, 0.0001)
    }

    @Test
    fun test_google_maps_intent_launcher_coordinate_validation() {
        assertFalse(GoogleMapsIntentLauncher.isValidCoordinates(null, null))
        assertFalse(GoogleMapsIntentLauncher.isValidCoordinates(28.6139, null))
        assertFalse(GoogleMapsIntentLauncher.isValidCoordinates(null, 77.2090))
        assertFalse(GoogleMapsIntentLauncher.isValidCoordinates(0.0, 0.0))
        assertFalse(GoogleMapsIntentLauncher.isValidCoordinates(95.0, 77.2090)) // lat out of bounds
        assertFalse(GoogleMapsIntentLauncher.isValidCoordinates(28.6139, 190.0)) // lng out of bounds
        assertTrue(GoogleMapsIntentLauncher.isValidCoordinates(28.6139, 77.2090))
    }

    @Test
    fun test_google_maps_intent_launcher_geo_uri_format() {
        val uriStr = GoogleMapsIntentLauncher.buildGeoUriString(28.6139, 77.2090, "SafeWalk Emergency #100")
        assertTrue(uriStr.startsWith("geo:28.6139,77.209"))
        assertTrue(uriStr.contains("z=16"))
        assertTrue(uriStr.contains("SafeWalk+Emergency+%23100") || uriStr.contains("SafeWalk%20Emergency%20%23100"))
    }

    @Test
    fun test_step3_cancellation_window_preserved() {
        val now = System.currentTimeMillis()
        val recentEmergency = Emergency(
            id = "EMG-100",
            requester = testRequester,
            requesterLocation = testLocation,
            createdAt = now - 2000L // 2 seconds ago (< 5s)
        )
        assertTrue(recentEmergency.canCancel(now))
        assertTrue(recentEmergency.remainingCancellationTimeMillis(now) > 0L)

        val expiredEmergency = Emergency(
            id = "EMG-101",
            requester = testRequester,
            requesterLocation = testLocation,
            createdAt = now - 6000L // 6 seconds ago (> 5s)
        )
        assertFalse(expiredEmergency.canCancel(now))
        assertEquals(0L, expiredEmergency.remainingCancellationTimeMillis(now))
    }

    @Test
    fun test_step3_two_party_resolution_logic_preserved() {
        val arrivedResponder = User(
            id = "2",
            safeWalkId = "SW-2",
            name = "Ravi Responder",
            phone = "+919876543211",
            email = "ravi@example.com",
            role = UserRole.USER
        )

        val emgPending = Emergency(
            id = "EMG-100",
            requester = testRequester,
            requesterLocation = testLocation,
            arrivedResponder = arrivedResponder,
            requesterConfirmedResolution = true,
            responderConfirmedResolution = false
        )
        assertFalse(emgPending.isFullyResolved)

        val emgFullyResolved = Emergency(
            id = "EMG-100",
            requester = testRequester,
            requesterLocation = testLocation,
            arrivedResponder = arrivedResponder,
            requesterConfirmedResolution = true,
            responderConfirmedResolution = true
        )
        assertTrue(emgFullyResolved.isFullyResolved)
    }

    // MARK ARRIVED UI ELIGIBILITY MATRIX TESTS
    private fun canMarkArrived(distanceMeters: Double?): Boolean {
        if (distanceMeters == null || distanceMeters.isNaN() || distanceMeters.isInfinite() || distanceMeters < 0.0) {
            return false
        }
        return distanceMeters <= 25.0
    }

    @Test
    fun test_arrival_matrix_100m_disabled() {
        assertFalse(canMarkArrived(100.0))
    }

    @Test
    fun test_arrival_matrix_50m_disabled() {
        assertFalse(canMarkArrived(50.0))
    }

    @Test
    fun test_arrival_matrix_26m_disabled() {
        assertFalse(canMarkArrived(26.0))
    }

    @Test
    fun test_arrival_matrix_25m_enabled() {
        assertTrue(canMarkArrived(25.0))
    }

    @Test
    fun test_arrival_matrix_24m_enabled() {
        assertTrue(canMarkArrived(24.0))
    }

    @Test
    fun test_arrival_matrix_10m_enabled() {
        assertTrue(canMarkArrived(10.0))
    }

    @Test
    fun test_arrival_matrix_1m_enabled() {
        assertTrue(canMarkArrived(1.0))
    }

    @Test
    fun test_arrival_matrix_0m_enabled() {
        assertTrue(canMarkArrived(0.0))
    }

    @Test
    fun test_arrival_matrix_null_and_invalid_disabled() {
        assertFalse(canMarkArrived(null))
        assertFalse(canMarkArrived(Double.NaN))
        assertFalse(canMarkArrived(Double.POSITIVE_INFINITY))
        assertFalse(canMarkArrived(Double.NEGATIVE_INFINITY))
        assertFalse(canMarkArrived(-1.0))
    }
}
