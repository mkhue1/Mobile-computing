package com.example.gamercalendar.ui.components.app

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.gamercalendar.data.model.User
import com.example.gamercalendar.ui.components.labels.UserAvatar

/**
 * Standard top app bar used throughout the app.
 * Displays the Roundtable branding and a profile action, which shows the user's avatar once known.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    user: User? = null,
    onProfileClick: () -> Unit = {}
) {
    TopAppBar(
        title = {
            Text(
                text = "Roundtable",
                style = MaterialTheme.typography.titleLarge
            )
        },
        actions = {
            IconButton(
                onClick = onProfileClick
            ) {
                if (user != null) {
                    UserAvatar(
                        user = user,
                        size = 32.dp,
                        modifier = Modifier.semantics { contentDescription = "Profile" }
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "Profile"
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            actionIconContentColor = MaterialTheme.colorScheme.onBackground
        )
    )
}