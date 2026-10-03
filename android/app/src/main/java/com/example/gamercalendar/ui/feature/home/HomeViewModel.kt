package com.example.gamercalendar.ui.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gamercalendar.data.model.GamingSession
import com.example.gamercalendar.data.model.SessionInvite
import com.example.gamercalendar.data.model.SessionStatus
import com.example.gamercalendar.data.repository.SessionRepository
import com.example.gamercalendar.util.SessionTime
import com.example.gamercalendar.util.apiErrorDetail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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

data class InviteListItem(
    val invite: SessionInvite,
    val item: SessionListItem
)

data class HomeUiState(
    val sessions: List<SessionListItem> = emptyList(),
    val invites: List<InviteListItem> = emptyList(),
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
                val (sessions, games, invites) = coroutineScope {
                    val sessions = async { repository.getSessions() }
                    val games = async { repository.getGames() }
                    val invites = async { loadInvites() }
                    Triple(sessions.await(), games.await(), invites.await())
                }
                val gameNames = games.associate { it.id to it.name }
                val now = System.currentTimeMillis()

                val upcoming = sessions
                    .mapNotNull { it.toListItem(gameNames) }
                    .filter { it.endEpochMillis > now }
                    .sortedBy { it.startEpochMillis }

                val openInvites = invites
                    .mapNotNull { (invite, session) ->
                        session.toListItem(gameNames)?.let { InviteListItem(invite, it) }
                    }
                    .filter { it.item.session.status == SessionStatus.OPEN && it.item.endEpochMillis > now }
                    .sortedBy { it.item.startEpochMillis }

                _uiState.update {
                    it.copy(
                        sessions = upcoming,
                        invites = openInvites,
                        isLoading = false,
                        isRefreshing = false
                    )
                }
            } catch (e: CancellationException) {
                throw e
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

    /** Pending invites paired with their session. Invites whose session can't be loaded are skipped. */
    private suspend fun loadInvites(): List<Pair<SessionInvite, GamingSession>> = coroutineScope {
        repository.getSessionInvites()
            .map { invite ->
                async {
                    try {
                        invite to repository.getSession(invite.session_id)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                }
            }
            .awaitAll()
            .filterNotNull()
    }

    private fun GamingSession.toListItem(gameNames: Map<String, String>): SessionListItem? {
        val start = SessionTime.parseIso(start_at) ?: return null
        val end = SessionTime.parseIso(end_at) ?: return null
        return SessionListItem(this, gameNames[game_id], start, end)
    }
}
