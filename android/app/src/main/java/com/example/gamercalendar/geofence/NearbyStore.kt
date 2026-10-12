package com.example.gamercalendar.geofence

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.nearbyDataStore: DataStore<Preferences> by preferencesDataStore(name = "nearby")

/**
 * On-device record of nearby-session watching: whether it is on, which circles the phone is
 * inside, and which of those have already posted a notification.
 */
class NearbyStore(private val context: Context) {

    val enabled: Flow<Boolean> = context.nearbyDataStore.data.map { prefs ->
        prefs[enabledKey] == true
    }

    val insideIds: Flow<Set<String>> = context.nearbyDataStore.data.map { prefs ->
        decodeIds(prefs[insideKey])
    }

    suspend fun isEnabled(): Boolean = context.nearbyDataStore.data.first()[enabledKey] == true

    suspend fun setEnabled(enabled: Boolean) {
        context.nearbyDataStore.edit { prefs -> prefs[enabledKey] = enabled }
    }

    suspend fun insideIds(): Set<String> = decodeIds(context.nearbyDataStore.data.first()[insideKey])

    suspend fun notifiedIds(): Set<String> = decodeIds(context.nearbyDataStore.data.first()[notifiedKey])

    suspend fun addInside(ids: Set<String>) = updateIds(insideKey) { it + ids }

    suspend fun removeInside(ids: Set<String>) = updateIds(insideKey) { it - ids }

    /** Drops circles we are no longer watching, so a session that fell out of the list leaves the home screen. */
    suspend fun retainInside(ids: Set<String>) = updateIds(insideKey) { it.intersect(ids) }

    suspend fun markNotified(ids: Set<String>) = updateIds(notifiedKey) { it + ids }

    suspend fun unmarkNotified(ids: Set<String>) = updateIds(notifiedKey) { it - ids }

    /** Forgets alerts for sessions we stopped watching, so entering one of them later can alert again. */
    suspend fun retainNotified(ids: Set<String>) = updateIds(notifiedKey) { it.intersect(ids) }

    suspend fun setLabels(labels: Map<String, String>) {
        val json = JSONObject()
        labels.forEach { (id, title) -> json.put(id, title) }
        context.nearbyDataStore.edit { prefs -> prefs[labelsKey] = json.toString() }
    }

    suspend fun labelsFor(ids: Set<String>): List<Pair<String, String>> {
        val raw = context.nearbyDataStore.data.first()[labelsKey]
        val json = raw?.let { JSONObject(it) } ?: JSONObject()
        return ids.map { id -> id to (json.optString(id).ifBlank { "A session near you" }) }
    }

    /** The fix the circles were last registered around, so a small move does not replace them. */
    suspend fun anchor(): Pair<Double, Double>? {
        val prefs = context.nearbyDataStore.data.first()
        val lat = prefs[anchorLatKey] ?: return null
        val lng = prefs[anchorLngKey] ?: return null
        return lat to lng
    }

    suspend fun setAnchor(latitude: Double, longitude: Double) {
        context.nearbyDataStore.edit { prefs ->
            prefs[anchorLatKey] = latitude
            prefs[anchorLngKey] = longitude
        }
    }

    suspend fun clearAnchor() {
        context.nearbyDataStore.edit { prefs ->
            prefs.remove(anchorLatKey)
            prefs.remove(anchorLngKey)
        }
    }

    suspend fun clearInside() {
        context.nearbyDataStore.edit { prefs ->
            prefs.remove(insideKey)
            prefs.remove(notifiedKey)
            prefs.remove(labelsKey)
            prefs.remove(anchorLatKey)
            prefs.remove(anchorLngKey)
        }
    }

    private suspend fun updateIds(key: Preferences.Key<String>, transform: (Set<String>) -> Set<String>) {
        context.nearbyDataStore.edit { prefs ->
            prefs[key] = encodeIds(transform(decodeIds(prefs[key])))
        }
    }

    private fun decodeIds(raw: String?): Set<String> {
        if (raw.isNullOrBlank()) return emptySet()
        val array = JSONArray(raw)
        return buildSet {
            for (index in 0 until array.length()) add(array.getString(index))
        }
    }

    private fun encodeIds(ids: Set<String>): String = JSONArray(ids.toList()).toString()

    private companion object {
        val enabledKey = booleanPreferencesKey("enabled")
        val insideKey = stringPreferencesKey("inside")
        val notifiedKey = stringPreferencesKey("notified")
        val labelsKey = stringPreferencesKey("labels")
        val anchorLatKey = doublePreferencesKey("anchor_lat")
        val anchorLngKey = doublePreferencesKey("anchor_lng")
    }
}
