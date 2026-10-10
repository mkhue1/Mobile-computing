package com.example.gamercalendar.ui.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gamercalendar.data.model.CalendarSession
import com.example.gamercalendar.ui.components.cards.SessionCard
import com.kizitonwose.calendar.compose.HorizontalCalendar
import com.kizitonwose.calendar.compose.rememberCalendarState
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.core.daysOfWeek
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch

// How many session labels fit in a month cell before the rest collapse into "+N".
private const val MONTH_MAX_LABELS = 2

@Composable
internal fun MonthScreen(
    viewModel: MonthViewModel,
    onSelectDate: (LocalDate) -> Unit,
    onSessionClick: (sessionId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val weekDays = remember { daysOfWeek() }

    val calendarState = rememberCalendarState(
        startMonth = viewModel.startMonth,
        endMonth = viewModel.endMonth,
        firstVisibleMonth = viewModel.monthToShow(),
        firstDayOfWeek = weekDays.first()
    )
    val coroutineScope = rememberCoroutineScope()
    val visibleMonth = calendarState.firstVisibleMonth.yearMonth

    Column(modifier = modifier) {
        CalendarHeader(
            title = monthTitle(visibleMonth),
            onPrevious = {
                if (visibleMonth.isAfter(viewModel.startMonth)) {
                    coroutineScope.launch {
                        calendarState.animateScrollToMonth(visibleMonth.minusMonths(1))
                    }
                }
            },
            onNext = {
                if (visibleMonth.isBefore(viewModel.endMonth)) {
                    coroutineScope.launch {
                        calendarState.animateScrollToMonth(visibleMonth.plusMonths(1))
                    }
                }
            }
        )
        Spacer(Modifier.height(12.dp))

        WeekdayLabels(weekDays)
        Spacer(Modifier.height(8.dp))

        HorizontalCalendar(
            state = calendarState,
            dayContent = { day ->
                DayCell(
                    date = day.date,
                    isInRange = day.position == DayPosition.MonthDate,
                    isSelected = day.date == uiState.selectedDate,
                    sessions = uiState.sessionsByDate[day.date].orEmpty(),
                    maxLabels = MONTH_MAX_LABELS,
                    onClick = { onSelectDate(day.date) }
                )
            }
        )
        Spacer(Modifier.height(16.dp))

        SelectedDaySessions(
            date = uiState.selectedDate,
            sessions = uiState.selectedDaySessions,
            onSessionClick = onSessionClick
        )
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

/**
 * One day in the month grid: the date, then a short coloured label per session so a day's plans
 * can be read at a glance. [isInRange] is false for the faded days of neighbouring months.
 */
@Composable
private fun DayCell(
    date: LocalDate,
    isInRange: Boolean,
    isSelected: Boolean,
    sessions: List<CalendarSession>,
    maxLabels: Int,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .padding(1.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else Color.Transparent
            )
            .clickable(enabled = isInRange, onClick = onClick)
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        DayNumber(date = date, isInRange = isInRange, isSelected = isSelected)

        if (isInRange) {
            DayLabels(sessions = sessions, maxLabels = maxLabels)
        }
    }
}

@Composable
private fun DayLabels(
    sessions: List<CalendarSession>,
    maxLabels: Int
) {
    sessions.take(maxLabels).forEach { session ->
        val background = gameColor(session)
        WordSafeText(
            text = session.title,
            fontSize = 9.sp,
            minFontSize = 7.sp,
            lineHeight = 11.sp,
            maxLines = 2,
            color = readableOn(background),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(background)
                .padding(horizontal = 2.dp, vertical = 1.dp)
        )
    }

    val hidden = sessions.size - maxLabels
    if (hidden > 0) {
        Text(
            text = "+$hidden",
            modifier = Modifier.padding(top = 2.dp),
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SelectedDaySessions(
    date: LocalDate,
    sessions: List<CalendarSession>,
    onSessionClick: (sessionId: String) -> Unit
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
            SessionCard(
                session = session.session,
                startEpochMillis = session.startEpochMillis,
                endEpochMillis = session.endEpochMillis,
                modifier = Modifier.fillMaxWidth(),
                onClick = { onSessionClick(session.id) }
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun monthTitle(month: YearMonth): String =
    "${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year}"
