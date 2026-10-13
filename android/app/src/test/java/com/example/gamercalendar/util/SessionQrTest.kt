package com.example.gamercalendar.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionQrTest {
    private val sessionId = "550e8400-e29b-41d4-a716-446655440000"

    @Test
    fun parse_acceptsSessionId() {
        assertEquals(sessionId, SessionQr.parse(sessionId))
        assertEquals(sessionId, SessionQr.parse("  $sessionId  "))
        assertEquals(sessionId, SessionQr.parse(SessionQr.encode(sessionId)))
    }

    @Test
    fun parse_rejectsEmpty() {
        assertNull(SessionQr.parse(""))
        assertNull(SessionQr.parse("   "))
    }

    @Test
    fun parse_rejectsPrefixedOrShortValues() {
        assertNull(SessionQr.parse("roundtable:session:$sessionId"))
        assertNull(SessionQr.parse("not-a-session"))
    }
}
