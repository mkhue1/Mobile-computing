package com.example.gamercalendar.geofence

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

internal object NearbyNotifications {

    private const val CHANNEL_ID = "nearby_sessions"
    private const val NOTIFICATION_ID = 42

    fun show(context: Context, sessions: List<Pair<String, String>>, alert: Boolean = true) {
        if (sessions.isEmpty()) {
            cancel(context)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannel(context)

        val title = if (sessions.size == 1) {
            sessions.first().second
        } else {
            "${sessions.size} sessions near you"
        }
        val text = if (sessions.size == 1) {
            "You're within 2 km of this session."
        } else {
            sessions.take(3).joinToString { it.second }
        }

        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_NEARBY, true)
            if (sessions.size == 1) putExtra(MainActivity.EXTRA_NEARBY_SESSION_ID, sessions.first().first)
        }
        val pending = PendingIntent.getActivity(
            context,
            0,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_nearby)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setSilent(!alert)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Nearby sessions",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "When you are within 2 km of a public in-person session"
        }
        manager.createNotificationChannel(channel)
    }
}
