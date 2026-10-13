package com.example.gamercalendar.ui.feature.calendar

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.gamercalendar.data.api.ApiClient
import com.example.gamercalendar.data.api.ApiService
import com.example.gamercalendar.data.model.CalendarSession
import com.example.gamercalendar.data.model.ExternalCalendar
import com.example.gamercalendar.data.model.ExternalSession
import com.example.gamercalendar.data.repository.CalendarRepository
import com.example.gamercalendar.data.session.CalendarPreferences
import java.io.IOException
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import kotlin.collections.groupBy

/** Which calendar view is showing. */
enum class CalendarViewMode(val label: String) {
    MONTH("Month"),
    WEEK("Week")
}

/** What the calendar shell shows besides the sessions themselves. */
data class CalendarUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

/**
 * State shared by the Month and Week views: it loads the user's sessions and the events from the
 * phone's calendar, and remembers which view is showing and which day is selected, so switching
 * views keeps the day. Each view then has its own view model that builds what it shows from these.
 */
class CalendarViewModel(private val repository: CalendarRepository, private val calendarPreferences: CalendarPreferences) : ViewModel() {

    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    private val _sessionsByDate = MutableStateFlow<Map<LocalDate, List<CalendarSession>>>(emptyMap())

    /** The user's sessions, grouped by the day each one starts. */
    val sessionsByDate: StateFlow<Map<LocalDate, List<CalendarSession>>> = _sessionsByDate.asStateFlow()

    private val _externalEvents = MutableStateFlow<Map<LocalDate, List<ExternalSession>>>(emptyMap())

    /** Events from the phone's calendar (loaded in 3 month blocks around month being viewed),
     * grouped by the day each one starts. **/
    val externalEvents: StateFlow<Map<LocalDate, List<ExternalSession>>> = _externalEvents.asStateFlow()

    private val _calendars = MutableStateFlow<List<ExternalCalendar>>(emptyList())

    /** The calendars on the phone, for the calendar picker. */
    val calendars: StateFlow<List<ExternalCalendar>> = _calendars.asStateFlow()

    private val _excludedCalendarIds = MutableStateFlow<Set<Long>>(emptySet())

    /** Calendars the user has unticked in the picker (does not want to display) */
    val excludedCalendarIds: StateFlow<Set<Long>> = _excludedCalendarIds.asStateFlow()

    private val _viewMode = MutableStateFlow(CalendarViewMode.MONTH) // month is the default
    val viewMode: StateFlow<CalendarViewMode> = _viewMode.asStateFlow()

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private var loadedAround: YearMonth? = null
    private var requestedMonth: YearMonth? = null // the month the visible view last asked for
    private var eventJob: Job? = null

    init {
        viewModelScope.launch { loadCalendars() }
    }

    fun setViewMode(mode: CalendarViewMode) {
        _viewMode.value = mode
    }

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
    }

    /** Call again after creating, cancelling or leaving a session so the calendar stays current. */
    fun refresh() {
        loadedAround = null
    }

    /** Loads both gaming sessions and the phone's calendar events for a given month and the months either side of it. **/
    fun loadAround(month: YearMonth) {
        requestedMonth = month
        // don't load data again if it's the currently loaded set
        if (month == loadedAround) {
            return
        }
        val zone = ZoneId.systemDefault()
        val start = month.minusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()   // 1st of previous month
        val end = month.plusMonths(2).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()

        eventJob?.cancel()
        eventJob = viewModelScope.launch {
            try {
                val sessions = repository.getMySessionsInRange(start, end)
                val externalEvents = repository.getExternalEvents(start, end)
                _sessionsByDate.value = sessions.groupBy { it.date }
                _externalEvents.value = externalEvents

            } catch (e: CancellationException) {
                throw e // never swallow coroutine cancellation
            }
            catch (e: SecurityException) {
                // calendar permission not granted: show no external events and try again next time
                _externalEvents.value = emptyMap()
            }
            catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = e.message ?: "Couldn't load sessions.")
                }
            }
        }

        loadedAround = month
    }

    /** :oads/reloads the phone's calendars and which calendars are hidden */
    fun loadCalendars() {
        viewModelScope.launch {
            try {
                _calendars.value = repository.getCalendars()
                _excludedCalendarIds.value = repository.getExcludedCalendarIds()
            } catch (e: CancellationException) {
                throw e // never swallow coroutine cancellation
            } catch (e: SecurityException) {
                _calendars.value = emptyList() // calendar permission not granted yet
            } catch (e: Exception) {}
        }
    }

    /** Applies the picker's choice and reloads events for the block around the current month on screen. */
    fun setExcludedCalendars(ids: Set<Long>) {
        if (ids == _excludedCalendarIds.value) return
        _excludedCalendarIds.value = ids
        try {
            viewModelScope.launch {
                calendarPreferences.setExcludedCalendarIds(ids)
                loadedAround = null
                requestedMonth?.let { loadAround(it) }
            }
        } catch (e : Exception) {}
    }
}

class CalendarViewModelFactory(
    context: Context,
    private val api: ApiService = ApiClient.api
) : ViewModelProvider.Factory {
    // factory can outlive the screen creating it, so only hold the app's context
    private val appContext = context.applicationContext

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(CalendarViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        val calendarPreferences = CalendarPreferences(appContext)
        val repository = CalendarRepository(api, appContext.contentResolver, calendarPreferences)
        return CalendarViewModel(repository, calendarPreferences) as T
    }
}
