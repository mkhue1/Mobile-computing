package com.example.gamercalendar.nfc

/**
 * Text exchanged over NFC when one phone is sharing a profile for a friend request.
 * The prefix keeps this distinct from a later session-invite payload.
 */
object NfcFriendPayload {
    const val PREFIX = "roundtable:friend:"

    private val UUID_PATTERN = Regex(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    )

    fun encode(userId: String): String = PREFIX + userId

    fun encodeBytes(userId: String): ByteArray = encode(userId).toByteArray(Charsets.UTF_8)

    /** User id when [raw] is a friend payload, otherwise null. */
    fun parse(raw: String): String? {
        val trimmed = raw.trim()
        if (!trimmed.startsWith(PREFIX)) return null
        val userId = trimmed.removePrefix(PREFIX).trim()
        if (!UUID_PATTERN.matches(userId)) return null
        return userId
    }

    fun parse(bytes: ByteArray): String? = parse(bytes.toString(Charsets.UTF_8))
}
