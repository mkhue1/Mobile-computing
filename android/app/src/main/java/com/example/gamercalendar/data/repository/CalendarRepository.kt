package com.example.gamercalendar.data.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import android.util.Log
import com.example.gamercalendar.data.model.SessionStatus
import com.example.gamercalendar.data.model.SessionType
import com.example.gamercalendar.data.api.ApiService
import com.example.gamercalendar.data.model.CalendarSession
import com.example.gamercalendar.data.model.GamingSession
import java.text.SimpleDateFormat
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Calendar
import kotlin.time.Instant


// Projection array. Creating indices for this array instead of doing
// dynamic lookups improves performance.
// see https://developer.android.com/identity/providers/calendar-provider :)
private val EVENT_PROJECTION: Array<String> = arrayOf(
    CalendarContract.Instances.EVENT_ID, // 0
    CalendarContract.Instances.BEGIN, // 1
    CalendarContract.Instances.END, // 2
    CalendarContract.Instances.TITLE // 3
)

// The indices for the projection array above.
private const val PROJECTION_ID_INDEX: Int = 0
private const val PROJECTION_BEGIN_INDEX: Int = 1
private const val PROJECTION_END_INDEX: Int = 2
private const val PROJECTION_TITLE_INDEX: Int = 3


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

    fun getExternalEvents(startMillis: Long, endMillis: Long){
        // calendar instance search needs to provide start and end time for search and add it to URI path
        val builder: Uri.Builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, startMillis)
        ContentUris.appendId(builder, endMillis)
        // search for all instances
        val cur: Cursor? = contentResolver.query(builder.build(), EVENT_PROJECTION, null, null, null)
        while (cur?.moveToNext() ?: false) {
            // get key instance values
            val eventID: Long = cur.getLong(PROJECTION_ID_INDEX)
            val beginVal: Long = cur.getLong(PROJECTION_BEGIN_INDEX)
            val endVal: Long = cur.getLong(PROJECTION_END_INDEX)
            val title: String? = cur.getString(PROJECTION_TITLE_INDEX)

            // Log fetched instances
            Log.i("INFO", "Event: $title")
            val calendar1 = Calendar.getInstance().apply {
                timeInMillis = beginVal
            }
            val calendar2 = Calendar.getInstance().apply {
                timeInMillis = endVal
            }
            val formatter = SimpleDateFormat("MM/dd/yyyy")
            Log.i("INFO", "Start Date: ${formatter.format(calendar1.time)}")
            Log.i("INFO", "End Date: ${formatter.format(calendar2.time)}")
        }
        cur?.close()


    }






}