package com.example.gamercalendar.ui.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.gamercalendar.ui.components.buttons.SecondaryButton
import com.example.gamercalendar.ui.components.cards.SessionCard
import com.example.gamercalendar.ui.components.feedback.EmptyState
import com.example.gamercalendar.ui.components.feedback.ErrorText
import com.example.gamercalendar.ui.components.feedback.LoadingIndicator

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onSessionClick: (sessionId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val query by viewModel.query.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    // Runs each time the screen is shown, so a session you have just joined drops out of the list.
    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Find a session",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search by game or session name") },
            singleLine = true,
            trailingIcon = {
                if (query.isNotEmpty()) {
                    TextButton(onClick = { viewModel.onQueryChange("") }) {
                        Text("Clear")
                    }
                }
            }
        )
        Spacer(Modifier.height(12.dp))

        when {
            uiState.isLoading && uiState.results.isEmpty() -> LoadingIndicator()

            uiState.errorMessage != null -> {
                ErrorText(text = uiState.errorMessage.orEmpty())
                SecondaryButton(text = "Retry", onClick = viewModel::refresh)
            }

            uiState.results.isEmpty() -> EmptyState(
                title = if (query.isBlank()) "No public sessions right now" else "No sessions found",
                message = if (query.isBlank()) {
                    "Check back later, or create one for others to join."
                } else {
                    "Try a different game or session name."
                }
            )

            else -> {
                // Results stay on screen while a newer search loads, with a thin bar on top.
                if (uiState.isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                }

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.results, key = { it.id }) { result ->
                        SessionCard(
                            session = result.session,
                            startEpochMillis = result.startEpochMillis,
                            endEpochMillis = result.endEpochMillis,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onSessionClick(result.id) }
                        )
                    }
                }
            }
        }
    }
}