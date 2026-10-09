package com.example.gamercalendar.ui.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gamercalendar.data.model.CalendarSession
import java.time.LocalDate

@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    onSessionClick: (sessionId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()

    // The calendar leaves composition while a session's details are open, so this runs again
    // each time the user comes back and picks up any edits, cancellations or leaves.
    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = if (viewMode == CalendarViewMode.WEEK) 8.dp else 16.dp,
                vertical = 16.dp
            )
    ) {
        ViewModeSelector(
            selected = viewMode,
            onSelect = viewModel::setViewMode
        )
        Spacer(Modifier.height(12.dp))

        if (uiState.isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
        }

        uiState.errorMessage?.let { message ->
            ErrorBanner(message = message, onRetry = viewModel::refresh)
            Spacer(Modifier.height(12.dp))
        }

        when (viewMode) {
            CalendarViewMode.MONTH -> MonthTab(calendar = viewModel, onSessionClick = onSessionClick)
            CalendarViewMode.WEEK -> WeekTab(calendar = viewModel, onSessionClick = onSessionClick)
        }
    }
}

// Each view gets its own view model, built on the shared one. They live as long as the
// calendar's place in the back stack, so a view keeps its state while the other one is showing.
@Composable
private fun MonthTab(
    calendar: CalendarViewModel,
    onSessionClick: (sessionId: String) -> Unit
) {
    val monthViewModel: MonthViewModel = viewModel(factory = MonthViewModelFactory(calendar))
    MonthScreen(
        viewModel = monthViewModel,
        onSelectDate = calendar::selectDate,
        onSessionClick = onSessionClick
    )
}

@Composable
private fun WeekTab(
    calendar: CalendarViewModel,
    onSessionClick: (sessionId: String) -> Unit
) {
    val weekViewModel: WeekViewModel = viewModel(factory = WeekViewModelFactory(calendar))
    WeekScreen(
        viewModel = weekViewModel,
        onSelectDate = calendar::selectDate,
        onSessionClick = onSessionClick
    )
}

@Composable
private fun ViewModeSelector(
    selected: CalendarViewMode,
    onSelect: (CalendarViewMode) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CalendarViewMode.entries.forEach { mode ->
            FilterChip(
                selected = mode == selected,
                onClick = { onSelect(mode) },
                label = { Text(mode.label) }
            )
        }
    }
}

@Composable
internal fun CalendarHeader(
    title: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious) {
            Text(text = "\u2190", fontSize = 20.sp) // left arrow
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        IconButton(onClick = onNext) {
            Text(text = "\u2192", fontSize = 20.sp) // right arrow
        }
    }
}

@Composable
private fun ErrorBanner(
    message: String,
    onRetry: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = message,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.error
        )
        TextButton(onClick = onRetry) {
            Text("Retry")
        }
    }
}

// Shared by the Month and Week views.

// Each game gets one of these colours, so sessions of the same game look alike at a glance.
private val GAME_COLORS = listOf(
    Color(0xFFEE5428), // orange
    Color(0xFFDD55B5), // magenta
    Color(0xFF99CC55), // green
    Color(0xFF4F7AE3), // blue
    Color(0xFFF2A93B), // amber
    Color(0xFF3FB8AF), // teal
    Color(0xFF9B6BDF), // purple
    Color(0xFFE5646E)  // coral
)

internal fun gameColor(session: CalendarSession): Color =
    GAME_COLORS[Math.floorMod(session.gameName.hashCode(), GAME_COLORS.size)]

/** Dark text on light colours and white text on dark ones, for readable contrast. */
internal fun readableOn(background: Color): Color =
    if (background.luminance() > 0.179f) Color(0xFF1B1B1F) else Color.White

/**
 * Text that never splits a word across two lines. Compose breaks a word that is wider than the
 * available width, giving "Teamfigh" / "t Tactics". This lays the text out normally, checks
 * whether a word was split, and if so shrinks the font a little and lays it out again, until no
 * word is split or [minFontSize] is reached. It looks at the real layout, so it is correct for
 * whatever font and font scale the app is using.
 */
@Composable
internal fun WordSafeText(
    text: String,
    fontSize: TextUnit,
    minFontSize: TextUnit,
    maxLines: Int,
    color: Color,
    modifier: Modifier = Modifier,
    lineHeight: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null
) {
    var size by remember(text, fontSize) { mutableStateOf(fontSize) }

    Text(
        text = text,
        modifier = modifier,
        fontSize = size,
        lineHeight = lineHeight,
        fontWeight = fontWeight,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        color = color,
        onTextLayout = { layout ->
            if (size.value > minFontSize.value && layout.splitsAWord(text)) {
                size = (size.value - 0.5f).sp
            }
        }
    )
}

/** True if any line ends in the middle of a word (not at a space or after a hyphen). */
private fun TextLayoutResult.splitsAWord(text: String): Boolean {
    for (line in 0 until lineCount - 1) {
        val end = getLineEnd(line)
        if (end in 1 until text.length &&
            !text[end - 1].isWhitespace() && text[end - 1] != '-' &&
            !text[end].isWhitespace()
        ) {
            return true
        }
    }
    return false
}

@Composable
internal fun DayNumber(
    date: LocalDate,
    isInRange: Boolean,
    isSelected: Boolean
) {
    val isToday = date == LocalDate.now()

    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(
                when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    isToday -> MaterialTheme.colorScheme.primaryContainer
                    else -> Color.Transparent
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            fontSize = 12.sp,
            fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
            color = when {
                isSelected -> MaterialTheme.colorScheme.onPrimary
                !isInRange -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                isToday -> MaterialTheme.colorScheme.onPrimaryContainer
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
    }
}