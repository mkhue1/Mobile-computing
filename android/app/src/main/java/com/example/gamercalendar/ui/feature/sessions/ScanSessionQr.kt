package com.example.gamercalendar.ui.feature.sessions

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gamercalendar.ui.components.buttons.SecondaryButton
import com.example.gamercalendar.ui.components.feedback.ErrorText
import com.example.gamercalendar.ui.components.feedback.LoadingIndicator
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

@Composable
fun ScanSessionQr(
    onJoined: (sessionId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: JoinSessionByQrViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

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
            scanLauncher.launch(sessionQrScanOptions())
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
            scanLauncher.launch(sessionQrScanOptions())
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.messageShown()
        }
    }

    LaunchedEffect(uiState.joinedSessionId) {
        val sessionId = uiState.joinedSessionId ?: return@LaunchedEffect
        viewModel.consumeJoined()
        onJoined(sessionId)
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SecondaryButton(
            text = "Scan QR code",
            onClick = ::startQrScan,
            enabled = !uiState.isResolving && !uiState.isJoining
        )

        if (uiState.isResolving) {
            LoadingIndicator()
        }

        uiState.error?.let { ErrorText(text = it) }
    }

    uiState.pendingSession?.let { session ->
        val name = session.title?.takeIf { it.isNotBlank() } ?: session.game.name
        AlertDialog(
            onDismissRequest = viewModel::dismissPendingSession,
            title = { Text("Join session?") },
            text = { Text("Join $name?") },
            confirmButton = {
                TextButton(
                    onClick = viewModel::confirmJoin,
                    enabled = !uiState.isJoining
                ) {
                    Text(
                        text = "Join",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = viewModel::dismissPendingSession,
                    enabled = !uiState.isJoining
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun sessionQrScanOptions(): ScanOptions =
    ScanOptions().apply {
        setDesiredBarcodeFormats(ScanOptions.QR_CODE)
        setPrompt("Scan a Roundtable session QR code")
        setBeepEnabled(false)
        setOrientationLocked(true)
    }
