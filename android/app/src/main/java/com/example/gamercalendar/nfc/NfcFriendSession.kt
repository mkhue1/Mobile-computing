package com.example.gamercalendar.nfc

/**
 * In-memory gate for [FriendNfcShareService]. The user id is advertised only while
 * the share sheet is open; a locked or background phone returns an error APDU.
 */
object NfcFriendSession {
    @Volatile
    private var userId: String? = null

    fun start(userId: String) {
        this.userId = userId
    }

    fun stop() {
        userId = null
    }

    fun payloadOrNull(): ByteArray? {
        val id = userId ?: return null
        return NfcFriendPayload.encodeBytes(id)
    }
}
