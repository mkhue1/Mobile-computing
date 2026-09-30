package com.example.gamercalendar.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gamercalendar.data.model.User
import com.example.gamercalendar.ui.components.cards.AppCard
import com.example.gamercalendar.ui.components.feedback.EmptyState
import com.example.gamercalendar.ui.components.feedback.ErrorText
import com.example.gamercalendar.ui.components.feedback.LoadingIndicator
import com.example.gamercalendar.ui.components.layout.ScreenContainer
import com.example.gamercalendar.viewmodel.FriendsHubViewModel

private val TAB_TITLES = listOf("Friends", "Requests", "Groups")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsHubScreen(
    viewModel: FriendsHubViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(0) }
    var pendingRemoveFriendId by remember { mutableStateOf<String?>(null) }

    // Runs each time the hub is shown, so leaving and coming back refreshes the list.
    LaunchedEffect(Unit) {
        viewModel.load()
    }

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize()
    ) {
        ScreenContainer(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Friends & groups",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            TabRow(selectedTabIndex = selectedTab) {
                TAB_TITLES.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            when (selectedTab) {
                0 -> FriendsTab(
                    friends = uiState.friends,
                    isLoading = uiState.isLoading,
                    error = uiState.error,
                    actionError = uiState.actionError,
                    onRemoveClick = { friendId -> pendingRemoveFriendId = friendId }
                )

                1 -> EmptyState(
                    title = "Requests coming soon",
                    message = "Friend requests will show up here."
                )

                else -> EmptyState(
                    title = "Groups coming soon",
                    message = "Your groups will show up here."
                )
            }
        }
    }

    pendingRemoveFriendId?.let { friendId ->
        ConfirmDialog(
            title = "Remove friend?",
            text = "You'll need to send a new request to be friends again.",
            confirmLabel = "Remove",
            dismissLabel = "Cancel",
            onConfirm = {
                pendingRemoveFriendId = null
                viewModel.removeFriend(friendId)
            },
            onDismiss = { pendingRemoveFriendId = null }
        )
    }
}

@Composable
private fun FriendsTab(
    friends: List<User>,
    isLoading: Boolean,
    error: String?,
    actionError: String?,
    onRemoveClick: (String) -> Unit
) {
    error?.let { ErrorText(text = it) }
    actionError?.let { ErrorText(text = it) }

    when {
        isLoading && friends.isEmpty() -> LoadingIndicator()

        friends.isEmpty() -> {
            if (error == null) {
                EmptyState(
                    title = "No friends yet",
                    message = "Add a friend to start scheduling sessions together."
                )
            }
        }

        else -> friends.forEach { friend ->
            FriendRow(friend = friend, onRemoveClick = { onRemoveClick(friend.id) })
        }
    }
}

@Composable
private fun FriendRow(
    friend: User,
    onRemoveClick: () -> Unit
) {
    AppCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = friend.username,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            TextButton(onClick = onRemoveClick) {
                Text(text = "Remove", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = confirmLabel, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissLabel)
            }
        }
    )
}
