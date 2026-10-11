package com.example.gamercalendar.ui.feature.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gamercalendar.R
import com.example.gamercalendar.data.model.place
import com.example.gamercalendar.ui.components.cards.SessionCard
import com.example.gamercalendar.ui.theme.BackgroundDark
import com.example.gamercalendar.util.SessionTime
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.CameraMoveStartedReason
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState

private val DefaultCamera = LatLng(-37.8136, 144.9631)
private const val DEFAULT_ZOOM = 12f
private const val SINGLE_SESSION_ZOOM = 14f
private const val TRACKING_ZOOM = 15f
private const val BOUNDS_PADDING_PX = 120
private val locationPermissions = arrayOf(
    Manifest.permission.ACCESS_COARSE_LOCATION,
    Manifest.permission.ACCESS_FINE_LOCATION
)

/**
 * Full-screen map in-person sessions, colored to match the app theme.
 */
@Composable
fun SessionsMapScreen(
    onSessionClick: (sessionId: String) -> Unit,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: SessionsMapViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val mapStyle = remember {
        MapStyleOptions.loadRawResourceStyle(context, R.raw.roundtablemaptheme)
    }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(DefaultCamera, DEFAULT_ZOOM)
    }
    var mapReady by remember { mutableStateOf(false) }
    var selectedSessionId by remember { mutableStateOf<String?>(null) }
    val selectedSession = uiState.sessions.find { it.id == selectedSessionId }
    var hasLocationPermission by remember { mutableStateOf(context.hasLocationPermission()) }
    var followPhone by remember { mutableStateOf(true) }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        hasLocationPermission = granted.any { it.value }
        if (!hasLocationPermission) followPhone = false
    }

    val density = context.resources.displayMetrics.density
    val fallbackBitmap = remember(density) { sessionMarkerBitmap(cover = null, density = density) }
    val fallbackIcon = remember(mapReady, fallbackBitmap) {
        if (!mapReady) null else BitmapDescriptorFactory.fromBitmap(fallbackBitmap)
    }
    val markerIcons = remember(mapReady, uiState.markerBitmaps) {
        if (!mapReady) {
            emptyMap()
        } else {
            uiState.markerBitmaps.mapValues { (_, bitmap) ->
                BitmapDescriptorFactory.fromBitmap(bitmap)
            }
        }
    }

    val positions = uiState.sessions.mapNotNull { session ->
        session.place?.let { LatLng(it.lat, it.lng) }
    }.distinct()

    LaunchedEffect(Unit) {
        viewModel.load()
        if (!hasLocationPermission) {
            locationPermissionLauncher.launch(locationPermissions)
        }
    }

    // A drag or pinch means the user is looking around, so stop following the phone.
    LaunchedEffect(cameraPositionState.isMoving) {
        if (
            cameraPositionState.isMoving &&
            cameraPositionState.cameraMoveStartedReason == CameraMoveStartedReason.GESTURE
        ) {
            followPhone = false
        }
    }

    DisposableEffect(hasLocationPermission, followPhone) {
        val stopTracking = if (hasLocationPermission && followPhone) {
            context.trackPhone(cameraPositionState)
        } else {
            ({})
        }
        onDispose(stopTracking)
    }

    // Without a location fix, frame the sessions instead of the phone.
    LaunchedEffect(mapReady, positions, hasLocationPermission) {
        if (!mapReady || hasLocationPermission || positions.isEmpty()) return@LaunchedEffect
        val update = if (positions.size == 1) {
            CameraUpdateFactory.newLatLngZoom(positions.first(), SINGLE_SESSION_ZOOM)
        } else {
            val bounds = LatLngBounds.builder().apply {
                positions.forEach { include(it) }
            }.build()
            CameraUpdateFactory.newLatLngBounds(bounds, BOUNDS_PADDING_PX)
        }
        cameraPositionState.animate(update)
    }

    Box(modifier = modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            contentDescription = "Map of session locations",
            googleMapOptionsFactory = {
                GoogleMapOptions().backgroundColor(BackgroundDark.toArgb())
            },
            properties = MapProperties(
                mapStyleOptions = mapStyle,
                isMyLocationEnabled = hasLocationPermission
            ),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                mapToolbarEnabled = false,
                myLocationButtonEnabled = false
            ),
            onMapClick = { selectedSessionId = null },
            onMapLoaded = { mapReady = true }
        ) {
            val icon = fallbackIcon ?: return@GoogleMap
            uiState.sessions.forEach { session ->
                val place = session.place ?: return@forEach
                val label = session.title ?: session.game.name
                Marker(
                    state = rememberMarkerState(
                        key = session.id,
                        position = LatLng(place.lat, place.lng)
                    ),
                    icon = markerIcons[session.id] ?: icon,
                    anchor = Offset(0.5f, 1f),
                    title = label,
                    contentDescription = label,
                    onClick = {
                        selectedSessionId = session.id
                        true
                    }
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (onBack != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                ) {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                }
            }

            if (uiState.isLoading) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            uiState.error?.let { message ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                ) {
                    Text(
                        text = message,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (!uiState.isLoading && uiState.error == null && uiState.sessions.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                ) {
                    Text(
                        text = "No public in-person sessions",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        ) {
            IconButton(
                onClick = {
                    if (hasLocationPermission) {
                        followPhone = true
                    } else {
                        locationPermissionLauncher.launch(locationPermissions)
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.MyLocation,
                    contentDescription = "Track my location",
                    tint = if (followPhone && hasLocationPermission) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }

        selectedSession?.let { session ->
            val start = SessionTime.parseIso(session.start_at)
            val end = SessionTime.parseIso(session.end_at)
            if (start != null && end != null) {
                SessionCard(
                    session = session,
                    startEpochMillis = start,
                    endEpochMillis = end,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 12.dp, end = 12.dp, bottom = 40.dp),
                    onClick = { onSessionClick(session.id) }
                )
            }
        }
    }
}

private fun Context.hasLocationPermission(): Boolean {
    return locationPermissions.any { permission ->
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }
}

/** Follows the phone until the returned function is called. Requires a location permission. */
@SuppressLint("MissingPermission")
private fun Context.trackPhone(cameraPositionState: CameraPositionState): () -> Unit {
    val client = LocationServices.getFusedLocationProviderClient(this)
    var active = true
    val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            moveCamera(cameraPositionState, result.lastLocation)
        }
    }
    val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3_000L)
        .setMinUpdateIntervalMillis(1_000L)
        .setMinUpdateDistanceMeters(5f)
        .build()

    client.requestLocationUpdates(request, callback, Looper.getMainLooper())
    client.lastLocation.addOnSuccessListener { location ->
        if (active) moveCamera(cameraPositionState, location)
    }

    return {
        active = false
        client.removeLocationUpdates(callback)
    }
}

private fun moveCamera(cameraPositionState: CameraPositionState, location: android.location.Location?) {
    if (location == null) return
    cameraPositionState.position = CameraPosition.fromLatLngZoom(
        LatLng(location.latitude, location.longitude),
        TRACKING_ZOOM
    )
}
