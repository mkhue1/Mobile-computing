package com.example.gamercalendar.biometric

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.biometricDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "biometric_login"
)

data class BiometricSession(
    val token: String,
    val username: String,
    val email: String,
)

data class EncryptedBiometricCredential(
    val iv: ByteArray,
    val ciphertext: ByteArray,
    val username: String,
    val email: String,
)

/**
 * Holds the login token in Android Keystore so it can only be read after a strong biometric check.
 * */
class BiometricCredentialStore(
    private val context: Context
) {
    private val ivKey = stringPreferencesKey("iv")
    private val ciphertextKey = stringPreferencesKey("ciphertext")
    private val usernameKey = stringPreferencesKey("username")
    private val emailKey = stringPreferencesKey("email")
    private val declinedKey = booleanPreferencesKey("setup_declined")

    suspend fun hasCredential(): Boolean = read() != null

    suspend fun storedUsername(): String? {
        return context.biometricDataStore.data.first()[usernameKey]
    }

    suspend fun isSetupDeclined(): Boolean {
        return context.biometricDataStore.data.first()[declinedKey] == true
    }

    suspend fun setSetupDeclined(declined: Boolean) {
        context.biometricDataStore.edit { prefs ->
            prefs[declinedKey] = declined
        }
    }

    suspend fun read(): EncryptedBiometricCredential? {
        val prefs = context.biometricDataStore.data.first()
        val iv = prefs[ivKey] ?: return null
        val ciphertext = prefs[ciphertextKey] ?: return null
        val username = prefs[usernameKey] ?: return null
        val email = prefs[emailKey] ?: return null
        return EncryptedBiometricCredential(
            iv = Base64.decode(iv, Base64.NO_WRAP),
            ciphertext = Base64.decode(ciphertext, Base64.NO_WRAP),
            username = username,
            email = email,
        )
    }

    suspend fun save(iv: ByteArray, ciphertext: ByteArray, username: String, email: String) {
        context.biometricDataStore.edit { prefs ->
            prefs[ivKey] = Base64.encodeToString(iv, Base64.NO_WRAP)
            prefs[ciphertextKey] = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
            prefs[usernameKey] = username
            prefs[emailKey] = email
            prefs[declinedKey] = false
        }
    }

    suspend fun clear() {
        context.biometricDataStore.edit { prefs ->
            val declined = prefs[declinedKey] == true
            prefs.clear()
            if (declined) prefs[declinedKey] = true
        }
        deleteKey()
    }

    fun encryptCipher(): Cipher {
        val cipher = newCipher()
        try {
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        } catch (_: android.security.keystore.KeyPermanentlyInvalidatedException) {
            deleteKey()
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        }
        return cipher
    }

    fun decryptCipher(iv: ByteArray): Cipher {
        val key = existingKey() ?: throw BiometricCredentialInvalid(
            "Biometric sign-in is no longer available. Sign in with your password."
        )
        val cipher = newCipher()
        try {
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        } catch (_: android.security.keystore.KeyPermanentlyInvalidatedException) {
            throw BiometricCredentialInvalid(
                "Biometric sign-in was reset because your fingerprint or face changed. Sign in with your password."
            )
        }
        return cipher
    }

    private fun existingKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return keyStore.getKey(KEY_ALIAS, null) as? SecretKey
    }

    private fun secretKey(): SecretKey {
        existingKey()?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(-1)
        }
        generator.init(builder.build())
        return generator.generateKey()
    }

    private fun deleteKey() {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) {
            keyStore.deleteEntry(KEY_ALIAS)
        }
    }

    private fun newCipher(): Cipher {
        return Cipher.getInstance(
            KeyProperties.KEY_ALGORITHM_AES + "/" +
                KeyProperties.BLOCK_MODE_GCM + "/" +
                KeyProperties.ENCRYPTION_PADDING_NONE
        )
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "gamercalendar_biometric_login"
    }
}
