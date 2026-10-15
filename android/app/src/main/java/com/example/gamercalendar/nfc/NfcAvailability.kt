package com.example.gamercalendar.nfc

import android.content.Context
import android.nfc.NfcAdapter

enum class NfcAvailability {
    Ready,
    Disabled,
    Unsupported
}

fun Context.nfcAvailability(): NfcAvailability {
    val adapter = NfcAdapter.getDefaultAdapter(this) ?: return NfcAvailability.Unsupported
    return if (adapter.isEnabled) NfcAvailability.Ready else NfcAvailability.Disabled
}
