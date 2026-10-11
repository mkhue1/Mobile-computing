package com.example.gamercalendar.geofence

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.gamercalendar.data.api.ApiClient
import com.example.gamercalendar.data.model.SessionStatus
import com.example.gamercalendar.data.model.place
import com.example.gamercalendar.data.repository.SessionRepository
import com.example.gamercalendar.data.session.SessionManager
import com.example.gamercalendar.util.SessionTime
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private const val RADIUS_METERS = 2000f
private const val MAX_GEOFENCES = 100
private const val WATCH_RADIUS_KM = 30.0
private const val WITHIN_DAYS = 7
private const val MOVE_REFRESH_METERS = 10_000.0
private const val FRESH_LOCATION_MS = 5 * 60 * 1000L

/**
 * Registers a 2 km circle around each public in-person session starting this week
 * within 30 km. Play Services watches the circles. This process does not keep a location trail.
 */
object SessionGeofences {

    private var appContext: Context? = null

    fun install(context: Context) {
        appContext = context.applicationContext
    }

    /** Used on logout, when the activity context may already be the application context. */
    suspend fun disable() {
        appContext?.let { disable(it) }
    }

    suspend fun disable(context: Context) {
        val app = context.applicationContext
        val store = NearbyStore(app)
        store.setEnabled(false)
        store.clearInside()
        NearbyNotifications.cancel(app)
        removeAll(app)
    }

    /**
     * Registers again only after the phone has moved about 10 km from the last fix.
     * Opening the app without that move leaves the circles already being watched.
     */
    suspend fun refreshIfEnabled(context: Context) {
        val app = context.applicationContext
        val store = NearbyStore(app)
        if (!store.isEnabled() || !hasFineLocation(app)) return
        val location = currentLocation(app) ?: return
        val anchor = store.anchor()
        if (anchor != null &&
            distanceMeters(anchor.first, anchor.second, location.latitude, location.longitude) < MOVE_REFRESH_METERS
        ) {
            return
        }
        refresh(app, location)
    }

    /**
     * Replaces the watched circles with sessions starting this week within 30 km.
     * Returns false when the phone's location could not be read.
     */
    @SuppressLint("MissingPermission")
    suspend fun refresh(context: Context, location: android.location.Location? = null): Boolean {
        val app = context.applicationContext
        if (!hasFineLocation(app)) return false

        val fix = location ?: currentLocation(app) ?: return false
        val sessions = SessionRepository().getSessionsNear(fix.latitude, fix.longitude, WATCH_RADIUS_KM, WITHIN_DAYS)
        val now = System.currentTimeMillis()

        val nearest = sessions.mapNotNull { session ->
            val place = session.place ?: return@mapNotNull null
            if (session.status != SessionStatus.OPEN) return@mapNotNull null
            val end = SessionTime.parseIso(session.end_at) ?: return@mapNotNull null
            val remaining = end - now
            if (remaining <= 0) return@mapNotNull null
            NearbyCandidate(
                id = session.id,
                title = session.title ?: session.game.name,
                lat = place.lat,
                lng = place.lng,
                remainingMillis = remaining
            )
        }.take(MAX_GEOFENCES)

        val store = NearbyStore(app)
        val watchedIds = nearest.map { it.id }.toSet()
        store.setLabels(nearest.associate { it.id to it.title })
        store.retainInside(watchedIds)
        store.retainNotified(watchedIds)
        val stillInside = store.insideIds()
        if (stillInside.isEmpty()) NearbyNotifications.cancel(app)
        else NearbyNotifications.show(app, store.labelsFor(stillInside), alert = false)

        removeAll(app)
        try {
            if (nearest.isNotEmpty()) {
                val request = GeofencingRequest.Builder()
                    .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                    .addGeofences(nearest.map { candidate ->
                        Geofence.Builder()
                            .setRequestId(candidate.id)
                            .setCircularRegion(candidate.lat, candidate.lng, RADIUS_METERS)
                            .setExpirationDuration(candidate.remainingMillis)
                            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
                            .build()
                    })
                    .build()
                LocationServices.getGeofencingClient(app).addGeofences(request, pendingIntent(app)).await()
            }
        } catch (e: Exception) {
            store.clearAnchor()
            throw e
        }
        store.setAnchor(fix.latitude, fix.longitude)
        return true
    }

    /**
     * Drops circles for sessions the user has joined, so they leave Near you and stop notifying.
     * Safe to call again; sessions that are not currently inside are ignored.
     */
    suspend fun dropJoined(context: Context, joinedIds: Set<String>) {
        if (joinedIds.isEmpty()) return
        withContext(NonCancellable) {
            val app = context.applicationContext
            val store = NearbyStore(app)
            val dropped = store.insideIds().intersect(joinedIds)
            if (dropped.isEmpty()) return@withContext
            store.removeInside(dropped)
            store.unmarkNotified(dropped)
            try {
                LocationServices.getGeofencingClient(app).removeGeofences(dropped.toList()).await()
            } catch (_: Exception) {
                // The circle may already have expired.
            }
            val remaining = store.insideIds()
            if (remaining.isEmpty()) NearbyNotifications.cancel(app)
            else NearbyNotifications.show(app, store.labelsFor(remaining), alert = false)
        }
    }

    /** Boot starts a fresh process, so the API client has to be pointed at the stored login first. */
    suspend fun refreshAfterBoot(context: Context) {
        val app = context.applicationContext
        val sessionManager = SessionManager(app)
        val token = sessionManager.getToken()
        if (token.isNullOrBlank()) return
        if (!NearbyStore(app).isEnabled()) return
        ApiClient.init(sessionManager)
        install(app)
        refresh(app)
    }

    private suspend fun removeAll(context: Context) {
        try {
            LocationServices.getGeofencingClient(context)
                .removeGeofences(pendingIntent(context))
                .await()
        } catch (_: Exception) {
            // Nothing was registered yet, or location services are off.
        }
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, SessionGeofenceReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentLocation(context: Context): android.location.Location? {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val last = client.lastLocation.await()
        val fresh = last != null && System.currentTimeMillis() - last.time < FRESH_LOCATION_MS
        if (fresh) return last
        return client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await() ?: last
    }

    private fun hasFineLocation(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }
}

private data class NearbyCandidate(
    val id: String,
    val title: String,
    val lat: Double,
    val lng: Double,
    val remainingMillis: Long
)

private fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val earth = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2).pow(2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
    return 2 * earth * asin(sqrt(a))
}
