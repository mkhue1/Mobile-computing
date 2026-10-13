package com.example.gamercalendar.util

/**
 * Text stored in a public-session QR code.
 */
object SessionQr {
    private val UUID_PATTERN = Regex(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    )

    fun encode(sessionId: String): String = sessionId

    /** Session id when [raw] is a session QR payload, otherwise null. */
    fun parse(raw: String): String? {
        val sessionId = raw.trim()
        if (!UUID_PATTERN.matches(sessionId)) return null
        return sessionId
    }
}
