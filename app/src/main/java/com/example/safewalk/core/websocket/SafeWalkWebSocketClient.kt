package com.example.safewalk.core.websocket

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

enum class WebSocketConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING
}

data class StompMessage(
    val destination: String,
    val subscriptionId: String?,
    val body: String
)

/**
 * Production-ready OkHttp-based STOMP WebSocket client for SafeWalk.
 * Provides resilient real-time bidirectional messaging, automatic reconnection,
 * subscription restoration, and thread-safe session management.
 */
class SafeWalkWebSocketClient(
    private val okHttpClient: OkHttpClient,
    private val tokenProvider: () -> String?,
    private val baseUrlProvider: () -> String,
    private val externalScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    companion object {
        private const val TAG = "SafeWalkWebSocketClient"
        private const val INITIAL_RECONNECT_DELAY_MS = 2000L
        private const val MAX_RECONNECT_DELAY_MS = 15000L
    }

    private val _connectionState = MutableStateFlow(WebSocketConnectionState.DISCONNECTED)
    val connectionState: StateFlow<WebSocketConnectionState> = _connectionState.asStateFlow()

    private val _messages = MutableSharedFlow<StompMessage>(extraBufferCapacity = 64)
    val messages: SharedFlow<StompMessage> = _messages.asSharedFlow()

    private var activeWebSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var isIntentionallyDisconnected = false
    private var reconnectDelayMs = INITIAL_RECONNECT_DELAY_MS

    private val subscriptionCounter = AtomicInteger(0)
    // destination -> subscriptionId
    private val activeSubscriptions = ConcurrentHashMap<String, String>()

    @Synchronized
    fun connect() {
        val currentState = _connectionState.value
        if (currentState == WebSocketConnectionState.CONNECTED || currentState == WebSocketConnectionState.CONNECTING) {
            Log.d(TAG, "Already connected or connecting; skipping duplicate connection attempt.")
            return
        }

        val token = tokenProvider()
        if (token.isNullOrBlank()) {
            Log.w(TAG, "Cannot connect WebSocket: No authentication token available.")
            _connectionState.value = WebSocketConnectionState.DISCONNECTED
            return
        }

        isIntentionallyDisconnected = false
        _connectionState.value = WebSocketConnectionState.CONNECTING

        val baseUrl = baseUrlProvider()
        val wsUrl = buildWebSocketUrl(baseUrl)
        Log.i(TAG, "Connecting to WebSocket at $wsUrl...")

        val request = Request.Builder()
            .url(wsUrl)
            .addHeader("Authorization", "Bearer $token")
            .build()

        activeWebSocket = okHttpClient.newWebSocket(request, createWebSocketListener(token))
    }

    private fun buildWebSocketUrl(httpBaseUrl: String): String {
        val clean = httpBaseUrl.trimEnd('/')
        val wsBase = when {
            clean.startsWith("https://") -> clean.replaceFirst("https://", "wss://")
            clean.startsWith("http://") -> clean.replaceFirst("http://", "ws://")
            clean.startsWith("ws://") || clean.startsWith("wss://") -> clean
            else -> "ws://$clean"
        }
        return if (wsBase.endsWith("/ws")) wsBase else "$wsBase/ws"
    }

    private fun createWebSocketListener(token: String): WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "Transport WebSocket connected. Sending STOMP CONNECT frame...")
                val connectFrame = StompFrame.buildConnect(token)
                webSocket.send(connectFrame.toWireFormat())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val frames = StompFrame.parse(text)
                for (frame in frames) {
                    handleStompFrame(webSocket, frame)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket server requested closing ($code: $reason)")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closed ($code: $reason)")
                handleDisconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "WebSocket failure: ${t.message} (HTTP code: ${response?.code})")
                handleDisconnect()
            }
        }
    }

    private fun handleStompFrame(webSocket: WebSocket, frame: StompFrame) {
        when (frame.command) {
            StompFrame.COMMAND_CONNECTED -> {
                Log.i(TAG, "STOMP session CONNECTED! Version: ${frame.headers["version"]}, User: ${frame.headers["user-name"]}")
                _connectionState.value = WebSocketConnectionState.CONNECTED
                reconnectDelayMs = INITIAL_RECONNECT_DELAY_MS

                // Restore all active subscriptions
                for ((destination, subId) in activeSubscriptions) {
                    val subFrame = StompFrame.buildSubscribe(destination, subId)
                    webSocket.send(subFrame.toWireFormat())
                    Log.d(TAG, "Restored subscription to $destination (id: $subId)")
                }
            }

            StompFrame.COMMAND_MESSAGE -> {
                val destination = frame.headers[StompFrame.HEADER_DESTINATION] ?: ""
                val subId = frame.headers[StompFrame.HEADER_SUBSCRIPTION]
                Log.d(TAG, "Received STOMP message on destination: $destination")
                _messages.tryEmit(StompMessage(destination = destination, subscriptionId = subId, body = frame.body))
            }

            StompFrame.COMMAND_ERROR -> {
                Log.e(TAG, "Received STOMP ERROR frame: ${frame.headers["message"]}. Body: ${frame.body}")
            }

            StompFrame.COMMAND_RECEIPT -> {
                Log.d(TAG, "Received STOMP RECEIPT: ${frame.headers["receipt-id"]}")
            }
        }
    }

    private fun handleDisconnect() {
        activeWebSocket = null
        if (isIntentionallyDisconnected) {
            _connectionState.value = WebSocketConnectionState.DISCONNECTED
            return
        }

        _connectionState.value = WebSocketConnectionState.RECONNECTING
        scheduleReconnect()
    }

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = externalScope.launch {
            Log.i(TAG, "Scheduling WebSocket reconnect in ${reconnectDelayMs}ms...")
            delay(reconnectDelayMs)
            reconnectDelayMs = minOf(reconnectDelayMs * 2, MAX_RECONNECT_DELAY_MS)
            connect()
        }
    }

    @Synchronized
    fun subscribe(destination: String): String {
        val existingSubId = activeSubscriptions[destination]
        if (existingSubId != null) {
            return existingSubId
        }

        val subId = "sub-${subscriptionCounter.incrementAndGet()}"
        activeSubscriptions[destination] = subId

        if (_connectionState.value == WebSocketConnectionState.CONNECTED) {
            activeWebSocket?.let { ws ->
                val subFrame = StompFrame.buildSubscribe(destination, subId)
                ws.send(subFrame.toWireFormat())
                Log.d(TAG, "Subscribed to $destination with id $subId")
            }
        }
        return subId
    }

    @Synchronized
    fun unsubscribe(destination: String) {
        val subId = activeSubscriptions.remove(destination) ?: return
        if (_connectionState.value == WebSocketConnectionState.CONNECTED) {
            activeWebSocket?.let { ws ->
                val unsubFrame = StompFrame.buildUnsubscribe(subId)
                ws.send(unsubFrame.toWireFormat())
                Log.d(TAG, "Unsubscribed from $destination (id: $subId)")
            }
        }
    }

    @Synchronized
    fun disconnect() {
        Log.i(TAG, "Disconnecting WebSocket intentionally...")
        isIntentionallyDisconnected = true
        reconnectJob?.cancel()
        reconnectJob = null

        activeWebSocket?.let { ws ->
            try {
                ws.send(StompFrame.buildDisconnect().toWireFormat())
                ws.close(1000, "User logout")
            } catch (_: Exception) {}
        }
        activeWebSocket = null
        activeSubscriptions.clear()
        _connectionState.value = WebSocketConnectionState.DISCONNECTED
    }
}
