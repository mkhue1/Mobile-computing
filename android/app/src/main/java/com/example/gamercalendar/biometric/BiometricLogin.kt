package com.example.gamercalendar.biometric

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import java.security.GeneralSecurityException
import javax.crypto.Cipher
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

open class BiometricLoginException(message: String) : Exception(message)

/** Saved biometric login can no longer be decrypted. The store has been cleared. */
class BiometricCredentialInvalid(message: String) : BiometricLoginException(message)

class BiometricCancelled : Exception()

/**
 * Enables biometric sign-in and later decrypts the saved session token.
 * Cancelling the system prompt throws [BiometricCancelled] and does not sign the user in.
 */
class BiometricLogin(
    context: Context
) {
    private val appContext = context.applicationContext
    private val store = BiometricCredentialStore(appContext)
    private var promptInProgress = false

    fun status(): BiometricStatus = BiometricSupport.status(appContext)

    suspend fun hasCredential(): Boolean = store.hasCredential()

    suspend fun storedUsername(): String? = store.storedUsername()

    suspend fun isSetupDeclined(): Boolean = store.isSetupDeclined()

    suspend fun setSetupDeclined(declined: Boolean) {
        store.setSetupDeclined(declined)
    }

    suspend fun disable() {
        store.clear()
    }

    suspend fun enable(activity: FragmentActivity, session: BiometricSession) {
        val cipher = try {
            store.encryptCipher()
        } catch (e: GeneralSecurityException) {
            throw BiometricLoginException(
                e.message ?: "Couldn't set up biometric sign-in on this device"
            )
        }
        val authed = prompt(
            activity = activity,
            cipher = cipher,
            title = "Enable biometric sign-in",
            subtitle = "Confirm your fingerprint or face"
        )
        val iv = authed.iv?.copyOf() ?: throw BiometricLoginException("Couldn't save biometric sign-in")
        val encrypted = try {
            authed.doFinal(session.token.toByteArray(Charsets.UTF_8))
        } catch (e: GeneralSecurityException) {
            throw BiometricLoginException(
                e.message ?: "Couldn't save biometric sign-in"
            )
        }
        store.save(iv, encrypted, session.username, session.email)
    }

    suspend fun decrypt(activity: FragmentActivity): BiometricSession {
        val encrypted = store.read() ?: throw BiometricLoginException("Biometric sign-in is not set up")
        val cipher = try {
            store.decryptCipher(encrypted.iv)
        } catch (e: BiometricCredentialInvalid) {
            store.clear()
            throw e
        } catch (e: GeneralSecurityException) {
            store.clear()
            throw BiometricCredentialInvalid(
                e.message ?: "Biometric sign-in is no longer available. Sign in with your password."
            )
        }
        val authed = prompt(
            activity = activity,
            cipher = cipher,
            title = "Sign in to Roundtable",
            subtitle = "Use your fingerprint or face"
        )
        val token = try {
            String(authed.doFinal(encrypted.ciphertext), Charsets.UTF_8)
        } catch (_: GeneralSecurityException) {
            store.clear()
            throw BiometricCredentialInvalid(
                "Couldn't unlock your saved sign-in. Use your password."
            )
        }
        if (token.isBlank()) {
            store.clear()
            throw BiometricCredentialInvalid(
                "Couldn't unlock your saved sign-in. Use your password."
            )
        }
        return BiometricSession(token, encrypted.username, encrypted.email)
    }

    private suspend fun prompt(
        activity: FragmentActivity,
        cipher: Cipher,
        title: String,
        subtitle: String,
    ): Cipher {
        if (promptInProgress) {
            throw BiometricCancelled()
        }
        promptInProgress = true
        try {
            return suspendCancellableCoroutine { continuation ->
                val prompt = BiometricPrompt(
                    activity,
                    ContextCompat.getMainExecutor(activity),
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            val authed = result.cryptoObject?.cipher
                            if (!continuation.isActive) return
                            if (authed == null) {
                                continuation.resumeWithException(
                                    BiometricLoginException("Couldn't verify biometrics. Use your password.")
                                )
                            } else {
                                continuation.resume(authed)
                            }
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            if (!continuation.isActive) return
                            if (
                                errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                                errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                                errorCode == BiometricPrompt.ERROR_CANCELED
                            ) {
                                continuation.resumeWithException(BiometricCancelled())
                            } else {
                                continuation.resumeWithException(
                                    BiometricLoginException(biometricErrorMessage(errorCode, errString))
                                )
                            }
                        }

                        override fun onAuthenticationFailed() {
                            // One unmatched fingerprint or face. The system prompt stays up,
                            // and nothing is decrypted until a later attempt succeeds.
                        }
                    }
                )
                continuation.invokeOnCancellation {
                    prompt.cancelAuthentication()
                }
                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setNegativeButtonText("Use password")
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                    .setConfirmationRequired(false)
                    .build()
                prompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
            }
        } finally {
            promptInProgress = false
        }
    }
}

fun biometricErrorMessage(errorCode: Int, errString: CharSequence): String {
    return when (errorCode) {
        BiometricPrompt.ERROR_LOCKOUT ->
            "Too many attempts. Use your password instead."
        BiometricPrompt.ERROR_LOCKOUT_PERMANENT ->
            "Biometrics are locked. Use your password, or unlock them in system settings."
        BiometricPrompt.ERROR_NO_BIOMETRICS ->
            "Set up fingerprint or face unlock in your phone settings first."
        BiometricPrompt.ERROR_HW_NOT_PRESENT ->
            "This device doesn't support biometric sign-in."
        BiometricPrompt.ERROR_HW_UNAVAILABLE ->
            "Biometrics are temporarily unavailable. Use your password."
        BiometricPrompt.ERROR_TIMEOUT ->
            "Biometric check timed out. Try again."
        BiometricPrompt.ERROR_UNABLE_TO_PROCESS ->
            "Couldn't read your biometrics. Try again."
        else -> errString.toString().ifBlank { "Biometric sign-in failed" }
    }
}
