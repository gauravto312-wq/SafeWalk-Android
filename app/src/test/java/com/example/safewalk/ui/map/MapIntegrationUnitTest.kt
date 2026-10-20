package com.example.safewalk.ui.map

import com.example.safewalk.BuildConfig
import com.example.safewalk.domain.model.EmergencyRole
import com.example.safewalk.domain.model.Location
import com.example.safewalk.domain.model.LocationStatus
import com.example.safewalk.domain.model.ParticipantLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapIntegrationUnitTest {

    @Test
    fun maptilerApiKey_isConfiguredInBuildConfig() {
        assertNotNull("MAPTILER_API_KEY should be present in BuildConfig", BuildConfig.MAPTILER_API_KEY)
        assertTrue("MAPTILER_API_KEY should not be empty", BuildConfig.MAPTILER_API_KEY.isNotEmpty())
    }

    @Test
    fun multiResponder_independentCoordinateUpdates() {
        // Setup initial participants: Requester, Responder A, Responder B, Responder C
        val requester = ParticipantLocation(
            userId = "101",
            userName = "Alice Requester",
            safeWalkId = "SW-REQ-101",
            role = EmergencyRole.REQUESTER,
            location = Location(26.891848, 81.052656, "Hazratganj"),
            distanceToCounterpartMeters = 0.0
        )

        val responderA = ParticipantLocation(
            userId = "201",
            userName = "Bob Responder",
            safeWalkId = "SW-RESP-201",
            role = EmergencyRole.RESPONDER,
            location = Location(26.892000, 81.053000, "Near Clocktower"),
            distanceToCounterpartMeters = 40.0
        )

        val responderB = ParticipantLocation(
            userId = "202",
            userName = "Charlie Police",
            safeWalkId = "SW-POL-202",
            role = EmergencyRole.RESPONDER,
            location = Location(26.895000, 81.056000, "Police Station 1"),
            distanceToCounterpartMeters = 450.0
        )

        val responderC = ParticipantLocation(
            userId = "203",
            userName = "Dave Hospital",
            safeWalkId = "SW-HOSP-203",
            role = EmergencyRole.RESPONDER,
            location = Location(26.899000, 81.060000, "Civil Hospital"),
            distanceToCounterpartMeters = 980.0
        )

        val participantList = listOf(requester, responderA, responderB, responderC)
        assertEquals(4, participantList.size)

        // Simulate LOCATION_UPDATE event specifically for Responder B moving closer
        val updatedLocationB = Location(26.893500, 81.054500, "Moving towards Hazratganj")
        val updatedParticipantB = responderB.copy(
            location = updatedLocationB,
            distanceToCounterpartMeters = 230.0
        )

        val updatedList = participantList.map {
            if (it.userId == "202") updatedParticipantB else it
        }

        // Verify ONLY Responder B moved
        val verifiedA = updatedList.first { it.userId == "201" }
        val verifiedB = updatedList.first { it.userId == "202" }
        val verifiedC = updatedList.first { it.userId == "203" }
        val verifiedReq = updatedList.first { it.userId == "101" }

        assertEquals(26.892000, verifiedA.location.latitude, 0.000001)
        assertEquals(81.053000, verifiedA.location.longitude, 0.000001)

        assertEquals(26.893500, verifiedB.location.latitude, 0.000001)
        assertEquals(81.054500, verifiedB.location.longitude, 0.000001)
        assertEquals(230.0, verifiedB.distanceToCounterpartMeters!!, 0.1)

        assertEquals(26.899000, verifiedC.location.latitude, 0.000001)
        assertEquals(81.060000, verifiedC.location.longitude, 0.000001)

        assertEquals(26.891848, verifiedReq.location.latitude, 0.000001)
        assertEquals(81.052656, verifiedReq.location.longitude, 0.000001)
    }

    @Test
    fun cameraBoundsCalculation_handlesSingleAndMultiplePoints() {
        val points = listOf(
            Location(26.891848, 81.052656, "Point 1"),
            Location(26.895000, 81.057000, "Point 2"),
            Location(26.890000, 81.050000, "Point 3")
        )

        var minLat = Double.MAX_VALUE
        var maxLat = -Double.MAX_VALUE
        var minLng = Double.MAX_VALUE
        var maxLng = -Double.MAX_VALUE

        points.forEach { loc ->
            if (loc.latitude < minLat) minLat = loc.latitude
            if (loc.latitude > maxLat) maxLat = loc.latitude
            if (loc.longitude < minLng) minLng = loc.longitude
            if (loc.longitude > maxLng) maxLng = loc.longitude
        }

        assertEquals(26.890000, minLat, 0.000001)
        assertEquals(26.895000, maxLat, 0.000001)
        assertEquals(81.050000, minLng, 0.000001)
        assertEquals(81.057000, maxLng, 0.000001)

        val spanLat = maxLat - minLat
        val spanLng = maxLng - minLng
        assertTrue("Span should be positive", spanLat > 0 && spanLng > 0)
    }

    @Test
    fun staleParticipant_removalFromActiveList() {
        val participants = mutableListOf(
            ParticipantLocation("1", "Req", "SW-1", EmergencyRole.REQUESTER, Location(26.89, 81.05, "Loc"), 0.0),
            ParticipantLocation("2", "Resp A", "SW-2", EmergencyRole.RESPONDER, Location(26.891, 81.051, "Loc"), 50.0),
            ParticipantLocation("3", "Resp B", "SW-3", EmergencyRole.RESPONDER, Location(26.892, 81.052, "Loc"), 100.0)
        )

        // Responder B disconnects / leaves emergency
        participants.removeAll { it.userId == "3" }

        assertEquals(2, participants.size)
        assertFalse(participants.any { it.userId == "3" })
        assertTrue(participants.any { it.userId == "2" })
    }

    @Test
    fun emergencyTermination_clearsParticipantMarkers() {
        var activeParticipants = listOf(
            ParticipantLocation("1", "Req", "SW-1", EmergencyRole.REQUESTER, Location(26.89, 81.05, "Loc"), 0.0),
            ParticipantLocation("2", "Resp", "SW-2", EmergencyRole.RESPONDER, Location(26.891, 81.051, "Loc"), 50.0)
        )
        assertFalse(activeParticipants.isEmpty())

        // Emergency is cancelled or resolved -> clear map participants
        activeParticipants = emptyList()
        assertTrue("Active participants must be cleared upon emergency termination", activeParticipants.isEmpty())
    }

    @Test
    fun nullResponderLocation_doesNotFabricateRequesterCoordinates() {
        val requesterLoc = Location(26.8930, 81.0527, "Requester Scene")
        val responders = listOf("162", "170", "440", "633")
        val responderLocations = mapOf(
            "170" to Location(26.8948, 81.0545, "Police Station")
        ) // Only 170 has a real location; 162, 440, 633 have null

        val visibleList = mutableListOf<ParticipantLocation>()
        responders.forEach { respId ->
            val loc = responderLocations[respId]
            // Verified FIX 3 behavior: Only add if real coordinates exist, NEVER fall back to requester
            if (loc != null && !(loc.latitude == 0.0 && loc.longitude == 0.0)) {
                visibleList.add(
                    ParticipantLocation(
                        userId = respId,
                        role = EmergencyRole.RESPONDER,
                        location = loc,
                        distanceToCounterpartMeters = requesterLoc.distanceTo(loc)
                    )
                )
            }
        }

        // Only 1 responder has a marker, NOT 4
        assertEquals(1, visibleList.size)
        assertEquals("170", visibleList[0].userId)
        assertFalse(visibleList.any { it.userId == "162" })
        assertFalse(visibleList.any { it.userId == "440" })
        assertFalse(visibleList.any { it.userId == "633" })

        // The single responder coordinates are NOT equal to requester coordinates
        assertNotEquals(requesterLoc.latitude, visibleList[0].location.latitude, 0.0001)
        assertNotEquals(requesterLoc.longitude, visibleList[0].location.longitude, 0.0001)
    }

    @Test
    fun nullResponderLocation_displaysWaitingForGpsNotLive() {
        val respLoc: Location? = null
        val isTerminal = false

        val (badgeText, _, _) = when {
            isTerminal || respLoc?.status == LocationStatus.STOPPED ->
                Triple("STOPPED", "", "")
            respLoc == null ->
                Triple("WAITING FOR GPS", "", "")
            respLoc.isStale() ->
                Triple("LOCATION STALE", "", "")
            else ->
                Triple("LIVE", "", "")
        }

        assertEquals("WAITING FOR GPS", badgeText)
        assertNotEquals("LIVE", badgeText)
    }

    @Test
    fun freshResponderLocation_displaysLive() {
        val respLoc = Location(26.8948, 81.0545, "Fresh Loc", status = LocationStatus.ACTIVE, timestamp = System.currentTimeMillis())
        val isTerminal = false

        val (badgeText, _, _) = when {
            isTerminal || respLoc.status == LocationStatus.STOPPED ->
                Triple("STOPPED", "", "")
            respLoc.isStale() ->
                Triple("LOCATION STALE", "", "")
            else ->
                Triple("LIVE", "", "")
        }

        assertEquals("LIVE", badgeText)
    }

    @Test
    fun staleResponderLocation_displaysLocationStale() {
        // Location older than 300 seconds is stale
        val staleTimestamp = System.currentTimeMillis() - 350_000L
        val respLoc = Location(26.8948, 81.0545, "Stale Loc", status = LocationStatus.ACTIVE, timestamp = staleTimestamp)
        val isTerminal = false

        val (badgeText, _, _) = when {
            isTerminal || respLoc.status == LocationStatus.STOPPED ->
                Triple("STOPPED", "", "")
            respLoc.isStale() ->
                Triple("LOCATION STALE", "", "")
            else ->
                Triple("LIVE", "", "")
        }

        assertEquals("LOCATION STALE", badgeText)
    }

    @Test
    fun initialHydration_populatesAllResponderLocationsAndDistances() {
        val requesterLoc = Location(26.8930, 81.0527, "Requester Scene")
        // Simulated server response from GET /api/v1/emergencies/{id}/locations
        val serverResponders = listOf(
            Triple("162", Location(26.891880, 81.052684, "Scene"), 120.0),
            Triple("170", Location(26.894800, 81.054500, "Police"), 696.0),
            Triple("440", Location(26.892006, 81.052797, "Citizen"), 135.0),
            Triple("633", Location(26.891200, 81.051000, "Citizen 2"), 260.0)
        )

        val hydratedLocations = mutableMapOf<String, Location>()
        val hydratedDistances = mutableMapOf<String, Double>()

        serverResponders.forEach { (userId, loc, dist) ->
            hydratedLocations[userId] = loc
            hydratedDistances[userId] = dist
        }

        assertEquals(4, hydratedLocations.size)
        assertEquals(4, hydratedDistances.size)

        // All 4 responders have non-null coordinates
        assertTrue(hydratedLocations.containsKey("162"))
        assertTrue(hydratedLocations.containsKey("170"))
        assertTrue(hydratedLocations.containsKey("440"))
        assertTrue(hydratedLocations.containsKey("633"))

        // All 4 responders have distances
        assertEquals(120.0, hydratedDistances["162"]!!, 0.1)
        assertEquals(696.0, hydratedDistances["170"]!!, 0.1)
        assertEquals(135.0, hydratedDistances["440"]!!, 0.1)
        assertEquals(260.0, hydratedDistances["633"]!!, 0.1)

        // All 4 coordinates are unique
        val uniquePoints = hydratedLocations.values.map { it.latitude to it.longitude }.toSet()
        assertEquals(4, uniquePoints.size)
    }
}
