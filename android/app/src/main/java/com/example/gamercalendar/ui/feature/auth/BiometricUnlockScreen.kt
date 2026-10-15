package com.example.gamercalendar.ui.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gamercalendar.biometric.BiometricStatus
import com.example.gamercalendar.ui.components.app.AppScaffold
import com.example.gamercalendar.ui.components.buttons.DefaultButton
import com.example.gamercalendar.ui.components.buttons.SecondaryButton
import com.example.gamercalendar.ui.components.feedback.ErrorText
import com.example.gamercalendar.ui.components.inputs.DefaultTextField
import com.example.gamercalendar.ui.components.inputs.PasswordTextField
import com.example.gamercalendar.ui.components.layout.ScreenContainer

@Composable
fun BiometricUnlockScreen(
    authViewModel: AuthViewModel
) {
    val uiState by authViewModel.uiState.collectAsStateWithLifecycle()
    val storedUsername by authViewModel.username.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var identifier by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var prompted by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(storedUsername) {
        if (identifier.isBlank() && !storedUsername.isNullOrBlank()) {
            identifier = storedUsername.orEmpty()
        }
    }

    LaunchedEffect(uiState.biometricStatus) {
        if (uiState.biometricStatus != BiometricStatus.Ready) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (prompted) return@repeatOnLifecycle
            prompted = true
            authViewModel.runWithBiometricHost(context, authViewModel::unlockWithBiometric)
        }
    }

    AppScaffold { innerPadding ->
        ScreenContainer(
            modifier = Modifier.padding(innerPadding)
        ) {
            Spacer(modifier = Modifier.weight(1f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp)
                    .align(Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "ROUNDTABLE",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Welcome back!",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Sign in to continue",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (uiState.biometricStatus == BiometricStatus.Ready && uiState.biometricEnabled) {
                    DefaultButton(
                        text = if (uiState.biometricBusy) "Waiting for biometrics…" else "Use biometrics",
                        onClick = {
                            authViewModel.runWithBiometricHost(context, authViewModel::unlockWithBiometric)
                        },
                        enabled = !uiState.isLoading && !uiState.biometricBusy
                    )
                } else {
                    Text(
                        text = biometricFallbackMessage(uiState.biometricStatus),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                (uiState.biometricError ?: uiState.error)?.let {
                    ErrorText(text = it)
                }

                Text(
                    text = "Or sign in with your password",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                DefaultTextField(
                    value = identifier,
                    onValueChange = {
                        identifier = it
                        authViewModel.clearError()
                    },
                    label = "Email or username",
                    keyboardType = KeyboardType.Email
                )

                PasswordTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        authViewModel.clearError()
                    }
                )

                SecondaryButton(
                    text = if (uiState.isLoading) "Please wait…" else "Log in",
                    onClick = { authViewModel.login(identifier, password) },
                    enabled = !uiState.isLoading && !uiState.biometricBusy
                )
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

fun biometricFallbackMessage(status: BiometricStatus): String {
    return when (status) {
        BiometricStatus.NoneEnrolled ->
            "Add a fingerprint or face unlock in your phone settings to sign in with biometrics."
        BiometricStatus.TemporarilyUnavailable ->
            "Biometrics are temporarily unavailable. Use your password, or try again later."
        BiometricStatus.Unavailable ->
            "This device doesn't support biometric sign-in. Use your password."
        BiometricStatus.Ready ->
            "Use your fingerprint or face, or sign in with your password."
    }
}
