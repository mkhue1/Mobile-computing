package com.example.gamercalendar.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gamercalendar.viewmodel.UserViewModel

@Composable
fun UsersScreen(
    userViewModel: UserViewModel = viewModel()
) {
    val users by userViewModel.users.collectAsState()
    val error by userViewModel.error.collectAsState()

    Column(
        modifier = Modifier.padding(16.dp)
    ) {
        Button(
            onClick = {
                userViewModel.loadUsers()
            }
        ) {
            Text("Load users")
        }

        error?.let {
            Text("Error: $it")
        }

        users.forEach { user ->
            Text(
                text = "${user.name} - ${user.email}"
            )
        }
    }
}