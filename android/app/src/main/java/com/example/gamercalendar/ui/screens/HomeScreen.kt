package com.example.gamercalendar.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gamercalendar.ui.components.buttons.DefaultButton
import com.example.gamercalendar.ui.components.buttons.SecondaryButton
import com.example.gamercalendar.ui.components.cards.SessionCard
import com.example.gamercalendar.ui.components.feedback.EmptyState
import com.example.gamercalendar.ui.components.feedback.ErrorText
import com.example.gamercalendar.ui.components.feedback.LoadingIndicator
import com.example.gamercalendar.ui.components.layout.ScreenContainer
import com.example.gamercalendar.viewmodel.HomeViewModel
import com.example.gamercalendar.viewmodel.InviteListItem

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

                uiState.inviteError?.let {
                    ErrorText(text = it)
                }

                uiState.invites.forEach { invite ->
                    InviteItem(
                        invite = invite,
                        isResponding = invite.invite.id in uiState.respondingInviteIds,
                        onClick = { onSessionClick(invite.item.session.id) },
                        onAccept = { viewModel.acceptInvite(invite.invite) },
                        onDecline = { viewModel.declineInvite(invite.invite) }
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
                        gameName = item.gameName,
                        startEpochMillis = item.startEpochMillis,
                        endEpochMillis = item.endEpochMillis,
                        onClick = { onSessionClick(item.session.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun InviteItem(
    invite: InviteListItem,
    isResponding: Boolean,
    onClick: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SessionCard(
            session = invite.item.session,
            gameName = invite.item.gameName,
            startEpochMillis = invite.item.startEpochMillis,
            endEpochMillis = invite.item.endEpochMillis,
            onClick = onClick
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton(
                text = "Decline",
                onClick = onDecline,
                enabled = !isResponding,
                modifier = Modifier.weight(1f)
            )
            DefaultButton(
                text = "Accept",
                onClick = onAccept,
                enabled = !isResponding,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
