package com.example.gamercalendar.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gamercalendar.data.model.GamingSession
import com.example.gamercalendar.data.repository.SessionRepository
import com.example.gamercalendar.util.SessionTime
import com.example.gamercalendar.util.apiErrorDetail
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SessionListItem(
    val session: GamingSession,
    val gameName: String?,
    val startEpochMillis: Long,
    val endEpochMillis: Long
)

data class HomeUiState(
    val sessions: List<SessionListItem> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null
)

class HomeViewModel : ViewModel() {

    private val repository = SessionRepository()

    private val _uiState = MutableStateFlow(HomeUiState(isLoading = true))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /** Background load, e.g. when the screen is shown. */
    fun loadSessions() = load(isRefresh = false)

    /** User-initiated pull to refresh. */
    fun refresh() = load(isRefresh = true)

    private fun load(isRefresh: Boolean) {
        viewModelScope.launch {
            _uiState.update {
                if (isRefresh) it.copy(isRefreshing = true, error = null)
                else it.copy(isLoading = true, error = null)
            }
            try {
                val (sessions, games) = coroutineScope {
                    val sessions = async { repository.getSessions() }
                    val games = async { repository.getGames() }
                    sessions.await() to games.await()
                }
                val gameNames = games.associate { it.id to it.name }
                val now = System.currentTimeMillis()

                val upcoming = sessions
                    .mapNotNull { session ->
                        val start = SessionTime.parseIso(session.start_at) ?: return@mapNotNull null
                        val end = SessionTime.parseIso(session.end_at) ?: return@mapNotNull null
                        SessionListItem(session, gameNames[session.game_id], start, end)
                    }
                    .filter { it.endEpochMillis > now }
                    .sortedBy { it.startEpochMillis }

                _uiState.update {
                    it.copy(sessions = upcoming, isLoading = false, isRefreshing = false)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = apiErrorDetail(e) ?: e.message ?: "Couldn't load sessions"
                    )
                }
            }
        }
    }
}
