package com.example.safewalk.core.websocket

/**
 * Represents a STOMP 1.2 protocol frame.
 * Frames consist of a command, headers, and a body, terminated by a NULL byte (U+0000).
 */
data class StompFrame(
    val command: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String = ""
) {
    /**
     * Serializes this frame to STOMP wire format string.
     */
    fun toWireFormat(): String {
        val sb = StringBuilder()
        sb.append(command).append("\n")
        for ((k, v) in headers) {
            sb.append(k).append(":").append(v).append("\n")
        }
        sb.append("\n")
        sb.append(body)
        sb.append(NULL_BYTE)
        return sb.toString()
    }

    companion object {
        const val NULL_BYTE = "\u0000"

        // Commands
        const val COMMAND_CONNECT = "CONNECT"
        const val COMMAND_CONNECTED = "CONNECTED"
        const val COMMAND_SUBSCRIBE = "SUBSCRIBE"
        const val COMMAND_UNSUBSCRIBE = "UNSUBSCRIBE"
        const val COMMAND_SEND = "SEND"
        const val COMMAND_MESSAGE = "MESSAGE"
        const val COMMAND_RECEIPT = "RECEIPT"
        const val COMMAND_ERROR = "ERROR"
        const val COMMAND_DISCONNECT = "DISCONNECT"

        // Header Keys
        const val HEADER_DESTINATION = "destination"
        const val HEADER_ID = "id"
        const val HEADER_SUBSCRIPTION = "subscription"
        const val HEADER_ACCEPT_VERSION = "accept-version"
        const val HEADER_HEARTBEAT = "heart-beat"
        const val HEADER_AUTHORIZATION = "Authorization"
        const val HEADER_CONTENT_TYPE = "content-type"

        /**
         * Parses a raw text string containing one or more STOMP frames.
         */
        fun parse(rawText: String): List<StompFrame> {
            val frames = mutableListOf<StompFrame>()
            val rawFrames = rawText.split(NULL_BYTE)

            for (part in rawFrames) {
                val trimmed = part.trim { it == '\r' || it == '\n' || it == ' ' }
                if (trimmed.isEmpty()) continue

                val lines = trimmed.lines()
                if (lines.isEmpty()) continue

                val command = lines[0].trim()
                val headers = mutableMapOf<String, String>()
                var headerEndIdx = lines.size
                var bodyStartIdx = -1

                for (i in 1 until lines.size) {
                    val line = lines[i]
                    if (line.isEmpty() || line == "\r") {
                        bodyStartIdx = i + 1
                        break
                    }
                    val colonIdx = line.indexOf(':')
                    if (colonIdx > 0) {
                        val key = line.substring(0, colonIdx).trim()
                        val value = line.substring(colonIdx + 1).trim()
                        headers[key] = value
                    }
                }

                val body = if (bodyStartIdx in 0..lines.size) {
                    lines.subList(bodyStartIdx, lines.size).joinToString("\n")
                } else {
                    ""
                }

                frames.add(StompFrame(command = command, headers = headers, body = body))
            }
            return frames
        }

        fun buildConnect(token: String): StompFrame {
            return StompFrame(
                command = COMMAND_CONNECT,
                headers = mapOf(
                    HEADER_ACCEPT_VERSION to "1.1,1.2",
                    HEADER_HEARTBEAT to "10000,10000",
                    HEADER_AUTHORIZATION to "Bearer $token"
                )
            )
        }

        fun buildSubscribe(destination: String, subscriptionId: String): StompFrame {
            return StompFrame(
                command = COMMAND_SUBSCRIBE,
                headers = mapOf(
                    HEADER_ID to subscriptionId,
                    HEADER_DESTINATION to destination
                )
            )
        }

        fun buildUnsubscribe(subscriptionId: String): StompFrame {
            return StompFrame(
                command = COMMAND_UNSUBSCRIBE,
                headers = mapOf(
                    HEADER_ID to subscriptionId
                )
            )
        }

        fun buildDisconnect(): StompFrame {
            return StompFrame(command = COMMAND_DISCONNECT)
        }
    }
}
