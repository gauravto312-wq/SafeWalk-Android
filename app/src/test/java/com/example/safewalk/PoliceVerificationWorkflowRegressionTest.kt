package com.example.safewalk

import com.example.safewalk.data.model.dto.RegisterRequestDto
import com.example.safewalk.data.model.dto.UserResponseDto
import com.example.safewalk.data.model.dto.toDomain
import com.example.safewalk.domain.model.User
import com.example.safewalk.domain.model.UserRole
import com.example.safewalk.domain.model.VerificationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression test suite verifying:
 * 1. Police registration fields (badgeNumber, department, policeStation).
 * 2. Backend DTO to Android Domain User mapping (badgeId, department, rejectionReason, verificationStatus).
 * 3. Authority verification invariants: unverified / rejected officers are NEVER eligible for emergency response dispatch.
 * 4. FSM verification states (NONE, PENDING, VERIFIED, REJECTED).
 */
class PoliceVerificationWorkflowRegressionTest {

    @Test
    fun testRegisterRequestDto_includesPoliceFields() {
        val dto = RegisterRequestDto(
            name = "Officer Vikram Singh",
            mobileNumber = "+919876543210",
            password = "SecretPassword123!",
            emergencyPassword = "9999",
            role = "POLICE",
            badgeNumber = "DL-POLICE-007",
            department = "North Zone Precinct",
            policeStation = "North Zone Precinct"
        )

        assertEquals("DL-POLICE-007", dto.badgeNumber)
        assertEquals("North Zone Precinct", dto.department)
        assertEquals("North Zone Precinct", dto.policeStation)
        assertEquals("POLICE", dto.role)
    }

    @Test
    fun testUserResponseDto_mapsToDomain_verifiedPolice() {
        val dto = UserResponseDto(
            id = 42L,
            name = "Inspector Rajesh",
            mobileNumber = "+919876543211",
            role = "POLICE",
            accountStatus = "ACTIVE",
            policeVerified = true,
            verificationStatus = "VERIFIED",
            badgeNumber = "DL-POLICE-042",
            department = "South Delhi Central",
            policeStation = "South Delhi Central",
            rejectionReason = null,
            avatarUrl = "/api/v1/users/42/avatar"
        )

        val user = dto.toDomain()

        assertEquals("42", user.id)
        assertEquals("Inspector Rajesh", user.name)
        assertEquals(UserRole.POLICE, user.role)
        assertTrue("Verified police must have isVerified=true", user.isVerified)
        assertEquals(VerificationStatus.VERIFIED, user.verificationStatus)
        assertEquals("DL-POLICE-042", user.badgeId)
        assertEquals("South Delhi Central", user.department)
        assertNull(user.rejectionReason)
        assertTrue("Verified police officer MUST be dispatch eligible", user.isAuthorityAndVerified)
    }

    @Test
    fun testUserResponseDto_mapsToDomain_rejectedPolice_preservesReasonAndExcludesDispatch() {
        val dto = UserResponseDto(
            id = 43L,
            name = "Constable Sharma",
            mobileNumber = "+919876543212",
            role = "POLICE",
            accountStatus = "ACTIVE",
            policeVerified = false,
            verificationStatus = "REJECTED",
            badgeNumber = "DL-POLICE-043",
            department = "Central Station",
            policeStation = "Central Station",
            rejectionReason = "ID badge expired and seal blurry",
            avatarUrl = null
        )

        val user = dto.toDomain()

        assertEquals("43", user.id)
        assertEquals(UserRole.POLICE, user.role)
        assertFalse("Rejected officer must NOT be verified", user.isVerified)
        assertEquals(VerificationStatus.REJECTED, user.verificationStatus)
        assertEquals("ID badge expired and seal blurry", user.rejectionReason)
        assertEquals("DL-POLICE-043", user.badgeId)
        assertFalse("Rejected officer must NEVER be dispatch eligible", user.isAuthorityAndVerified)
    }

    @Test
    fun testUserResponseDto_mapsToDomain_pendingPolice_excludesDispatch() {
        val dto = UserResponseDto(
            id = 44L,
            name = "Officer Neha",
            mobileNumber = "+919876543213",
            role = "POLICE",
            accountStatus = "ACTIVE",
            policeVerified = false,
            verificationStatus = "PENDING",
            badgeNumber = "DL-POLICE-044",
            department = "Cyber Crime Cell",
            policeStation = "Cyber Crime Cell",
            rejectionReason = null,
            avatarUrl = null
        )

        val user = dto.toDomain()

        assertEquals(UserRole.POLICE, user.role)
        assertFalse("Pending officer must NOT be verified", user.isVerified)
        assertEquals(VerificationStatus.PENDING, user.verificationStatus)
        assertFalse("Pending officer must NEVER be dispatch eligible", user.isAuthorityAndVerified)
    }

    @Test
    fun testCitizenUser_alwaysHasNoneVerificationStatus() {
        val dto = UserResponseDto(
            id = 101L,
            name = "Aarav Citizen",
            mobileNumber = "+919876543214",
            role = "USER",
            accountStatus = "ACTIVE",
            policeVerified = false,
            verificationStatus = null
        )

        val user = dto.toDomain()

        assertEquals(UserRole.USER, user.role)
        assertEquals(VerificationStatus.NONE, user.verificationStatus)
        assertFalse("Citizen must not be authority and verified", user.isAuthorityAndVerified)
    }

    @Test
    fun testVerificationStatus_fromString_allTransitionsSupported() {
        assertEquals(VerificationStatus.VERIFIED, VerificationStatus.fromString("VERIFIED"))
        assertEquals(VerificationStatus.VERIFIED, VerificationStatus.fromString("APPROVED"))
        assertEquals(VerificationStatus.PENDING, VerificationStatus.fromString("PENDING"))
        assertEquals(VerificationStatus.REJECTED, VerificationStatus.fromString("REJECTED"))
        assertEquals(VerificationStatus.NONE, VerificationStatus.fromString("NONE"))
        assertEquals(VerificationStatus.NONE, VerificationStatus.fromString(null))
        assertEquals(VerificationStatus.NONE, VerificationStatus.fromString("unknown_status"))
    }
}
