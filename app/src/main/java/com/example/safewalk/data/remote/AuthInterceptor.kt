package com.example.safewalk.data.remote

import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp Interceptor that automatically attaches the JWT Bearer token
 * to authenticated HTTP requests.
 */
class AuthInterceptor(
    private val tokenProvider: () -> String?,
    private val onUnauthorized: (() -> Unit)? = null
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val requestBuilder = originalRequest.newBuilder()

        // Set default Accept header
        if (originalRequest.header("Accept") == null) {
            requestBuilder.header("Accept", "application/json")
        }

        // Attach JWT Bearer token if available and not already provided
        val token = tokenProvider()
        if (!token.isNullOrBlank() && originalRequest.header("Authorization") == null) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        val request = requestBuilder.build()
        val response = chain.proceed(request)

        // Detect 401 Unauthorized on protected routes
        if (response.code == 401) {
            val path = request.url.encodedPath
            val isAuthEndpoint = path.contains("/auth/login") || path.contains("/auth/register")
            if (!isAuthEndpoint) {
                onUnauthorized?.invoke()
            }
        }

        return response
    }
}
