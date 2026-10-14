package com.example.gamercalendar.ui.feature.friends

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.example.gamercalendar.ui.components.labels.UserAvatar
import com.example.gamercalendar.ui.components.layout.ScreenContainer

private val TAB_TITLES = listOf("Friends", "Requests", "Groups")

private const val REQUESTS_TAB = 1

/** A rounded segmented switcher; the selected tab is filled purple, and Requests shows a count badge. */
@Composable
private fun HubTabs(
    selected: Int,
    onSelect: (Int) -> Unit,
    requestCount: Int
) {
    val shape = RoundedCornerShape(12.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), shape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        TAB_TITLES.forEachIndexed { index, title ->
            val isSelected = index == selected
            val background by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                label = "tabBackground"
            )
            val content by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "tabContent"
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(background)
                    .selectable(
                        selected = isSelected,
                        onClick = { onSelect(index) },
                        role = Role.Tab
                    )
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = content
                )
                if (index == REQUESTS_TAB && requestCount > 0) {
                    Spacer(Modifier.width(6.dp))
                    CountBadge(count = requestCount, onSelectedTab = isSelected)
                }
            }
        }
    }
}

@Composable
private fun CountBadge(count: Int, onSelectedTab: Boolean) {
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
            .clip(CircleShape)
            .background(
                if (onSelectedTab) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.primary
            )
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (onSelectedTab) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onPrimary
        )
    }
}

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
                text = "Friends & Groups",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            HubTabs(
                selected = selectedTab,
                onSelect = { selectedTab = it },
                requestCount = uiState.incomingRequests.size
            )

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
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UserAvatar(user = friend)

                Text(
                    text = friend.username,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

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
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UserAvatar(user = requestUi.otherUser)

                    Text(
                        text = requestUi.otherUser?.username ?: "Unknown user",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row {
                    TextButton(onClick = onAccept) {
                        Text(text = "Accept", color = MaterialTheme.colorScheme.primary)
                    }
                    TextButton(onClick = onDecline) {
                        Text(text = "Decline", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            SteamRelationHint(
                relation = requestUi.request.steam_relation,
                personaName = requestUi.request.steam_persona_name
            )
        }
    }
}

@Composable
private fun SteamRelationHint(
    relation: String?,
    personaName: String?
) {
    val (label, tagColor) = when (relation) {
        "mutual" -> {
            val text = if (!personaName.isNullOrBlank()) {
                "Steam friend · $personaName"
            } else {
                "Steam friend"
            }
            text to MaterialTheme.colorScheme.primary
        }
        "not_friends" -> "Not a Steam friend" to MaterialTheme.colorScheme.onSurfaceVariant
        else -> return
    }

    Tag(text = label, color = tagColor)
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
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UserAvatar(user = requestUi.otherUser)

                Text(
                    text = requestUi.otherUser?.username ?: "Unknown user",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

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