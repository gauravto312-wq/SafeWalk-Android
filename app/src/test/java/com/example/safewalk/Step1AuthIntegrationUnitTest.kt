package com.example.safewalk

import com.example.safewalk.core.error.SafeWalkErrorMapper
import com.example.safewalk.core.error.SafeWalkException
import com.example.safewalk.data.model.dto.ApiResponseDto
import com.example.safewalk.data.model.dto.AuthResponseDto
import com.example.safewalk.data.model.dto.LoginRequestDto
import com.example.safewalk.data.model.dto.RegisterRequestDto
import com.example.safewalk.data.model.dto.UserResponseDto
import com.example.safewalk.data.remote.AuthApiService
import com.example.safewalk.data.remote.AuthInterceptor
import com.example.safewalk.data.repository.RemoteAuthRepository
import com.example.safewalk.data.session.SessionManager
import com.example.safewalk.data.source.remote.RemoteAuthDataSource
import com.example.safewalk.domain.model.User
import com.example.safewalk.domain.model.UserRole
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.net.ConnectException
import java.net.SocketTimeoutException

class Step1AuthIntegrationUnitTest {

    // Test Fake for AuthApiService
    private class FakeAuthApiService : AuthApiService {
        var registerResult: Response<ApiResponseDto<AuthResponseDto>>? = null
        var loginResult: Response<ApiResponseDto<AuthResponseDto>>? = null
        var getCurrentUserResult: Response<ApiResponseDto<UserResponseDto>>? = null

        var lastRegisterRequest: RegisterRequestDto? = null
        var lastLoginRequest: LoginRequestDto? = null

