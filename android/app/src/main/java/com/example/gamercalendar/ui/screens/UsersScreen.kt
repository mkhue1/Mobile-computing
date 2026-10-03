package com.example.gamercalendar.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gamercalendar.ui.components.feedback.ErrorText
import com.example.gamercalendar.ui.components.feedback.LoadingIndicator
import com.example.gamercalendar.util.openCustomTab
import com.example.gamercalendar.viewmodel.AuthViewModel
import com.example.gamercalendar.viewmodel.SteamViewModel
import com.example.gamercalendar.viewmodel.UserViewModel

@Composable
fun UsersScreen(
    authViewModel: AuthViewModel,
    userViewModel: UserViewModel = viewModel(),
    steamViewModel: SteamViewModel = viewModel()
) {
    val users by userViewModel.users.collectAsState()
    val error by userViewModel.error.collectAsState()
    val username by authViewModel.username.collectAsState()
    val email by authViewModel.email.collectAsState()
    val steamState by steamViewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        steamViewModel.refresh()
    }

    LaunchedEffect(steamState.authUrl) {
        val url = steamState.authUrl ?: return@LaunchedEffect
        openCustomTab(context, url)
        steamViewModel.consumeAuthUrl()
    }

    Column(
        modifier = Modifier.padding(16.dp)
    ) {
        Text("Logged in as: ${username.orEmpty()}")
        Text(email.orEmpty())

        Button(
            onClick = { authViewModel.logout() },
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text("Log out")
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Steam account",
            style = MaterialTheme.typography.titleMedium
        )

        when {
            steamState.isLoading -> LoadingIndicator()
            steamState.linked -> {
                Text("Connected${steamState.steamId?.let { " ($it)" } ?: ""}")
                TextButton(onClick = steamViewModel::disconnectSteam) {
                    Text("Disconnect Steam")
                }
            }
            else -> {
                Text("Not connected")
                Button(
                    onClick = steamViewModel::connectSteam,
                    enabled = !steamState.isConnecting,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(if (steamState.isConnecting) "Connecting…" else "Connect Steam")
                }
            }
        }

        steamState.message?.let { Text(it) }
        steamState.error?.let { ErrorText(text = it) }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))

        Text("More options")
        TextButton(onClick = { /* reserved for future settings */ }) {
            Text("Settings (coming soon)")
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                userViewModel.loadUsers()
            }
        ) {
            Text("Load users")
        }

        error?.let {
            Text("Error: $it")
        }

        users.forEach { user ->
            Text(
                text = "${user.username} - ${user.email}"
            )
        }
    }
}
