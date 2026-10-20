package com.example.safewalk

import com.example.safewalk.core.websocket.SafeWalkWebSocketClient
import com.example.safewalk.core.websocket.StompFrame
import com.example.safewalk.core.websocket.StompMessage
import com.example.safewalk.core.websocket.WebSocketConnectionState
import com.example.safewalk.data.model.dto.EmergencyResponseDto
import com.example.safewalk.data.model.dto.UserResponseDto
import com.example.safewalk.data.model.dto.toDomain
import com.example.safewalk.data.source.mock.MockEmergencyDataSource
import com.example.safewalk.data.source.remote.RemoteEmergencyDataSource
import com.example.safewalk.data.repository.RemoteEmergencyRepository
import com.example.safewalk.domain.model.EmergencyStatus
import com.example.safewalk.domain.model.Location
import com.example.safewalk.domain.model.RealtimeEmergencyEvent
import com.example.safewalk.domain.model.User
import com.example.safewalk.domain.model.UserRole
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Step6WebSocketUnitTest {

    private val gson = Gson()

    // =========================================================================
    // 1. STOMP 1.2 PROTOCOL FRAMING UNIT TESTS
    // =========================================================================

    @Test
    fun test_stomp_build_connect_frame() {
        val token = "eyJhbGciOiJIUzI1NiJ9.testToken"
        val frame = StompFrame.buildConnect(token)

        assertEquals("CONNECT", frame.command)
        assertEquals("1.1,1.2", frame.headers["accept-version"])
        assertEquals("10000,10000", frame.headers["heart-beat"])
        assertEquals("Bearer $token", frame.headers["Authorization"])

        val wire = frame.toWireFormat()
        assertTrue(wire.startsWith("CONNECT\n"))
        assertTrue(wire.contains("accept-version:1.1,1.2\n"))
        assertTrue(wire.contains("Authorization:Bearer $token\n"))
        assertTrue(wire.endsWith("\u0000"))
    }

    @Test
    fun test_stomp_build_subscribe_frame() {
        val frame = StompFrame.buildSubscribe("/topic/sos/123", "sub-1")

        assertEquals("SUBSCRIBE", frame.command)
        assertEquals("/topic/sos/123", frame.headers["destination"])
        assertEquals("sub-1", frame.headers["id"])

        val wire = frame.toWireFormat()
        assertTrue(wire.startsWith("SUBSCRIBE\n"))
        assertTrue(wire.contains("destination:/topic/sos/123\n"))
        assertTrue(wire.contains("id:sub-1\n"))
        assertTrue(wire.endsWith("\u0000"))
    }

    @Test
    fun test_stomp_build_unsubscribe_frame() {
        val frame = StompFrame.buildUnsubscribe("sub-1")

        assertEquals("UNSUBSCRIBE", frame.command)
        assertEquals("sub-1", frame.headers["id"])

        val wire = frame.toWireFormat()
        assertTrue(wire.startsWith("UNSUBSCRIBE\n"))
        assertTrue(wire.contains("id:sub-1\n"))
        assertTrue(wire.endsWith("\u0000"))
    }

    @Test
    fun test_stomp_build_disconnect_frame() {
        val frame = StompFrame.buildDisconnect()
        assertEquals("DISCONNECT", frame.command)
        val wire = frame.toWireFormat()
        assertTrue(wire.startsWith("DISCONNECT\n"))
        assertTrue(wire.endsWith("\u0000"))
    }

    @Test
    fun test_stomp_parse_connected_frame() {
        val wire = "CONNECTED\nversion:1.2\nuser-name:+919999922222\n\n\u0000"
        val frames = StompFrame.parse(wire)

        assertEquals(1, frames.size)
        val frame = frames[0]
        assertEquals("CONNECTED", frame.command)
        assertEquals("1.2", frame.headers["version"])
        assertEquals("+919999922222", frame.headers["user-name"])
        assertEquals("", frame.body)
    }

    @Test
    fun test_stomp_parse_message_frame_with_json_body() {
        val bodyJson = """{"eventType":"SOS_CREATED","message":"New emergency","data":{"id":100}}"""
        val wire = "MESSAGE\ndestination:/topic/emergencies\nsubscription:sub-1\nmessage-id:msg-001\n\n$bodyJson\u0000"
        val frames = StompFrame.parse(wire)

        assertEquals(1, frames.size)
        val frame = frames[0]
        assertEquals("MESSAGE", frame.command)
        assertEquals("/topic/emergencies", frame.headers["destination"])
        assertEquals("sub-1", frame.headers["subscription"])
        assertEquals("msg-001", frame.headers["message-id"])
        assertEquals(bodyJson, frame.body)
    }

    @Test
    fun test_stomp_parse_error_frame() {
        val wire = "ERROR\nmessage:Authentication failed\n\nToken expired\u0000"
        val frames = StompFrame.parse(wire)

        assertEquals(1, frames.size)
        val frame = frames[0]
        assertEquals("ERROR", frame.command)
        assertEquals("Authentication failed", frame.headers["message"])
        assertEquals("Token expired", frame.body)
    }

    @Test
    fun test_stomp_parse_multiple_frames_in_stream() {
        val stream = "CONNECTED\nversion:1.2\n\n\u0000\nMESSAGE\ndestination:/topic/emergencies\n\n{}\u0000"
        val frames = StompFrame.parse(stream)

        assertEquals(2, frames.size)
        assertEquals("CONNECTED", frames[0].command)
        assertEquals("MESSAGE", frames[1].command)
    }

    // =========================================================================
    // 2. SAFEWALK WEBSOCKET CLIENT STATE TESTS
    // =========================================================================

    @Test
    fun test_websocket_client_initial_state() {
        val client = SafeWalkWebSocketClient(
            okHttpClient = OkHttpClient(),
            tokenProvider = { null },
            baseUrlProvider = { "http://10.0.2.2:8080/" }
        )

        assertEquals(WebSocketConnectionState.DISCONNECTED, client.connectionState.value)
    }

    @Test
    fun test_websocket_client_connect_without_token_stays_disconnected() {
        val client = SafeWalkWebSocketClient(
            okHttpClient = OkHttpClient(),
            tokenProvider = { "" },
            baseUrlProvider = { "http://10.0.2.2:8080/" }
        )

        client.connect()
        assertEquals(WebSocketConnectionState.DISCONNECTED, client.connectionState.value)
    }

    @Test
    fun test_websocket_client_subscribe_and_unsubscribe_tracking() {
        val client = SafeWalkWebSocketClient(
            okHttpClient = OkHttpClient(),
            tokenProvider = { "valid_token" },
            baseUrlProvider = { "http://10.0.2.2:8080/" }
        )

        val subId1 = client.subscribe("/topic/emergencies")
        assertNotNull(subId1)
        assertTrue(subId1.startsWith("sub-"))

        // Duplicate subscription to same topic returns same subscription ID
        val subId2 = client.subscribe("/topic/emergencies")
        assertEquals(subId1, subId2)

        // Distinct topic generates distinct subscription ID
        val subId3 = client.subscribe("/topic/sos/55")
        assertTrue(subId3 != subId1)

        client.unsubscribe("/topic/emergencies")
        client.disconnect()
        assertEquals(WebSocketConnectionState.DISCONNECTED, client.connectionState.value)
    }

    // =========================================================================
    // 3. REALTIME EVENT DESERIALIZATION & DOMAIN CONVERSION TESTS
    // =========================================================================

    @Test
    fun test_emergency_response_dto_maps_to_domain_correctly() {
        val dto = EmergencyResponseDto(
            id = 42L,
            requesterId = 1L,
            requesterName = "Aarav Sharma",
            requesterPhone = "+919999911111",
            responderId = 2L,
            responderName = "Officer Vijay",
            responderPhone = "+919999922222",
            responders = listOf(
                UserResponseDto(
                    id = 2L,
                    name = "Officer Vijay",
                    mobileNumber = "+919999922222",
                    role = "POLICE",
                    accountStatus = "ACTIVE"
                )
            ),
            type = "GENERAL",
            status = "ACCEPTED",
            latitude = 28.6139,
            longitude = 77.2090,
            address = "Connaught Place, New Delhi",
            createdAt = "2026-10-04T01:30:00",
            distanceMeters = 150.0
        )

        val domain = dto.toDomain()
        assertEquals("EMG-42", domain.id)
        assertEquals("42", domain.numericId)
        assertEquals("Aarav Sharma", domain.requester.name)
        assertEquals(EmergencyStatus.ACCEPTED, domain.status)
        assertEquals(1, domain.acceptedResponders.size)
        assertEquals("Officer Vijay", domain.acceptedResponders[0].name)
        assertEquals(UserRole.POLICE, domain.acceptedResponders[0].role)
        assertEquals(28.6139, domain.requesterLocation.latitude, 0.0001)
        assertEquals(77.2090, domain.requesterLocation.longitude, 0.0001)
        assertEquals(150.0, domain.distanceMeters ?: 0.0, 0.0001)
    }

    @Test
    fun test_location_update_event_structure() {
        val loc = Location(latitude = 28.6140, longitude = 77.2095, address = "Live Location")
        val event = RealtimeEmergencyEvent.LocationUpdated(
            emergencyId = "EMG-42",
            participantId = "2",
            location = loc,
            distanceToCounterpartMeters = 45.0
        )

        assertEquals("EMG-42", event.emergencyId)
        assertEquals("2", event.participantId)
        assertEquals("LOCATION_UPDATED", event.eventType)
        assertEquals(45.0, event.distanceToCounterpartMeters ?: 0.0, 0.0001)
    }

    @Test
    fun test_responder_arrived_event_structure() {
        val responder = User(
            id = "2",
            safeWalkId = "SW-2",
            name = "Officer Vijay",
            phone = "+919999922222",
            email = "",
            role = UserRole.POLICE
        )
        val event = RealtimeEmergencyEvent.ResponderArrived(
            emergencyId = "EMG-42",
            responder = responder
        )

        assertEquals("EMG-42", event.emergencyId)
        assertEquals("RESPONDER_ARRIVED", event.eventType)
        assertEquals("Officer Vijay", event.responder.name)
    }

    @Test
    fun test_emergency_resolved_event_structure() {
        val event = RealtimeEmergencyEvent.EmergencyResolved(
            emergencyId = "EMG-42",
            resolvedAt = 1700000000000L
        )

        assertEquals("EMG-42", event.emergencyId)
        assertEquals("SOS_RESOLVED", event.eventType)
        assertEquals(1700000000000L, event.resolvedAt)
    }

    @Test
    fun test_emergency_cancelled_event_structure() {
        val event = RealtimeEmergencyEvent.EmergencyCancelled(
            emergencyId = "EMG-42",
            reason = "False alarm"
        )

        assertEquals("EMG-42", event.emergencyId)
        assertEquals("SOS_CANCELLED", event.eventType)
        assertEquals("False alarm", event.reason)
    }

    @Test
    fun test_resolution_confirmation_pending_structure() {
        val event = RealtimeEmergencyEvent.ResolutionConfirmationPending(
            emergencyId = "EMG-42",
            requesterResolveConfirmed = true,
            responderResolveConfirmed = false,
            message = "Requester confirmed resolution"
        )

        assertEquals("EMG-42", event.emergencyId)
        assertEquals("RESOLUTION_CONFIRMATION_PENDING", event.eventType)
        assertTrue(event.requesterResolveConfirmed)
        assertFalse(event.responderResolveConfirmed)
    }
}
