package com.example.gamercalendar.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gamercalendar.data.model.User
import com.example.gamercalendar.ui.components.cards.AppCard
import com.example.gamercalendar.ui.components.buttons.SecondaryButton
import com.example.gamercalendar.ui.components.feedback.EmptyState
import com.example.gamercalendar.ui.components.feedback.ErrorText
import com.example.gamercalendar.ui.components.feedback.LoadingIndicator
import com.example.gamercalendar.ui.components.inputs.DefaultTextField
import com.example.gamercalendar.ui.components.labels.Tag
import com.example.gamercalendar.ui.components.layout.ScreenContainer
import com.example.gamercalendar.viewmodel.AddFriendViewModel
import com.example.gamercalendar.viewmodel.AddFriendViewModel.Companion.MIN_QUERY_LENGTH

@Composable
fun AddFriendScreen(
    onDone: () -> Unit,
    onOpenProfile: () -> Unit = {},
    viewModel: AddFriendViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    ScreenContainer(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Add friend",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = "Suggested from Steam",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        when {
            uiState.isLoading -> LoadingIndicator()
            !uiState.steamLinked -> {
                EmptyState(
                    title = "Connect Steam to get suggestions",
                    message = "Link your Steam account from your profile to see friends already on Roundtable."
                )
                TextButton(onClick = onOpenProfile) {
                    Text("Open profile")
                }
            }
            uiState.isLoadingSteamSuggestions -> LoadingIndicator()
            uiState.steamSuggestions.isNotEmpty() -> {
                uiState.steamSuggestions.forEach { user ->
                    AddFriendRow(
                        user = user,
                        isPendingFromThem = user.id in uiState.pendingFromUserIds,
                        isSent = user.id in uiState.sentTo,
                        onAddClick = { viewModel.sendRequest(user) }
                    )
                }
            }
            else -> {
                EmptyState(
                    title = "No Steam suggestions",
                    message = uiState.steamSuggestionsMessage
                        ?: "None of your Steam friends have linked their account yet."
                )
            }
        }

        Text(
            text = "Search by username",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        DefaultTextField(
            value = uiState.query,
            onValueChange = viewModel::onQueryChange,
            label = "Search by username"
        )

        uiState.error?.let { ErrorText(text = it) }

        when {
            uiState.isLoading && uiState.results.isEmpty() -> Unit

            uiState.query.trim().length < MIN_QUERY_LENGTH -> {
                if (uiState.error == null) {
                    EmptyState(
                        title = "Search by username to find friends",
                        message = "Keep typing to see matching users."
                    )
                }
            }

            uiState.isSearching -> LoadingIndicator()

            uiState.results.isEmpty() -> {
                if (uiState.error == null) {
                    EmptyState(
                        title = "No users found",
                        message = "Try a different username."
                    )
                }
            }

            else -> uiState.results.forEach { user ->
                AddFriendRow(
                    user = user,
                    isPendingFromThem = user.id in uiState.pendingFromUserIds,
                    isSent = user.id in uiState.sentTo,
                    onAddClick = { viewModel.sendRequest(user) }
                )
            }
        }

        SecondaryButton(text = "Done", onClick = onDone)
    }
}

@Composable
private fun AddFriendRow(
    user: User,
    isPendingFromThem: Boolean,
    isSent: Boolean,
    onAddClick: () -> Unit
) {
    AppCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = user.username,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            when {
                isPendingFromThem -> Tag(text = "Respond in Requests")
                isSent -> Tag(text = "Requested")
                else -> TextButton(onClick = onAddClick) { Text(text = "Add") }
            }
        }
    }
}
