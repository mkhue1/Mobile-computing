package com.example.gamercalendar.firebase

import android.util.Log
import com.example.gamercalendar.data.api.ApiClient
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Receives push messages from Firebase. Expected data: "type", "id", "title", "body" and, for invites, "session_id".
 */
class PushMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val type = SocialNotificationType.fromMessageType(data["type"]) ?: run {
            Log.w(TAG, "Ignoring push message with unknown type: ${data["type"]}")
            return
        }

        SocialNotifications.show(
            context = applicationContext,
            type = type,
            // fall back to Firebase's message id so a message without one still shows on its own
            id = data["id"] ?: message.messageId ?: System.currentTimeMillis().toString(),
            title = data["title"] ?: type.channelName,
            body = data["body"].orEmpty(),
            sessionId = data["session_id"]
        )
    }

    // A service has no lifecycle scope of its own, so it gets one that ends when the service does.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        // Only possible while the app is running with someone logged in. Otherwise there's no login
        // to send it with
        if (!ApiClient.isSignedIn()) return
        scope.launch { PushTokenRegistration.register(token) }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "PushMessaging"
    }
}
