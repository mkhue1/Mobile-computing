package com.example.gamercalendar.ui.feature.sessions

import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuBoxScope
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.example.gamercalendar.data.model.Game
import com.example.gamercalendar.data.model.GameSearchResult
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
private enum class TimeField { START, END }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateSessionScreen(
    onSaved: () -> Unit,
    onCancel: () -> Unit,
    viewModel: CreateSessionViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val form = uiState.form
    val context = LocalContext.current

    var showDatePicker by remember { mutableStateOf(false) }
    var editingTime by remember { mutableStateOf<TimeField?>(null) }

    LaunchedEffect(uiState.savedSession) {
        if (uiState.savedSession != null) {
            val message = if (uiState.isEditing) "Session updated" else "Session created"
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            onSaved()
        }
    }

    ScreenContainer(
        modifier = Modifier.verticalScroll(rememberScrollState())
    ) {
        Text(
            text = if (uiState.isEditing) "Edit session" else "Create session",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        if (uiState.isLoadingSession) {
            LoadingIndicator()
            return@ScreenContainer
        }

        uiState.loadError?.let { loadError ->
            ErrorText(text = loadError)
            SecondaryButton(text = "Retry", onClick = viewModel::loadExistingSession)
            SecondaryButton(text = "Back", onClick = onCancel)
            return@ScreenContainer
        }

        GameSearchField(
            query = form.gameQuery,
            results = uiState.gameResults,
            isSearching = uiState.isSearchingGames,
            error = uiState.gameSearchError,
            hasSelection = form.gameId != null,
            onQueryChange = viewModel::onGameQueryChange,
            onGameSelected = viewModel::selectGame
        )

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
                        enabled = uiState.canSelectVisibility(visibility),
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

            if (uiState.isEditing) {
                Text(
                    text = if (uiState.originalVisibility == SessionVisibility.GROUP) {
                        "Group sessions can't change who can see them."
                    } else {
                        "Sessions can't be moved into a group after they're created."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (form.visibility == SessionVisibility.GROUP) {
            when {
                uiState.groupsError != null -> {
                    ErrorText(text = "Couldn't load groups: ${uiState.groupsError}")
                    SecondaryButton(text = "Retry", onClick = viewModel::loadGroups)
                }

                !uiState.isLoadingGroups && uiState.groups.isEmpty() -> {
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
                    optionIcon = Icons.Default.Groups,
                    onSelected = { group -> viewModel.updateForm { it.copy(groupId = group.id) } },
                    isLoading = uiState.isLoadingGroups,
                    enabled = uiState.canChangeGroup
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
            text = when {
                uiState.isSubmitting && uiState.isEditing -> "Saving…"
                uiState.isSubmitting -> "Creating…"
                uiState.isEditing -> "Save changes"
                else -> "Create session"
            },
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
private fun GameSearchField(
    query: String,
    results: List<GameSearchResult>,
    isSearching: Boolean,
    error: String?,
    hasSelection: Boolean,
    onQueryChange: (String) -> Unit,
    onGameSelected: (GameSearchResult) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val hasSomethingToShow = results.isNotEmpty() || error != null || !isSearching
    val showResults = expanded && query.isNotBlank() && !hasSelection && hasSomethingToShow

    ExposedDropdownMenuBox(
        expanded = showResults,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                onQueryChange(it)
                expanded = true
            },
            singleLine = true,
            label = { Text("Game") },
            placeholder = { Text("Search for a game") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null
                )
            },
            trailingIcon = {
                if (isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                }
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                .fillMaxWidth()
        )

        StyledDropdownMenu(
            expanded = showResults,
            onDismissRequest = { expanded = false }
        ) {
            when {
                error != null -> DropdownMessage(text = error, isError = true)

                results.isEmpty() -> DropdownMessage(text = "No games found")

                else -> results.forEach { game ->
                    StyledDropdownItem(
                        text = highlightMatch(game.name, query),
                        icon = Icons.Default.SportsEsports,
                        imageUrl = game.cover_url,
                        onClick = {
                            onGameSelected(game)
                            expanded = false
                        }
                    )
                }
            }
        }
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
    optionIcon: ImageVector,
    onSelected: (T) -> Unit,
    isLoading: Boolean = false,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    val isInteractive = enabled && !isLoading

    ExposedDropdownMenuBox(
        expanded = expanded && isInteractive,
        onExpandedChange = { expanded = it && isInteractive }
    ) {
        OutlinedTextField(
            value = if (isLoading) "Loading…" else selected?.let(optionLabel).orEmpty(),
            onValueChange = {},
            readOnly = true,
            enabled = isInteractive,
            singleLine = true,
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            trailingIcon = {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )

        StyledDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                StyledDropdownItem(
                    text = AnnotatedString(optionLabel(option)),
                    icon = optionIcon,
                    isSelected = option == selected,
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
private fun ExposedDropdownMenuBoxScope.StyledDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    ExposedDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(12.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        content = content
    )
}

@Composable
private fun StyledDropdownItem(
    text: AnnotatedString,
    icon: ImageVector,
    imageUrl: String? = null,
    onClick: () -> Unit,
    isSelected: Boolean = false
) {
    val primary = MaterialTheme.colorScheme.primary

    DropdownMenuItem(
        text = {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isSelected) primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingIcon = {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                // The icon shows while the cover loads, if it fails, or when there's no cover.
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = primary,
                    modifier = Modifier.size(18.dp)
                )
                if (imageUrl != null) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                }
            }
        },
        trailingIcon = if (isSelected) {
            {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = primary
                )
            }
        } else {
            null
        },
        onClick = onClick,
        modifier = if (isSelected) Modifier.background(primary.copy(alpha = 0.08f)) else Modifier
    )
}

@Composable
private fun DropdownMessage(
    text: String,
    isError: Boolean = false
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun highlightMatch(text: String, query: String): AnnotatedString {
    val needle = query.trim()
    val start = text.indexOf(needle, ignoreCase = true)
    if (needle.isEmpty() || start < 0) return AnnotatedString(text)

    return buildAnnotatedString {
        append(text)
        addStyle(
            style = SpanStyle(
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            ),
            start = start,
            end = start + needle.length
        )
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
