package com.example.safewalk

import com.example.safewalk.data.model.dto.EmergencyResponseDto
import com.example.safewalk.data.model.dto.toDomain
import com.example.safewalk.domain.model.Emergency
import com.example.safewalk.domain.model.EmergencyStatus
import com.example.safewalk.domain.model.Location
import com.example.safewalk.domain.model.RealtimeEmergencyEvent
import com.example.safewalk.domain.model.User
import com.example.safewalk.domain.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class Feature1EmergencyExpiryUnitTest {

    @Test
    fun testEmergencyStatusExpiredProperties() {
        val expiredStatus = EmergencyStatus.EXPIRED
        assertEquals("Emergency Expired", expiredStatus.displayName)
        assertTrue("EXPIRED must be a terminal state", expiredStatus.isTerminal)
    }

    @Test
    fun testAllTerminalStates() {
        assertTrue("RESOLVED is terminal", EmergencyStatus.RESOLVED.isTerminal)
        assertTrue("CANCELLED is terminal", EmergencyStatus.CANCELLED.isTerminal)
        assertTrue("EXPIRED is terminal", EmergencyStatus.EXPIRED.isTerminal)
        org.junit.Assert.assertFalse("ACTIVE is not terminal", EmergencyStatus.ACTIVE.isTerminal)
        org.junit.Assert.assertFalse("ACCEPTED is not terminal", EmergencyStatus.ACCEPTED.isTerminal)
    }

    @Test
    fun testEmergencyResponseDtoToDomainMappingForExpired() {
        val now = LocalDateTime.now()
        val expiredTimeStr = now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

        val dto = EmergencyResponseDto(
            id = 450L,
            requesterId = 100L,
            requesterName = "Aman Sharma",
            requesterPhone = "+919876543210",
            status = "EXPIRED",
            latitude = 28.6139,
            longitude = 77.2090,
            address = "Connaught Place, New Delhi",
            createdAt = now.minusHours(5).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            expiredAt = expiredTimeStr
        )

        val domain = dto.toDomain()

        assertEquals("EMG-450", domain.id)
        assertEquals(EmergencyStatus.EXPIRED, domain.status)
        assertTrue("Emergency with EXPIRED status must be terminal", domain.isTerminal)
        assertNotNull("expiredAt must be parsed", domain.expiredAt)
        assertNotNull("formattedExpiredTime must be non-null", domain.formattedExpiredTime)
    }

    @Test
    fun testRealtimeEmergencyExpiredEventCreation() {
        val event = RealtimeEmergencyEvent.EmergencyExpired(
            emergencyId = "EMG-450",
            expiredAt = 1729000000000L
        )

        assertEquals("EMG-450", event.emergencyId)
        assertEquals("SOS_EXPIRED", event.eventType)
        assertEquals(1729000000000L, event.expiredAt)
    }
}
