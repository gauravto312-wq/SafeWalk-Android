package com.example.safewalk

import android.content.SharedPreferences
import com.example.safewalk.core.error.SafeWalkErrorMapper
import com.example.safewalk.core.error.SafeWalkException
import com.example.safewalk.data.model.dto.ApiResponseDto
import com.example.safewalk.data.model.dto.AuthResponseDto
import com.example.safewalk.data.model.dto.LoginRequestDto
import com.example.safewalk.data.model.dto.RegisterRequestDto
import com.example.safewalk.data.model.dto.UserResponseDto
import com.example.safewalk.data.model.dto.VerificationDocumentResponseDto
import com.example.safewalk.data.model.dto.toDomain
import com.example.safewalk.data.remote.AuthApiService
import com.example.safewalk.data.repository.RemoteAuthRepository
import com.example.safewalk.data.session.SessionManager
import com.example.safewalk.data.source.remote.RemoteAuthDataSource
import com.example.safewalk.domain.model.User
import com.example.safewalk.domain.model.UserRole
import com.example.safewalk.domain.model.VerificationStatus
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class Step2PoliceHospitalIntegrationUnitTest {

    private class FakeAuthApiService : AuthApiService {
        var registerResult: Response<ApiResponseDto<AuthResponseDto>>? = null
        var loginResult: Response<ApiResponseDto<AuthResponseDto>>? = null
        var getCurrentUserResult: Response<ApiResponseDto<UserResponseDto>>? = null
        var uploadDocResult: Response<ApiResponseDto<VerificationDocumentResponseDto>>? = null
        var getDocsResult: Response<ApiResponseDto<List<VerificationDocumentResponseDto>>>? = null

        var lastRegisterRequest: RegisterRequestDto? = null
        var lastLoginRequest: LoginRequestDto? = null

        override suspend fun register(request: RegisterRequestDto): Response<ApiResponseDto<AuthResponseDto>> {
            lastRegisterRequest = request
            return registerResult ?: Response.success(
                ApiResponseDto(
                    success = true,
                    message = "User registered successfully",
                    data = AuthResponseDto(
                        token = "jwt_step2_token",
                        tokenType = "Bearer",
                        user = UserResponseDto(
                            id = 201L,
                            name = request.name,
                            mobileNumber = request.mobileNumber,
                            role = request.role ?: "USER",
                            accountStatus = "ACTIVE",
                            policeVerified = false,
                            verificationStatus = if (request.role == "POLICE" || request.role == "HOSPITAL") "PENDING" else "NONE"
                        )
                    )
                )
            )
        }

        override suspend fun login(request: LoginRequestDto): Response<ApiResponseDto<AuthResponseDto>> {
            lastLoginRequest = request
            return loginResult ?: Response.success(
                ApiResponseDto(
                    success = true,
                    message = "Login successful",
                    data = AuthResponseDto(
                        token = "jwt_step2_token",
                        tokenType = "Bearer",
                        user = UserResponseDto(
                            id = 201L,
                            name = "Authority User",
                            mobileNumber = request.mobileNumber,
                            role = "POLICE",
                            accountStatus = "ACTIVE",
                            policeVerified = false,
                            verificationStatus = "PENDING"
                        )
                    )
                )
            )
        }

        override suspend fun getCurrentUser(): Response<ApiResponseDto<UserResponseDto>> {
            return getCurrentUserResult ?: Response.success(
                ApiResponseDto(
                    success = true,
                    message = "Current user profile retrieved successfully",
                    data = UserResponseDto(
                        id = 201L,
                        name = "Authority User",
                        mobileNumber = "+919876500001",
                        role = "POLICE",
                        accountStatus = "ACTIVE",
                        policeVerified = false,
                        verificationStatus = "PENDING"
                    )
                )
            )
        }

        override suspend fun uploadVerificationDocument(
            file: MultipartBody.Part,
            title: RequestBody?
        ): Response<ApiResponseDto<VerificationDocumentResponseDto>> {
            return uploadDocResult ?: Response.success(
                ApiResponseDto(
                    success = true,
                    message = "Verification document uploaded successfully",
                    data = VerificationDocumentResponseDto(
                        id = 99L,
                        userId = 201L,
                        title = "Official Badge ID",
                        fileName = "badge.pdf",
                        mimeType = "application/pdf",
                        fileSize = 1024L,
                        uploadedAt = "2026-10-02T10:00:00"
                    )
                )
            )
        }

        override suspend fun getVerificationDocuments(): Response<ApiResponseDto<List<VerificationDocumentResponseDto>>> {
            return getDocsResult ?: Response.success(
                ApiResponseDto(
                    success = true,
                    message = "Verification documents retrieved successfully",
                    data = listOf(
                        VerificationDocumentResponseDto(
                            id = 1L,
                            userId = 201L,
                            title = "Department ID Card",
                            fileName = "police_badge.pdf",
                            mimeType = "application/pdf",
                            fileSize = 1024L,
                            uploadedAt = "2026-10-02T10:00:00"
                        )
                    )
                )
            )
        }

        override suspend fun getEmergencyContacts(): Response<ApiResponseDto<List<com.example.safewalk.data.model.dto.EmergencyContactResponseDto>>> {
            return Response.success(ApiResponseDto(success = true, message = "OK", data = emptyList()))
        }

        override suspend fun addEmergencyContact(request: com.example.safewalk.data.model.dto.EmergencyContactRequestDto): Response<ApiResponseDto<com.example.safewalk.data.model.dto.EmergencyContactResponseDto>> {
            return Response.success(ApiResponseDto(success = true, message = "OK", data = com.example.safewalk.data.model.dto.EmergencyContactResponseDto(1L, 201L, 202L, "SW-202", "Contact", "1234567890", null, "")))
        }

        override suspend fun replaceEmergencyContact(contactIdentifier: String, request: com.example.safewalk.data.model.dto.EmergencyContactRequestDto): Response<ApiResponseDto<com.example.safewalk.data.model.dto.EmergencyContactResponseDto>> {
            return Response.success(ApiResponseDto(success = true, message = "OK", data = com.example.safewalk.data.model.dto.EmergencyContactResponseDto(1L, 201L, 203L, "SW-203", "Replaced", "1234567890", null, "")))
        }

        override suspend fun removeEmergencyContact(contactIdentifier: String): Response<ApiResponseDto<Unit>> {
            return Response.success(ApiResponseDto(success = true, message = "OK", data = Unit))
        }

        override suspend fun uploadAvatar(file: okhttp3.MultipartBody.Part): Response<ApiResponseDto<UserResponseDto>> {
            return Response.success(ApiResponseDto(success = true, message = "OK", data = UserResponseDto(201L, "Officer", "9876543210", "POLICE", "ACTIVE", true, avatarUrl = "/api/v1/users/me/avatar")))
        }
    }

    private class FakeSharedPreferences : SharedPreferences {
        private val map = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = map
        override fun getString(key: String?, defValue: String?): String? = map[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
        override fun getInt(key: String?, defValue: Int): Int = map[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = map[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = map[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = map.containsKey(key)
        override fun edit(): SharedPreferences.Editor = FakeEditor(map)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        private class FakeEditor(private val map: MutableMap<String, Any?>) : SharedPreferences.Editor {
            private val temp = mutableMapOf<String, Any?>()
            private var clearFlag = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = this
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = this
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = this
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = this
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = this
            override fun remove(key: String?): SharedPreferences.Editor {
                if (key != null) temp.remove(key)
                return this
            }
            override fun clear(): SharedPreferences.Editor {
                clearFlag = true
                temp.clear()
                return this
            }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                if (clearFlag) map.clear()
                map.putAll(temp)
            }
        }
    }

    private lateinit var fakeApi: FakeAuthApiService
    private lateinit var dataSource: RemoteAuthDataSource
    private lateinit var sessionManager: SessionManager
    private lateinit var repository: RemoteAuthRepository

    @Before
    fun setUp() {
        fakeApi = FakeAuthApiService()
        dataSource = RemoteAuthDataSource(fakeApi)
        val fakePrefs = FakeSharedPreferences()
        sessionManager = SessionManager(injectedPrefs = fakePrefs)
        repository = RemoteAuthRepository(
            remoteDataSource = dataSource,
            sessionManager = sessionManager
        )
    }

    @Test
    fun test01_policeRegistration_transmitsRoleAndReceivesPendingStatus() = runBlocking {
        val result = repository.register(
            name = "Inspector Vijay",
            email = "vijay@police.gov.in",
            phone = "+919876500001",
            password = "Password123!",
            confirmPassword = "Password123!",
            role = UserRole.POLICE,
            emergencyPassword = "SafeWalkEmergency999"
        )

        assertTrue("Police registration should succeed", result.isSuccess)
        val user = result.getOrThrow()
        assertEquals("Inspector Vijay", user.name)
        assertEquals(UserRole.POLICE, user.role)
        assertEquals(VerificationStatus.PENDING, user.verificationStatus)
        assertFalse("Police should not be verified initially", user.isVerified)

        // Verify request DTO sent to API contains role = "POLICE"
        assertNotNull(fakeApi.lastRegisterRequest)
        assertEquals("POLICE", fakeApi.lastRegisterRequest?.role)
        assertEquals("+919876500001", fakeApi.lastRegisterRequest?.mobileNumber)

        // Verify session saved
        assertTrue(sessionManager.hasActiveSession())
        assertEquals("jwt_step2_token", sessionManager.getToken())
    }

    @Test
    fun test02_hospitalRegistration_transmitsRoleAndReceivesPendingStatus() = runBlocking {
        val result = repository.register(
            name = "City Care Hospital",
            email = "admin@citycare.org",
            phone = "+919876500002",
            password = "Password123!",
            confirmPassword = "Password123!",
            role = UserRole.HOSPITAL,
            emergencyPassword = "SafeWalkEmergency999"
        )

        assertTrue("Hospital registration should succeed", result.isSuccess)
        val user = result.getOrThrow()
        assertEquals("City Care Hospital", user.name)
        assertEquals(UserRole.HOSPITAL, user.role)
        assertEquals(VerificationStatus.PENDING, user.verificationStatus)
        assertFalse(user.isVerified)

        // Verify request DTO sent to API contains role = "HOSPITAL"
        assertEquals("HOSPITAL", fakeApi.lastRegisterRequest?.role)
        assertEquals("+919876500002", fakeApi.lastRegisterRequest?.mobileNumber)
    }

    @Test
    fun test03_citizenRegistration_preservesExistingBehaviorAndNoneStatus() = runBlocking {
        val result = repository.register(
            name = "Citizen Priya",
            email = "priya@safewalk.local",
            phone = "+919876500003",
            password = "Password123!",
            confirmPassword = "Password123!",
            role = UserRole.USER
        )

        assertTrue("Citizen registration should succeed", result.isSuccess)
        val user = result.getOrThrow()
        assertEquals(UserRole.USER, user.role)
        assertEquals(VerificationStatus.NONE, user.verificationStatus)
        assertFalse(user.isVerified)
        assertEquals("USER", fakeApi.lastRegisterRequest?.role)
    }

    @Test
    fun test04_adminRegistration_isBlockedOnClient() = runBlocking {
        val result = repository.register(
            name = "Malicious Admin",
            email = "admin@safewalk.local",
            phone = "+919876500099",
            password = "Password123!",
            confirmPassword = "Password123!",
            role = UserRole.ADMIN
        )

        assertTrue("Admin registration must fail on client", result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is SafeWalkException.ValidationError)
        assertTrue(ex?.message?.contains("ADMIN registration is not permitted") == true)
        assertNull("No network request should have been dispatched", fakeApi.lastRegisterRequest)
    }

    @Test
    fun test05_duplicateMobile_returnsExactConflictMessage() {
        val errorJson = """
            {
                "success": false,
                "message": "Mobile number already registered: +919876500001",
                "status": 409
            }
        """.trimIndent()
        val errorBody = errorJson.toResponseBody("application/json".toMediaType())
        val exception = SafeWalkErrorMapper.mapHttpError(409, errorBody)

        assertTrue(exception is SafeWalkException.ConflictError)
        assertEquals(
            "This mobile number is already registered across SafeWalk accounts",
            exception.message
        )
    }

    @Test
    fun test06_verificationStatusDtoMapping() {
        // Police Pending
        val pendingDto = UserResponseDto(
            id = 1L,
            name = "Cop",
            mobileNumber = "123",
            role = "POLICE",
            accountStatus = "ACTIVE",
            policeVerified = false,
            verificationStatus = "PENDING"
        )
        val pendingUser = pendingDto.toDomain()
        assertEquals(VerificationStatus.PENDING, pendingUser.verificationStatus)
        assertFalse(pendingUser.isVerified)

        // Police Verified
        val verifiedDto = UserResponseDto(
            id = 2L,
            name = "Cop",
            mobileNumber = "123",
            role = "POLICE",
            accountStatus = "ACTIVE",
            policeVerified = true,
            verificationStatus = "VERIFIED"
        )
        val verifiedUser = verifiedDto.toDomain()
        assertEquals(VerificationStatus.VERIFIED, verifiedUser.verificationStatus)
        assertTrue(verifiedUser.isVerified)

        // Hospital Rejected
        val rejectedDto = UserResponseDto(
            id = 3L,
            name = "Hospital",
            mobileNumber = "123",
            role = "HOSPITAL",
            accountStatus = "ACTIVE",
            policeVerified = false,
            verificationStatus = "REJECTED"
        )
        val rejectedUser = rejectedDto.toDomain()
        assertEquals(VerificationStatus.REJECTED, rejectedUser.verificationStatus)
        assertFalse(rejectedUser.isVerified)

        // Citizen None
        val citizenDto = UserResponseDto(
            id = 4L,
            name = "Citizen",
            mobileNumber = "123",
            role = "USER",
            accountStatus = "ACTIVE",
            policeVerified = false,
            verificationStatus = "NONE"
        )
        val citizenUser = citizenDto.toDomain()
        assertEquals(VerificationStatus.NONE, citizenUser.verificationStatus)
    }

    @Test
    fun test07_verificationDocumentMapping() {
        val dto = VerificationDocumentResponseDto(
            id = 42L,
            userId = 201L,
            title = "Official Badge ID",
            fileName = "badge.pdf",
            mimeType = "application/pdf",
            fileSize = 2048L,
            uploadedAt = "2026-10-02T12:30:00"
        )
        val domainDoc = dto.toDomain()
        assertEquals("42", domainDoc.id)
        assertEquals("Official Badge ID", domainDoc.title)
        assertEquals("badge.pdf", domainDoc.fileName)
        assertTrue(domainDoc.uploadedAt > 0)
    }

    @Test
    fun test08_uploadVerificationDocument_succeedsAndUpdatesUser() = runBlocking {
        // Log in first to establish active session
        repository.login("+919876500001", "Password123!")

        val result = repository.uploadVerificationDocument(
            title = "Official Badge ID",
            fileName = "badge.pdf",
            fileBytes = "%PDF-1.4 sample content".toByteArray(),
            mimeType = "application/pdf"
        )

        assertTrue("Document upload should succeed", result.isSuccess)
        val user = result.getOrThrow()
        assertEquals(2, user.documents.size) // 1 from initial login + 1 uploaded
        assertEquals("Official Badge ID", user.documents[1].title)
        assertEquals("badge.pdf", user.documents[1].fileName)
    }

    @Test
    fun test09_sessionRestoration_fetchesAuthorityDocuments() = runBlocking {
        sessionManager.saveSession(
            "valid_token",
            User(
                id = "201",
                safeWalkId = "SW-201",
                name = "Authority User",
                phone = "+919876500001",
                email = "",
                role = UserRole.POLICE,
                verificationStatus = VerificationStatus.PENDING
            )
        )

        val result = repository.restoreSession()
        assertTrue("Session restoration should succeed", result.isSuccess)
        val user = result.getOrThrow()
        assertNotNull(user)
        assertEquals(UserRole.POLICE, user?.role)
        val userDocs = user?.documents
        assertNotNull(userDocs)
        assertEquals(1, userDocs?.size)
        assertEquals("police_badge.pdf", userDocs?.get(0)?.fileName)
    }

    @Test
    fun test10_authorityLogin_fetchesVerificationDocuments() = runBlocking {
        val result = repository.login("+919876500001", "Password123!")
        assertTrue("Login should succeed", result.isSuccess)
        val user = result.getOrThrow()
        assertEquals(UserRole.POLICE, user.role)
        assertEquals(1, user.documents.size)
        assertEquals("Department ID Card", user.documents[0].title)
    }
}
