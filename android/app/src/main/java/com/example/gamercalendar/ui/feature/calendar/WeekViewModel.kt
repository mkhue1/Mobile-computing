package com.example.gamercalendar.ui.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.gamercalendar.data.model.CalendarSession
import com.example.gamercalendar.data.model.ExternalSession
import com.kizitonwose.calendar.core.firstDayOfWeekFromLocale
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

private const val MINUTES_PER_DAY = 24 * 60

private const val DEFAULT_START_HOUR = 8

// Overlapping sessions that start at least this far apart are stacked like cascading cards, so
// each keeps most of the column's width. Sessions starting together share the width instead.
// (Overlaps can't be created any more, so this only covers older data.)
private const val CASCADE_MIN_GAP_MINUTES = 45

/**
 * One day of the shown week, with its sessions and timed phone events already laid out as blocks.
 * All-day phone events have no place on the hour grid, so they are listed separately.
 */
internal data class WeekDayUi(
    val date: LocalDate,
    val isToday: Boolean,
    val isSelected: Boolean,
    val blocks: List<PositionedEntry>,
    val allDayEvents: List<ExternalSession>
)

/** Something drawn in the week grid: one of the user's sessions or an event from the phone's calendar. */
internal sealed interface WeekEntry {
    val date: LocalDate // the day it starts
    val lastDate: LocalDate // the last day it still has time on
    val startTime: LocalTime
    val endTime: LocalTime

    data class Game(val session: CalendarSession) : WeekEntry {
        override val date: LocalDate get() = session.date
        override val lastDate: LocalDate get() = session.lastDate()
        override val startTime: LocalTime get() = session.startTime
        override val endTime: LocalTime get() = session.endTime
    }

    data class External(val event: ExternalSession) : WeekEntry {
        override val date: LocalDate get() = event.date
        override val lastDate: LocalDate get() = event.lastDate()
        override val startTime: LocalTime get() = event.startTime
        override val endTime: LocalTime get() = event.endTime
    }
}

/** What the Week view shows: seven days and the hour the grid should open at. */
internal data class WeekUiState(
    val days: List<WeekDayUi>,
    val initialScrollHour: Int
)

/**
 * Builds the Week view's state: which week is shown, each day's blocks (including the pieces of
 * sessions that cross midnight) and where the grid should open. The screen only draws it.
 */
internal class WeekViewModel(
    sessionsByDate: StateFlow<Map<LocalDate, List<CalendarSession>>>,
    externalEvents: StateFlow<Map<LocalDate, List<ExternalSession>>>,
    private val selectedDate: StateFlow<LocalDate>
) : ViewModel() {

    private val firstDayOfWeek = firstDayOfWeekFromLocale()
    private val weekStart = MutableStateFlow(startOfWeek(selectedDate.value, firstDayOfWeek))

    val uiState: StateFlow<WeekUiState> =
        combine(sessionsByDate, externalEvents, selectedDate, weekStart) { sessions, events, selected, start ->
            buildWeek(start, selected, sessions, events)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = buildWeek(
                start = weekStart.value,
                selected = selectedDate.value,
                sessionsByDate = sessionsByDate.value,
                eventsByDate = externalEvents.value
            )
        )

    fun previousWeek() {
        weekStart.update { it.minusWeeks(1) }
    }

    fun nextWeek() {
        weekStart.update { it.plusWeeks(1) }
    }

    /** Shows the week containing the selected day, for example when switching in from Month view. */
    fun showSelectedWeek() {
        weekStart.value = startOfWeek(selectedDate.value, firstDayOfWeek)
    }
}

internal class WeekViewModelFactory(
    private val calendar: CalendarViewModel
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(WeekViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        return WeekViewModel(calendar.sessionsByDate, calendar.externalEvents, calendar.selectedDate) as T
    }
}

// Layout: where each session and phone event sits in the week grid.

private data class Slot(
    val entry: WeekEntry,
    val startMinute: Int,
    val endMinute: Int,
    val continuesFromPrevious: Boolean,
    val continuesToNext: Boolean
)

internal data class PositionedEntry(
    val entry: WeekEntry,
    val startMinute: Int,
    val endMinute: Int,
    val column: Int,
    val columnCount: Int,
    val cascade: Boolean,
    val continuesFromPrevious: Boolean,
    val continuesToNext: Boolean
)

/** The last calendar day on which the session still has time (ending exactly at midnight counts as the day before). */
internal fun CalendarSession.lastDate(): LocalDate =
    Instant.ofEpochMilli(maxOf(endEpochMillis - 1, startEpochMillis))
        .atZone(ZoneId.systemDefault())
        .toLocalDate()

/** The last calendar day on which the event still has time (ending exactly at midnight counts as the day before). */
internal fun ExternalSession.lastDate(): LocalDate =
    if (endTime == LocalTime.MIDNIGHT && endDate.isAfter(date)) endDate.minusDays(1) else maxOf(date, endDate)

