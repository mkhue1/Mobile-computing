package com.example.gamercalendar.ui.screens

import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gamercalendar.data.model.Game
import com.example.gamercalendar.data.model.SessionType
import com.example.gamercalendar.data.model.SessionVisibility
import com.example.gamercalendar.data.model.UserGroup
import com.example.gamercalendar.ui.components.buttons.DefaultButton
import com.example.gamercalendar.ui.components.buttons.SecondaryButton
import com.example.gamercalendar.ui.components.feedback.ErrorText
import com.example.gamercalendar.ui.components.feedback.LoadingIndicator
import com.example.gamercalendar.ui.components.inputs.DefaultTextField
import com.example.gamercalendar.ui.components.layout.ScreenContainer
import com.example.gamercalendar.util.SessionTime
import com.example.gamercalendar.viewmodel.CreateSessionViewModel

private enum class TimeField { START, END }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateSessionScreen(
    onSessionCreated: () -> Unit,
    onCancel: () -> Unit,
    viewModel: CreateSessionViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val form = uiState.form
    val context = LocalContext.current

    var showDatePicker by remember { mutableStateOf(false) }
    var editingTime by remember { mutableStateOf<TimeField?>(null) }

    LaunchedEffect(uiState.createdSession) {
        if (uiState.createdSession != null) {
            Toast.makeText(context, "Session created", Toast.LENGTH_SHORT).show()
            onSessionCreated()
        }
    }

    ScreenContainer(
        modifier = Modifier.verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Create session",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        when {
            uiState.isLoadingGames -> LoadingIndicator()

            uiState.gamesError != null -> {
                ErrorText(text = "Couldn't load games: ${uiState.gamesError}")
                SecondaryButton(text = "Retry", onClick = viewModel::loadGames)
            }

            uiState.games.isEmpty() -> {
                Text(
                    text = "No games in the database yet. Add one through the API docs (/docs) for now.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SecondaryButton(text = "Refresh", onClick = viewModel::loadGames)
            }

            else -> SelectDropdown(
                label = "Game",
                placeholder = "Choose a game",
                options = uiState.games,
                selected = uiState.games.firstOrNull { it.id == form.gameId },
                optionLabel = Game::name,
                onSelected = { game -> viewModel.updateForm { it.copy(gameId = game.id) } }
            )
        }

        DefaultTextField(
            value = form.title,
            onValueChange = { value -> viewModel.updateForm { it.copy(title = value) } },
            label = "Title (optional)"
        )

        LabeledSection(label = "Date") {
            SecondaryButton(
                text = SessionTime.formatDate(form.dateUtcMillis),
                onClick = { showDatePicker = true }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LabeledSection(label = "Start", modifier = Modifier.weight(1f)) {
                SecondaryButton(
                    text = SessionTime.formatTime(context, form.startHour, form.startMinute),
                    onClick = { editingTime = TimeField.START }
                )
            }
            LabeledSection(label = "End", modifier = Modifier.weight(1f)) {
                SecondaryButton(
                    text = SessionTime.formatTime(context, form.endHour, form.endMinute),
                    onClick = { editingTime = TimeField.END }
                )
            }
        }

        if (form.endsNextDay) {
            Text(
                text = "Ends the next day",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        LabeledSection(label = "Session type") {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SessionType.entries.forEachIndexed { index, type ->
                    SegmentedButton(
                        selected = form.sessionType == type,
                        onClick = { viewModel.updateForm { it.copy(sessionType = type) } },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = SessionType.entries.size
                        )
                    ) {
                        Text(type.label)
                    }
                }
            }
        }

        if (form.sessionType == SessionType.IN_PERSON) {
            DefaultTextField(
                value = form.locationName,
                onValueChange = { value -> viewModel.updateForm { it.copy(locationName = value) } },
                label = "Location"
            )
        }

        LabeledSection(label = "Who can see it") {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SessionVisibility.entries.forEachIndexed { index, visibility ->
                    SegmentedButton(
                        selected = form.visibility == visibility,
                        onClick = { viewModel.updateForm { it.copy(visibility = visibility) } },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = SessionVisibility.entries.size
                        ),
                        icon = {}
                    ) {
                        Text(text = visibility.label, maxLines = 1)
                    }
                }
            }
        }

        if (form.visibility == SessionVisibility.GROUP) {
            when {
                uiState.isLoadingGroups -> LoadingIndicator()

                uiState.groupsError != null -> {
                    ErrorText(text = "Couldn't load groups: ${uiState.groupsError}")
                    SecondaryButton(text = "Retry", onClick = viewModel::loadGroups)
                }

                uiState.groups.isEmpty() -> {
                    Text(
                        text = "You're not in any groups yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SecondaryButton(text = "Refresh", onClick = viewModel::loadGroups)
                }

                else -> SelectDropdown(
                    label = "Group",
                    placeholder = "Choose a group",
                    options = uiState.groups,
                    selected = uiState.groups.firstOrNull { it.id == form.groupId },
                    optionLabel = UserGroup::name,
                    onSelected = { group -> viewModel.updateForm { it.copy(groupId = group.id) } }
                )
            }
        }

        DefaultTextField(
            value = form.playerLimit,
            onValueChange = { value ->
                viewModel.updateForm { it.copy(playerLimit = value.filter(Char::isDigit)) }
            },
            label = "Player limit (optional)",
            placeholder = "No limit",
            keyboardType = KeyboardType.Number
        )

        OutlinedTextField(
            value = form.description,
            onValueChange = { value -> viewModel.updateForm { it.copy(description = value) } },
            label = { Text("Notes (optional)") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            shape = RoundedCornerShape(12.dp)
        )

        uiState.error?.let {
            ErrorText(text = it)
        }

        DefaultButton(
            text = if (uiState.isSubmitting) "Creating…" else "Create session",
            onClick = viewModel::submit,
            enabled = !uiState.isSubmitting
        )

        SecondaryButton(
            text = "Cancel",
            onClick = onCancel,
            enabled = !uiState.isSubmitting
        )
    }

    if (showDatePicker) {
        SessionDatePickerDialog(
            initialDateUtcMillis = form.dateUtcMillis,
            onConfirm = { date ->
                viewModel.updateForm { it.copy(dateUtcMillis = date) }
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }

    editingTime?.let { field ->
        val isStart = field == TimeField.START
        SessionTimePickerDialog(
            title = if (isStart) "Start time" else "End time",
            initialHour = if (isStart) form.startHour else form.endHour,
            initialMinute = if (isStart) form.startMinute else form.endMinute,
            onConfirm = { hour, minute ->
                viewModel.updateForm {
                    if (isStart) {
                        it.copy(startHour = hour, startMinute = minute)
                    } else {
                        it.copy(endHour = hour, endMinute = minute)
                    }
                }
                editingTime = null
            },
            onDismiss = { editingTime = null }
        )
    }
}

@Composable
private fun LabeledSection(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> SelectDropdown(
    label: String,
    placeholder: String,
    options: List<T>,
    selected: T?,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selected?.let(optionLabel).orEmpty(),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionDatePickerDialog(
    initialDateUtcMillis: Long,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val todayUtcMillis = remember { SessionTime.todayUtcMillis() }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialDateUtcMillis,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                return utcTimeMillis >= todayUtcMillis
            }
        }
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { state.selectedDateMillis?.let(onConfirm) ?: onDismiss() }
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionTimePickerDialog(
    title: String,
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val state = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = DateFormat.is24HourFormat(context)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
