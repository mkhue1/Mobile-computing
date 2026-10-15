package com.example.gamercalendar.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.gamercalendar.data.model.SessionPlace

/**
 * Opens a location in the Google Maps app, or the browser if it isn't installed.
 * Locations without a place are searched for by name.
 */
fun Context.openInGoogleMaps(name: String, place: SessionPlace?) {
    val uri = Uri.parse("https://www.google.com/maps/search/").buildUpon()
        .appendQueryParameter("api", "1")
        .appendQueryParameter("query", place?.let { "${it.lat},${it.lng}" } ?: name)
        .apply { place?.placeId?.let { appendQueryParameter("query_place_id", it) } }
        .build()

    try {
        startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, "No app available to open maps", Toast.LENGTH_SHORT).show()
    }
}
