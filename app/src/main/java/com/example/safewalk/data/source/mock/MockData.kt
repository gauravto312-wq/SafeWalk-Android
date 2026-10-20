package com.example.safewalk.data.source.mock

import com.example.safewalk.domain.model.Emergency
import com.example.safewalk.domain.model.EmergencyStatus
import com.example.safewalk.domain.model.Location
import com.example.safewalk.domain.model.User
import com.example.safewalk.domain.model.UserRole
import com.example.safewalk.domain.model.VerificationStatus

/**
 * Static mock data provider for SafeWalk offline development phase.
 */
object MockData {

    val mockLocations = listOf(
        Location(28.6139, 77.2090, "Connaught Place, Radial Road 2, New Delhi"),
        Location(28.6185, 77.2155, "Barakhamba Road, Near Metro Gate 3"),
        Location(28.6250, 77.2100, "Shivaji Stadium Bus Terminal"),
        Location(28.6120, 77.2270, "India Gate Circle, North Exit"),
        Location(28.6300, 77.2180, "Mandi House Roundabout")
    )

    val defaultLocation = mockLocations[0]

    val mockUsers = listOf(
        User(
            id = "user-001",
            safeWalkId = "SW-GAURAV-101",
            name = "Gaurav Mishra",
            phone = "+91 98765 43210",
            email = "gaurav@safewalk.local",
            role = UserRole.USER,
            isVerified = true,
            status = "Ready",
            verificationStatus = VerificationStatus.NONE,
            emergencyContacts = listOf("SW-RAHUL-102"),
            emergencyPassword = null
        ),
        User(
            id = "user-002",
            safeWalkId = "SW-RAHUL-102",
            name = "Rahul Sharma",
            phone = "+91 98765 43211",
            email = "rahul@safewalk.local",
            role = UserRole.USER,
            isVerified = true,
            status = "Ready",
            verificationStatus = VerificationStatus.NONE,
            emergencyPassword = null
        ),
        User(
            id = "user-003",
            safeWalkId = "SW-AMAN-103",
            name = "Aman Verma",
            phone = "+91 98765 43212",
            email = "aman@safewalk.local",
            role = UserRole.USER,
            isVerified = false,
            status = "Standby",
            verificationStatus = VerificationStatus.NONE,
            emergencyPassword = null
        ),
        User(
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
            designation = "Patrol Sergeant",
            emergencyPassword = null
        ),
        User(
            id = "hosp-001",
            safeWalkId = "SW-HOSP-102",
            name = "City Emergency Hospital",
            phone = "+91 102",
            email = "emergency@cityhospital.local",
            role = UserRole.HOSPITAL,
            isVerified = true,
            status = "Medical Unit Ready",
            verificationStatus = VerificationStatus.APPROVED,
            hospitalName = "City Emergency Hospital",
            licenseId = "HOSP-DL-4401",
            designation = "Trauma Dispatch Unit",
            emergencyPassword = null
        ),
        User(
            id = "admin-001",
            safeWalkId = "SW-ADMIN-001",
            name = "SafeWalk Administrator",
            phone = "+91 99999 88888",
            email = "admin@safewalk.local",
            role = UserRole.ADMIN,
            isVerified = true,
            status = "System Active",
            verificationStatus = VerificationStatus.APPROVED,
            emergencyPassword = null
        ),
        User(
            id = "user-004",
            safeWalkId = "SW-ROHIT-104",
            name = "Rohit Kumar",
            phone = "+91 98765 43213",
            email = "rohit@safewalk.local",
            role = UserRole.USER,
            isVerified = true,
            status = "Ready",
            verificationStatus = VerificationStatus.NONE,
            emergencyPassword = null
        )
    )

    val initialEmergencies: List<Emergency> = listOf(
        Emergency(
            id = "EMG-2026-8801",
            requester = mockUsers[1], // Rahul Sharma
            status = EmergencyStatus.ACTIVE,
            createdAt = System.currentTimeMillis() - (8 * 60 * 1000), // 8 mins ago
            requesterLocation = mockLocations[1],
            acceptedResponders = emptyList()
        ),
        Emergency(
            id = "EMG-2026-8802",
            requester = mockUsers[2], // Aman Verma
            status = EmergencyStatus.ACCEPTED,
            createdAt = System.currentTimeMillis() - (15 * 60 * 1000), // 15 mins ago
            requesterLocation = mockLocations[2],
            acceptedResponders = listOf(mockUsers[0]), // Gaurav Mishra accepted
            responderLocations = mapOf(mockUsers[0].id to mockLocations[0])
        ),
        Emergency(
            id = "EMG-2026-8799",
            requester = mockUsers[1],
            status = EmergencyStatus.RESOLVED,
            createdAt = System.currentTimeMillis() - (24 * 60 * 60 * 1000), // 1 day ago
            requesterLocation = mockLocations[3],
            acceptedResponders = listOf(mockUsers[3]),
            arrivedResponder = mockUsers[3],
            requesterConfirmedResolution = true,
            responderConfirmedResolution = true
        )
    )
}
