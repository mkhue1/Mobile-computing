package com.example.gamercalendar.data.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.calendarDataStore: DataStore<Preferences> by preferencesDataStore(name = "calendar_prefs")

class CalendarPreferences(context: Context) {
    private val dataStore = context.applicationContext.calendarDataStore
    private val excludedKey = stringSetPreferencesKey("excluded_calendar_ids")

    /** calendars the user has hidden, null if defaults. */
    val excludedCalendarIds: Flow<Set<Long>?> = dataStore.data.map { prefs ->
        prefs[excludedKey]?.mapNotNull { it.toLongOrNull() }?.toSet()
    }

    suspend fun setExcludedCalendarIds(ids: Set<Long>) {
        dataStore.edit { it[excludedKey] = ids.map(Long::toString).toSet() }
    }
}