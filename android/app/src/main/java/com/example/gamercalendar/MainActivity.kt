package com.example.gamercalendar

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gamercalendar.data.api.ApiClient
import com.example.gamercalendar.data.repository.AuthRepository
import com.example.gamercalendar.data.session.SessionManager
import com.example.gamercalendar.ui.screens.AuthScreen
import com.example.gamercalendar.ui.screens.UsersScreen
import com.example.gamercalendar.ui.theme.GamerCalendarTheme
import com.example.gamercalendar.viewmodel.AuthViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        val sessionManager = SessionManager(applicationContext)
        ApiClient.init(sessionManager)
        val authRepository = AuthRepository(sessionManager)
        val authFactory = AuthViewModel.Factory(sessionManager, authRepository)

        setContent {
            GamerCalendarTheme {
                val authViewModel: AuthViewModel = viewModel(factory = authFactory)
                val uiState by authViewModel.uiState.collectAsState()
                val token by authViewModel.accessToken.collectAsState()

                when {
                    uiState.isCheckingSession -> {
                        Text(
                            text = "Loading…",
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    !token.isNullOrBlank() -> {
                        UsersScreen(authViewModel = authViewModel)
                    }
                    else -> {
                        AuthScreen(authViewModel = authViewModel)
                    }
                }
            }
        }
    }
}
