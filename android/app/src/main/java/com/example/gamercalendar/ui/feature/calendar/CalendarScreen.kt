package com.example.gamercalendar.ui.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gamercalendar.data.model.CalendarSession
import com.example.gamercalendar.data.model.ExternalSession
import com.example.gamercalendar.ui.feature.calendar.CalendarViewModel
import com.kizitonwose.calendar.compose.HorizontalCalendar
import com.kizitonwose.calendar.compose.rememberCalendarState
import com.kizitonwose.calendar.compose.WeekCalendar
import com.kizitonwose.calendar.compose.weekcalendar.rememberWeekCalendarState
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.core.daysOfWeek
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

private enum class CalendarViewMode(val label: String) {
    MONTH("Month"),
    WEEK("Week")
}

@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    val currentMonth = remember { YearMonth.now() }
    val startMonth = remember { currentMonth.minusMonths(12) }
    val endMonth = remember { currentMonth.plusMonths(12) }
    val rangeStart = remember { startMonth.atDay(1) }
    val rangeEnd = remember { endMonth.atEndOfMonth() }
    val weekDays = remember { daysOfWeek() }

    val monthState = rememberCalendarState(
        startMonth = startMonth,
        endMonth = endMonth,
        firstVisibleMonth = currentMonth,
        firstDayOfWeek = weekDays.first()
    )
    val weekState = rememberWeekCalendarState(
        startDate = rangeStart,
        endDate = rangeEnd,
        firstVisibleWeekDate = LocalDate.now(),
        firstDayOfWeek = weekDays.first()
    )

    val coroutineScope = rememberCoroutineScope()
    var viewMode by remember { mutableStateOf(CalendarViewMode.MONTH) } // month is the default
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }

    // When the view changes, jump the new view to the day the user had selected.
    LaunchedEffect(viewMode) {
        when (viewMode) {
            CalendarViewMode.MONTH ->
                monthState.scrollToMonth(YearMonth.from(selectedDate).coerceIn(startMonth, endMonth))
            CalendarViewMode.WEEK ->
                weekState.scrollToWeek(selectedDate.coerceIn(rangeStart, rangeEnd))
        }
    }

    val visibleMonth = monthState.firstVisibleMonth.yearMonth
    val headerTitle = when (viewMode) {
        CalendarViewMode.MONTH -> monthTitle(visibleMonth)
        CalendarViewMode.WEEK -> {
            val days = weekState.firstVisibleWeek.days
            rangeTitle(days.first().date, days.last().date)
        }
    }

    // when viewmode changes to week, maintain record of current month
    val anchorMonth = when (viewMode) {
        CalendarViewMode.MONTH -> visibleMonth
        CalendarViewMode.WEEK -> YearMonth.from(weekState.firstVisibleWeek.days.first().date)
    }

    LaunchedEffect(anchorMonth) {
        viewModel.loadEventsAround(anchorMonth)
    }


    val onPrevious: () -> Unit = {
        coroutineScope.launch {
            when (viewMode) {
                CalendarViewMode.MONTH -> {
                    if (visibleMonth.isAfter(startMonth)) {
                        monthState.animateScrollToMonth(visibleMonth.minusMonths(1))
                    }
                }
                CalendarViewMode.WEEK -> {
                    val weekStart = weekState.firstVisibleWeek.days.first().date
                    if (weekStart.isAfter(rangeStart)) {
                        weekState.animateScrollToWeek(weekStart.minusWeeks(1).coerceAtLeast(rangeStart))
                    }
                }
            }
        }
    }

    val onNext: () -> Unit = {
        coroutineScope.launch {
            when (viewMode) {
                CalendarViewMode.MONTH -> {
                    if (visibleMonth.isBefore(endMonth)) {
                        monthState.animateScrollToMonth(visibleMonth.plusMonths(1))
                    }
                }
                CalendarViewMode.WEEK -> {
                    val nextWeekStart = weekState.firstVisibleWeek.days.first().date.plusWeeks(1)
                    if (!nextWeekStart.isAfter(rangeEnd)) {
                        weekState.animateScrollToWeek(nextWeekStart)
                    }
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        ViewModeSelector(
            selected = viewMode,
            onSelect = { viewMode = it }
        )
        Spacer(Modifier.height(12.dp))

        CalendarHeader(
            title = headerTitle,
            onPrevious = onPrevious,
            onNext = onNext
        )
        Spacer(Modifier.height(12.dp))

        WeekdayLabels(weekDays)
        Spacer(Modifier.height(8.dp))

        when (viewMode) {
            CalendarViewMode.MONTH -> {
                HorizontalCalendar(
                    state = monthState,
                    dayContent = { day ->
                        DayCell(
                            date = day.date,
                            isInRange = day.position == DayPosition.MonthDate,
                            isSelected = day.date == selectedDate,
                            hasSessions = uiState.sessionsByDate.containsKey(day.date),
                            hasExternalEvents = uiState.events.containsKey(day.date),
                            onClick = { selectedDate = day.date }
                        )
                    }
                )
            }

            CalendarViewMode.WEEK -> {
                WeekCalendar(
                    state = weekState,
                    dayContent = { day ->
                        DayCell(
                            date = day.date,
                            isInRange = true,
                            isSelected = day.date == selectedDate,
                            hasSessions = uiState.sessionsByDate.containsKey(day.date),
                            hasExternalEvents = uiState.events.containsKey(day.date),
                            onClick = { selectedDate = day.date }
                        )
                    }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        if (uiState.isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
        }

        uiState.errorMessage?.let { message ->
            ErrorBanner(message = message, onRetry = viewModel::refresh)
            Spacer(Modifier.height(12.dp))
        }

        SelectedDaySessions(
            date = selectedDate,
            sessions = uiState.sessionsByDate[selectedDate].orEmpty(),
            externalSessions = uiState.events[selectedDate].orEmpty()
        )
    }
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
private fun CalendarHeader(
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
private fun WeekdayLabels(weekDays: List<DayOfWeek>) {
    Row(modifier = Modifier.fillMaxWidth()) {
        weekDays.forEach { day ->
            Text(
                text = day.getDisplayName(TextStyle.SHORT, Locale.getDefault()).uppercase(),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** One day in the month or week grid. [isInRange] is false for the faded days of neighbouring months. */
@Composable
private fun DayCell(
    date: LocalDate,
    isInRange: Boolean,
    isSelected: Boolean,
    hasSessions: Boolean,
    hasExternalEvents: Boolean,
    onClick: () -> Unit
) {
    val isToday = date == LocalDate.now()

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(CircleShape)
            .background(
                when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    isToday -> MaterialTheme.colorScheme.primaryContainer
                    else -> Color.Transparent
                }
            )
            .clickable(enabled = isInRange, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = date.dayOfMonth.toString(),
                fontSize = 14.sp,
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    isSelected -> MaterialTheme.colorScheme.onPrimary
                    !isInRange -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                    isToday -> MaterialTheme.colorScheme.onPrimaryContainer
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
            if (hasSessions && isInRange) {
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.primary
                        )
                )
            }
            if (hasExternalEvents && isInRange) {
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                )
            }
        }
    }
}

@Composable
private fun SelectedDaySessions(
    date: LocalDate,
    sessions: List<CalendarSession>,
    externalSessions: List<ExternalSession>,
) {
    Text(
        text = date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(Modifier.height(8.dp))

    if (sessions.isEmpty()) {
        Text(
            text = "No sessions on this day",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        sessions.forEach { session ->
            SessionCard(session)
            Spacer(Modifier.height(8.dp))
        }
    }
    Spacer(Modifier.height(8.dp))

    Text(
        text = "Other events:",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(Modifier.height(8.dp))

    if (externalSessions.isEmpty()) {
        Text(
            text = "No external events",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        externalSessions.forEach { session ->
            ExternalSessionCard(session)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SessionCard(session: CalendarSession) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = session.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${session.gameName} \u00B7 ${if (session.isOnline) "Online" else "In person"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${session.startTime.format(timeFormatter)} \u2013 ${session.endTime.format(timeFormatter)}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = playersLabel(session),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            session.locationName?.let { location ->
                Text(
                    text = location,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ExternalSessionCard(session: ExternalSession) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = session.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${session.startTime.format(timeFormatter)} \u2013 ${session.endTime.format(timeFormatter)}",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private fun playersLabel(session: CalendarSession): String {
    val limit = session.playerLimit
    return when {
        limit != null -> "${session.playerCount}/$limit players"
        session.playerCount == 1 -> "1 player"
        else -> "${session.playerCount} players"
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

private fun monthTitle(month: YearMonth): String =
    "${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year}"

private fun rangeTitle(start: LocalDate, end: LocalDate): String {
    val locale = Locale.getDefault()
    val startText = start.format(DateTimeFormatter.ofPattern("d MMM", locale))
    val endText = end.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale))
    return "$startText \u2013 $endText"
}