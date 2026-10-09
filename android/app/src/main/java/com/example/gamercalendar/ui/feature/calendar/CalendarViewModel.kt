package com.example.gamercalendar.ui.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.gamercalendar.data.api.ApiClient
import com.example.gamercalendar.data.api.ApiService
import com.example.gamercalendar.data.model.CalendarSession
import com.example.gamercalendar.data.repository.CalendarRepository
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
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
 * State shared by the Month and Week views: it loads the user's sessions and remembers which
 * view is showing and which day is selected, so switching views keeps the day. Each view then
 * has its own view model that builds what it shows from these.
 */
class CalendarViewModel(private val repository: CalendarRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    private val _sessionsByDate = MutableStateFlow<Map<LocalDate, List<CalendarSession>>>(emptyMap())

    /** The user's sessions, grouped by the day each one starts. */
    val sessionsByDate: StateFlow<Map<LocalDate, List<CalendarSession>>> = _sessionsByDate.asStateFlow()

    private val _viewMode = MutableStateFlow(CalendarViewMode.MONTH) // month is the default
    val viewMode: StateFlow<CalendarViewMode> = _viewMode.asStateFlow()

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    fun setViewMode(mode: CalendarViewMode) {
        _viewMode.value = mode
    }

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
    }

    /** Call again after creating, cancelling or leaving a session so the calendar stays current. */
    fun refresh() = loadSessions()

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
                    it.copy(isLoading = false, errorMessage = "Can't reach the server. Check your connection.")
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = e.message ?: "Couldn't load sessions.")
                }
            }
        }
    }
}

class CalendarViewModelFactory(
    private val api: ApiService = ApiClient.api
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(CalendarViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        return CalendarViewModel(CalendarRepository(api)) as T
    }
}