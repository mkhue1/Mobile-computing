package com.example.gamercalendar.ui.feature.calendar

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gamercalendar.data.model.CalendarSession
import com.example.gamercalendar.data.model.ExternalSession
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.ceil

// Week timeline sizing. One hour is HOUR_HEIGHT tall, and each day is DAY_COLUMN_WIDTH wide, so
// the week scrolls sideways and a block has room for several lines of text.
private val HOUR_HEIGHT = 88.dp

private val DAY_COLUMN_WIDTH = 112.dp

private val TIME_GUTTER_WIDTH = 34.dp

private val TIMELINE_VIEWPORT_HEIGHT = 480.dp

private val MIN_BLOCK_HEIGHT = 24.dp

private val CASCADE_INDENT = 10.dp

// Blocks narrower than this (only possible for old overlapping data) show just a title.
private val NARROW_BLOCK_WIDTH = 64.dp

// Text metrics used to decide how many lines a block can show.
private const val BLOCK_PADDING_H = 6f

private const val BLOCK_PADDING_V = 4f

private const val TEXT_LINE_HEIGHT = 14f

private const val TEXT_LINE_GAP = 2f

private const val TITLE_CHAR_WIDTH = 6.6f // average width of one title character in dp, rounded up

// How many all-day phone events are listed under a day's date before the rest collapse into "+N".
private const val MAX_ALL_DAY_LABELS = 1

private val blockTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

@Composable
internal fun WeekScreen(
    viewModel: WeekViewModel,
    onSelectDate: (LocalDate) -> Unit,
    onSessionClick: (sessionId: String) -> Unit,
    onVisibleMonthChange: (YearMonth) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val firstDate = uiState.days.first().date

    // Open on the week of the selected day, for example when switching in from Month view.
    LaunchedEffect(Unit) { viewModel.showSelectedWeek() }

    // Load the phone's events around the month this week starts in.
    LaunchedEffect(firstDate) { onVisibleMonthChange(YearMonth.from(firstDate)) }

    Column(modifier = modifier) {
        CalendarHeader(
            title = rangeTitle(uiState.days.first().date, uiState.days.last().date),
            onPrevious = viewModel::previousWeek,
            onNext = viewModel::nextWeek
        )
        Spacer(Modifier.height(12.dp))

        WeekTimeline(
            state = uiState,
            onSelectDate = onSelectDate,
            onSessionClick = onSessionClick
        )
    }
}

/**
 * The shown week as a time grid. The day columns scroll sideways, with the date headers moving
 * together with them, while the hour labels stay in place. Tapping a block opens the session.
 */
