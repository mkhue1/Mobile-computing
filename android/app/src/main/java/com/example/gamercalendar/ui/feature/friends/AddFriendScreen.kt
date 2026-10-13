package com.example.gamercalendar.ui.feature.friends

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.WindowManager
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gamercalendar.data.model.User
import com.example.gamercalendar.nfc.NfcAvailability
import com.example.gamercalendar.nfc.NfcFriendReader
import com.example.gamercalendar.nfc.NfcFriendSession
import com.example.gamercalendar.nfc.nfcAvailability
import com.example.gamercalendar.ui.components.buttons.SecondaryButton
import com.example.gamercalendar.ui.components.cards.AppCard
import com.example.gamercalendar.ui.components.dialogs.MyQrCodeDialog
import com.example.gamercalendar.ui.components.feedback.EmptyState
import com.example.gamercalendar.ui.components.feedback.ErrorText
import com.example.gamercalendar.ui.components.feedback.LoadingIndicator
import com.example.gamercalendar.ui.components.inputs.DefaultTextField
import com.example.gamercalendar.ui.components.labels.Tag
import com.example.gamercalendar.ui.components.labels.UserAvatar
import com.example.gamercalendar.ui.components.layout.ScreenContainer
import com.example.gamercalendar.ui.feature.friends.AddFriendViewModel.Companion.MIN_QUERY_LENGTH
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

@Composable
fun AddFriendScreen(
    onDone: () -> Unit,
    onOpenProfile: () -> Unit = {},
    viewModel: AddFriendViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showQrDialog by remember { mutableStateOf(false) }
    var showNfcShare by remember { mutableStateOf(false) }
    var showNfcRead by remember { mutableStateOf(false) }

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

    fun requireNfc(onReady: () -> Unit) {
        when (context.nfcAvailability()) {
            NfcAvailability.Unsupported -> {
                Toast.makeText(
                    context,
                    "This phone doesn't have NFC",
                    Toast.LENGTH_SHORT
                ).show()
            }
            NfcAvailability.Disabled -> {
                Toast.makeText(
                    context,
                    "Turn on NFC to add a friend this way",
                    Toast.LENGTH_SHORT
                ).show()
                try {
                    context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
                } catch (_: Exception) {
                }
            }
            NfcAvailability.Ready -> onReady()
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

        SecondaryButton(
            text = "My QR code",
            onClick = { showQrDialog = true },
            enabled = uiState.currentUserId != null
        )

        SecondaryButton(
            text = "Scan QR code",
            onClick = ::startQrScan,
            enabled = !uiState.isResolvingDiscoveredUser
        )

        SecondaryButton(
            text = "Share with NFC",
            onClick = { requireNfc { showNfcShare = true } },
            enabled = uiState.currentUserId != null
        )

        SecondaryButton(
            text = "Read with NFC",
            onClick = { requireNfc { showNfcRead = true } },
            enabled = !uiState.isResolvingDiscoveredUser
        )

        if (uiState.isResolvingDiscoveredUser) {
            LoadingIndicator()
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

    uiState.currentUserId?.let { userId ->
        if (showNfcShare) {
            NfcShareDialog(
                userId = userId,
                username = uiState.currentUsername,
                onDismiss = { showNfcShare = false }
            )
        }
    }

    if (showNfcRead) {
        NfcReadDialog(
            onPayload = { raw ->
                showNfcRead = false
                viewModel.onNfcPayload(raw)
            },
            onDismiss = { showNfcRead = false }
        )
    }

    uiState.pendingConfirmUser?.let { user ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDiscoveredFriend,
            title = { Text("Send friend request?") },
            text = {
                Text("Send a friend request to ${user.username}?")
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDiscoveredFriend) {
                    Text(
                        text = "Send",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDiscoveredFriend) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun NfcShareDialog(
    userId: String,
    username: String?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    DisposableEffect(userId) {
        val activity = context.findActivity()
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        NfcFriendSession.start(userId)
        onDispose {
            NfcFriendSession.stop()
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share with NFC") },
        text = {
            Text(
                buildString {
                    append("Hold the other phone against this one. On their phone, tap Read with NFC.")
                    if (!username.isNullOrBlank()) {
                        append("\n\nSharing as $username.")
                    }
                }
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
private fun NfcReadDialog(
    onPayload: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        val reader = activity?.let { NfcFriendReader(it, readerListener(context, onPayload)) }
        if (reader?.start() != true) {
            Toast.makeText(context, "Couldn't start NFC reader", Toast.LENGTH_SHORT).show()
        }
        onDispose { reader?.stop() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Read with NFC") },
        text = {
            Text("Hold your phone against theirs. They need Share with NFC open.")
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun readerListener(
    context: Context,
    onPayload: (String) -> Unit
): NfcFriendReader.Listener = object : NfcFriendReader.Listener {
    override fun onPayload(raw: String) {
        onPayload(raw)
    }

    override fun onError(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
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
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UserAvatar(user = user)

                Text(
                    text = user.username,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            when {
                isPendingFromThem -> Tag(text = "Respond in Requests")
                isSent -> Tag(text = "Requested")
                else -> TextButton(onClick = onAddClick) { Text(text = "Add") }
            }
        }
    }
}
