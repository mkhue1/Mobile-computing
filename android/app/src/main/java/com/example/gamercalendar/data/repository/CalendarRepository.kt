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
import com.example.gamercalendar.data.model.ExternalSession
import com.example.gamercalendar.data.model.GamingSession
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset


// Projection array. Creating indices for this array instead of doing
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


class CalendarRepository(private val api: ApiService, private val contentResolver: ContentResolver) {

    /** Loads the user's sessions, excluding cancel ones, sorted by date and start time. */
    suspend fun getMySessions(): List<CalendarSession> {
        val zone = ZoneId.systemDefault()
        return api.getSessions()
            .filter { it.status != SessionStatus.CANCELLED }
            .map { it.toCalendarSession(zone) }
            .sortedWith(compareBy({ it.date }, { it.startTime }))
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
            isOnline = session_type == SessionType.ONLINE,
            locationName = location_name,
            playerCount = player_count,
            playerLimit = player_limit
        )
    }

    fun getExternalEvents(startMillis: Long, endMillis: Long): Map<LocalDate, List<ExternalSession>> {
        // calendar instance search needs to provide start and end time for search and add it to URI path
        val builder: Uri.Builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, startMillis)
        ContentUris.appendId(builder, endMillis)
        val localZone = ZoneId.systemDefault()
        val eventList = mutableListOf<ExternalSession>()
        // search for all instances, close cursor if error
        contentResolver.query(builder.build(), EVENT_PROJECTION, null, null, null)?.use { cur ->
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
                        startTime = start.toLocalTime(),
                        endTime = end.toLocalTime()
                    )
                )
            }
        }
        return eventList
            .sortedWith(compareBy({ it.date }, { it.startTime }))
            .groupBy { it.date }
    }






}