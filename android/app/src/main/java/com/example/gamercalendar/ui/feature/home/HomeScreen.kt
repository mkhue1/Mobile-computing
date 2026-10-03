package com.example.gamercalendar.ui.feature.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gamercalendar.ui.components.buttons.DefaultButton
import com.example.gamercalendar.ui.components.cards.SessionCard
import com.example.gamercalendar.ui.components.feedback.EmptyState
import com.example.gamercalendar.ui.components.feedback.ErrorText
import com.example.gamercalendar.ui.components.feedback.LoadingIndicator
import com.example.gamercalendar.ui.components.layout.ScreenContainer
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onCreateSessionClick: () -> Unit,
    onSessionClick: (sessionId: String) -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Runs each time Home is shown, so returning from Create session refreshes the list.
    LaunchedEffect(Unit) {
        viewModel.loadSessions()
    }

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize()
    ) {
        // fillMaxSize before verticalScroll so the whole screen, not just the content, can be pulled.
        ScreenContainer(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Home",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            DefaultButton(
                text = "Create session",
                onClick = onCreateSessionClick
            )

            if (uiState.invites.isNotEmpty()) {
                Text(
                    text = "Session invites",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )

                uiState.invites.forEach { invite ->
                    SessionCard(
                        session = invite.item.session,
                        startEpochMillis = invite.item.startEpochMillis,
                        endEpochMillis = invite.item.endEpochMillis,
                        onClick = { onSessionClick(invite.item.session.id) }
                    )
                }
            }

            Text(
                text = "Upcoming sessions",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )

            uiState.error?.let {
                ErrorText(text = it)
            }

            when {
                uiState.isLoading && uiState.sessions.isEmpty() -> LoadingIndicator()

                uiState.sessions.isEmpty() -> {
                    if (uiState.error == null) {
                        EmptyState(
                            title = "No upcoming sessions",
                            message = "Sessions you organise or join will show up here."
                        )
                    }
                }

                else -> uiState.sessions.forEach { item ->
                    SessionCard(
                        session = item.session,
                        startEpochMillis = item.startEpochMillis,
                        endEpochMillis = item.endEpochMillis,
                        onClick = { onSessionClick(item.session.id) }
                    )
                }
            }
        }
    }
}