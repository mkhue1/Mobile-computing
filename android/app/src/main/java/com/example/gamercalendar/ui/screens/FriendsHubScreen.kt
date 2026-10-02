package com.example.gamercalendar.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.example.gamercalendar.data.model.UserGroup
import com.example.gamercalendar.ui.components.buttons.DefaultButton
import com.example.gamercalendar.ui.components.cards.AppCard
import com.example.gamercalendar.ui.components.dialogs.ConfirmDialog
import com.example.gamercalendar.ui.components.dialogs.GroupNameDialog
import com.example.gamercalendar.ui.components.feedback.EmptyState
import com.example.gamercalendar.ui.components.feedback.ErrorText
import com.example.gamercalendar.ui.components.feedback.LoadingIndicator
import com.example.gamercalendar.ui.components.labels.Tag
import com.example.gamercalendar.ui.components.layout.ScreenContainer
import com.example.gamercalendar.viewmodel.FriendRequestUi
import com.example.gamercalendar.viewmodel.FriendsHubViewModel

private val TAB_TITLES = listOf("Friends", "Requests", "Groups")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsHubScreen(
    onAddFriendClick: () -> Unit = {},
    onGroupClick: (String) -> Unit = {},
    viewModel: FriendsHubViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(0) }
    var pendingRemoveFriendId by remember { mutableStateOf<String?>(null) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }

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
                0 -> {
                    DefaultButton(text = "Add friend", onClick = onAddFriendClick)

                    FriendsTab(
                        friends = uiState.friends,
                        isLoading = uiState.isLoading,
                        error = uiState.error,
                        actionError = uiState.actionError,
                        onRemoveClick = { friendId -> pendingRemoveFriendId = friendId }
                    )
                }

                1 -> RequestsTab(
                    incomingRequests = uiState.incomingRequests,
                    outgoingRequests = uiState.outgoingRequests,
                    isLoading = uiState.isLoading,
                    error = uiState.error,
                    actionError = uiState.actionError,
                    onAccept = viewModel::acceptRequest,
                    onDecline = viewModel::declineRequest
                )

                else -> {
                    DefaultButton(
                        text = "Create group",
                        onClick = { showCreateGroupDialog = true },
                        enabled = !uiState.isWorking
                    )

                    GroupsTab(
                        groups = uiState.groups,
                        isLoading = uiState.isLoading,
                        error = uiState.error,
                        actionError = uiState.actionError,
                        onGroupClick = onGroupClick
                    )
                }
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

    if (showCreateGroupDialog) {
        GroupNameDialog(
            title = "Create group",
            confirmLabel = "Create",
            onConfirm = { name ->
                showCreateGroupDialog = false
                viewModel.createGroup(name)
            },
            onDismiss = { showCreateGroupDialog = false }
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
private fun RequestsTab(
    incomingRequests: List<FriendRequestUi>,
    outgoingRequests: List<FriendRequestUi>,
    isLoading: Boolean,
    error: String?,
    actionError: String?,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit
) {
    error?.let { ErrorText(text = it) }
    actionError?.let { ErrorText(text = it) }

    if (isLoading && incomingRequests.isEmpty() && outgoingRequests.isEmpty()) {
        LoadingIndicator()
        return
    }

    if (incomingRequests.isEmpty() && outgoingRequests.isEmpty()) {
        if (error == null) {
            EmptyState(
                title = "No requests",
                message = "Friend requests you send or receive will show up here."
            )
        }
        return
    }

    Text(
        text = "Incoming",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground
    )

    if (incomingRequests.isEmpty()) {
        Text(
            text = "No incoming requests",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        incomingRequests.forEach { requestUi ->
            IncomingRequestRow(
                requestUi = requestUi,
                onAccept = { onAccept(requestUi.request.id) },
                onDecline = { onDecline(requestUi.request.id) }
            )
        }
    }

    Text(
        text = "Outgoing",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground
    )

    if (outgoingRequests.isEmpty()) {
        Text(
            text = "No outgoing requests",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        outgoingRequests.forEach { requestUi ->
            OutgoingRequestRow(requestUi = requestUi)
        }
    }
}

@Composable
private fun IncomingRequestRow(
    requestUi: FriendRequestUi,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    AppCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = requestUi.otherUser?.username ?: "Unknown user",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row {
                TextButton(onClick = onAccept) {
                    Text(text = "Accept", color = MaterialTheme.colorScheme.primary)
                }
                TextButton(onClick = onDecline) {
                    Text(text = "Decline", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun OutgoingRequestRow(
    requestUi: FriendRequestUi
) {
    AppCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = requestUi.otherUser?.username ?: "Unknown user",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Tag(text = "Pending")
        }
    }
}

@Composable
private fun GroupsTab(
    groups: List<UserGroup>,
    isLoading: Boolean,
    error: String?,
    actionError: String?,
    onGroupClick: (String) -> Unit
) {
    error?.let { ErrorText(text = it) }
    actionError?.let { ErrorText(text = it) }

    when {
        isLoading && groups.isEmpty() -> LoadingIndicator()

        groups.isEmpty() -> {
            if (error == null) {
                EmptyState(
                    title = "No groups yet",
                    message = "Create a group to start scheduling sessions together."
                )
            }
        }

        else -> groups.forEach { group ->
            GroupRow(group = group, onClick = { onGroupClick(group.id) })
        }
    }
}

@Composable
private fun GroupRow(
    group: UserGroup,
    onClick: () -> Unit
) {
    AppCard(onClick = onClick) {
        Text(
            text = group.name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}