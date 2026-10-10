package com.example.gamercalendar.ui.feature.calendar

import android.content.ContentResolver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.gamercalendar.data.api.ApiClient
import com.example.gamercalendar.data.api.ApiService
import com.example.gamercalendar.data.model.CalendarSession
import com.example.gamercalendar.data.model.ExternalCalendar
import com.example.gamercalendar.data.model.ExternalSession
import com.example.gamercalendar.data.repository.CalendarRepository
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
class CalendarViewModel(private val repository: CalendarRepository) : ViewModel() {

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
    private var calendarsLoaded = false

    fun setViewMode(mode: CalendarViewMode) {
        _viewMode.value = mode
    }

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
    }

    /** Call again after creating, cancelling or leaving a session so the calendar stays current. */
    fun refresh() {
        loadSessions()
        // the views ask for their month again when they appear, which reloads the external events
        loadedAround = null
    }

    private fun loadSessions() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val sessions = repository.getMySessions()
                _sessionsByDate.value = sessions.groupBy { it.date }
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: CancellationException) {
                throw e // never swallow coroutine cancellation
            } catch (e: HttpException) {
                val message = if (e.code() == 401) {
                    "Your login has expired. Please log in again."
                } else {
                    "Server error (${e.code()}). Please try again."
                }
                _uiState.update { it.copy(isLoading = false, errorMessage = message) }
            } catch (e: IOException) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Can't reach the server. Check your connection."
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Couldn't load sessions."
                    )
                }
            }
        }
    }

    /** Loads the phone's calendar events for a given month and the months either side of it. **/
    fun loadEventsAround(month: YearMonth) {
        requestedMonth = month
        // don't load data again if it's the currently loaded set
        if (month == loadedAround) {
            return
        }
        val zone = ZoneId.systemDefault()
        val start = month.minusMonths(1).atDay(1).atStartOfDay(zone)   // 1st of previous month
        val end = month.plusMonths(2).atDay(1).atStartOfDay(zone)      // 1st of the month after next
        loadExternalEvents(start.toInstant().toEpochMilli(), end.toInstant().toEpochMilli())
        loadedAround = month
    }

    private fun loadExternalEvents(startMillis: Long, endMillis: Long) {
        eventJob?.cancel()
        eventJob = viewModelScope.launch {
            try {
                ensureCalendarsLoaded()
                _externalEvents.value =
                    repository.getExternalEvents(startMillis, endMillis, _excludedCalendarIds.value)
            } catch (e: CancellationException) {
                throw e // never swallow coroutine cancellation
            } catch (e: SecurityException) {
                // calendar permission not granted: show no external events and try again next time
                loadedAround = null
                _externalEvents.value = emptyMap()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = e.message ?: "Couldn't load external events.")
                }
                loadedAround = null
            }
        }
    }

    /**
     * The first time ran, reads the phone's calendars and hides holidays
     * so holidays don't appear and then disappear.
     */
    private suspend fun ensureCalendarsLoaded() {
        if (calendarsLoaded) return
        val calendars = repository.getCalendars()
        _calendars.value = calendars
        _excludedCalendarIds.value = calendars.filter { it.isHoliday }.map { it.id }.toSet()
        calendarsLoaded = true
    }

    /** :oads/reloads the phone's calendars */
    fun loadCalendars() {
        viewModelScope.launch {
            try {
                _calendars.value = repository.getCalendars()
            } catch (e: CancellationException) {
                throw e // never swallow coroutine cancellation
            } catch (e: SecurityException) {
                _calendars.value = emptyList() // calendar permission not granted
            }
        }
    }

    /** Applies the picker's choice and reloads events for the block around the current month on screen. */
    fun setExcludedCalendars(ids: Set<Long>) {
        if (ids == _excludedCalendarIds.value) return
        _excludedCalendarIds.value = ids
        calendarsLoaded = true
        loadedAround = null
        requestedMonth?.let { loadEventsAround(it) }
    }
}

class CalendarViewModelFactory(
    private val contentResolver: ContentResolver,
    private val api: ApiService = ApiClient.api
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(CalendarViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        return CalendarViewModel(CalendarRepository(api, contentResolver)) as T
    }
}
