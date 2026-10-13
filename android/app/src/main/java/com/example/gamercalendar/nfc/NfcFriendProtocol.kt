package com.example.gamercalendar.nfc

/**
 * Proprietary AID and short APDUs for phone-to-phone friend sharing.
 * [AID_HEX] must match `res/xml/nfc_friend_apdu_service.xml`.
 */
object NfcFriendProtocol {
    const val AID_HEX = "F0526F756E647461626C65"

    val AID: ByteArray = hexToBytes(AID_HEX)

    private const val CLA: Byte = 0x00
    private const val INS_SELECT: Byte = 0xA4.toByte()
    private const val INS_READ_BINARY: Byte = 0xB0.toByte()
    private const val P1_SELECT_BY_NAME: Byte = 0x04

    val SW_OK = byteArrayOf(0x90.toByte(), 0x00)
    val SW_NOT_FOUND = byteArrayOf(0x6A.toByte(), 0x82.toByte())
    val SW_WRONG_LENGTH = byteArrayOf(0x67.toByte(), 0x00)
    val SW_WRONG_P1P2 = byteArrayOf(0x6A.toByte(), 0x86.toByte())
    val SW_INS_NOT_SUPPORTED = byteArrayOf(0x6D.toByte(), 0x00)

    fun selectCommand(): ByteArray {
        val command = ByteArray(6 + AID.size)
        command[0] = CLA
        command[1] = INS_SELECT
        command[2] = P1_SELECT_BY_NAME
        command[3] = 0x00
        command[4] = AID.size.toByte()
        AID.copyInto(command, destinationOffset = 5)
        command[command.lastIndex] = 0x00
        return command
    }

    fun readCommand(): ByteArray = byteArrayOf(CLA, INS_READ_BINARY, 0x00, 0x00, 0x00)

    fun isSelect(command: ByteArray): Boolean =
        command.size >= 4 && command[1] == INS_SELECT && command[2] == P1_SELECT_BY_NAME

    fun isReadBinary(command: ByteArray): Boolean =
        command.size >= 4 && command[1] == INS_READ_BINARY

    fun isSuccess(response: ByteArray): Boolean {
        if (response.size < 2) return false
        return response[response.size - 2] == SW_OK[0] && response[response.size - 1] == SW_OK[1]
    }

    fun payloadFromRead(response: ByteArray): ByteArray? {
        if (!isSuccess(response)) return null
        return response.copyOf(response.size - 2)
    }

    private fun hexToBytes(hex: String): ByteArray =
        ByteArray(hex.length / 2) { index ->
            hex.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
}
