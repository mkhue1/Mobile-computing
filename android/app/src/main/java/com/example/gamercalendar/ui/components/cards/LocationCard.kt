package com.example.gamercalendar.ui.components.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.gamercalendar.data.model.SessionPlace
import com.example.gamercalendar.util.openInGoogleMaps
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState

private const val MAP_ZOOM = 15f

/**
 * Card showing a session's location in full, for in-person sessions.
 * Shows a map when the location is a Google Maps place, and opens Google Maps when tapped.
 */
@Composable
fun LocationCard(
    name: String,
    place: SessionPlace?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val openInMaps = { context.openInGoogleMaps(name, place) }

    AppCard(
        modifier = modifier,
        onClick = openInMaps,
        contentPadding = PaddingValues(0.dp)
    ) {
        if (place != null) {
            PlaceMap(place = place, onClick = openInMaps)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Place,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                place?.address?.let { address ->
                    Text(
                        text = address,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = "Open in Google Maps",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PlaceMap(place: SessionPlace, onClick: () -> Unit) {
    val position = LatLng(place.lat, place.lng)
    val cameraPositionState = remember(position) {
        CameraPositionState(CameraPosition.fromLatLngZoom(position, MAP_ZOOM))
    }
    val markerState = remember(position) { MarkerState(position) }

    // Lite mode renders a static image, so the map doesn't fight the screen's scrolling.
    GoogleMap(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
        cameraPositionState = cameraPositionState,
        googleMapOptionsFactory = { GoogleMapOptions().liteMode(true) },
        uiSettings = MapUiSettings(mapToolbarEnabled = false, zoomControlsEnabled = false),
        onMapClick = { onClick() }
    ) {
        Marker(state = markerState, title = place.name)
    }
}
