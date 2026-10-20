package com.example.safewalk.data

import com.example.safewalk.data.model.dto.RegisterRequestDto
import com.example.safewalk.data.model.dto.UserResponseDto
import com.example.safewalk.data.model.dto.toDomain
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthEmailSerializationTest {

    private val gson = Gson()

    @Test
    fun registerRequestDto_serializesEmailCorrectly() {
        val dto = RegisterRequestDto(
            name = "Aarav Sharma",
            mobileNumber = "+919876543210",
            password = "password123",
            email = "aarav.sharma@example.com"
        )

        val json = gson.toJson(dto)
        assertTrue(json.contains("\"email\":\"aarav.sharma@example.com\""))
        assertTrue(json.contains("\"mobileNumber\":\"+919876543210\""))
    }

    @Test
    fun registerRequestDto_omitsNullEmailWhenSerialized() {
        val dto = RegisterRequestDto(
            name = "Aarav Sharma",
            mobileNumber = "+919876543210",
            password = "password123",
            email = null
        )

        val json = gson.toJson(dto)
        // Gson default omits null fields
        assertTrue(!json.contains("\"email\":"))
    }

    @Test
    fun userResponseDto_deserializesEmailCorrectly() {
        val json = """
            {
                "id": 42,
                "name": "Aarav Sharma",
                "mobileNumber": "+919876543210",
                "email": "aarav.sharma@example.com",
                "role": "USER",
                "accountStatus": "ACTIVE"
            }
        """.trimIndent()

        val dto = gson.fromJson(json, UserResponseDto::class.java)
        assertEquals(42L, dto.id)
        assertEquals("aarav.sharma@example.com", dto.email)

        val domainUser = dto.toDomain()
        assertEquals("aarav.sharma@example.com", domainUser.email)
    }

    @Test
    fun userResponseDto_handlesNullEmailGracefully() {
        val json = """
            {
                "id": 42,
                "name": "Legacy User",
                "mobileNumber": "+919876543210",
                "email": null,
                "role": "USER",
                "accountStatus": "ACTIVE"
            }
        """.trimIndent()

        val dto = gson.fromJson(json, UserResponseDto::class.java)
        assertNull(dto.email)

        val domainUser = dto.toDomain()
        assertEquals("", domainUser.email)
    }
}