/** Puts every entry on each day it covers, so one that crosses midnight shows on both days. */
private fun <T> spreadAcrossDays(
    items: List<T>,
    firstDate: (T) -> LocalDate,
    lastDate: (T) -> LocalDate
): Map<LocalDate, List<T>> {
    val spread = mutableMapOf<LocalDate, MutableList<T>>()
    items.forEach { item ->
        val last = lastDate(item)
        var day = firstDate(item)
        while (!day.isAfter(last)) {
            spread.getOrPut(day) { mutableListOf() }.add(item)
            day = day.plusDays(1)
        }
    }
    return spread
}

/** The part of an entry that falls on [date]: from midnight if it started earlier, to midnight if it ends later. */
private fun WeekEntry.toSlot(date: LocalDate): Slot {
    val last = lastDate
    val start = if (date == this.date) startTime.hour * 60 + startTime.minute else 0
    val end = if (date == last) {
        (endTime.hour * 60 + endTime.minute).let { if (it == 0) MINUTES_PER_DAY else it }
    } else {
        MINUTES_PER_DAY
    }
    return Slot(
        entry = this,
        startMinute = start,
        endMinute = end,
        continuesFromPrevious = date != this.date,
        continuesToNext = date != last
    )
}

/**
 * Works out where each of one day's entries sits horizontally. Entries that overlap in time
 * form a cluster and share the column's width; non-overlapping ones use the full width.
 */
private fun layoutDay(date: LocalDate, entries: List<WeekEntry>): List<PositionedEntry> {
    val slots = entries
        .map { it.toSlot(date) }
        .sortedWith(compareBy({ it.startMinute }, { it.endMinute }))

    val clusters = mutableListOf<MutableList<Slot>>()
    var clusterEnd = 0
    for (slot in slots) {
        if (clusters.isEmpty() || slot.startMinute >= clusterEnd) {
            clusters.add(mutableListOf(slot))
            clusterEnd = slot.endMinute
        } else {
            clusters.last().add(slot)
            clusterEnd = maxOf(clusterEnd, slot.endMinute)
        }
    }
    return clusters.flatMap { assignColumns(it) }
}

/** Gives each entry in an overlapping cluster the first column that is free when it starts. */
private fun assignColumns(cluster: List<Slot>): List<PositionedEntry> {
    val cascade = cluster.size > 1 &&
            cluster.zipWithNext().all { (earlier, later) ->
                later.startMinute - earlier.startMinute >= CASCADE_MIN_GAP_MINUTES
            }

    val columnEnds = mutableListOf<Int>() // minute at which each column becomes free again
    val columns = cluster.map { slot ->
        val free = columnEnds.indexOfFirst { it <= slot.startMinute }
        if (free >= 0) {
            columnEnds[free] = slot.endMinute
            free
        } else {
            columnEnds.add(slot.endMinute)
            columnEnds.lastIndex
        }
    }
    return cluster.mapIndexed { index, slot ->
        PositionedEntry(
            entry = slot.entry,
            startMinute = slot.startMinute,
            endMinute = slot.endMinute,
            column = columns[index],
            columnCount = columnEnds.size,
            cascade = cascade,
            continuesFromPrevious = slot.continuesFromPrevious,
            continuesToNext = slot.continuesToNext
        )
    }
}

private fun startOfWeek(date: LocalDate, firstDayOfWeek: DayOfWeek): LocalDate =
    date.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))

private fun buildWeek(
    start: LocalDate,
    selected: LocalDate,
    sessionsByDate: Map<LocalDate, List<CalendarSession>>,
    eventsByDate: Map<LocalDate, List<ExternalSession>>
): WeekUiState {
    val (allDayEvents, timedEvents) = eventsByDate.values.flatten().partition { it.isAllDay }
    val entries = sessionsByDate.values.flatten().map { WeekEntry.Game(it) } +
            timedEvents.map { WeekEntry.External(it) }
    val entriesByDay = spreadAcrossDays(entries, { it.date }, { it.lastDate })
    val allDayByDay = spreadAcrossDays(allDayEvents, { it.date }, { it.lastDate() })

    val today = LocalDate.now()
    val days = List(7) { offset ->
        val date = start.plusDays(offset.toLong())
        WeekDayUi(
            date = date,
            isToday = date == today,
            isSelected = date == selected,
            blocks = layoutDay(date, entriesByDay[date].orEmpty()),
            allDayEvents = allDayByDay[date].orEmpty()
        )
    }
    return WeekUiState(days = days, initialScrollHour = initialScrollHour(days))
}

/** Opens the grid an hour before the week's earliest block, or near 8am for an empty week. */
private fun initialScrollHour(days: List<WeekDayUi>): Int {
    val earliest = days
        .flatMap { it.blocks }
        .minOfOrNull { it.startMinute / 60 }
        ?: DEFAULT_START_HOUR
    return (earliest - 1).coerceAtLeast(0)
}
