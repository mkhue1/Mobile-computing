package com.example.gamercalendar.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Play Services starts this when the phone enters or leaves a session circle.
 * Several circles in one event become one notification.
 */
class SessionGeofenceReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                handle(app, intent)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return
        val ids = event.triggeringGeofences?.map { it.requestId }?.toSet().orEmpty()
        if (ids.isEmpty()) return

        val store = NearbyStore(context)
        when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> {
                val fresh = ids - store.notifiedIds()
                store.addInside(ids)
                if (fresh.isEmpty()) return
                store.markNotified(fresh)
                NearbyNotifications.show(context, store.labelsFor(store.insideIds()))
            }

            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                store.removeInside(ids)
                store.unmarkNotified(ids)
                val remaining = store.insideIds()
                if (remaining.isEmpty()) NearbyNotifications.cancel(context)
                else NearbyNotifications.show(context, store.labelsFor(remaining), alert = false)
            }
        }
    }
}
