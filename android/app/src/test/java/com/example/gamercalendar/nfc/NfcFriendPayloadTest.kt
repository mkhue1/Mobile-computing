package com.example.gamercalendar.nfc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NfcFriendPayloadTest {
    private val userId = "550e8400-e29b-41d4-a716-446655440000"

    @Test
    fun parse_acceptsFriendPayload() {
        assertEquals(userId, NfcFriendPayload.parse("roundtable:friend:$userId"))
        assertEquals(userId, NfcFriendPayload.parse(NfcFriendPayload.encodeBytes(userId)))
    }

    @Test
    fun parse_rejectsBareUuid() {
        assertNull(NfcFriendPayload.parse(userId))
    }

    @Test
    fun parse_rejectsEmpty() {
        assertNull(NfcFriendPayload.parse(""))
        assertNull(NfcFriendPayload.parse("   "))
    }

    @Test
    fun parse_rejectsOtherPrefix() {
        assertNull(NfcFriendPayload.parse("roundtable:session:$userId"))
    }
}
