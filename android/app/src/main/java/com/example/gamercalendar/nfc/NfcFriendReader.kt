package com.example.gamercalendar.nfc

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Handler
import android.os.Looper
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reads a friend payload from a phone that has [FriendNfcShareService] active.
 */
class NfcFriendReader(
    private val activity: Activity,
    private val listener: Listener
) : NfcAdapter.ReaderCallback {
    interface Listener {
        fun onPayload(raw: String)
        fun onError(message: String)
    }

    private val adapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val stopped = AtomicBoolean(false)
    private val delivered = AtomicBoolean(false)

    fun start(): Boolean {
        val nfc = adapter ?: return false
        if (!nfc.isEnabled) return false
        stopped.set(false)
        delivered.set(false)
        nfc.enableReaderMode(
            activity,
            this,
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null
        )
        return true
    }

    fun stop() {
        stopped.set(true)
        adapter?.disableReaderMode(activity)
    }

    override fun onTagDiscovered(tag: Tag) {
        if (stopped.get() || delivered.get()) return
        val isoDep = IsoDep.get(tag)
        if (isoDep == null) {
            postError("Couldn't read that device. Hold the phones together and try again.")
            return
        }
        try {
            isoDep.connect()
            isoDep.timeout = 3_000
            val selected = isoDep.transceive(NfcFriendProtocol.selectCommand())
            if (!NfcFriendProtocol.isSuccess(selected)) {
                postError("That device isn't sharing a Roundtable profile.")
                return
            }
            val read = isoDep.transceive(NfcFriendProtocol.readCommand())
            val body = NfcFriendProtocol.payloadFromRead(read)
            if (body == null) {
                postError("Couldn't read that profile. Hold the phones together and try again.")
                return
            }
            deliver(body.toString(Charsets.UTF_8))
        } catch (_: IOException) {
            postError("Couldn't read that device. Hold the phones together and try again.")
        } finally {
            try {
                isoDep.close()
            } catch (_: IOException) {
            }
        }
    }

    private fun deliver(raw: String) {
        if (stopped.get() || !delivered.compareAndSet(false, true)) return
        mainHandler.post {
            if (!stopped.get()) listener.onPayload(raw)
        }
    }

    private fun postError(message: String) {
        if (stopped.get()) return
        mainHandler.post {
            if (!stopped.get()) listener.onError(message)
        }
    }
}
