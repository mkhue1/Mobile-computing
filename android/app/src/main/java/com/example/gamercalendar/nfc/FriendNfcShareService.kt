package com.example.gamercalendar.nfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle

/**
 * Emulates a card the other phone reads with [NfcFriendReader].
 * Responds only while [NfcFriendSession] is active.
 */
class FriendNfcShareService : HostApduService() {
    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        if (commandApdu == null || commandApdu.size < 4) {
            return NfcFriendProtocol.SW_WRONG_LENGTH
        }
        return when {
            NfcFriendProtocol.isSelect(commandApdu) -> handleSelect(commandApdu)
            NfcFriendProtocol.isReadBinary(commandApdu) -> handleRead(commandApdu)
            else -> NfcFriendProtocol.SW_INS_NOT_SUPPORTED
        }
    }

    override fun onDeactivated(reason: Int) = Unit

    private fun handleSelect(command: ByteArray): ByteArray {
        if (command.size < 5) return NfcFriendProtocol.SW_WRONG_LENGTH
        val length = command[4].toInt() and 0xFF
        if (command.size < 5 + length) return NfcFriendProtocol.SW_WRONG_LENGTH
        val aid = command.copyOfRange(5, 5 + length)
        if (!aid.contentEquals(NfcFriendProtocol.AID)) return NfcFriendProtocol.SW_NOT_FOUND
        if (NfcFriendSession.payloadOrNull() == null) return NfcFriendProtocol.SW_NOT_FOUND
        return NfcFriendProtocol.SW_OK
    }

    private fun handleRead(command: ByteArray): ByteArray {
        val payload = NfcFriendSession.payloadOrNull() ?: return NfcFriendProtocol.SW_NOT_FOUND
        if (command.size < 5) return NfcFriendProtocol.SW_WRONG_LENGTH
        val p1 = command[2].toInt() and 0xFF
        if (p1 and 0x80 != 0) return NfcFriendProtocol.SW_WRONG_P1P2
        val offset = (p1 shl 8) or (command[3].toInt() and 0xFF)
        if (offset > payload.size) return NfcFriendProtocol.SW_WRONG_P1P2
        var le = command[4].toInt() and 0xFF
        if (le == 0) le = 256
        val end = minOf(offset + le, payload.size)
        return payload.copyOfRange(offset, end) + NfcFriendProtocol.SW_OK
    }
}
