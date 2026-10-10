package com.example.gamercalendar.data.model

import java.time.LocalDate
import java.time.LocalTime

/** A session prepared for the calendar UI, with times converted to the phone's timezone. */
data class CalendarSession(
    val id: String,
    val title: String,
    val gameName: String,
    val date: LocalDate,
    val session: GamingSession,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val isOnline: Boolean,
    val locationName: String?,
    val playerCount: Int,
    val playerLimit: Int?
)

/** An event from the phone's own calendars, with times converted to the phone's timezone. */
data class ExternalSession(
    val id: String,
    val title: String,
    val date: LocalDate,
    val endDate: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val isAllDay: Boolean
)

/** One of the external calendars on the phone **/
data class ExternalCalendar(
    val id: Long,
    val name: String,
    val accountName: String,
    val color: Int,
    val ownerAccount: String?
) {
    // hide google public holiday calendars by default
    val isHoliday: Boolean
        get() = ownerAccount?.endsWith("#holiday@group.v.calendar.google.com") == true
}