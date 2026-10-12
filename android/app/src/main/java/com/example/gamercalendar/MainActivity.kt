package com.example.gamercalendar

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gamercalendar.data.api.ApiClient
import com.example.gamercalendar.data.repository.AuthRepository
import com.example.gamercalendar.data.session.SessionManager
import com.example.gamercalendar.ui.components.app.AppScaffold
import com.example.gamercalendar.ui.components.layout.ScreenContainer
import com.example.gamercalendar.ui.navigation.AppNavigation
import com.example.gamercalendar.ui.feature.auth.AuthScreen
import com.example.gamercalendar.ui.theme.GamerCalendarTheme
import com.example.gamercalendar.geofence.NearbyOpenEvents
import com.example.gamercalendar.geofence.SessionGeofences
import com.example.gamercalendar.ui.feature.auth.AuthViewModel
import com.example.gamercalendar.util.SteamLinkEvent
import com.example.gamercalendar.util.SteamLinkEvents
import com.google.android.libraries.places.api.Places
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            val token = FirebaseMessaging.getInstance().token.await()
            Log.d("FCM", "token: $token")
        }

        // TODO: REMOVE LAN PERMISSIONS WHEN WE DEPLOY
        if (
            Build.VERSION.SDK_INT >= 37 &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_LOCAL_NETWORK
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_LOCAL_NETWORK),
                1001
            )
        }
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_CALENDAR
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_CALENDAR),
                1001
            )
        }

        handleSteamDeepLink(intent)
        handleNearbyIntent(intent)
        SessionGeofences.install(applicationContext)

        if (BuildConfig.GOOGLE_MAPS_API_KEY.isNotBlank() && !Places.isInitialized()) {
            Places.initializeWithNewPlacesApiEnabled(applicationContext, BuildConfig.GOOGLE_MAPS_API_KEY)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
        }

        val sessionManager = SessionManager(applicationContext)
        ApiClient.init(sessionManager)
        val authRepository = AuthRepository(sessionManager)
        val authFactory = AuthViewModel.Factory(sessionManager, authRepository)

        setContent {
            GamerCalendarTheme {
                val authViewModel: AuthViewModel = viewModel(factory = authFactory)
                val uiState by authViewModel.uiState.collectAsStateWithLifecycle()
                val token by authViewModel.accessToken.collectAsStateWithLifecycle()

                when {
                    uiState.isCheckingSession -> {
                        AppScaffold { innerPadding ->
                            ScreenContainer(
                                modifier = Modifier.padding(innerPadding)
                            ) {
                                Text("Loading…")
                            }
                        }
                    }
                    !token.isNullOrBlank() -> {
                        AppNavigation(authViewModel = authViewModel)
                    }
                    else -> {
                        AuthScreen(authViewModel = authViewModel)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSteamDeepLink(intent)
        handleNearbyIntent(intent)
    }

    private fun handleNearbyIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_NEARBY, false) != true) return
        NearbyOpenEvents.emit(intent.getStringExtra(EXTRA_NEARBY_SESSION_ID))
        intent.removeExtra(EXTRA_OPEN_NEARBY)
    }

    companion object {
        const val EXTRA_OPEN_NEARBY = "open_nearby"
        const val EXTRA_NEARBY_SESSION_ID = "nearby_session_id"
    }

    private fun handleSteamDeepLink(intent: Intent?) {
        val data: Uri = intent?.data ?: return
        if (data.scheme != "gamercalendar" || data.host != "steam") return

        when (data.path?.trim('/')) {
            "linked" -> SteamLinkEvents.emit(SteamLinkEvent.Success)
            "error" -> SteamLinkEvents.emit(
                SteamLinkEvent.Error(data.getQueryParameter("reason"))
            )
        }
    }
}