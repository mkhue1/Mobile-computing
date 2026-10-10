package com.example.gamercalendar.ui.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.gamercalendar.data.api.ApiClient
import com.example.gamercalendar.data.api.ApiService
import com.example.gamercalendar.data.model.CalendarSession
import com.example.gamercalendar.data.repository.CalendarRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

data class SearchUiState(
    val results: List<CalendarSession> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

/** Searches the public sessions you can still join, by game name or session title. */
class SearchViewModel(private val repository: CalendarRepository) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    // Only one search runs at a time: starting a new one cancels the old one, so a slow
    // response to an earlier query can never overwrite the results of a newer one.
    private var searchJob: Job? = null

    /** Searches once the user has paused typing, not on every keystroke. */
    fun onQueryChange(text: String) {
        _query.value = text
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            search(text)
        }
    }

    /** Runs the current query again, for example when the screen is shown or after an error. */
    fun refresh() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch { search(_query.value) }
    }

    private suspend fun search(text: String) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        try {
            val results = repository.searchPublicSessions(text)
            _uiState.update { it.copy(results = results, isLoading = false) }
        } catch (e: CancellationException) {
            throw e // a newer search replaced this one
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
                it.copy(isLoading = false, errorMessage = e.message ?: "Search failed.")
            }
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}

class SearchViewModelFactory(
    private val api: ApiService = ApiClient.api
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(SearchViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        return SearchViewModel(CalendarRepository(api)) as T
    }
}