package com.example.safewalk.data.remote

import android.util.Log
import com.example.safewalk.data.session.SessionManager
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Singleton factory providing OkHttp and Retrofit instances configured for SafeWalk backend.
 */
object NetworkClient {

    private var currentBaseUrl: String = ""
    private var cachedRetrofit: Retrofit? = null
    private var cachedAuthApiService: AuthApiService? = null

    fun getOkHttpClient(
        sessionManager: SessionManager? = null,
        onUnauthorized: (() -> Unit)? = null
    ): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            redactHeader("Authorization")
            redactHeader("Cookie")
            level = HttpLoggingInterceptor.Level.NONE
        }

        val builder = OkHttpClient.Builder()
            .connectTimeout(ApiConfig.connectTimeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(ApiConfig.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(ApiConfig.WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .pingInterval(15, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .addInterceptor { chain ->
                val request = chain.request()
                try {
                    chain.proceed(request)
                } catch (e: java.io.IOException) {
                    Log.w("NetworkClient", "Request to ${request.url.host} failed: ${e.message}")
                    throw e
                }
            }

        if (sessionManager != null) {
            builder.addInterceptor(
                AuthInterceptor(
                    tokenProvider = { sessionManager.getToken() },
                    onUnauthorized = onUnauthorized
                )
            )
        }

        return builder.build()
    }

    @Synchronized
    fun getRetrofit(
        sessionManager: SessionManager? = null,
        onUnauthorized: (() -> Unit)? = null
    ): Retrofit {
        val baseUrl = ApiConfig.baseUrl
        if (cachedRetrofit == null || currentBaseUrl != baseUrl) {
            currentBaseUrl = baseUrl
            val okHttpClient = getOkHttpClient(sessionManager, onUnauthorized)
            cachedRetrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            cachedAuthApiService = null
            cachedEmergencyApiService = null
            cachedLocationApiService = null
            cachedNotificationApiService = null
        }
        return cachedRetrofit!!
    }

    private var cachedEmergencyApiService: EmergencyApiService? = null

    @Synchronized
    fun getAuthApiService(
        sessionManager: SessionManager? = null,
        onUnauthorized: (() -> Unit)? = null
    ): AuthApiService {
        val retrofit = getRetrofit(sessionManager, onUnauthorized)
        if (cachedAuthApiService == null) {
            cachedAuthApiService = retrofit.create(AuthApiService::class.java)
        }
        return cachedAuthApiService!!
    }

    @Synchronized
    fun getEmergencyApiService(
        sessionManager: SessionManager? = null,
        onUnauthorized: (() -> Unit)? = null
    ): EmergencyApiService {
        val retrofit = getRetrofit(sessionManager, onUnauthorized)
        if (cachedEmergencyApiService == null) {
            cachedEmergencyApiService = retrofit.create(EmergencyApiService::class.java)
        }
        return cachedEmergencyApiService!!
    }

    private var cachedLocationApiService: LocationApiService? = null
    private var cachedNotificationApiService: NotificationApiService? = null

    @Synchronized
    fun getLocationApiService(
        sessionManager: SessionManager? = null,
        onUnauthorized: (() -> Unit)? = null
    ): LocationApiService {
        val retrofit = getRetrofit(sessionManager, onUnauthorized)
        if (cachedLocationApiService == null) {
            cachedLocationApiService = retrofit.create(LocationApiService::class.java)
        }
        return cachedLocationApiService!!
    }

    @Synchronized
    fun getNotificationApiService(
        sessionManager: SessionManager? = null,
        onUnauthorized: (() -> Unit)? = null
    ): NotificationApiService {
        val retrofit = getRetrofit(sessionManager, onUnauthorized)
        if (cachedNotificationApiService == null) {
            cachedNotificationApiService = retrofit.create(NotificationApiService::class.java)
        }
        return cachedNotificationApiService!!
    }

    @Synchronized
    fun resetClient() {
        cachedRetrofit = null
        currentBaseUrl = ""
        cachedAuthApiService = null
        cachedEmergencyApiService = null
        cachedLocationApiService = null
        cachedNotificationApiService = null
    }
}
