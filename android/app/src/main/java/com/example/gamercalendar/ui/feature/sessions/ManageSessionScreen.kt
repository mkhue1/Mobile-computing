package com.example.gamercalendar.ui.feature.sessions

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gamercalendar.data.model.SessionType
import com.example.gamercalendar.data.model.User
import com.example.gamercalendar.data.model.place
import com.example.gamercalendar.ui.components.buttons.DefaultButton
import com.example.gamercalendar.ui.components.buttons.DestructiveButton
import com.example.gamercalendar.ui.components.buttons.SecondaryButton
import com.example.gamercalendar.ui.components.cards.AppCard
import com.example.gamercalendar.ui.components.cards.LocationCard
import com.example.gamercalendar.ui.components.cards.SessionCard
import com.example.gamercalendar.ui.components.dialogs.ConfirmDialog
import com.example.gamercalendar.ui.components.feedback.EmptyState
import com.example.gamercalendar.ui.components.feedback.ErrorText
import com.example.gamercalendar.ui.components.feedback.LoadingIndicator
import com.example.gamercalendar.ui.components.labels.Avatar
import com.example.gamercalendar.ui.components.labels.Tag
import com.example.gamercalendar.ui.components.layout.ScreenContainer
import com.example.gamercalendar.ui.components.layout.SectionTitle
private enum class PendingConfirmation { CANCEL, LEAVE }

