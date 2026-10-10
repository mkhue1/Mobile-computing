package com.example.gamercalendar.ui.feature.friends

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gamercalendar.data.model.User
import com.example.gamercalendar.ui.components.buttons.SecondaryButton
import com.example.gamercalendar.ui.components.cards.AppCard
import com.example.gamercalendar.ui.components.dialogs.MyQrCodeDialog
import com.example.gamercalendar.ui.components.feedback.EmptyState
import com.example.gamercalendar.ui.components.feedback.ErrorText
import com.example.gamercalendar.ui.components.feedback.LoadingIndicator
import com.example.gamercalendar.ui.components.inputs.DefaultTextField
import com.example.gamercalendar.ui.components.labels.Tag
import com.example.gamercalendar.ui.components.layout.ScreenContainer
import com.example.gamercalendar.ui.feature.friends.AddFriendViewModel.Companion.MIN_QUERY_LENGTH
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

@Composable
fun AddFriendScreen(
    onDone: () -> Unit,
    viewModel: AddFriendViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showQrDialog by remember { mutableStateOf(false) }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val contents = result.contents
        if (contents != null) {
            viewModel.onQrScanned(contents)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            scanLauncher.launch(friendQrScanOptions())
        } else {
            Toast.makeText(
                context,
                "Camera permission is required to scan QR codes",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun startQrScan() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            scanLauncher.launch(friendQrScanOptions())
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.messageShown()
        }
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

        SecondaryButton(
            text = "My QR code",
            onClick = { showQrDialog = true },
            enabled = uiState.currentUserId != null
        )

        SecondaryButton(
            text = "Scan QR code",
            onClick = ::startQrScan,
            enabled = !uiState.isResolvingQr
        )

        if (uiState.isResolvingQr) {
            LoadingIndicator()
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
            uiState.isLoading && uiState.results.isEmpty() -> LoadingIndicator()

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

    uiState.currentUserId?.let { userId ->
        if (showQrDialog) {
            MyQrCodeDialog(
                userId = userId,
                username = uiState.currentUsername,
                onDismiss = { showQrDialog = false }
            )
        }
    }

    uiState.pendingQrConfirmUser?.let { user ->
        AlertDialog(
            onDismissRequest = viewModel::dismissQrConfirm,
            title = { Text("Send friend request?") },
            text = {
                Text("Send a friend request to ${user.username}?")
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmQrFriendRequest) {
                    Text(
                        text = "Send",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissQrConfirm) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun friendQrScanOptions(): ScanOptions =
    ScanOptions().apply {
        setDesiredBarcodeFormats(ScanOptions.QR_CODE)
        setPrompt("Scan a friend's Roundtable QR code")
        setBeepEnabled(false)
        setOrientationLocked(true)
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
