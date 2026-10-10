package com.example.gamercalendar.data.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.provider.CalendarContract
import android.util.Log
import com.example.gamercalendar.data.model.SessionStatus
import com.example.gamercalendar.data.model.SessionType
import com.example.gamercalendar.data.api.ApiService
import com.example.gamercalendar.data.model.CalendarSession
import com.example.gamercalendar.data.model.ExternalCalendar
import com.example.gamercalendar.data.model.ExternalSession
import com.example.gamercalendar.data.model.GamingSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset


// Projection arrays. Creating indices for these arrays instead of doing
// dynamic lookups improves performance.
// see https://developer.android.com/identity/providers/calendar-provider :)
private val EVENT_PROJECTION: Array<String> = arrayOf(
    CalendarContract.Instances.EVENT_ID, // 0
    CalendarContract.Instances.BEGIN, // 1
    CalendarContract.Instances.END, // 2
    CalendarContract.Instances.TITLE, // 3
    CalendarContract.Instances.ALL_DAY // 4
)

// The indices for the projection array above.
private const val PROJECTION_ID_INDEX: Int = 0
private const val PROJECTION_BEGIN_INDEX: Int = 1
private const val PROJECTION_END_INDEX: Int = 2
private const val PROJECTION_TITLE_INDEX: Int = 3
private const val PROJECTION_ALL_DAY_INDEX: Int = 4

private val CALENDAR_PROJECTION: Array<String> = arrayOf(
    CalendarContract.Calendars._ID, // 0
    CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, // 1
    CalendarContract.Calendars.ACCOUNT_NAME, // 2
    CalendarContract.Calendars.CALENDAR_COLOR, // 3
    CalendarContract.Calendars.OWNER_ACCOUNT // 4
)

// The indices for the calendar projection array above.
private const val CALENDAR_ID_INDEX: Int = 0
private const val CALENDAR_NAME_INDEX: Int = 1
private const val CALENDAR_ACCOUNT_INDEX: Int = 2
private const val CALENDAR_COLOR_INDEX: Int = 3
private const val CALENDAR_OWNER_INDEX: Int = 4


class CalendarRepository(private val api: ApiService, private val contentResolver: ContentResolver) {

    /** Loads the user's sessions, excluding cancel ones, sorted by date and start time. */
    suspend fun getMySessions(): List<CalendarSession> {
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        return api.getSessions()
            .filter { it.status != SessionStatus.CANCELLED }
            .map { it.toCalendarSession(zone) }
            .filter { it.endEpochMillis > now }
            .sortedWith(compareBy({ it.date }, { it.startTime }))
    }

    suspend fun searchPublicSessions(query: String): List<CalendarSession> {
        val zone = ZoneId.systemDefault()
        return api.searchPublicSessions(query.trim().ifEmpty { null })
            .map { it.toCalendarSession(zone) }
    }

    /**
     * The server stores times in UTC. Converting to the phone's timezone here means a late-night
     * session lands on the calendar day the user actually experiences.
     */
    private fun GamingSession.toCalendarSession(zone: ZoneId): CalendarSession {
        val start = OffsetDateTime.parse(start_at).atZoneSameInstant(zone)
        val end = OffsetDateTime.parse(end_at).atZoneSameInstant(zone)
        val gameName = game.name

        return CalendarSession(
            id = id,
            title = title?.takeIf { it.isNotBlank() } ?: gameName,
            gameName = gameName,
            date = start.toLocalDate(),
            startTime = start.toLocalTime(),
            endTime = end.toLocalTime(),
            session = this,
            startEpochMillis = start.toInstant().toEpochMilli(),
            endEpochMillis = end.toInstant().toEpochMilli(),
            isOnline = session_type == SessionType.ONLINE,
            locationName = location_name,
            playerCount = player_count,
            playerLimit = player_limit
        )
    }

    /** Load details for all calendars on the phone **/
    suspend fun getCalendars(): List<ExternalCalendar> {
        return withContext(Dispatchers.IO) {
            val uri = CalendarContract.Calendars.CONTENT_URI
            val calendarList = mutableListOf<ExternalCalendar>()
            contentResolver.query(uri, CALENDAR_PROJECTION, null, null, null)?.use { cur ->
                while (cur.moveToNext()) {
                    calendarList.add(
                        ExternalCalendar(
                            id = cur.getLong(CALENDAR_ID_INDEX),
                            name = cur.getString(CALENDAR_NAME_INDEX) ?: "Unnamed calendar",
                            accountName = cur.getString(CALENDAR_ACCOUNT_INDEX) ?: "",
                            color = cur.getInt(CALENDAR_COLOR_INDEX),
                            ownerAccount = cur.getString(CALENDAR_OWNER_INDEX)
                        )
                    )
                }
            }
            calendarList.sortedWith(compareBy({ it.accountName }, { it.name }))
        }
    }

    /** Loads the phone's events between the two times, leaving out excluded calendars. */
    suspend fun getExternalEvents(
        startMillis: Long,
        endMillis: Long,
        excludedCalendarIds: Set<Long> = emptySet()
    ): Map<LocalDate, List<ExternalSession>> {
        return withContext(Dispatchers.IO) {
            // calendar instance search needs to provide start and end time for search and add it to URI path
            val builder: Uri.Builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
            ContentUris.appendId(builder, startMillis)
            ContentUris.appendId(builder, endMillis)
            val localZone = ZoneId.systemDefault()
            val eventList = mutableListOf<ExternalSession>()
            // selection has one ? placeholder per hidden calendar
            val selection = if (excludedCalendarIds.isEmpty()) {
                null
            } else {
                "${CalendarContract.Instances.CALENDAR_ID} NOT IN (${excludedCalendarIds.joinToString { "?" }})"
            }
            val selectionArgs = excludedCalendarIds.map { it.toString() }.toTypedArray()
            // search for all instances, close cursor if error
            contentResolver.query(builder.build(), EVENT_PROJECTION, selection, selectionArgs, null)?.use { cur ->
                while (cur.moveToNext()) {
                    // get key instance values
                    val eventID: Long = cur.getLong(PROJECTION_ID_INDEX)
                    val beginVal: Long = cur.getLong(PROJECTION_BEGIN_INDEX)
                    val endVal: Long = cur.getLong(PROJECTION_END_INDEX)
                    val title: String? = cur.getString(PROJECTION_TITLE_INDEX)
                    val isAllDay: Boolean = cur.getInt(PROJECTION_ALL_DAY_INDEX) == 1

                    // all-day events are stored as midnight UTC, so don't convert them into local tiemzone
                    val zone = if (isAllDay) ZoneOffset.UTC else localZone
                    val start = Instant.ofEpochMilli(beginVal).atZone(zone)
                    val end = Instant.ofEpochMilli(endVal).atZone(zone)

                    // Log fetched instances
                    Log.i("INFO", "Event: $title, start: $start, end: $end")
                    eventList.add(
                        ExternalSession(
                            id = eventID.toString(),
                            title = title ?: "",
                            date = start.toLocalDate(),
                            endDate = end.toLocalDate(),
                            startTime = start.toLocalTime(),
                            endTime = end.toLocalTime(),
                            isAllDay = isAllDay
                        )
                    )
                }
            }
             eventList
                .sortedWith(compareBy({ it.date }, { it.startTime }))
                .groupBy { it.date }
        }
    }






}