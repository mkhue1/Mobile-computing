package com.example.gamercalendar.firebase

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

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

    override fun onNewToken(token: String) {
        // TODO: send the token to the backend once the device token endpoint exists, so the server
        //  can reach this phone.
        Log.d(TAG, "New FCM token")
    }

    private companion object {
        const val TAG = "PushMessaging"
    }
}
