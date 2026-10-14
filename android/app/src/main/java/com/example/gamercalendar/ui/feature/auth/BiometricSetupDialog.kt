package com.example.gamercalendar.ui.feature.auth

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun BiometricSetupDialog(
    authViewModel: AuthViewModel
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = authViewModel::declineBiometricOffer,
        title = { Text("Sign in with biometrics?") },
        text = {
            Text(
                "Use your fingerprint or face next time you log in. You can still use your password, and you can turn this on later in Profile."
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    authViewModel.runWithBiometricHost(context, authViewModel::enableBiometric)
                }
            ) {
                Text("Enable")
            }
        },
        dismissButton = {
            TextButton(onClick = authViewModel::declineBiometricOffer) {
                Text("Not now")
            }
        }
    )
}
