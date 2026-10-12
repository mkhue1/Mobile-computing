package com.example.gamercalendar.firebase

import android.util.Log
import com.example.gamercalendar.data.api.ApiClient
import com.example.gamercalendar.data.model.DeviceTokenRegister
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** Tells the server which phone to send a logged-in user's push notifications to. */
internal object PushTokenRegistration {

    private const val TAG = "PushTokenRegistration"
    private const val REMOVE_TIMEOUT_MILLIS = 3_000L

    /** Registers the phone's current token for the logged-in user. Called after login and on launches. */
    suspend fun register() {
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            register(token)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't get the push token", e)
        }
    }

    /** Registers a provided token, e.g. a new one from PushMessagingService.onNewToken. */
    suspend fun register(token: String) {
        try {
            ApiClient.api.registerDeviceToken(DeviceTokenRegister(token))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't register the push token", e)
        }
    }

    /**
     * Stops the phone receiving the current user's notifications. Called on logout (must still have
     * user data available though, so we should only clear after this has been called).
     */
    suspend fun remove() {
        try {
            withTimeoutOrNull(REMOVE_TIMEOUT_MILLIS) {
                val token = FirebaseMessaging.getInstance().token.await()
                ApiClient.api.removeDeviceToken(token)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't remove the push token", e)
        }
    }
}