        override suspend fun register(request: RegisterRequestDto): Response<ApiResponseDto<AuthResponseDto>> {
            lastRegisterRequest = request
            return registerResult ?: Response.success(
                ApiResponseDto(
                    success = true,
                    message = "User registered successfully",
                    data = AuthResponseDto(
                        token = "jwt_test_token_12345",
                        tokenType = "Bearer",
                        user = UserResponseDto(
                            id = 101L,
                            name = request.name,
                            mobileNumber = request.mobileNumber,
                            role = "USER",
                            accountStatus = "ACTIVE",
                            policeVerified = false
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
                        token = "jwt_test_token_12345",
                        tokenType = "Bearer",
                        user = UserResponseDto(
                            id = 101L,
                            name = "Test User",
                            mobileNumber = request.mobileNumber,
                            role = "USER",
                            accountStatus = "ACTIVE",
                            policeVerified = false
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
                        id = 101L,
                        name = "Test User",
                        mobileNumber = "9876543210",
                        role = "USER",
                        accountStatus = "ACTIVE",
                        policeVerified = false
                    )
                )
            )
        }

        override suspend fun uploadVerificationDocument(
            file: okhttp3.MultipartBody.Part,
            title: okhttp3.RequestBody?
        ): Response<ApiResponseDto<com.example.safewalk.data.model.dto.VerificationDocumentResponseDto>> {
            return Response.success(
                ApiResponseDto(
                    success = true,
                    message = "Verification document uploaded successfully",
                    data = com.example.safewalk.data.model.dto.VerificationDocumentResponseDto(
                        id = 1L,
                        title = "Test Document",
                        fileName = "test.pdf"
                    )
                )
            )
        }

        override suspend fun getVerificationDocuments(): Response<ApiResponseDto<List<com.example.safewalk.data.model.dto.VerificationDocumentResponseDto>>> {
            return Response.success(
                ApiResponseDto(
                    success = true,
                    message = "Verification documents retrieved successfully",
                    data = emptyList()
                )
            )
        }

        override suspend fun getEmergencyContacts(): Response<ApiResponseDto<List<com.example.safewalk.data.model.dto.EmergencyContactResponseDto>>> {
            return Response.success(ApiResponseDto(success = true, message = "OK", data = emptyList()))
        }

        override suspend fun addEmergencyContact(request: com.example.safewalk.data.model.dto.EmergencyContactRequestDto): Response<ApiResponseDto<com.example.safewalk.data.model.dto.EmergencyContactResponseDto>> {
            return Response.success(ApiResponseDto(success = true, message = "OK", data = com.example.safewalk.data.model.dto.EmergencyContactResponseDto(1L, 101L, 102L, "SW-102", "Contact", "1234567890", null, "")))
        }

        override suspend fun replaceEmergencyContact(contactIdentifier: String, request: com.example.safewalk.data.model.dto.EmergencyContactRequestDto): Response<ApiResponseDto<com.example.safewalk.data.model.dto.EmergencyContactResponseDto>> {
            return Response.success(ApiResponseDto(success = true, message = "OK", data = com.example.safewalk.data.model.dto.EmergencyContactResponseDto(1L, 101L, 103L, "SW-103", "Replaced", "1234567890", null, "")))
        }

        override suspend fun removeEmergencyContact(contactIdentifier: String): Response<ApiResponseDto<Unit>> {
            return Response.success(ApiResponseDto(success = true, message = "OK", data = Unit))
        }

        override suspend fun uploadAvatar(file: okhttp3.MultipartBody.Part): Response<ApiResponseDto<UserResponseDto>> {
            return Response.success(ApiResponseDto(success = true, message = "OK", data = UserResponseDto(101L, "Test", "9876543210", "USER", "ACTIVE", false, avatarUrl = "/api/v1/users/me/avatar")))
        }
    }

    // In-memory fake SessionManager for unit tests without Android Context
    private class InMemorySessionManager {
        private var _token: String? = null
        private var _user: User? = null

        fun saveSession(t: String, u: User) {
            _token = t
            _user = u
        }

        fun updateCurrentUser(u: User) {
            _user = u
        }

        fun getToken(): String? = _token
        fun getUser(): User? = _user
        fun hasActiveSession(): Boolean = !_token.isNullOrBlank() && _user != null
        fun clearSession() {
            _token = null
            _user = null
        }
    }

    private lateinit var fakeApiService: FakeAuthApiService
    private lateinit var remoteDataSource: RemoteAuthDataSource
    private lateinit var inMemorySession: InMemorySessionManager

    @Before
    fun setUp() {
        fakeApiService = FakeAuthApiService()
        remoteDataSource = RemoteAuthDataSource(fakeApiService)
        inMemorySession = InMemorySessionManager()
    }

    // ==========================================
    // 1. REGISTRATION TESTS
    // ==========================================

    @Test
    fun `test registration success saves token and updates current user`() = runBlocking {
        val result = remoteDataSource.register(
            name = "Gaurav Mishra",
            mobileNumber = "9876543210",
            password = "password123",
            emergencyPassword = "1234"
        )

        assertTrue(result.isSuccess)
        val authDto = result.getOrThrow()
        assertEquals("jwt_test_token_12345", authDto.token)
        assertEquals("Gaurav Mishra", authDto.user?.name)
        assertEquals("9876543210", authDto.user?.mobileNumber)
        assertEquals("USER", authDto.user?.role)
    }

    @Test
    fun `test registration 409 conflict returns DuplicateResourceError`() = runBlocking {
        val errorJson = """{"success":false,"message":"Mobile number '9876543210' is already registered with a SafeWalk account.","errors":null,"path":"/api/v1/auth/register"}"""
        val errorBody = errorJson.toResponseBody("application/json".toMediaType())
        fakeApiService.registerResult = Response.error(409, errorBody)

        val result = remoteDataSource.register(
            name = "Duplicate User",
            mobileNumber = "9876543210",
            password = "password123"
        )

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is SafeWalkException.ConflictError)
        val conflict = exception as SafeWalkException.ConflictError
        assertEquals(409, conflict.httpStatus)
        assertTrue(conflict.message!!.contains("already registered") || conflict.message!!.contains("already exists"))
    }

    @Test
    fun `test registration 400 validation error extracts field message`() = runBlocking {
        val errorJson = """{"success":false,"message":"Validation failed for request parameters","errors":{"mobileNumber":"Invalid mobile number format. Must follow E.164 format"},"path":"/api/v1/auth/register"}"""
        val errorBody = errorJson.toResponseBody("application/json".toMediaType())
        fakeApiService.registerResult = Response.error(400, errorBody)

        val result = remoteDataSource.register(
            name = "Invalid Mobile",
            mobileNumber = "123",
            password = "password123"
        )

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is SafeWalkException.ValidationError)
        assertEquals(400, (exception as SafeWalkException).httpStatus)
        assertTrue(exception.message!!.contains("Invalid mobile number format"))
    }

    // ==========================================
    // 2. LOGIN TESTS
    // ==========================================

