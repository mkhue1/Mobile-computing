package com.example.gamercalendar.data.repository

import com.example.gamercalendar.data.model.SessionStatus
import com.example.gamercalendar.data.model.SessionType
import com.example.gamercalendar.data.api.ApiService
import com.example.gamercalendar.data.model.CalendarSession
import com.example.gamercalendar.data.model.GamingSession
import java.time.OffsetDateTime
import java.time.ZoneId

class CalendarRepository(private val api: ApiService) {

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

}