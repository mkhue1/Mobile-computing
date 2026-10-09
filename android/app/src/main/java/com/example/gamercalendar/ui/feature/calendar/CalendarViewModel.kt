package com.example.gamercalendar.ui.feature.calendar

import android.content.ContentResolver
import com.example.gamercalendar.data.api.ApiClient
import com.example.gamercalendar.data.api.ApiService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.gamercalendar.data.model.CalendarSession
import com.example.gamercalendar.data.model.ExternalSession
import com.example.gamercalendar.data.repository.CalendarRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

data class CalendarUiState(
    val sessionsByDate: Map<LocalDate, List<CalendarSession>> = emptyMap(),
    val events: Map<LocalDate, List<ExternalSession>> = emptyMap(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class CalendarViewModel(private val repository: CalendarRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(CalendarUiState())
    private var loadedAround: YearMonth? = null
    private var eventJob: Job? = null
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    init {
        loadSessions()
        val now = ZonedDateTime.now()
        loadEventsAround(YearMonth.now())
        loadedAround = YearMonth.now()
    }

    /** Call again after creating, cancelling or leaving a session so the calendar stays current. */
    fun refresh() {
        loadSessions()
        loadedAround = null
    }

    private fun loadSessions() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val sessions = repository.getMySessions()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        sessionsByDate = sessions.groupBy { session -> session.date }
                    )
                }
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

    fun loadEventsAround(month: YearMonth) {
        // don't load data again if it's the currently loaded set
        if (month == loadedAround) {
            return
        }
        val zone = ZoneId.systemDefault()
        val start = month.minusMonths(1).atDay(1).atStartOfDay(zone)   // 1st of previous month
        val end =
            month.plusMonths(2).atDay(1).atStartOfDay(zone)      // 1st of the month after next
        loadExternalEvents(start.toInstant().toEpochMilli(), end.toInstant().toEpochMilli())
        loadedAround = month

    }

    fun loadExternalEvents(startMillis: Long, endMillis: Long) {
        eventJob?.cancel()
        eventJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val events = repository.getExternalEvents(startMillis, endMillis)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        events = events
                    )
                }
            } catch (e: CancellationException) {
                throw e // never swallow coroutine cancellation
            } catch (e: SecurityException) {
                loadedAround = null
                _uiState.update { it.copy(events = emptyMap(), isLoading = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Couldn't load external events."
                    )
                }
                loadedAround = null
            }
        }
    }
}

class CalendarViewModelFactory(private val api: ApiService = ApiClient.api, private val contentResolver: ContentResolver) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(CalendarViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        return CalendarViewModel(CalendarRepository(api, contentResolver)) as T
    }
}