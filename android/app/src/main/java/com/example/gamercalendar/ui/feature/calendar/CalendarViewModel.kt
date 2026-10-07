package com.example.gamercalendar.ui.feature.calendar

import android.content.ContentResolver
import com.example.gamercalendar.data.api.ApiClient
import com.example.gamercalendar.data.api.ApiService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.gamercalendar.data.model.CalendarSession
import com.example.gamercalendar.data.repository.CalendarRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import java.time.LocalDate

data class CalendarUiState(
    val sessionsByDate: Map<LocalDate, List<CalendarSession>> = emptyMap(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class CalendarViewModel(private val repository: CalendarRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    init {
        loadSessions()
    }

    /** Call again after creating, cancelling or leaving a session so the calendar stays current. */
    fun refresh() = loadSessions()

    private fun loadSessions() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val sessions = repository.getMySessions()
                repository.getExternalEvents(System.currentTimeMillis(), System.currentTimeMillis() + 1000000000000000000)
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

class CalendarViewModelFactory(private val api: ApiService = ApiClient.api, private val contentResolver: ContentResolver) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(CalendarViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        return CalendarViewModel(CalendarRepository(api, contentResolver)) as T
    }
}