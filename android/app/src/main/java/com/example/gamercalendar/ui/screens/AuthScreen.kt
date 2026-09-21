package com.example.gamercalendar.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.gamercalendar.util.AuthValidation
import com.example.gamercalendar.viewmodel.AuthViewModel

@Composable
fun AuthScreen(
    authViewModel: AuthViewModel
) {
    val uiState by authViewModel.uiState.collectAsState()

    var isSignUp by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val passwordHint = AuthValidation.passwordError(password)

    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(if (isSignUp) "Sign up" else "Log in")

        if (isSignUp) {
            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    authViewModel.clearError()
                },
                label = { Text("Email") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )
            OutlinedTextField(
                value = username,
                onValueChange = {
                    username = it
                    authViewModel.clearError()
                },
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            OutlinedTextField(
                value = identifier,
                onValueChange = {
                    identifier = it
                    authViewModel.clearError()
                },
                label = { Text("Email or username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                authViewModel.clearError()
            },
            label = { Text("Password") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )

        if (isSignUp) {
            Text("Password: at least 8 characters, with a letter and a digit")
            passwordHint?.let { Text(it) }
        }

        uiState.error?.let {
            Text("Error: $it")
        }

        Button(
            onClick = {
                if (isSignUp) {
                    authViewModel.register(email, username, password)
                } else {
                    authViewModel.login(identifier, password)
                }
            },
            enabled = !uiState.isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                when {
                    uiState.isLoading -> "Please wait…"
                    isSignUp -> "Create account"
                    else -> "Log in"
                }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            TextButton(
                onClick = {
                    isSignUp = !isSignUp
                    authViewModel.clearError()
                }
            ) {
                Text(
                    if (isSignUp) "Already have an account? Log in"
                    else "Need an account? Sign up"
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}
