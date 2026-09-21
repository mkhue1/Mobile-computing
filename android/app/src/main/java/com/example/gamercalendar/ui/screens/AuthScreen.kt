package com.example.gamercalendar.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gamercalendar.ui.components.AppScaffold
import com.example.gamercalendar.ui.components.DefaultButton
import com.example.gamercalendar.ui.components.DefaultTextField
import com.example.gamercalendar.ui.components.PasswordTextField
import com.example.gamercalendar.ui.components.ScreenContainer
import com.example.gamercalendar.util.AuthValidation
import com.example.gamercalendar.viewmodel.AuthViewModel

@Composable
fun AuthScreen(
    authViewModel: AuthViewModel
) {
    val uiState by authViewModel.uiState.collectAsStateWithLifecycle()

    var isSignUp by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val passwordHint = AuthValidation.passwordError(password)

    AppScaffold { innerPadding ->
        ScreenContainer(
            modifier = Modifier.padding(innerPadding)
        ) {
            Spacer(
                modifier = Modifier.weight(1f)
            )

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
                        text = if (isSignUp) {
                            "Create an account"
                        } else {
                            "Welcome back!"
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Text(
                        text = if (isSignUp) {
                            "Create your account to get started"
                        } else {
                            "Sign in to continue"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isSignUp) {
                    DefaultTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            authViewModel.clearError()
                        },
                        label = "Email",
                        keyboardType = KeyboardType.Email
                    )

                    DefaultTextField(
                        value = username,
                        onValueChange = {
                            username = it
                            authViewModel.clearError()
                        },
                        label = "Username"
                    )
                } else {
                    DefaultTextField(
                        value = identifier,
                        onValueChange = {
                            identifier = it
                            authViewModel.clearError()
                        },
                        label = "Email or username"
                    )
                }

                PasswordTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        authViewModel.clearError()
                    }
                )

                if (isSignUp) {
                    Text(
                        text = "At least 8 characters, including a letter and a digit",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    passwordHint?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                uiState.error?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                DefaultButton(
                    text = when {
                        uiState.isLoading -> "Please wait…"
                        isSignUp -> "Create account"
                        else -> "Log in"
                    },
                    onClick = {
                        if (isSignUp) {
                            authViewModel.register(
                                email,
                                username,
                                password
                            )
                        } else {
                            authViewModel.login(
                                identifier,
                                password
                            )
                        }
                    },
                    enabled = !uiState.isLoading
                )

                TextButton(
                    onClick = {
                        isSignUp = !isSignUp
                        authViewModel.clearError()
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isSignUp) {
                                "Already have an account? Log in!"
                            } else {
                                "Don't have an account? Sign up!"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(
                modifier = Modifier.weight(1f)
            )
        }
    }
}