@Composable
fun ManageSessionScreen(
    onEditClick: (sessionId: String) -> Unit,
    onLeft: () -> Unit,
    viewModel: ManageSessionViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showInviteSheet by remember { mutableStateOf(false) }
    var pendingConfirmation by remember { mutableStateOf<PendingConfirmation?>(null) }
    var pendingRemoval by remember { mutableStateOf<User?>(null) }

    // Runs each time the screen is shown, so returning from Edit session shows the changes.
    LaunchedEffect(Unit) {
        viewModel.load()
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.messageShown()
        }
    }

    LaunchedEffect(uiState.hasLeft, uiState.hasDeclined) {
        if (uiState.hasLeft || uiState.hasDeclined) onLeft()
    }

    val session = uiState.session

    ScreenContainer(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        when {
            session == null && uiState.isLoading -> LoadingIndicator()

            session == null -> {
                ErrorText(text = uiState.error ?: "Couldn't load session")
                SecondaryButton(text = "Retry", onClick = viewModel::load)
            }

            else -> {
                Text(
                    text = when {
                        uiState.isOrganiser -> "Manage session"
                        uiState.pendingInvite != null -> "Session invite"
                        else -> "Session details"
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )

                SessionCard(
                    session = session,
                    startEpochMillis = uiState.startEpochMillis,
                    endEpochMillis = uiState.endEpochMillis
                )

                if (uiState.isCancelled) {
                    Text(
                        text = "This session has been cancelled.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                session.location_name
                    ?.takeIf { session.session_type == SessionType.IN_PERSON }
                    ?.let { location ->
                        SectionTitle(title = "Location")
                        LocationCard(name = location, place = session.place)
                    }

                session.description?.let { notes ->
                    SectionTitle(title = "Notes")
                    AppCard {
                        Text(
                            text = notes,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                SectionTitle(
                    title = "Players",
                    trailing = if (session.player_limit != null) {
                        "${session.player_count}/${session.player_limit}"
                    } else {
                        "${session.player_count}"
                    }
                )

                PlayersCard(
                    participants = uiState.participants.map { it.user },
                    organiserId = session.organiser_id,
                    currentUserId = uiState.currentUserId,
                    isLoading = uiState.isLoadingParticipants,
                    error = uiState.participantsError,
                    onRetry = viewModel::loadParticipants,
                    onRemove = if (uiState.isOrganiser && !uiState.isCancelled) {
                        { user -> pendingRemoval = user }
                    } else {
                        null
                    },
                    removeEnabled = !uiState.isWorking
                )

                if (uiState.isOrganiser && uiState.sentInvites.isNotEmpty()) {
                    SectionTitle(title = "Invited", trailing = "${uiState.sentInvites.size}")

                    AppCard {
                        uiState.sentInvites.forEachIndexed { index, invite ->
                            if (index > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                                )
                            }
                            InvitedRow(user = invite.receiver)
                        }
                    }
                }

                uiState.actionError?.let {
                    ErrorText(text = it)
                }

                if (!uiState.isCancelled) {
                    if (uiState.isOrganiser) {
                        DefaultButton(
                            text = "Invite friends",
                            onClick = {
                                viewModel.loadFriends()
                                showInviteSheet = true
                            },
                            enabled = !uiState.isWorking
                        )

                        SecondaryButton(
                            text = "Edit session",
                            onClick = { onEditClick(session.id) },
                            enabled = !uiState.isWorking
                        )

                        DestructiveButton(
                            text = "Cancel session",
                            onClick = { pendingConfirmation = PendingConfirmation.CANCEL },
                            enabled = !uiState.isWorking
                        )
                    } else if (uiState.pendingInvite != null) {
                        Text(
                            text = "You've been invited to this session.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SecondaryButton(
                                text = "Decline",
                                onClick = viewModel::declineInvite,
                                enabled = !uiState.isWorking,
                                modifier = Modifier.weight(1f)
                            )
                            DefaultButton(
                                text = "Accept",
                                onClick = viewModel::acceptInvite,
                                enabled = !uiState.isWorking,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else if (uiState.isParticipant) {
                        DestructiveButton(
                            text = "Leave session",
                            onClick = { pendingConfirmation = PendingConfirmation.LEAVE },
                            enabled = !uiState.isWorking
                        )
                    }
                }
            }
        }
    }

    if (showInviteSheet) {
        InviteFriendsSheet(
            friends = uiState.invitableFriends,
            isLoading = uiState.isLoadingFriends,
            error = uiState.friendsError,
            onRetry = viewModel::loadFriends,
            onSend = { ids ->
                viewModel.inviteFriends(ids)
                showInviteSheet = false
            },
            onDismiss = { showInviteSheet = false }
        )
    }

    when (pendingConfirmation) {
        PendingConfirmation.CANCEL -> ConfirmDialog(
            title = "Cancel session?",
            text = "Everyone in the session will see it as cancelled. This can't be undone.",
            confirmLabel = "Cancel session",
            dismissLabel = "Keep it",
            onConfirm = {
                pendingConfirmation = null
                viewModel.cancelSession()
            },
            onDismiss = { pendingConfirmation = null }
        )

        PendingConfirmation.LEAVE -> ConfirmDialog(
            title = "Leave session?",
            text = "You'll need a new invite to join again.",
            confirmLabel = "Leave",
            dismissLabel = "Stay",
            onConfirm = {
                pendingConfirmation = null
                viewModel.leaveSession()
            },
            onDismiss = { pendingConfirmation = null }
        )

        null -> Unit
    }

    pendingRemoval?.let { user ->
        ConfirmDialog(
            title = "Remove ${user.username}?",
            text = "They'll lose their spot and need a new invite to join again.",
            confirmLabel = "Remove",
            dismissLabel = "Keep",
            onConfirm = {
                pendingRemoval = null
                viewModel.removePlayer(user)
            },
            onDismiss = { pendingRemoval = null }
        )
    }
}

@Composable
private fun PlayersCard(
    participants: List<User>,
    organiserId: String,
    currentUserId: String?,
    isLoading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onRemove: ((User) -> Unit)? = null,
    removeEnabled: Boolean = true
) {
    AppCard {
        when {
            isLoading && participants.isEmpty() -> LoadingIndicator()

            error != null && participants.isEmpty() -> {
                ErrorText(text = error)
                TextButton(onClick = onRetry) {
                    Text("Retry")
                }
            }

            participants.isEmpty() -> EmptyState(
                title = "No players yet",
                message = "Invite some friends to get started."
            )

            else -> {
                val sorted = participants.sortedByDescending { it.id == organiserId }
                sorted.forEachIndexed { index, user ->
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                        )
                    }
                    val isOrganiser = user.id == organiserId
                    PlayerRow(
                        user = user,
                        isOrganiser = isOrganiser,
                        isCurrentUser = user.id == currentUserId,
                        onRemove = onRemove?.takeUnless { isOrganiser }?.let { remove -> { remove(user) } },
                        removeEnabled = removeEnabled
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerRow(
    user: User,
    isOrganiser: Boolean,
    isCurrentUser: Boolean,
    onRemove: (() -> Unit)? = null,
    removeEnabled: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(name = user.username)

        Text(
            text = user.username,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        if (isCurrentUser) {
            Tag(text = "You", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (isOrganiser) {
            Tag(text = "Organiser")
        }
        if (onRemove != null) {
            IconButton(
                onClick = onRemove,
                enabled = removeEnabled,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove ${user.username}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun InvitedRow(user: User) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(name = user.username)

        Text(
            text = user.username,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        Tag(text = "Pending", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InviteFriendsSheet(
    friends: List<User>,
    isLoading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onSend: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Invite friends",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            when {
                isLoading && friends.isEmpty() -> LoadingIndicator()

                error != null -> {
                    ErrorText(text = error)
                    SecondaryButton(text = "Retry", onClick = onRetry)
                }

                friends.isEmpty() -> EmptyState(
                    title = "No friends to invite",
                    message = "Everyone on your friends list is already in this session or invited."
                )

                else -> {
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        items(friends, key = { it.id }) { friend ->
                            val isSelected = friend.id in selectedIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.medium)
                                    .clickable {
                                        selectedIds = if (isSelected) {
                                            selectedIds - friend.id
                                        } else {
                                            selectedIds + friend.id
                                        }
                                    }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Avatar(name = friend.username)
                                Text(
                                    text = friend.username,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = null
                                )
                            }
                        }
                    }

                    DefaultButton(
                        text = when (selectedIds.size) {
                            0 -> "Select friends to invite"
                            1 -> "Send 1 invite"
                            else -> "Send ${selectedIds.size} invites"
                        },
                        onClick = { onSend(selectedIds.toList()) },
                        enabled = selectedIds.isNotEmpty()
                    )
                }
            }
        }
    }
}
