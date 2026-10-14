package com.example.gamercalendar.biometric

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.fragment.app.FragmentActivity

enum class BiometricStatus {
    /** Fingerprint or face is enrolled and can be used right now. */
    Ready,

    /** The device can do biometrics, but none are enrolled. */
    NoneEnrolled,

    /** Sensor is busy, or a lockout is still cooling down. */
    TemporarilyUnavailable,

    /** This device cannot use biometric sign-in. */
    Unavailable,
}

object BiometricSupport {
    fun status(context: Context): BiometricStatus {
        return when (
            BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        ) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricStatus.Ready
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricStatus.NoneEnrolled
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricStatus.TemporarilyUnavailable
            else -> BiometricStatus.Unavailable
        }
    }
}

fun Context.findFragmentActivity(): FragmentActivity? {
    var current: Context = this
    while (true) {
        if (current is FragmentActivity) return current
        if (current !is ContextWrapper) return null
        current = current.baseContext
    }
}
