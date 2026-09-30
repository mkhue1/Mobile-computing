package com.example.gamercalendar.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.example.gamercalendar.ui.components.buttons.DefaultButton
import com.example.gamercalendar.ui.components.layout.ScreenContainer

@Composable
fun HomeScreen(
    onCreateSessionClick: () -> Unit
) {
    ScreenContainer {
        Text(
            text = "Home",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = "Welcome to Roundtable.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        DefaultButton(
            text = "Create session",
            onClick = onCreateSessionClick
        )
    }
}
