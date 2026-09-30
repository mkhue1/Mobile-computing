package com.example.gamercalendar.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.gamercalendar.ui.components.buttons.DefaultButton
import com.example.gamercalendar.ui.components.buttons.SecondaryButton
import com.example.gamercalendar.ui.components.cards.AppCard
import com.example.gamercalendar.ui.components.feedback.EmptyState
import com.example.gamercalendar.ui.components.feedback.ErrorText
import com.example.gamercalendar.ui.components.feedback.LoadingIndicator
import com.example.gamercalendar.ui.components.inputs.DefaultTextField
import com.example.gamercalendar.ui.components.inputs.PasswordTextField
import com.example.gamercalendar.ui.components.layout.ScreenContainer

@Composable
fun CardTestScreen() {
    var text by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    ScreenContainer(
        modifier = Modifier.verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Component Testing",
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            text = "Cards",
            style = MaterialTheme.typography.titleLarge
        )

        AppCard {
            Text(
                text = "Dumb Card",
                style = MaterialTheme.typography.titleLarge
            )

            Text(
                text = "This is just here to test the generic AppCard component.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AppCard(
            onClick = {
                // Test click later
            }
        ) {
            Text(
                text = "Clickable Dumb Card",
                style = MaterialTheme.typography.titleLarge
            )

            Text(
                text = "You can tap this one.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        HorizontalDivider()

        Text(
            text = "Buttons",
            style = MaterialTheme.typography.titleLarge
        )

        DefaultButton(
            text = "Primary Button",
            onClick = {
                // Test action
            }
        )

        SecondaryButton(
            text = "Secondary Button",
            onClick = {
                // Test action
            }
        )

        DefaultButton(
            text = "Disabled Button",
            onClick = {},
            enabled = false
        )

        HorizontalDivider()

        Text(
            text = "Inputs",
            style = MaterialTheme.typography.titleLarge
        )

        DefaultTextField(
            value = text,
            onValueChange = {
                text = it
            },
            label = "Default text field"
        )

        DefaultTextField(
            value = text,
            onValueChange = {
                text = it
            },
            label = "Email",
            keyboardType = KeyboardType.Email
        )

        PasswordTextField(
            value = password,
            onValueChange = {
                password = it
            }
        )

        HorizontalDivider()

        Text(
            text = "Feedback",
            style = MaterialTheme.typography.titleLarge
        )

        ErrorText(
            text = "Something went wrong. This is an example error."
        )

        LoadingIndicator()

        HorizontalDivider()

        Text(
            text = "Empty State",
            style = MaterialTheme.typography.titleLarge
        )

        EmptyState(
            title = "Nothing here yet",
            message = "This is what an empty screen could look like."
        )

        HorizontalDivider()

        Text(
            text = "Layout Test",
            style = MaterialTheme.typography.titleLarge
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Theme primary",
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "Secondary text",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}