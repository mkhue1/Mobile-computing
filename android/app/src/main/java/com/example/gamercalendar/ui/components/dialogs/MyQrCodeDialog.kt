package com.example.gamercalendar.ui.components.dialogs

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.gamercalendar.util.QrCode

@Composable
fun MyQrCodeDialog(
    userId: String,
    username: String?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val qrBitmap = remember(userId) { QrCode.generateBitmap(userId) }

    fun saveQr() {
        try {
            QrCode.saveToGallery(
                context = context,
                bitmap = qrBitmap,
                displayName = "roundtable_qr_${userId.take(8)}.png"
            )
            Toast.makeText(context, "QR code saved", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(
                context,
                e.message ?: "Couldn't save QR code",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            saveQr()
        } else {
            Toast.makeText(
                context,
                "Storage permission is required to save",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun onSaveClick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveQr()
            return
        }

        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            saveQr()
        } else {
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    fun onShareClick() {
        try {
            QrCode.share(context, qrBitmap)
        } catch (e: Exception) {
            Toast.makeText(
                context,
                e.message ?: "Couldn't share QR code",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("My QR code") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (!username.isNullOrBlank()) {
                        "Share this code so others can add @$username"
                    } else {
                        "Share this code so others can add you as a friend"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = "QR code for your user ID",
                    modifier = Modifier.size(220.dp)
                )

                Text(
                    text = userId,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = ::onSaveClick) {
                    Text("Save")
                }
                TextButton(onClick = ::onShareClick) {
                    Text("Share")
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}
