package com.example.gamercalendar.ui.feature.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gamercalendar.data.model.GamingSession
import com.example.gamercalendar.data.model.SessionStatus
import com.example.gamercalendar.data.model.SessionVisibility
import com.example.gamercalendar.data.repository.SessionRepository
import com.example.gamercalendar.data.session.CurrentUserStore
import com.example.gamercalendar.util.SessionQr
import com.example.gamercalendar.util.SessionTime
import com.example.gamercalendar.util.apiErrorDetail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException

data class JoinSessionByQrUiState(
    val isResolving: Boolean = false,
    val isJoining: Boolean = false,
    val pendingSession: GamingSession? = null,
    val error: String? = null,
    val message: String? = null,
    val joinedSessionId: String? = null
)

class JoinSessionByQrViewModel : ViewModel() {

    private val sessionRepository = SessionRepository()
    private var resolveJob: Job? = null

    private val _uiState = MutableStateFlow(JoinSessionByQrUiState())
    val uiState: StateFlow<JoinSessionByQrUiState> = _uiState.asStateFlow()

    fun onQrScanned(rawContent: String) {
        val sessionId = SessionQr.parse(rawContent)
        if (sessionId == null) {
            _uiState.update {
                it.copy(error = "That QR code isn't a Roundtable session", pendingSession = null)
            }
            return
        }

        resolveJob?.cancel()
        resolveJob = viewModelScope.launch {
            _uiState.update {
                it.copy(isResolving = true, error = null, pendingSession = null)
            }
            try {
                val session = sessionRepository.getSession(sessionId)
                val reason = blockReason(session)
                if (reason != null) {
                    _uiState.update { it.copy(isResolving = false, error = reason) }
                    return@launch
                }
                _uiState.update { it.copy(isResolving = false, pendingSession = session) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val notFound = e is HttpException && e.code() == 404
                _uiState.update {
                    it.copy(
                        isResolving = false,
                        error = if (notFound) {
                            "No public session matches that QR code"
                        } else {
                            apiErrorDetail(e) ?: e.message ?: "Couldn't look up that session"
                        }
                    )
                }
            }
        }
    }

    fun confirmJoin() {
        val session = _uiState.value.pendingSession ?: return
        if (_uiState.value.isJoining) return

        viewModelScope.launch {
            _uiState.update { it.copy(isJoining = true, error = null) }
            try {
                sessionRepository.joinSession(session.id)
                _uiState.update {
                    it.copy(
                        isJoining = false,
                        pendingSession = null,
                        joinedSessionId = session.id,
                        message = "You joined the session"
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isJoining = false,
                        pendingSession = null,
                        error = apiErrorDetail(e) ?: e.message ?: "Couldn't join that session"
                    )
                }
            }
        }
    }

    fun dismissPendingSession() {
        if (_uiState.value.isJoining) return
        _uiState.update { it.copy(pendingSession = null) }
    }

    fun messageShown() {
        _uiState.update { it.copy(message = null) }
    }

    fun consumeJoined() {
        _uiState.update { it.copy(joinedSessionId = null) }
    }

    private suspend fun blockReason(session: GamingSession): String? {
        if (session.visibility != SessionVisibility.PUBLIC) {
            return "Only public sessions can be joined with a QR code"
        }
        if (session.status == SessionStatus.CANCELLED) {
            return "This session has been cancelled"
        }
        if (session.status != SessionStatus.OPEN) {
            return "This session has already finished"
        }
        val endMillis = SessionTime.parseIso(session.end_at)
        if (endMillis != null && endMillis <= System.currentTimeMillis()) {
            return "This session has already finished"
        }
        if (session.player_limit != null && session.player_count >= session.player_limit) {
            return "This session is full"
        }

        val currentUserId = CurrentUserStore.user.value?.id
        if (currentUserId != null && session.organiser_id == currentUserId) {
            return "That's your own session"
        }
        if (currentUserId != null && alreadyJoined(session.id, currentUserId)) {
            return "You're already in this session"
        }
        return null
    }

    private suspend fun alreadyJoined(sessionId: String, currentUserId: String): Boolean {
        return try {
            sessionRepository.getParticipants(sessionId).any { it.user.id == currentUserId }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }
}
