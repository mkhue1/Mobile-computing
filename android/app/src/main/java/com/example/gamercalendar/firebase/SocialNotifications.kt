package com.example.gamercalendar.firebase

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.gamercalendar.MainActivity
import com.example.gamercalendar.R

/**
 * The two types of social notification the server sends (session invites and freind requests).
 * Each type has its own channel so users can mute one without the affecting the other.
 */
internal enum class SocialNotificationType(
    val messageType: String,
    val channelId: String,
    val channelName: String,
    val channelDescription: String
) {
    SESSION_INVITE(
        messageType = "session_invite",
        channelId = "session_invites",
        channelName = "Session invites",
        channelDescription = "When someone invites you to a gaming session"
    ),
    FRIEND_REQUEST(
        messageType = "friend_request",
        channelId = "friend_requests",
        channelName = "Friend requests",
        channelDescription = "When someone sends you a friend request"
    );

    companion object {
        fun fromMessageType(name: String?): SocialNotificationType? = entries.firstOrNull { it.messageType == name }
    }
}

internal object SocialNotifications {

    /**
     * Shows one notification. id identifies the invite or request, so each gets its ownnotification.
     */
    fun show(
        context: Context,
        type: SocialNotificationType,
        id: String,
        title: String,
        body: String,
        sessionId: String? = null
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannels(context)

        val notificationId = "${type.messageType}:$id".hashCode()

        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_SOCIAL, type.messageType)
            sessionId?.let { putExtra(MainActivity.EXTRA_SOCIAL_SESSION_ID, it) }
        }

        val pending = PendingIntent.getActivity(
            context,
            notificationId,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, type.channelId)
            .setSmallIcon(R.drawable.ic_stat_social)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }


    // Makes sure noti channels exist, (creating is idempotent so we can call this as much as needed).
    private fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            SocialNotificationType.entries.map { type ->
                NotificationChannel(
                    type.channelId,
                    type.channelName,
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = type.channelDescription
                }
            }
        )
    }
}
