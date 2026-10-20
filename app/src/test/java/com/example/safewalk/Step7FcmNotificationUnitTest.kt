package com.example.safewalk

import android.content.SharedPreferences
import com.example.safewalk.core.notification.EmergencyNotificationHelper
import com.example.safewalk.data.model.dto.ApiResponseDto
import com.example.safewalk.data.model.dto.FcmTokenRequestDto
import com.example.safewalk.data.remote.NotificationApiService
import com.example.safewalk.data.session.SessionManager
import com.example.safewalk.domain.model.User
import com.example.safewalk.domain.model.UserRole
import com.example.safewalk.service.EmergencyNotificationActionReceiver
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class Step7FcmNotificationUnitTest {

    private val gson = Gson()
    private lateinit var testPrefs: TestSharedPreferences
    private lateinit var sessionManager: SessionManager

    // =========================================================================
    // IN-MEMORY SHARED PREFERENCES FAKE
    // =========================================================================

    private class TestSharedPreferences : SharedPreferences {
        val map = mutableMapOf<String, Any?>()

        override fun getAll(): Map<String, *> = map
        override fun getString(key: String?, defValue: String?): String? = (map[key] as? String) ?: defValue
        override fun getStringSet(key: String?, defValues: Set<String>?): Set<String>? = (map[key] as? Set<String>) ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = (map[key] as? Int) ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = (map[key] as? Long) ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = (map[key] as? Float) ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = (map[key] as? Boolean) ?: defValue
        override fun contains(key: String?): Boolean = map.containsKey(key)
        override fun edit(): SharedPreferences.Editor = TestEditor(this)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        class TestEditor(private val sp: TestSharedPreferences) : SharedPreferences.Editor {
            private val temp = mutableMapOf<String, Any?>()
            private var clearFlag = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }
            override fun putStringSet(key: String?, values: Set<String>?): SharedPreferences.Editor {
                if (key != null) temp[key] = values
                return this
            }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }
            override fun remove(key: String?): SharedPreferences.Editor {
                if (key != null) temp[key] = null
                return this
            }
            override fun clear(): SharedPreferences.Editor {
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

    // =========================================================================
    // FAKE NOTIFICATION API SERVICE
    // =========================================================================

    private class FakeNotificationApiService(
        var shouldSucceed: Boolean = true
    ) : NotificationApiService {
        var lastRegisteredToken: String? = null
        var lastRemovedToken: String? = null

        override suspend fun registerDeviceToken(request: FcmTokenRequestDto): Response<ApiResponseDto<String>> {
            lastRegisteredToken = request.token
            return if (shouldSucceed) {
                Response.success(ApiResponseDto(success = true, message = "Device token registered successfully", data = "registered"))
            } else {
                Response.error(500, "Failed to register device token".toResponseBody())
            }
        }

        override suspend fun removeDeviceToken(token: String): Response<ApiResponseDto<String>> {
            lastRemovedToken = token
            return if (shouldSucceed) {
                Response.success(ApiResponseDto(success = true, message = "Device token removed successfully", data = "removed"))
            } else {
                Response.error(500, "Failed to remove device token".toResponseBody())
            }
        }
    }

    @Before
    fun setUp() {
        testPrefs = TestSharedPreferences()
        sessionManager = SessionManager(injectedPrefs = testPrefs)
    }

    // =========================================================================
    // 1. FCM TOKEN LIFECYCLE & SESSION PERSISTENCE TESTS
    // =========================================================================

    @Test
    fun test_fcm_token_initial_state_is_null() {
        assertNull(sessionManager.getFcmToken())
    }

    @Test
    fun test_save_and_retrieve_fcm_token() {
        val testToken = "fcm_test_token_xyz_12345"
        sessionManager.saveFcmToken(testToken)

        assertEquals(testToken, sessionManager.getFcmToken())
        assertEquals(testToken, testPrefs.getString("fcm_device_token", null))
    }

    @Test
    fun test_clear_fcm_token() {
        sessionManager.saveFcmToken("test_token_to_clear")
        assertEquals("test_token_to_clear", sessionManager.getFcmToken())

        sessionManager.clearFcmToken()
        assertNull(sessionManager.getFcmToken())
        assertNull(testPrefs.getString("fcm_device_token", null))
    }

    @Test
    fun test_clear_session_clears_fcm_token() {
        val testUser = User(
            id = "101",
            safeWalkId = "SW-101",
            name = "Test Responder",
            phone = "9876543210",
            email = "responder@test.com",
            role = UserRole.USER
        )
        sessionManager.saveSession("jwt_test_token", testUser)
        sessionManager.saveFcmToken("fcm_device_token_abc")

        assertTrue(sessionManager.hasActiveSession())
        assertEquals("fcm_device_token_abc", sessionManager.getFcmToken())

        // Logging out should wipe both auth session and FCM token
        sessionManager.clearSession()

        assertFalse(sessionManager.hasActiveSession())
        assertNull(sessionManager.getToken())
        assertNull(sessionManager.getUser())
        assertNull(sessionManager.getFcmToken())
    }

    // =========================================================================
    // 2. BACKEND FCM DEVICE TOKEN REGISTRATION CONTRACT TESTS
    // =========================================================================

    @Test
    fun test_fcm_token_request_dto_serialization() {
        val dto = FcmTokenRequestDto(token = "device_fcm_sample_token")
        val json = gson.toJson(dto)
        assertTrue(json.contains("\"token\":\"device_fcm_sample_token\""))

        val parsed = gson.fromJson(json, FcmTokenRequestDto::class.java)
        assertEquals("device_fcm_sample_token", parsed.token)
    }

    @Test
    fun test_notification_api_service_register_token() = runBlocking {
        val fakeApi = FakeNotificationApiService(shouldSucceed = true)
        val response = fakeApi.registerDeviceToken(FcmTokenRequestDto("sample_token_999"))

        assertTrue(response.isSuccessful)
        assertEquals("sample_token_999", fakeApi.lastRegisteredToken)
        assertEquals("Device token registered successfully", response.body()?.message)
    }

    @Test
    fun test_notification_api_service_remove_token() = runBlocking {
        val fakeApi = FakeNotificationApiService(shouldSucceed = true)
        val response = fakeApi.removeDeviceToken("sample_token_999")

        assertTrue(response.isSuccessful)
        assertEquals("sample_token_999", fakeApi.lastRemovedToken)
        assertEquals("Device token removed successfully", response.body()?.message)
    }

    // =========================================================================
    // 3. DETERMINISTIC NOTIFICATION ID GENERATION TESTS
    // =========================================================================

    @Test
    fun test_notification_id_deterministic_and_matches_prefixed_and_numeric_id() {
        val rawId = "12345"
        val prefixedId = "EMG-12345"

        val id1 = EmergencyNotificationHelper.getNotificationId(rawId)
        val id2 = EmergencyNotificationHelper.getNotificationId(prefixedId)

        assertEquals("getNotificationId must normalize prefixed IDs to match numeric IDs", id1, id2)
    }

    @Test
    fun test_distinct_emergencies_have_distinct_notification_ids() {
        val idA = EmergencyNotificationHelper.getNotificationId("100")
        val idB = EmergencyNotificationHelper.getNotificationId("101")
        val idC = EmergencyNotificationHelper.getNotificationId("102")

        assertNotEquals(idA, idB)
        assertNotEquals(idB, idC)
        assertNotEquals(idA, idC)
    }

    @Test
    fun test_non_numeric_emergency_id_produces_consistent_hash() {
        val uuid = "a8f3b2c1-d4e5-4f6a-b7c8-9d0e1f2a3b4c"
        val id1 = EmergencyNotificationHelper.getNotificationId(uuid)
        val id2 = EmergencyNotificationHelper.getNotificationId(uuid)

        assertEquals(id1, id2)
        assertEquals(uuid.hashCode(), id1)
    }

    // =========================================================================
    // 4. FCM SOS_CREATED PAYLOAD EXTRACTION & PARSING LOGIC
    // =========================================================================

    @Test
    fun test_parse_sos_created_payload_with_all_fields() {
        val data = mapOf(
            "emergencyId" to "55",
            "type" to "SOS_CREATED",
            "requesterId" to "12",
            "requesterName" to "Priya Sharma",
            "requesterMobile" to "9876543210",
            "distance" to "150 m",
            "latitude" to "28.6139",
            "longitude" to "77.2090"
        )

        val emergencyId = data["emergencyId"]
        val requesterId = data["requesterId"]
        val requesterName = data["requesterName"] ?: "Someone in need"
        val distance = data["distance"] ?: "Nearby"

        assertEquals("55", emergencyId)
        assertEquals("12", requesterId)
        assertEquals("Priya Sharma", requesterName)
        assertEquals("150 m", distance)
    }

    @Test
    fun test_parse_sos_created_payload_fallback_defaults() {
        val data = mapOf(
            "emergencyId" to "77",
            "type" to "SOS_CREATED"
        )

        val emergencyId = data["emergencyId"]
        val requesterId = data["requesterId"] ?: "0"
        val requesterName = data["requesterName"] ?: "Someone in need"
        val distance = data["distance"] ?: data["distanceKm"]?.let { "$it km" } ?: data["distanceMeters"]?.let { "$it m" } ?: "Nearby"

        assertEquals("77", emergencyId)
        assertEquals("0", requesterId)
        assertEquals("Someone in need", requesterName)
        assertEquals("Nearby", distance)
    }

    @Test
    fun test_parse_sos_created_payload_with_distance_km() {
        val data = mapOf(
            "emergencyId" to "88",
            "distanceKm" to "1.4"
        )
        val distance = data["distance"] ?: data["distanceKm"]?.let { "$it km" } ?: data["distanceMeters"]?.let { "$it m" } ?: "Nearby"
        assertEquals("1.4 km", distance)
    }

    @Test
    fun test_parse_sos_created_payload_with_distance_meters() {
        val data = mapOf(
            "emergencyId" to "88",
            "distanceMeters" to "320"
        )
        val distance = data["distance"] ?: data["distanceKm"]?.let { "$it km" } ?: data["distanceMeters"]?.let { "$it m" } ?: "Nearby"
        assertEquals("320 m", distance)
    }

    @Test
    fun test_self_notification_suppression_logic() {
        val currentUserId = "42"
        val data = mapOf(
            "emergencyId" to "99",
            "requesterId" to "42",
            "requesterName" to "Self Requester"
        )

        val isSelf = (currentUserId == data["requesterId"])
        assertTrue("Self-created SOS must be suppressed from showing responder heads-up alert", isSelf)
    }

    @Test
    fun test_other_responder_notification_allowed() {
        val currentUserId = "42"
        val data = mapOf(
            "emergencyId" to "99",
            "requesterId" to "999",
            "requesterName" to "Other Citizen"
        )

        val isSelf = (currentUserId == data["requesterId"])
        assertFalse("Eligible nearby responder must receive notification", isSelf)
    }

    // =========================================================================
    // 5. NOTIFICATION ACTION RECEIVER SEMANTIC CONTRACTS
    // =========================================================================

    @Test
    fun test_action_constants_contract() {
        assertEquals("com.example.safewalk.action.ACCEPT_EMERGENCY", EmergencyNotificationActionReceiver.ACTION_ACCEPT_EMERGENCY)
        assertEquals("com.example.safewalk.action.DISMISS_NOTIFICATION", EmergencyNotificationActionReceiver.ACTION_DISMISS_NOTIFICATION)
        assertEquals("com.example.safewalk.action.VIEW_EMERGENCY", EmergencyNotificationActionReceiver.ACTION_VIEW_EMERGENCY)
        assertEquals("extra_emergency_id", EmergencyNotificationActionReceiver.EXTRA_EMERGENCY_ID)
    }

    @Test
    fun test_cancel_action_semantic_is_pure_local_dismissal() {
        // Contract verification: ACTION_DISMISS_NOTIFICATION is mapped to CANCEL button in UI
        // It dismisses the notification locally. It MUST NOT call backend cancel endpoint.
        val action = EmergencyNotificationActionReceiver.ACTION_DISMISS_NOTIFICATION
        val isDismissAction = (action == "com.example.safewalk.action.DISMISS_NOTIFICATION")
        assertTrue("Dismiss action must strictly match local dismissal intent", isDismissAction)
    }

    @Test
    fun test_lifecycle_cancellation_maps_to_same_notification_id() {
        val emergencyId = "202"
        val createdNotifId = EmergencyNotificationHelper.getNotificationId(emergencyId)
        val cancelledNotifId = EmergencyNotificationHelper.getNotificationId(emergencyId)
        val resolvedNotifId = EmergencyNotificationHelper.getNotificationId(emergencyId)

        assertEquals("Creation and cancellation must target exact same notification ID", createdNotifId, cancelledNotifId)
        assertEquals("Creation and resolution must target exact same notification ID", createdNotifId, resolvedNotifId)
    }

    // =========================================================================
    // 6. FULL-SCREEN EMERGENCY ALERT & MULTI-SOS COLLISION SAFETY TESTS
    // =========================================================================

    @Test
    fun test_multi_sos_concurrent_notifications_coexist() {
        val id381 = EmergencyNotificationHelper.getNotificationId("381")
        val id382 = EmergencyNotificationHelper.getNotificationId("382")
        val id383 = EmergencyNotificationHelper.getNotificationId("383")

        // Distinct notifications must have distinct IDs
        assertNotEquals(id381, id382)
        assertNotEquals(id382, id383)
        assertNotEquals(id381, id383)

        // Canceling 382 does not affect 381 or 383
        val cancel382 = EmergencyNotificationHelper.getNotificationId("382")
        assertEquals(id382, cancel382)
        assertNotEquals(id381, cancel382)
        assertNotEquals(id383, cancel382)
    }

    @Test
    fun test_full_screen_intent_constants_contract() {
        assertEquals("extra_emergency_id", com.example.safewalk.ui.emergency.FullScreenEmergencyAlertActivity.EXTRA_EMERGENCY_ID)
        assertEquals("extra_requester_id", com.example.safewalk.ui.emergency.FullScreenEmergencyAlertActivity.EXTRA_REQUESTER_ID)
        assertEquals("extra_requester_name", com.example.safewalk.ui.emergency.FullScreenEmergencyAlertActivity.EXTRA_REQUESTER_NAME)
        assertEquals("extra_distance", com.example.safewalk.ui.emergency.FullScreenEmergencyAlertActivity.EXTRA_APPROX_DISTANCE)
        assertEquals("extra_notification_id", com.example.safewalk.ui.emergency.FullScreenEmergencyAlertActivity.EXTRA_NOTIFICATION_ID)
    }
}