@Composable
private fun WeekTimeline(
    state: WeekUiState,
    onSelectDate: (LocalDate) -> Unit,
    onSessionClick: (sessionId: String) -> Unit
) {
    val verticalState = rememberScrollState()
    // One state drives both the header row and the grid, so they always move together.
    val horizontalState = rememberScrollState()
    val density = LocalDensity.current
    val lineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val firstDate = state.days.first().date

    // Open near the week's first session (or 8am) so the user doesn't land at midnight.
    LaunchedEffect(firstDate, state.initialScrollHour) {
        verticalState.scrollTo(with(density) { (HOUR_HEIGHT * state.initialScrollHour).roundToPx() })
    }

    // When the week changes, bring the selected day (or the first day) into view.
    LaunchedEffect(firstDate) {
        val index = state.days.indexOfFirst { it.isSelected }.coerceAtLeast(0)
        horizontalState.scrollTo(with(density) { (DAY_COLUMN_WIDTH * index).roundToPx() })
    }

    Column {
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(TIME_GUTTER_WIDTH))
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(horizontalState)
            ) {
                state.days.forEach { day ->
                    DayHeader(
                        day = day,
                        onClick = { onSelectDate(day.date) },
                        modifier = Modifier.width(DAY_COLUMN_WIDTH)
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(TIMELINE_VIEWPORT_HEIGHT)
                .verticalScroll(verticalState)
        ) {
            HourGutter()

            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(horizontalState)
            ) {
                Row(
                    modifier = Modifier
                        .height(HOUR_HEIGHT * 24)
                        .drawBehind {
                            val hourPx = HOUR_HEIGHT.toPx()
                            repeat(24) { hour ->
                                val y = hour * hourPx
                                drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                            }
                        }
                ) {
                    state.days.forEach { day ->
                        DayColumn(
                            day = day,
                            onSessionClick = onSessionClick,
                            modifier = Modifier.width(DAY_COLUMN_WIDTH)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayHeader(
    day: WeekDayUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(horizontal = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (day.isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).uppercase(),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        DayNumber(date = day.date, isInRange = true, isSelected = day.isSelected)

        // All-day phone events have no hour to sit at, so they are listed under the date.
        day.allDayEvents.take(MAX_ALL_DAY_LABELS).forEach { event ->
            ExternalEventLabel(title = event.title, modifier = Modifier.padding(horizontal = 2.dp))
        }
        val hidden = day.allDayEvents.size - MAX_ALL_DAY_LABELS
        if (hidden > 0) {
            Text(
                text = "+$hidden",
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HourGutter() {
    val use24Hour = DateFormat.is24HourFormat(LocalContext.current)

    Column(modifier = Modifier.width(TIME_GUTTER_WIDTH)) {
        repeat(24) { hour ->
            Box(modifier = Modifier.height(HOUR_HEIGHT)) {
                if (hour > 0) {
                    Text(
                        text = hourLabel(hour, use24Hour),
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = (-6).dp)
                            .padding(end = 4.dp),
                        textAlign = TextAlign.End,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DayColumn(
    day: WeekDayUi,
    onSessionClick: (sessionId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val dividerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .background(
                when {
                    day.isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    day.isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.04f)
                    else -> Color.Transparent
                }
            )
            .drawBehind {
                drawLine(dividerColor, Offset(size.width, 0f), Offset(size.width, size.height), strokeWidth = 1f)
            }
    ) {
        day.blocks.forEach { item ->
            // Cascading blocks are shifted right by a small indent and run to the column's edge;
            // side-by-side blocks split the column evenly.
            val indent = minOf(CASCADE_INDENT, maxWidth / (item.columnCount + 2))
            val blockX = if (item.cascade) indent * item.column else maxWidth / item.columnCount * item.column
            val blockWidth = if (item.cascade) maxWidth - blockX else maxWidth / item.columnCount
            val blockHeight = maxOf(
                HOUR_HEIGHT * ((item.endMinute - item.startMinute) / 60f),
                MIN_BLOCK_HEIGHT
            )

            val blockModifier = Modifier
                .offset(x = blockX, y = HOUR_HEIGHT * (item.startMinute / 60f))
                .width(blockWidth)
                .height(blockHeight)

            when (val entry = item.entry) {
                is WeekEntry.Game -> SessionBlock(
                    session = entry.session,
                    blockWidth = blockWidth,
                    blockHeight = blockHeight,
                    depth = if (item.cascade) item.column else 0,
                    continuesFromPrevious = item.continuesFromPrevious,
                    continuesToNext = item.continuesToNext,
                    onClick = { onSessionClick(entry.session.id) },
                    modifier = blockModifier
                )

                is WeekEntry.External -> ExternalEventBlock(
                    event = entry.event,
                    blockHeight = blockHeight,
                    continuesFromPrevious = item.continuesFromPrevious,
                    continuesToNext = item.continuesToNext,
                    modifier = blockModifier
                )
            }
        }

    }
}

/**
 * A coloured card for one session: the title first, then as many detail rows (game, place,
 * players) as the block's height allows, so a short session shows less and a long one shows more.
 * A session that crosses midnight is drawn as two pieces; the edge where it carries on into the
 * neighbouring day is cut square and carries a short note, so the pieces read as one session.
 */
@Composable
private fun SessionBlock(
    session: CalendarSession,
    blockWidth: Dp,
    blockHeight: Dp,
    depth: Int,
    continuesFromPrevious: Boolean,
    continuesToNext: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val continued = continuesFromPrevious || continuesToNext
    val corner = 8.dp
    val shape = RoundedCornerShape(
        topStart = if (continuesFromPrevious) 0.dp else corner,
        topEnd = if (continuesFromPrevious) 0.dp else corner,
        bottomEnd = if (continuesToNext) 0.dp else corner,
        bottomStart = if (continuesToNext) 0.dp else corner
    )
    val background = lerp(gameColor(session), Color.White, 0.2f * depth)
    val content = readableOn(background)
    val narrow = blockWidth < NARROW_BLOCK_WIDTH

    // How many lines of text fit, then split them between the note, the title and the details.
    val lineBudget = (
            (blockHeight.value - 2 * BLOCK_PADDING_V + TEXT_LINE_GAP) / (TEXT_LINE_HEIGHT + TEXT_LINE_GAP)
            ).toInt().coerceAtLeast(1)
    val note = continuationNote(session, continuesFromPrevious, continuesToNext)
    val noteLines = if (note != null && !narrow && lineBudget >= 2) 1 else 0
    val titleLinesNeeded = if (narrow) {
        1
    } else {
        val textWidth = blockWidth.value - 2f - 2 * BLOCK_PADDING_H
        ceil(session.title.length * TITLE_CHAR_WIDTH / textWidth).toInt().coerceIn(1, 4)
    }
    val titleLines = minOf(titleLinesNeeded, lineBudget - noteLines)
    val details = if (narrow) {
        emptyList()
    } else {
        detailRows(session).take(lineBudget - noteLines - titleLines)
    }

    Column(
        modifier = modifier
            .padding(1.dp)
            .clip(shape)
            .background(background)
            // An outline would draw a seam along the edge where the session carries on.
            .then(
                if (continued) Modifier
                else Modifier.border(1.dp, MaterialTheme.colorScheme.background, shape)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = BLOCK_PADDING_H.dp, vertical = BLOCK_PADDING_V.dp),
        verticalArrangement = Arrangement.spacedBy(TEXT_LINE_GAP.dp)
    ) {
        if (noteLines == 1 && continuesFromPrevious) {
            note?.let { ContinuationNote(text = it, color = content) }
        }

        WordSafeText(
            text = session.title,
            fontSize = 12.sp,
            minFontSize = 9.sp,
            lineHeight = TEXT_LINE_HEIGHT.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = titleLines,
            color = content
        )
        details.forEach { (icon, text) ->
            BlockInfoRow(icon = icon, text = text, color = content)
        }

        if (noteLines == 1 && !continuesFromPrevious) {
            note?.let { ContinuationNote(text = it, color = content) }
        }
    }
}

/**
 * A grey card for an event from the phone's calendar: the title, then its times if there is room.
 * It isn't one of the app's sessions, so tapping it does nothing.
 */
@Composable
private fun ExternalEventBlock(
    event: ExternalSession,
    blockHeight: Dp,
    continuesFromPrevious: Boolean,
    continuesToNext: Boolean,
    modifier: Modifier = Modifier
) {
    val corner = 8.dp
    val shape = RoundedCornerShape(
        topStart = if (continuesFromPrevious) 0.dp else corner,
        topEnd = if (continuesFromPrevious) 0.dp else corner,
        bottomEnd = if (continuesToNext) 0.dp else corner,
        bottomStart = if (continuesToNext) 0.dp else corner
    )
    val content = MaterialTheme.colorScheme.onSurfaceVariant
    val lineBudget = (
            (blockHeight.value - 2 * BLOCK_PADDING_V + TEXT_LINE_GAP) / (TEXT_LINE_HEIGHT + TEXT_LINE_GAP)
            ).toInt().coerceAtLeast(1)
    val showTimes = lineBudget >= 2

    Column(
        modifier = modifier
            .padding(1.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = BLOCK_PADDING_H.dp, vertical = BLOCK_PADDING_V.dp),
        verticalArrangement = Arrangement.spacedBy(TEXT_LINE_GAP.dp)
    ) {
        WordSafeText(
            text = event.title,
            fontSize = 12.sp,
            minFontSize = 9.sp,
            lineHeight = TEXT_LINE_HEIGHT.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = (if (showTimes) lineBudget - 1 else 1).coerceAtMost(3),
            color = content
        )
        if (showTimes) {
            Text(
                text = "${event.startTime.format(blockTimeFormatter)} – ${event.endTime.format(blockTimeFormatter)}",
                fontSize = 11.sp,
                lineHeight = TEXT_LINE_HEIGHT.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = content
            )
        }
    }
}

@Composable
private fun ContinuationNote(text: String, color: Color) {
    Text(
        text = text,
        fontSize = 10.sp,
        lineHeight = TEXT_LINE_HEIGHT.sp,
        fontStyle = FontStyle.Italic,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = color.copy(alpha = 0.8f)
    )
}

/** "↑ from Fri" on the part that carries on from yesterday, "until Sat ↓" on the part that runs into tomorrow. */
private fun continuationNote(
    session: CalendarSession,
    fromPrevious: Boolean,
    toNext: Boolean
): String? {
    val locale = Locale.getDefault()
    return when {
        fromPrevious && toNext -> "all day"
        fromPrevious -> "\u2191 from ${session.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)}"
        toNext -> "until ${session.lastDate().dayOfWeek.getDisplayName(TextStyle.SHORT, locale)} \u2193"
        else -> null
    }
}

@Composable
private fun BlockInfoRow(
    icon: ImageVector,
    text: String,
    color: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(12.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = text,
            fontSize = 11.sp,
            lineHeight = TEXT_LINE_HEIGHT.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = color
        )
    }
}

/** Detail rows in priority order: the game (when the title is custom), where, and players. */
private fun detailRows(session: CalendarSession): List<Pair<ImageVector, String>> = buildList {
    if (session.title != session.gameName) {
        add(Icons.Default.SportsEsports to session.gameName)
    }
    add(Icons.Default.Place to whereText(session))
    add(Icons.Default.Person to playersText(session))
}

private fun whereText(session: CalendarSession): String =
    if (session.isOnline) "Online" else session.locationName ?: "In person"

private fun playersText(session: CalendarSession): String {
    val limit = session.playerLimit
    return when {
        limit != null -> "${session.playerCount}/$limit players"
        session.playerCount == 1 -> "1 player"
        else -> "${session.playerCount} players"
    }
}

private fun hourLabel(hour: Int, use24Hour: Boolean): String = when {
    use24Hour -> "%02d:00".format(hour)
    hour == 12 -> "12 PM"
    hour > 12 -> "${hour - 12} PM"
    else -> "$hour AM"
}

private fun rangeTitle(start: LocalDate, end: LocalDate): String {
    val locale = Locale.getDefault()
    val startText = start.format(DateTimeFormatter.ofPattern("d MMM", locale))
    val endText = end.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale))
    return "$startText \u2013 $endText"
}