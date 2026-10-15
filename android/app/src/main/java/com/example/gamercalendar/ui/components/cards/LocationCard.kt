package com.example.gamercalendar.ui.components.cards

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.gamercalendar.R
import com.example.gamercalendar.data.model.SessionPlace
import com.example.gamercalendar.ui.theme.BackgroundDark
import com.example.gamercalendar.util.openInGoogleMaps
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import kotlinx.coroutines.delay

private const val MAP_ZOOM = 15f

private const val MAP_COVER_TIMEOUT_MS = 3000L

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
    val context = LocalContext.current
    val position = LatLng(place.lat, place.lng)
    val camera = remember(position) { CameraPosition.fromLatLngZoom(position, MAP_ZOOM) }
    val cameraPositionState = remember(position) { CameraPositionState(camera) }
    val markerState = remember(position) { MarkerState(position) }
    val mapStyle = remember {
        MapStyleOptions.loadRawResourceStyle(context, R.raw.roundtablemaptheme)
    }
    var loaded by remember(position) { mutableStateOf(false) }
    // onMapLoaded may never come (e.g. offline), so don't leave the cover up forever.
    LaunchedEffect(position) {
        delay(MAP_COVER_TIMEOUT_MS)
        loaded = true
    }
    val coverAlpha by animateFloatAsState(
        targetValue = if (loaded) 0f else 1f,
        animationSpec = tween(200),
        label = "mapCover"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
    ) {
        // Lite mode renders a static image, so the map doesn't fight the screen's scrolling.
        // The camera goes in the options too, so the first frame is already at the session.
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            googleMapOptionsFactory = {
                GoogleMapOptions()
                    .liteMode(true)
                    .camera(camera)
                    .backgroundColor(BackgroundDark.toArgb())
            },
            properties = MapProperties(mapStyleOptions = mapStyle),
            uiSettings = MapUiSettings(mapToolbarEnabled = false, zoomControlsEnabled = false),
            onMapLoaded = { loaded = true },
            onMapClick = { onClick() }
        ) {
            Marker(state = markerState, title = place.name)
        }

        // Hides the map's blank and unstyled frames until it has finished drawing.
        if (coverAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(coverAlpha)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
