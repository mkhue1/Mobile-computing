package com.example.gamercalendar.ui.feature.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gamercalendar.R
import com.example.gamercalendar.data.model.User
import com.example.gamercalendar.ui.components.buttons.DefaultButton
import com.example.gamercalendar.ui.components.buttons.DestructiveButton
import com.example.gamercalendar.ui.components.buttons.SecondaryButton
import com.example.gamercalendar.ui.components.dialogs.ConfirmDialog
import com.example.gamercalendar.ui.components.feedback.ErrorText
import com.example.gamercalendar.ui.components.feedback.LoadingIndicator
import com.example.gamercalendar.ui.components.labels.UserAvatar
import com.example.gamercalendar.ui.components.layout.ScreenContainer
import com.example.gamercalendar.ui.components.layout.SectionTitle
import com.example.gamercalendar.ui.feature.auth.AuthViewModel
import com.example.gamercalendar.util.openCustomTab
import kotlinx.coroutines.launch

private val SteamBlue = Color(0xFF1B2838)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsersScreen(
    authViewModel: AuthViewModel,
    userViewModel: UserViewModel = viewModel(),
    steamViewModel: SteamViewModel = viewModel()
) {
    val uiState by userViewModel.uiState.collectAsStateWithLifecycle()
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

    // DataStore copies from login, shown until /auth/me returns.
    val storedUsername by authViewModel.username.collectAsStateWithLifecycle()
    val storedEmail by authViewModel.email.collectAsStateWithLifecycle()
    var showPhotoSheet by remember { mutableStateOf(false) }
    var showRemoveDialog by remember { mutableStateOf(false) }
    var showDisconnectDialog by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { userViewModel.uploadPhoto(context, it) }
    }
    val launchPicker = {
        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    // Animates the sheet away, then runs the chosen action once it's gone.
    fun closeSheetThen(action: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            showPhotoSheet = false
            action()
        }
    }

    LaunchedEffect(Unit) {
        userViewModel.load()
    }

    val user = uiState.user
    val hasPhoto = user?.avatar_updated_at != null

    ScreenContainer(
        modifier = Modifier.verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Profile",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        // Who you are: avatar, name and email together, centred.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            UsersScreenAvatarButton(
                user = user,
                isBusy = uiState.isUploading,
                onClick = {
                    if (hasPhoto) showPhotoSheet = true else launchPicker()
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = user?.username ?: storedUsername.orEmpty(),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = user?.email ?: storedEmail.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (uiState.isLoading && user == null) {
            LoadingIndicator()
        }

        uiState.error?.let {
            ErrorText(text = it)
        }

        SectionTitle(title = "Connected accounts")

        ConnectedAccountRow(
            name = "Steam",
            status = when {
                steamState.isLoading -> "Checking…"
                steamState.isConnecting -> "Connecting…"
                steamState.linked -> "Connected"
                else -> "Not connected"
            },
            detail = steamState.personaName?.takeIf { steamState.linked && it.isNotBlank() },
            icon = painterResource(R.drawable.ic_steam),
            iconBackground = SteamBlue,
            isLinked = steamState.linked,
            isBusy = steamState.isLoading || steamState.isConnecting,
            onConnect = steamViewModel::connectSteam,
            onDisconnect = { showDisconnectDialog = true }
        )

        steamState.message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        steamState.error?.let { ErrorText(text = it) }

        Spacer(modifier = Modifier.height(8.dp))

        DestructiveButton(
            text = "Log out",
            onClick = { authViewModel.logout() }
        )
    }

    if (showPhotoSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPhotoSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DefaultButton(
                    text = "Change photo",
                    onClick = { closeSheetThen(launchPicker) }
                )
                DestructiveButton(
                    text = "Remove photo",
                    onClick = { closeSheetThen { showRemoveDialog = true } }
                )
                SecondaryButton(
                    text = "Cancel",
                    onClick = { closeSheetThen {} }
                )
            }
        }
    }

    if (showRemoveDialog) {
        ConfirmDialog(
            title = "Remove photo?",
            text = "Your profile will show your initial instead.",
            confirmLabel = "Remove",
            dismissLabel = "Cancel",
            onConfirm = {
                showRemoveDialog = false
                userViewModel.removePhoto()
            },
            onDismiss = { showRemoveDialog = false }
        )
    }

    if (showDisconnectDialog) {
        ConfirmDialog(
            title = "Disconnect Steam?",
            text = "You can connect it again at any time.",
            confirmLabel = "Disconnect",
            dismissLabel = "Keep",
            onConfirm = {
                showDisconnectDialog = false
                steamViewModel.disconnectSteam()
            },
            onDismiss = { showDisconnectDialog = false }
        )
    }
}

/**
 * One linked-account row: a coloured icon block on the left, the account name with a small
 * uppercase status under it, and a round action on the right (+ to connect, − to disconnect).
 * When [detail] is set (for example a linked Steam profile name), it replaces the status line.
 * Add another service later by calling this again with its own name, icon and colour.
 */
@Composable
private fun ConnectedAccountRow(
    name: String,
    status: String,
    icon: Painter,
    iconBackground: Color,
    isLinked: Boolean,
    isBusy: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    detail: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            // When not connected the whole row is the connect button.
            .clickable(enabled = !isLinked && !isBusy, onClick = onConnect),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(iconBackground),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = status.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Box(
            modifier = Modifier.padding(end = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                isBusy -> CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )

                isLinked -> RoundAction(
                    icon = Icons.Default.Remove,
                    description = "Disconnect $name",
                    background = MaterialTheme.colorScheme.error,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    onClick = onDisconnect
                )

                else -> RoundAction(
                    icon = Icons.Default.Add,
                    description = "Connect $name",
                    background = MaterialTheme.colorScheme.primary,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    onClick = onConnect
                )
            }
        }
    }
}

@Composable
private fun RoundAction(
    icon: ImageVector,
    description: String,
    background: Color,
    tint: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * The 96dp avatar as the photo control: tappable, with a camera badge, and a spinner
 * overlay while a photo is uploading or being removed.
 */
@Composable
private fun UsersScreenAvatarButton(
    user: User?,
    isBusy: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(96.dp)
            // Not clipped, so the badge can sit on the circle's edge; the ripple is drawn as a circle instead.
            .clickable(
                interactionSource = null,
                indication = ripple(bounded = false, radius = 48.dp),
                enabled = !isBusy,
                role = Role.Button,
                onClick = onClick
            )
            .semantics { contentDescription = "Change profile photo" }
    ) {
        // The button's description covers it; don't also read out the initial.
        // The ring in the accent colour lifts the avatar off the dark background.
        UserAvatar(
            user = user,
            size = 96.dp,
            modifier = Modifier
                .clearAndSetSemantics {}
                .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), CircleShape)
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .border(2.dp, MaterialTheme.colorScheme.background, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PhotoCamera,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(16.dp)
            )
        }

        if (isBusy) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator()
            }
        }
    }
}