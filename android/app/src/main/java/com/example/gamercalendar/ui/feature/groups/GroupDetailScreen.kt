package com.example.gamercalendar.ui.feature.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gamercalendar.data.model.GroupMemberResponse
import com.example.gamercalendar.data.model.User
import com.example.gamercalendar.ui.components.buttons.DefaultButton
import com.example.gamercalendar.ui.components.buttons.DestructiveButton
import com.example.gamercalendar.ui.components.buttons.SecondaryButton
import com.example.gamercalendar.ui.components.cards.AppCard
import com.example.gamercalendar.ui.components.dialogs.ConfirmDialog
import com.example.gamercalendar.ui.components.dialogs.GroupNameDialog
import com.example.gamercalendar.ui.components.feedback.EmptyState
import com.example.gamercalendar.ui.components.feedback.ErrorText
import com.example.gamercalendar.ui.components.feedback.LoadingIndicator
import com.example.gamercalendar.ui.components.inputs.DefaultTextField
import com.example.gamercalendar.ui.components.labels.Avatar
import com.example.gamercalendar.ui.components.labels.Tag
import com.example.gamercalendar.ui.components.layout.ScreenContainer
import com.example.gamercalendar.ui.components.layout.SectionTitle
private enum class GroupDetailConfirmation { DELETE, LEAVE }

@Composable
fun GroupDetailScreen(
    onLeft: () -> Unit,
    viewModel: GroupDetailViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var showRenameDialog by remember { mutableStateOf(false) }
    var pendingConfirmation by remember { mutableStateOf<GroupDetailConfirmation?>(null) }
    var pendingRemoveMemberId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    LaunchedEffect(uiState.hasLeft, uiState.hasDeleted) {
        if (uiState.hasLeft || uiState.hasDeleted) onLeft()
    }

    val group = uiState.group

    ScreenContainer(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        when {
            group == null && uiState.isLoading -> LoadingIndicator()

            group == null -> {
                ErrorText(text = uiState.error ?: "Couldn't load group")
                SecondaryButton(text = "Retry", onClick = viewModel::load)
            }

            else -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    if (uiState.isOwner) {
                        TextButton(onClick = { showRenameDialog = true }) {
                            Text("Rename")
                        }
                    }
                }

                SectionTitle(title = "Members")

                AppCard {
                    group.members.forEachIndexed { index, member ->
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                            )
                        }
                        MemberRow(
                            member = member,
                            isOwner = uiState.isOwner,
                            onRemoveClick = { pendingRemoveMemberId = member.user_id }
                        )
                    }
                }

                uiState.actionError?.let { ErrorText(text = it) }

                SectionTitle(title = "Add friend to group")

                if (uiState.addableFriends.isEmpty()) {
                    EmptyState(
                        title = "No friends to add",
                        message = "All your friends are already in this group."
                    )
                } else {
                    DefaultTextField(
                        value = uiState.friendSearch,
                        onValueChange = viewModel::onFriendSearchChange,
                        label = "Search friends",
                        placeholder = "Username"
                    )

                    val filteredFriends = uiState.filteredAddableFriends

                    if (filteredFriends.isEmpty()) {
                        EmptyState(
                            title = "No friends match",
                            message = "Try a different username."
                        )
                    } else {
                        AppCard {
                            filteredFriends.forEachIndexed { index, friend ->
                                if (index > 0) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                                    )
                                }
                                AddableFriendRow(
                                    friend = friend,
                                    enabled = !uiState.isWorking,
                                    onAddClick = { viewModel.addMember(friend.id) }
                                )
                            }
                        }
                    }
                }

                if (!uiState.isOwner) {
                    DestructiveButton(
                        text = "Leave group",
                        onClick = { pendingConfirmation = GroupDetailConfirmation.LEAVE },
                        enabled = !uiState.isWorking
                    )
                }

                if (uiState.isOwner) {
                    DestructiveButton(
                        text = "Delete group",
                        onClick = { pendingConfirmation = GroupDetailConfirmation.DELETE },
                        enabled = !uiState.isWorking
                    )
                }
            }
        }
    }

    if (showRenameDialog && group != null) {
        GroupNameDialog(
            title = "Rename group",
            confirmLabel = "Save",
            initialName = group.name,
            onConfirm = { name ->
                showRenameDialog = false
                viewModel.rename(name)
            },
            onDismiss = { showRenameDialog = false }
        )
    }

    when (pendingConfirmation) {
        GroupDetailConfirmation.DELETE -> ConfirmDialog(
            title = "Delete group?",
            text = "This removes it for everyone. This can't be undone.",
            confirmLabel = "Delete",
            dismissLabel = "Cancel",
            onConfirm = {
                pendingConfirmation = null
                viewModel.deleteGroup()
            },
            onDismiss = { pendingConfirmation = null }
        )

        GroupDetailConfirmation.LEAVE -> ConfirmDialog(
            title = "Leave group?",
            text = "You'll need to be added back by a member to rejoin.",
            confirmLabel = "Leave",
            dismissLabel = "Cancel",
            onConfirm = {
                pendingConfirmation = null
                viewModel.leave()
            },
            onDismiss = { pendingConfirmation = null }
        )

        null -> Unit
    }

    pendingRemoveMemberId?.let { memberId ->
        ConfirmDialog(
            title = "Remove member?",
            text = "They'll need to be added back to rejoin the group.",
            confirmLabel = "Remove",
            dismissLabel = "Cancel",
            onConfirm = {
                pendingRemoveMemberId = null
                viewModel.removeMember(memberId)
            },
            onDismiss = { pendingRemoveMemberId = null }
        )
    }
}

@Composable
private fun MemberRow(
    member: GroupMemberResponse,
    isOwner: Boolean,
    onRemoveClick: () -> Unit
) {
    val isMemberOwner = member.role == "owner"
    val username = member.user?.username ?: "Unknown user"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(name = username)

        Text(
            text = username,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        Tag(text = if (isMemberOwner) "Owner" else "Member")

        if (isOwner && !isMemberOwner) {
            TextButton(onClick = onRemoveClick) {
                Text(text = "Remove", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun AddableFriendRow(
    friend: User,
    enabled: Boolean,
    onAddClick: () -> Unit
) {
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

        TextButton(onClick = onAddClick, enabled = enabled) {
            Text("Add")
        }
    }
}