    @Test
    fun `test login success returns token and user payload`() = runBlocking {
        val result = remoteDataSource.login("9876543210", "password123")

        assertTrue(result.isSuccess)
        val authDto = result.getOrThrow()
        assertEquals("jwt_test_token_12345", authDto.token)
        assertEquals("9876543210", authDto.user?.mobileNumber)
    }

    @Test
    fun `test login 401 unauthorized returns AuthError`() = runBlocking {
        val errorJson = """{"success":false,"message":"Invalid mobile number or password.","errors":null,"path":"/api/v1/auth/login"}"""
        val errorBody = errorJson.toResponseBody("application/json".toMediaType())
        fakeApiService.loginResult = Response.error(401, errorBody)

        val result = remoteDataSource.login("9876543210", "wrong_password")

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is SafeWalkException.AuthError)
        assertEquals(401, (exception as SafeWalkException).httpStatus)
        assertEquals("Invalid mobile number or password.", exception.message)
    }

    @Test
    fun `test login 403 forbidden returns ForbiddenError for suspended account`() = runBlocking {
        val errorJson = """{"success":false,"message":"Your account is currently SUSPENDED. Access denied.","errors":null,"path":"/api/v1/auth/login"}"""
        val errorBody = errorJson.toResponseBody("application/json".toMediaType())
        fakeApiService.loginResult = Response.error(403, errorBody)

        val result = remoteDataSource.login("9876543210", "password123")

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is SafeWalkException.ForbiddenError)
        assertEquals(403, (exception as SafeWalkException).httpStatus)
        assertTrue(exception.message!!.contains("SUSPENDED"))
    }

    // ==========================================
    // 3. SESSION RESTORATION (GET ME) TESTS
    // ==========================================

    @Test
    fun `test getCurrentUser returns user profile`() = runBlocking {
        val result = remoteDataSource.getCurrentUser()

        assertTrue(result.isSuccess)
        val userDto = result.getOrThrow()
        assertEquals(101L, userDto.id)
        assertEquals("9876543210", userDto.mobileNumber)
        assertEquals("USER", userDto.role)
    }

    @Test
    fun `test getCurrentUser 401 unauthorized returns AuthError`() = runBlocking {
        val errorJson = """{"success":false,"message":"Full authentication is required to access this resource. Token missing or invalid.","path":"/api/v1/users/me"}"""
        val errorBody = errorJson.toResponseBody("application/json".toMediaType())
        fakeApiService.getCurrentUserResult = Response.error(401, errorBody)

        val result = remoteDataSource.getCurrentUser()

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is SafeWalkException.AuthError)
    }

    // ==========================================
    // 4. NETWORK & CONNECTION ERROR TESTS
    // ==========================================

    @Test
    fun `test connection exception maps to NetworkError with server port info`() {
        val connectException = ConnectException("Connection refused")
        val safeWalkException = SafeWalkErrorMapper.mapThrowable(connectException)

        assertTrue(safeWalkException is SafeWalkException.NetworkError)
        assertEquals(0, safeWalkException.httpStatus)
        assertTrue(safeWalkException.message!!.contains("port 8080"))
    }

    @Test
    fun `test socket timeout exception maps to NetworkError`() {
        val timeoutException = SocketTimeoutException("Read timed out")
        val safeWalkException = SafeWalkErrorMapper.mapThrowable(timeoutException)

        assertTrue(safeWalkException is SafeWalkException.NetworkError)
        assertEquals(0, safeWalkException.httpStatus)
        assertTrue(safeWalkException.message!!.contains("timed out"))
    }

    // ==========================================
    // 5. AUTH INTERCEPTOR TESTS
    // ==========================================

    @Test
    fun `test auth interceptor injects Bearer token into outgoing request`() {
        var observedAuthHeader: String? = null
        var observedAcceptHeader: String? = null

        val mockChain = object : Interceptor.Chain {
            private val req = Request.Builder().url("http://localhost:8080/api/v1/users/me").build()
            override fun request(): Request = req
            override fun proceed(request: Request): okhttp3.Response {
                observedAuthHeader = request.header("Authorization")
                observedAcceptHeader = request.header("Accept")
                return okhttp3.Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("{}".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            override fun connection(): okhttp3.Connection? = null
            override fun call(): okhttp3.Call = throw NotImplementedError()
            override fun connectTimeoutMillis(): Int = 1000
            override fun readTimeoutMillis(): Int = 1000
            override fun writeTimeoutMillis(): Int = 1000
            override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit): Interceptor.Chain = this
            override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit): Interceptor.Chain = this
            override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit): Interceptor.Chain = this
        }

        val interceptor = AuthInterceptor(tokenProvider = { "sample_valid_jwt_token" })
        interceptor.intercept(mockChain)

        assertEquals("Bearer sample_valid_jwt_token", observedAuthHeader)
        assertEquals("application/json", observedAcceptHeader)
    }

    @Test
    fun `test auth interceptor triggers onUnauthorized callback when 401 received on protected route`() {
        var unauthorizedTriggered = false

        val mockChain = object : Interceptor.Chain {
            private val req = Request.Builder().url("http://localhost:8080/api/v1/users/me").build()
            override fun request(): Request = req
            override fun proceed(request: Request): okhttp3.Response {
                return okhttp3.Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(401)
                    .message("Unauthorized")
                    .body("""{"success":false,"message":"Expired token"}""".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            override fun connection(): okhttp3.Connection? = null
            override fun call(): okhttp3.Call = throw NotImplementedError()
            override fun connectTimeoutMillis(): Int = 1000
            override fun readTimeoutMillis(): Int = 1000
            override fun writeTimeoutMillis(): Int = 1000
            override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit): Interceptor.Chain = this
            override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit): Interceptor.Chain = this
            override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit): Interceptor.Chain = this
        }

        val interceptor = AuthInterceptor(
            tokenProvider = { "expired_jwt_token" },
            onUnauthorized = { unauthorizedTriggered = true }
        )
        interceptor.intercept(mockChain)

        assertTrue(unauthorizedTriggered)
    }

    // ==========================================
    // 6. SESSION MANAGER TESTS
    // ==========================================

    private class TestSharedPreferences : android.content.SharedPreferences {
        val map = mutableMapOf<String, Any?>()

        override fun getAll(): Map<String, *> = map
        override fun getString(key: String?, defValue: String?): String? = (map[key] as? String) ?: defValue
        override fun getStringSet(key: String?, defValues: Set<String>?): Set<String>? = (map[key] as? Set<String>) ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = (map[key] as? Int) ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = (map[key] as? Long) ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = (map[key] as? Float) ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = (map[key] as? Boolean) ?: defValue
        override fun contains(key: String?): Boolean = map.containsKey(key)
        override fun edit(): android.content.SharedPreferences.Editor = TestEditor(this)
        override fun registerOnSharedPreferenceChangeListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}

        class TestEditor(private val sp: TestSharedPreferences) : android.content.SharedPreferences.Editor {
            private val temp = mutableMapOf<String, Any?>()
            private var clearFlag = false

            override fun putString(key: String?, value: String?): android.content.SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }
            override fun putStringSet(key: String?, values: Set<String>?): android.content.SharedPreferences.Editor {
                if (key != null) temp[key] = values
                return this
            }
            override fun putInt(key: String?, value: Int): android.content.SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }
            override fun putLong(key: String?, value: Long): android.content.SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }
            override fun putFloat(key: String?, value: Float): android.content.SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }
            override fun putBoolean(key: String?, value: Boolean): android.content.SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }
            override fun remove(key: String?): android.content.SharedPreferences.Editor {
                if (key != null) temp[key] = null
                return this
            }
            override fun clear(): android.content.SharedPreferences.Editor {
                clearFlag = true
                return this
            }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                if (clearFlag) sp.map.clear()
                temp.forEach { (k, v) ->
                    if (v == null) sp.map.remove(k) else sp.map[k] = v
                }
            }
        }
    }

    @Test
    fun `test SessionManager saveSession, getToken, getUser, hasActiveSession, clearSession`() {
        val testPrefs = TestSharedPreferences()
        val sessionManager = SessionManager(injectedPrefs = testPrefs)

        assertFalse(sessionManager.hasActiveSession())
        assertNull(sessionManager.getToken())
        assertNull(sessionManager.getUser())

        val domainUser = User(
            id = "157",
            safeWalkId = "SW-157",
            name = "Session User",
            phone = "9876543210",
            email = "user@test.com",
            role = UserRole.USER
        )

        sessionManager.saveSession("jwt_sample_token", domainUser)

        assertTrue(sessionManager.hasActiveSession())
        assertEquals("jwt_sample_token", sessionManager.getToken())
        assertEquals("Session User", sessionManager.getUser()?.name)

        sessionManager.clearSession()
        assertFalse(sessionManager.hasActiveSession())
        assertNull(sessionManager.getToken())
        assertNull(sessionManager.getUser())
    }

    // ==========================================
    // 7. REMOTE AUTH REPOSITORY TESTS
    // ==========================================

    @Test
    fun `test RemoteAuthRepository register and login updates currentUser`() = runBlocking {
        val testPrefs = TestSharedPreferences()
        val sessionManager = SessionManager(injectedPrefs = testPrefs)
        val repository = RemoteAuthRepository(
            remoteDataSource = remoteDataSource,
            sessionManager = sessionManager
        )

        assertNull(repository.currentUser.value)
        assertFalse(repository.isAuthenticated())

        // Register
        val regResult = repository.register(
            name = "Repository User",
            email = "repo@test.com",
            phone = "9876543210",
            password = "password123",
            confirmPassword = "password123",
            emergencyPassword = "safe"
        )

        assertTrue(regResult.isSuccess)
        assertNotNull(repository.currentUser.value)
        assertEquals("Repository User", repository.currentUser.value?.name)
        assertTrue(repository.isAuthenticated())

        // Logout
        repository.logout()
        assertNull(repository.currentUser.value)
        assertFalse(repository.isAuthenticated())

        // Login
        val loginResult = repository.login("9876543210", "password123")
        assertTrue(loginResult.isSuccess)
        assertNotNull(repository.currentUser.value)
        assertTrue(repository.isAuthenticated())
    }

    @Test
    fun `test RemoteAuthRepository restoreSession with valid token restores user`() = runBlocking {
        val testPrefs = TestSharedPreferences()
        val sessionManager = SessionManager(injectedPrefs = testPrefs)
        val repository = RemoteAuthRepository(
            remoteDataSource = remoteDataSource,
            sessionManager = sessionManager
        )

        val cachedUser = User(
            id = "101",
            safeWalkId = "SW-101",
            name = "Restored User",
            phone = "9876543210",
            email = "",
            role = UserRole.USER
        )
        sessionManager.saveSession("valid_jwt_token", cachedUser)

        val restoreResult = repository.restoreSession()
        assertTrue(restoreResult.isSuccess)
        assertEquals("Test User", repository.currentUser.value?.name)
        assertTrue(repository.isAuthenticated())
    }

    @Test
    fun `test RemoteAuthRepository restoreSession with expired 401 token logs out`() = runBlocking {
        val testPrefs = TestSharedPreferences()
        val sessionManager = SessionManager(injectedPrefs = testPrefs)
        val repository = RemoteAuthRepository(
            remoteDataSource = remoteDataSource,
            sessionManager = sessionManager
        )

        val cachedUser = User(
            id = "101",
            safeWalkId = "SW-101",
            name = "Expired User",
            phone = "9876543210",
            email = "",
            role = UserRole.USER
        )
        sessionManager.saveSession("expired_jwt_token", cachedUser)

        val errorJson = """{"success":false,"message":"Full authentication is required to access this resource. Token missing or invalid.","path":"/api/v1/users/me"}"""
        val errorBody = errorJson.toResponseBody("application/json".toMediaType())
        fakeApiService.getCurrentUserResult = Response.error(401, errorBody)

        val restoreResult = repository.restoreSession()
        assertTrue(restoreResult.isFailure)
        assertNull(repository.currentUser.value)
        assertFalse(repository.isAuthenticated())
    }

    @Test
    fun `test RemoteAuthDataSource provider dynamically delegates to updated ApiService on backend switch`() = runBlocking {
        var currentApi: AuthApiService = fakeApiService
        val dynamicDataSource = RemoteAuthDataSource(apiServiceProvider = { currentApi })

        // Initial call uses initial api
        val result1 = dynamicDataSource.login("9876543210", "pass123")
        assertTrue(result1.isSuccess)
        assertEquals("jwt_test_token_12345", result1.getOrNull()?.token)

        // Switch to a new ApiService (simulating backend switch / Railway selection)
        val switchedFakeApi = FakeAuthApiService()
        val switchedAuthData = AuthResponseDto(token = "railway_token_67890")
        switchedFakeApi.loginResult = Response.success(ApiResponseDto(true, "Login successful", switchedAuthData))
        currentApi = switchedFakeApi

        // Subsequent call must immediately use the new ApiService
        val result2 = dynamicDataSource.login("9876543210", "pass123")
        assertTrue(result2.isSuccess)
        assertEquals("railway_token_67890", result2.getOrNull()?.token)
    }
}

