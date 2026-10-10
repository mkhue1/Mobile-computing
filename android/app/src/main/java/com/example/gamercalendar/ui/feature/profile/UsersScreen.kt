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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsersScreen(
    authViewModel: AuthViewModel,
    viewModel: UserViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // DataStore copies from login, shown until /auth/me returns.
    val storedUsername by authViewModel.username.collectAsStateWithLifecycle()
    val storedEmail by authViewModel.email.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showPhotoSheet by remember { mutableStateOf(false) }
    var showRemoveDialog by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.uploadPhoto(context, it) }
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
        viewModel.load()
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

        Column(
            modifier = Modifier.fillMaxWidth(),
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
            Text(
                text = user?.username ?: storedUsername.orEmpty(),
                style = MaterialTheme.typography.titleLarge,
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

        SectionTitle(title = "More options")
        TextButton(onClick = { /* reserved for future settings */ }) {
            Text("Settings (coming soon)")
        }

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
                viewModel.removePhoto()
            },
            onDismiss = { showRemoveDialog = false }
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
        UserAvatar(
            user = user,
            size = 96.dp,
            modifier = Modifier.clearAndSetSemantics {}
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
