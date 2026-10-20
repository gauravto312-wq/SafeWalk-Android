package com.example.safewalk

import com.example.safewalk.core.error.SafeWalkErrorMapper
import com.example.safewalk.core.error.SafeWalkException
import com.example.safewalk.data.model.dto.ApiResponseDto
import com.example.safewalk.data.model.dto.CancelEmergencyRequestDto
import com.example.safewalk.data.model.dto.CreateEmergencyRequestDto
import com.example.safewalk.data.model.dto.EmergencyLocationResponseDto
import com.example.safewalk.data.model.dto.EmergencyResponseDto
import com.example.safewalk.data.model.dto.ParticipantLocationDto
import com.example.safewalk.data.model.dto.UserResponseDto
import com.example.safewalk.data.remote.EmergencyApiService
import com.example.safewalk.data.repository.RemoteEmergencyRepository
import com.example.safewalk.data.source.remote.RemoteEmergencyDataSource
import com.example.safewalk.domain.model.Emergency
import com.example.safewalk.domain.model.EmergencyStatus
import com.example.safewalk.domain.model.Location
import com.example.safewalk.domain.model.User
import com.example.safewalk.domain.model.UserRole
import com.example.safewalk.domain.model.VerificationStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class Step3EmergencyIntegrationUnitTest {

    private class FakeEmergencyApiService : EmergencyApiService {
        var createResult: Response<ApiResponseDto<EmergencyResponseDto>>? = null
        var getActiveResult: Response<ApiResponseDto<List<EmergencyResponseDto>>>? = null
        var getByIdResult: Response<ApiResponseDto<EmergencyResponseDto>>? = null
        var getMyActiveResult: Response<ApiResponseDto<EmergencyResponseDto>>? = null
        var acceptResult: Response<ApiResponseDto<EmergencyResponseDto>>? = null
        var arriveResult: Response<ApiResponseDto<EmergencyResponseDto>>? = null
        var rejectResult: Response<ApiResponseDto<EmergencyResponseDto>>? = null
        var cancelResult: Response<ApiResponseDto<EmergencyResponseDto>>? = null
        var resolveResult: Response<ApiResponseDto<EmergencyResponseDto>>? = null
        var getLocationsResult: Response<ApiResponseDto<EmergencyLocationResponseDto>>? = null

        var lastCreateRequest: CreateEmergencyRequestDto? = null
        var lastCancelRequest: CancelEmergencyRequestDto? = null
        var lastAcceptedId: Long? = null
        var lastCancelledId: Long? = null
        var lastResolvedId: Long? = null

        override suspend fun createEmergency(request: CreateEmergencyRequestDto): Response<ApiResponseDto<EmergencyResponseDto>> {
            lastCreateRequest = request
            return createResult ?: Response.success(
                ApiResponseDto(
                    success = true,
                    message = "Emergency SOS created successfully",
                    data = EmergencyResponseDto(
                        id = 101L,
                        requesterId = 1L,
                        requesterName = "Citizen Priya",
                        requesterPhone = "+919999911111",
                        type = request.type,
                        status = "ACTIVE",
                        latitude = request.latitude,
                        longitude = request.longitude,
                        address = request.address,
                        description = request.description,
                        responders = emptyList(),
                        createdAt = "2026-10-02T10:00:00"
                    )
                )
            )
        }

        override suspend fun getActiveEmergencies(status: String?): Response<ApiResponseDto<List<EmergencyResponseDto>>> {
            return getActiveResult ?: Response.success(
                ApiResponseDto(
                    success = true,
                    message = "Active emergencies retrieved",
                    data = emptyList()
                )
            )
        }

        override suspend fun getEmergencyById(id: Long): Response<ApiResponseDto<EmergencyResponseDto>> {
            return getByIdResult ?: Response.success(
                ApiResponseDto(
                    success = true,
                    message = "Emergency retrieved",
                    data = EmergencyResponseDto(
                        id = id,
                        requesterId = 1L,
                        requesterName = "Citizen Priya",
                        requesterPhone = "+919999911111",
                        type = "GENERAL",
                        status = "ACTIVE",
                        latitude = 28.6139,
                        longitude = 77.2090,
                        responders = emptyList()
                    )
                )
            )
        }

        override suspend fun getMyActiveEmergency(): Response<ApiResponseDto<EmergencyResponseDto>> {
            return getMyActiveResult ?: Response.success(
                ApiResponseDto(
                    success = true,
                    message = "My active emergency retrieved",
                    data = EmergencyResponseDto(
                        id = 101L,
                        requesterId = 1L,
                        requesterName = "Citizen Priya",
                        requesterPhone = "+919999911111",
                        type = "GENERAL",
                        status = "ACTIVE",
                        latitude = 28.6139,
                        longitude = 77.2090
                    )
                )
            )
        }

        override suspend fun acceptEmergency(id: Long): Response<ApiResponseDto<EmergencyResponseDto>> {
            lastAcceptedId = id
            return acceptResult ?: Response.success(
                ApiResponseDto(
                    success = true,
                    message = "Emergency accepted",
                    data = EmergencyResponseDto(
                        id = id,
                        requesterId = 1L,
                        requesterName = "Citizen Priya",
                        requesterPhone = "+919999911111",
                        responderId = 2L,
                        responderName = "Responder Rahul",
                        responderPhone = "+919876543210",
                        type = "GENERAL",
                        status = "ACCEPTED",
                        latitude = 28.6139,
                        longitude = 77.2090,
                        responders = listOf(
                            UserResponseDto(
                                id = 2L,
                                name = "Responder Rahul",
                                mobileNumber = "+919876543210",
                                role = "USER",
                                accountStatus = "ACTIVE"
                            )
                        )
                    )
                )
            )
        }

        override suspend fun arriveEmergency(id: Long): Response<ApiResponseDto<EmergencyResponseDto>> {
            return arriveResult ?: Response.success(
                ApiResponseDto(
                    success = true,
                    message = "Arrival verified",
                    data = EmergencyResponseDto(
                        id = id,
                        requesterId = 1L,
                        requesterName = "Citizen Priya",
                        requesterPhone = "+919999911111",
                        type = "GENERAL",
                        status = "ACCEPTED",
                        latitude = 28.6139,
                        longitude = 77.2090,
                        arrivedResponderId = 2L,
                        arrivedResponderName = "Responder Rahul"
                    )
                )
            )
        }

        override suspend fun rejectEmergency(id: Long): Response<ApiResponseDto<EmergencyResponseDto>> {
            return rejectResult ?: Response.success(
                ApiResponseDto(
                    success = true,
                    message = "Emergency rejected",
                    data = EmergencyResponseDto(
                        id = id,
                        requesterId = 1L,
                        requesterName = "Citizen Priya",
                        type = "GENERAL",
                        status = "ACTIVE",
                        latitude = 28.6139,
                        longitude = 77.2090
                    )
                )
            )
        }

        override suspend fun cancelEmergency(id: Long, request: CancelEmergencyRequestDto): Response<ApiResponseDto<EmergencyResponseDto>> {
            lastCancelledId = id
            lastCancelRequest = request
            return cancelResult ?: Response.success(
                ApiResponseDto(
                    success = true,
                    message = "Emergency cancelled successfully",
                    data = EmergencyResponseDto(
                        id = id,
                        requesterId = 1L,
                        requesterName = "Citizen Priya",
                        requesterPhone = "+919999911111",
                        type = "GENERAL",
                        status = "CANCELLED",
                        latitude = 28.6139,
                        longitude = 77.2090
                    )
                )
            )
        }

        override suspend fun resolveEmergency(id: Long): Response<ApiResponseDto<EmergencyResponseDto>> {
            lastResolvedId = id
            return resolveResult ?: Response.success(
                ApiResponseDto(
                    success = true,
                    message = "Emergency resolved successfully",
                    data = EmergencyResponseDto(
                        id = id,
                        requesterId = 1L,
                        requesterName = "Citizen Priya",
                        requesterPhone = "+919999911111",
                        type = "GENERAL",
                        status = "RESOLVED",
                        latitude = 28.6139,
                        longitude = 77.2090,
                        requesterResolveConfirmed = true,
                        responderResolveConfirmed = true
                    )
                )
            )
        }

        override suspend fun getEmergencyLocations(id: Long): Response<ApiResponseDto<EmergencyLocationResponseDto>> {
            return getLocationsResult ?: Response.success(
                ApiResponseDto(
                    success = true,
                    message = "Locations retrieved",
                    data = EmergencyLocationResponseDto(
                        emergencyId = id,
                        status = "ACTIVE",
                        requester = ParticipantLocationDto(
                            userId = 1L,
                            name = "Citizen Priya",
                            mobileNumber = "+919999911111",
                            latitude = 28.6139,
                            longitude = 77.2090
                        ),
                        responders = emptyList()
                    )
                )
            )
        }

        override suspend fun getMyEmergencyHistory(
            page: Int,
            size: Int
        ): Response<ApiResponseDto<com.example.safewalk.data.model.dto.PageResponseDto<EmergencyResponseDto>>> {
            return Response.success(
                ApiResponseDto(
                    success = true,
                    message = "History retrieved",
                    data = com.example.safewalk.data.model.dto.PageResponseDto()
                )
            )
        }
    }

    private lateinit var fakeApiService: FakeEmergencyApiService
    private lateinit var remoteDataSource: RemoteEmergencyDataSource
    private lateinit var repository: RemoteEmergencyRepository

    private val citizenRequester = User(
        id = "1",
        safeWalkId = "SW-USER-1",
        name = "Citizen Priya",
        email = "priya@safewalk.org",
        phone = "+919999911111",
        role = UserRole.USER,
        isVerified = true,
        verificationStatus = VerificationStatus.NONE
    )

    private val citizenResponder = User(
        id = "2",
        safeWalkId = "SW-USER-2",
        name = "Responder Rahul",
        email = "rahul@safewalk.org",
        phone = "+919876543210",
        role = UserRole.USER,
        isVerified = true,
        verificationStatus = VerificationStatus.NONE
    )

    private val verifiedPolice = User(
        id = "170",
        safeWalkId = "SW-POLICE-170",
        name = "Inspector Vijay",
        email = "vijay@police.gov.in",
        phone = "+919876550001",
        role = UserRole.POLICE,
        isVerified = true,
        verificationStatus = VerificationStatus.VERIFIED
    )

    private val unverifiedPolice = User(
        id = "175",
        safeWalkId = "SW-POLICE-175",
        name = "Sub Inspector sir3",
        email = "sir3@police.gov.in",
        phone = "+918948757133",
        role = UserRole.POLICE,
        isVerified = false,
        verificationStatus = VerificationStatus.PENDING
    )

    private val defaultLocation = Location(
        latitude = 28.6139,
        longitude = 77.2090,
        address = "Connaught Place, New Delhi"
    )

    @Before
    fun setUp() {
        fakeApiService = FakeEmergencyApiService()
        remoteDataSource = RemoteEmergencyDataSource(fakeApiService)
        repository = RemoteEmergencyRepository(
            remoteDataSource = remoteDataSource,
            mockFallbackDataSource = null
        )
    }

    // =========================================================================
    // 1. SOS CREATION TESTS
    // =========================================================================

    @Test
    fun testCitizenCreatesSos_Success_ReturnsRealServerEmergencyIdAndActiveStatus() = runBlocking {
        val emergency = repository.createEmergency(citizenRequester, defaultLocation)

        assertEquals("EMG-101", emergency.id)
        assertEquals(EmergencyStatus.ACTIVE, emergency.status)
        assertEquals("1", emergency.requester.id)
        assertEquals("Citizen Priya", emergency.requester.name)
        assertFalse(emergency.isTerminal)
        assertEquals(28.6139, emergency.requesterLocation.latitude, 0.0001)
        assertEquals(77.2090, emergency.requesterLocation.longitude, 0.0001)

        // Verify request payload sent to API service
        assertNotNull(fakeApiService.lastCreateRequest)
        assertEquals(28.6139, fakeApiService.lastCreateRequest!!.latitude, 0.0001)
        assertEquals(77.2090, fakeApiService.lastCreateRequest!!.longitude, 0.0001)
        assertEquals("Connaught Place, New Delhi", fakeApiService.lastCreateRequest!!.address)
    }

    // =========================================================================
    // 2. ACTIVE EMERGENCY DISCOVERY & ELIGIBILITY TESTS
    // =========================================================================

    @Test
    fun testActiveEmergencyDiscovery_EligibleCitizen_CanDiscoverActiveEmergencies() = runBlocking {
        val dtoList = listOf(
            EmergencyResponseDto(
                id = 101L,
                requesterId = 1L,
                requesterName = "Priya",
                requesterPhone = "+919999911111",
                type = "GENERAL",
                status = "ACTIVE",
                latitude = 28.6139,
                longitude = 77.2090
            ),
            EmergencyResponseDto(
                id = 102L,
                requesterId = 3L,
                requesterName = "Amit",
                requesterPhone = "+919999922222",
                type = "MEDICAL",
                status = "ACCEPTED",
                latitude = 28.6145,
                longitude = 77.2095
            )
        )
        fakeApiService.getActiveResult = Response.success(
            ApiResponseDto(success = true, message = "OK", data = dtoList)
        )

        val result = repository.refreshActiveEmergencies()
        assertTrue(result.isSuccess)
        val emergencies = result.getOrThrow()
        assertEquals(2, emergencies.size)
        assertEquals("EMG-101", emergencies[0].id)
        assertEquals("EMG-102", emergencies[1].id)

        // Check citizen eligibility
        val eligibleList = repository.getEligibleEmergenciesForUser(citizenResponder, defaultLocation)
        assertEquals(2, eligibleList.size)
    }

    @Test
    fun testActiveEmergencyDiscovery_VerifiedAuthority_CanDiscoverActiveEmergencies() = runBlocking {
        val dtoList = listOf(
            EmergencyResponseDto(
                id = 101L,
                requesterId = 1L,
                requesterName = "Priya",
                requesterPhone = "+919999911111",
                type = "GENERAL",
                status = "ACTIVE",
                latitude = 28.6139,
                longitude = 77.2090
            )
        )
        fakeApiService.getActiveResult = Response.success(
            ApiResponseDto(success = true, message = "OK", data = dtoList)
        )

        repository.refreshActiveEmergencies()
        val eligibleList = repository.getEligibleEmergenciesForUser(verifiedPolice, defaultLocation)
        assertEquals(1, eligibleList.size)
        assertTrue(repository.isUserEligibleForEmergency(verifiedPolice, eligibleList[0], defaultLocation))
    }

    @Test
    fun testActiveEmergencyDiscovery_UnverifiedAuthority_DeniedAndFilteredOut() = runBlocking {
        val dtoList = listOf(
            EmergencyResponseDto(
                id = 101L,
                requesterId = 1L,
                requesterName = "Priya",
                requesterPhone = "+919999911111",
                type = "GENERAL",
                status = "ACTIVE",
                latitude = 28.6139,
                longitude = 77.2090
            )
        )
        fakeApiService.getActiveResult = Response.success(
            ApiResponseDto(success = true, message = "OK", data = dtoList)
        )

        repository.refreshActiveEmergencies()
        // Unverified police must receive empty eligible list
        val eligibleList = repository.getEligibleEmergenciesForUser(unverifiedPolice, defaultLocation)
        assertTrue(eligibleList.isEmpty())
        assertFalse(repository.isUserEligibleForEmergency(unverifiedPolice, repository.activeEmergencies.value[0], defaultLocation))
    }

    // =========================================================================
    // 3. EMERGENCY ACCEPTANCE & MULTI-RESPONDER TESTS
    // =========================================================================

    @Test
    fun testResponderAcceptsEmergency_Success_TransitionsToAccepted() = runBlocking {
        val result = repository.acceptEmergency("101", citizenResponder)
        assertTrue(result.isSuccess)
        val emg = result.getOrThrow()

        assertEquals("EMG-101", emg.id)
        assertEquals(EmergencyStatus.ACCEPTED, emg.status)
        assertEquals(1, emg.acceptedResponders.size)
        assertEquals("2", emg.acceptedResponders[0].id)
        assertEquals("Responder Rahul", emg.acceptedResponders[0].name)
        assertEquals(101L, fakeApiService.lastAcceptedId)
    }

    @Test
    fun testMultiResponder_SecondResponderAccepts_BothRespondersPresent() = runBlocking {
        // First responder already accepted
        val multiDto = EmergencyResponseDto(
            id = 101L,
            requesterId = 1L,
            requesterName = "Priya",
            requesterPhone = "+919999911111",
            type = "GENERAL",
            status = "ACCEPTED",
            latitude = 28.6139,
            longitude = 77.2090,
            responders = listOf(
                UserResponseDto(id = 2L, name = "Responder Rahul", mobileNumber = "+919876543210", role = "USER", accountStatus = "ACTIVE"),
                UserResponseDto(id = 170L, name = "Inspector Vijay", mobileNumber = "+919876550001", role = "POLICE", accountStatus = "ACTIVE", verificationStatus = "VERIFIED")
            )
        )
        fakeApiService.acceptResult = Response.success(
            ApiResponseDto(success = true, message = "Emergency accepted", data = multiDto)
        )

        val result = repository.acceptEmergency("101", verifiedPolice)
        assertTrue(result.isSuccess)
        val emg = result.getOrThrow()

        assertEquals(2, emg.acceptedResponders.size)
        assertTrue(emg.acceptedResponders.any { it.id == "2" })
        assertTrue(emg.acceptedResponders.any { it.id == "170" })
    }

    @Test
    fun testAcceptance_DuplicateAccept_ReturnsConflictError() = runBlocking {
        val errorJson = "{\"success\":false,\"message\":\"You have already accepted this emergency request.\",\"errorCode\":\"DUPLICATE_RESOURCE\"}"
        fakeApiService.acceptResult = Response.error(
            409,
            errorJson.toResponseBody("application/json".toMediaType())
        )

        val result = repository.acceptEmergency("101", citizenResponder)
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is SafeWalkException.ConflictError)
        assertEquals("You have already accepted this emergency request.", ex?.message)
    }

    @Test
    fun testAcceptance_UnverifiedAuthority_ReturnsForbiddenError() = runBlocking {
        val errorJson = "{\"success\":false,\"message\":\"Account verification pending. Unverified authorities cannot accept emergency requests.\",\"errorCode\":\"ACCOUNT_UNVERIFIED\"}"
        fakeApiService.acceptResult = Response.error(
            403,
            errorJson.toResponseBody("application/json".toMediaType())
        )

        val result = repository.acceptEmergency("101", unverifiedPolice)
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is SafeWalkException.ForbiddenError)
        assertEquals("Account verification pending. Unverified authorities cannot accept emergency requests.", ex?.message)
    }

    // =========================================================================
    // 4. REQUESTER CANCELLATION TESTS (5-SECOND STRICT WINDOW)
    // =========================================================================

    @Test
    fun testRequesterCancellation_Within5Seconds_Success() = runBlocking {
        fakeApiService.cancelResult = Response.success(
            ApiResponseDto(
                success = true,
                message = "Emergency cancelled successfully",
                data = EmergencyResponseDto(
                    id = 101L,
                    requesterId = 1L,
                    requesterName = "Citizen Priya",
                    requesterPhone = "+919999911111",
                    type = "GENERAL",
                    status = "CANCELLED"
                )
            )
        )

        val result = repository.cancelEmergency("101", "Password123!", "False alarm")
        assertTrue(result.isSuccess)
        val emg = result.getOrThrow()

        assertEquals(EmergencyStatus.CANCELLED, emg.status)
        assertTrue(emg.isTerminal)
        assertEquals(101L, fakeApiService.lastCancelledId)
        assertEquals("Password123!", fakeApiService.lastCancelRequest?.emergencyPassword)
        assertEquals("False alarm", fakeApiService.lastCancelRequest?.reason)
    }

    @Test
    fun testRequesterCancellation_After5SecondsExpired_ReturnsValidationError() = runBlocking {
        val errorJson = "{\"success\":false,\"message\":\"Cancellation window has expired. SOS can only be cancelled within 5 seconds of creation.\",\"errorCode\":\"CANCELLATION_EXPIRED\"}"
        fakeApiService.cancelResult = Response.error(
            400,
            errorJson.toResponseBody("application/json".toMediaType())
        )

        val result = repository.cancelEmergency("101", "Password123!", "Expired attempt")
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is SafeWalkException.ValidationError)
        assertEquals("Cancellation window has expired. SOS can only be cancelled within 5 seconds of creation.", ex?.message)
    }

    @Test
    fun testRequesterCancellation_InvalidPassword_ReturnsAuthError() = runBlocking {
        val errorJson = "{\"success\":false,\"message\":\"Invalid emergency password. Cancellation rejected.\",\"errorCode\":\"INVALID_CREDENTIALS\"}"
        fakeApiService.cancelResult = Response.error(
            401,
            errorJson.toResponseBody("application/json".toMediaType())
        )

        val result = repository.cancelEmergency("101", "WrongPassword", "Attempt")
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is SafeWalkException.AuthError)
        assertEquals("Invalid emergency password. Cancellation rejected.", ex?.message)
    }

    // =========================================================================
    // 5. TWO-PARTY RESOLUTION TESTS
    // =========================================================================

    @Test
    fun testResolution_RequesterAloneWithoutResponders_ResolvesImmediately() = runBlocking {
        fakeApiService.resolveResult = Response.success(
            ApiResponseDto(
                success = true,
                message = "Emergency successfully resolved",
                data = EmergencyResponseDto(
                    id = 101L,
                    requesterId = 1L,
                    requesterName = "Citizen Priya",
                    requesterPhone = "+919999911111",
                    type = "GENERAL",
                    status = "RESOLVED",
                    requesterResolveConfirmed = true,
                    responderResolveConfirmed = false,
                    responders = emptyList()
                )
            )
        )

        val result = repository.resolveEmergency("101", citizenRequester)
        assertTrue(result.isSuccess)
        val emg = result.getOrThrow()

        assertEquals(EmergencyStatus.RESOLVED, emg.status)
        assertTrue(emg.isTerminal)
        assertEquals(101L, fakeApiService.lastResolvedId)
    }

    @Test
    fun testResolution_WithResponders_RequesterConfirms_AwaitingResponder() = runBlocking {
        fakeApiService.resolveResult = Response.success(
            ApiResponseDto(
                success = true,
                message = "Resolution confirmed. Awaiting counterpart confirmation.",
                data = EmergencyResponseDto(
                    id = 101L,
                    requesterId = 1L,
                    requesterName = "Citizen Priya",
                    requesterPhone = "+919999911111",
                    type = "GENERAL",
                    status = "ACCEPTED",
                    requesterResolveConfirmed = true,
                    responderResolveConfirmed = false,
                    responders = listOf(
                        UserResponseDto(id = 2L, name = "Responder Rahul", mobileNumber = "+919876543210", role = "USER", accountStatus = "ACTIVE")
                    )
                )
            )
        )

        val result = repository.confirmResolution("101", citizenRequester)
        assertTrue(result.isSuccess)
        val emg = result.getOrThrow()

        assertEquals(EmergencyStatus.ACCEPTED, emg.status)
        assertTrue(emg.requesterConfirmedResolution)
        assertFalse(emg.responderConfirmedResolution)
        assertFalse(emg.isFullyResolved)
    }

    @Test
    fun testResolution_WithResponders_BothConfirm_TransitionsToResolved() = runBlocking {
        fakeApiService.resolveResult = Response.success(
            ApiResponseDto(
                success = true,
                message = "Emergency successfully resolved by both parties",
                data = EmergencyResponseDto(
                    id = 101L,
                    requesterId = 1L,
                    requesterName = "Citizen Priya",
                    requesterPhone = "+919999911111",
                    type = "GENERAL",
                    status = "RESOLVED",
                    requesterResolveConfirmed = true,
                    responderResolveConfirmed = true,
                    responders = listOf(
                        UserResponseDto(id = 2L, name = "Responder Rahul", mobileNumber = "+919876543210", role = "USER", accountStatus = "ACTIVE")
                    )
                )
            )
        )

        val result = repository.confirmResolution("101", citizenResponder)
        assertTrue(result.isSuccess)
        val emg = result.getOrThrow()

        assertEquals(EmergencyStatus.RESOLVED, emg.status)
        assertTrue(emg.isFullyResolved)
        assertTrue(emg.isTerminal)
    }

    // =========================================================================
    // 6. REMOTE EMERGENCY DATA SOURCE HTTP ERROR MAPPINGS
    // =========================================================================

    @Test
    fun testRemoteEmergencyDataSource_Http400_MapsToValidationError() = runBlocking {
        val errorJson = "{\"success\":false,\"message\":\"Invalid request params\"}"
        fakeApiService.getByIdResult = Response.error(400, errorJson.toResponseBody("application/json".toMediaType()))

        val result = remoteDataSource.getEmergencyById(999L)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SafeWalkException.ValidationError)
    }

    @Test
    fun testRemoteEmergencyDataSource_Http401_MapsToAuthError() = runBlocking {
        val errorJson = "{\"success\":false,\"message\":\"JWT expired\"}"
        fakeApiService.getByIdResult = Response.error(401, errorJson.toResponseBody("application/json".toMediaType()))

        val result = remoteDataSource.getEmergencyById(999L)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SafeWalkException.AuthError)
    }

    @Test
    fun testRemoteEmergencyDataSource_Http403_MapsToForbiddenError() = runBlocking {
        val errorJson = "{\"success\":false,\"message\":\"Access denied\"}"
        fakeApiService.getByIdResult = Response.error(403, errorJson.toResponseBody("application/json".toMediaType()))

        val result = remoteDataSource.getEmergencyById(999L)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SafeWalkException.ForbiddenError)
    }

    @Test
    fun testRemoteEmergencyDataSource_Http404_MapsToNotFoundError() = runBlocking {
        val errorJson = "{\"success\":false,\"message\":\"Emergency not found\"}"
        fakeApiService.getByIdResult = Response.error(404, errorJson.toResponseBody("application/json".toMediaType()))

        val result = remoteDataSource.getEmergencyById(999L)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SafeWalkException.NotFoundError)
    }

    @Test
    fun testRemoteEmergencyDataSource_Http409_MapsToConflictError() = runBlocking {
        val errorJson = "{\"success\":false,\"message\":\"Already accepted\"}"
        fakeApiService.acceptResult = Response.error(409, errorJson.toResponseBody("application/json".toMediaType()))

        val result = remoteDataSource.acceptEmergency(999L)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SafeWalkException.ConflictError)
    }

    @Test
    fun testRemoteEmergencyDataSource_Http500_MapsToServerError() = runBlocking {
        val errorJson = "{\"success\":false,\"message\":\"Internal server exception\"}"
        fakeApiService.getByIdResult = Response.error(500, errorJson.toResponseBody("application/json".toMediaType()))

        val result = remoteDataSource.getEmergencyById(999L)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SafeWalkException.ServerError)
    }
}
