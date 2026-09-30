package com.example.gamercalendar.util

import android.content.Context
import android.text.format.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Date/time helpers for session scheduling.
 *
 * Dates are held as UTC-midnight millis to match what the Material3 DatePicker
 * produces; they are combined with a local hour/minute to get a real instant.
 * Uses java.util rather than java.time because minSdk is 24.
 */
object SessionTime {

    private val utc: TimeZone = TimeZone.getTimeZone("UTC")

    fun localDateToUtcMillis(year: Int, month: Int, dayOfMonth: Int): Long {
        return Calendar.getInstance(utc).apply {
            clear()
            set(year, month, dayOfMonth)
        }.timeInMillis
    }

    fun todayUtcMillis(): Long {
        val now = Calendar.getInstance()
        return localDateToUtcMillis(
            now.get(Calendar.YEAR),
            now.get(Calendar.MONTH),
            now.get(Calendar.DAY_OF_MONTH)
        )
    }

    fun toEpochMillis(dateUtcMillis: Long, hour: Int, minute: Int, plusDays: Int = 0): Long {
        val date = Calendar.getInstance(utc).apply { timeInMillis = dateUtcMillis }
        return Calendar.getInstance().apply {
            clear()
            set(
                date.get(Calendar.YEAR),
                date.get(Calendar.MONTH),
                date.get(Calendar.DAY_OF_MONTH),
                hour,
                minute
            )
            add(Calendar.DAY_OF_MONTH, plusDays)
        }.timeInMillis
    }

    fun toIsoUtc(epochMillis: Long): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        format.timeZone = utc
        return format.format(Date(epochMillis))
    }

    /** Parses API timestamps such as `2026-10-01T10:00:00Z` or `2026-10-01T10:00:00.123456+00:00`. */
    fun parseIso(value: String): Long? {
        val withoutFraction = value.replace(Regex("\\.\\d+"), "")
        return try {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).parse(withoutFraction)?.time
        } catch (_: Exception) {
            null
        }
    }

    /** e.g. "Wed 1 Oct · 8:00 pm – 10:00 pm", in the device's time zone. */
    fun formatRange(context: Context, startEpochMillis: Long, endEpochMillis: Long): String {
        val date = SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(startEpochMillis))
        val time = DateFormat.getTimeFormat(context)
        return "$date · ${time.format(Date(startEpochMillis))} – ${time.format(Date(endEpochMillis))}"
    }

    fun formatDate(dateUtcMillis: Long): String {
        val format = SimpleDateFormat("EEE d MMM yyyy", Locale.getDefault())
        format.timeZone = utc
        return format.format(Date(dateUtcMillis))
    }

    fun formatTime(context: Context, hour: Int, minute: Int): String {
        val time = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }
        return DateFormat.getTimeFormat(context).format(time.time)
    }
}
