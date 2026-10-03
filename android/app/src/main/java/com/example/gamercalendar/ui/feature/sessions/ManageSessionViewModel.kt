package com.example.gamercalendar.ui.feature.sessions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gamercalendar.data.model.GamingSession
import com.example.gamercalendar.data.model.SentSessionInvite
import com.example.gamercalendar.data.model.SessionInvite
import com.example.gamercalendar.data.model.SessionParticipant
import com.example.gamercalendar.data.model.SessionStatus
import com.example.gamercalendar.data.model.User
import com.example.gamercalendar.data.repository.FriendRepository
import com.example.gamercalendar.data.repository.SessionRepository
import com.example.gamercalendar.data.repository.UserRepository
import com.example.gamercalendar.ui.navigation.Routes
import com.example.gamercalendar.util.SessionTime
import com.example.gamercalendar.util.apiErrorDetail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException

data class ManageSessionUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val session: GamingSession? = null,
    val gameName: String? = null,
    val startEpochMillis: Long = 0L,
    val endEpochMillis: Long = 0L,
    val currentUserId: String? = null,

    val participants: List<SessionParticipant> = emptyList(),
    val isLoadingParticipants: Boolean = false,
    val participantsError: String? = null,

    val friends: List<User> = emptyList(),
    val isLoadingFriends: Boolean = false,
    val friendsError: String? = null,

    /** The current user's pending invite to this session, if any. */
    val pendingInvite: SessionInvite? = null,

    /** Pending invites the organiser has sent; only loaded for the organiser. */
    val sentInvites: List<SentSessionInvite> = emptyList(),

    val isWorking: Boolean = false,
    val actionError: String? = null,
    val message: String? = null,
    val hasLeft: Boolean = false,
    val hasDeclined: Boolean = false
) {
    val isOrganiser: Boolean
        get() = session != null && session.organiser_id == currentUserId

    val isParticipant: Boolean
        get() = participants.any { it.user.id == currentUserId }

    val isCancelled: Boolean
        get() = session?.status == SessionStatus.CANCELLED

    /** Friends who aren't already in the session or waiting on an invite to it. */
    val invitableFriends: List<User>
        get() {
            val excludedIds = participants.map { it.user.id }.toSet() + sentInvites.map { it.receiver.id }
            return friends.filter { it.id !in excludedIds }
        }
}

class ManageSessionViewModel(
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val sessionId: String = checkNotNull(savedStateHandle[Routes.ARG_SESSION_ID])

    private val sessionRepository = SessionRepository()
    private val userRepository = UserRepository()
    private val friendRepository = FriendRepository()

    private val _uiState = MutableStateFlow(
        ManageSessionUiState(isLoading = true, isLoadingParticipants = true)
    )
    val uiState: StateFlow<ManageSessionUiState> = _uiState.asStateFlow()

    fun load() {
        loadSession()
        loadParticipants()
        loadPendingInvite()
    }

    private fun loadSentInvites() {
        viewModelScope.launch {
            try {
                val invites = sessionRepository.getSentInvites(sessionId)
                _uiState.update { it.copy(sentInvites = invites) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // The pending list is supplementary; the rest of the screen works without it.
            }
        }
    }

    private fun loadPendingInvite() {
        viewModelScope.launch {
            try {
                val invite = sessionRepository.getSessionInvites().firstOrNull { it.session_id == sessionId }
                _uiState.update { it.copy(pendingInvite = invite) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Without the invite the screen still works; the user just can't respond from here.
            }
        }
    }

    fun acceptInvite() {
        val invite = _uiState.value.pendingInvite ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, actionError = null) }
            try {
                sessionRepository.acceptInvite(invite)
                _uiState.update {
                    it.copy(isWorking = false, pendingInvite = null, message = "You joined the session")
                }
                // Refresh the player count and list now that the user is in.
                loadSession()
                loadParticipants()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isWorking = false, actionError = errorMessage(e, "Couldn't accept invite"))
                }
            }
        }
    }

    fun declineInvite() {
        val invite = _uiState.value.pendingInvite ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, actionError = null) }
            try {
                sessionRepository.declineInvite(invite)
                _uiState.update {
                    it.copy(
                        isWorking = false,
                        pendingInvite = null,
                        hasDeclined = true,
                        message = "Invite declined"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isWorking = false, actionError = errorMessage(e, "Couldn't decline invite"))
                }
            }
        }
    }

    private fun loadSession() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val (session, me) = coroutineScope {
                    val session = async { sessionRepository.getSession(sessionId) }
                    val me = async { userRepository.getCurrentUser() }
                    session.await() to me.await()
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        session = session,
                        gameName = session.game.name,
                        startEpochMillis = SessionTime.parseIso(session.start_at) ?: 0L,
                        endEpochMillis = SessionTime.parseIso(session.end_at) ?: 0L,
                        currentUserId = me.id
                    )
                }
                if (session.organiser_id == me.id) loadSentInvites()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = errorMessage(e, "Couldn't load session"))
                }
            }
        }
    }

    fun loadParticipants() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingParticipants = true, participantsError = null) }
            try {
                val participants = sessionRepository.getParticipants(sessionId)
                _uiState.update {
                    it.copy(participants = participants, isLoadingParticipants = false)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingParticipants = false,
                        participantsError = errorMessage(e, "Couldn't load players")
                    )
                }
            }
        }
    }

    fun loadFriends() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingFriends = true, friendsError = null) }
            try {
                val friends = friendRepository.getFriends()
                _uiState.update { it.copy(friends = friends, isLoadingFriends = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingFriends = false,
                        friendsError = errorMessage(e, "Couldn't load friends")
                    )
                }
            }
        }
    }

    fun inviteFriends(userIds: List<String>) {
        if (userIds.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, actionError = null) }
            var sent = 0
            var alreadyInvited = 0
            var failed = 0

            for (userId in userIds) {
                try {
                    sessionRepository.inviteToSession(sessionId, userId)
                    sent++
                } catch (e: HttpException) {
                    if (e.code() == 409) alreadyInvited++ else failed++
                } catch (_: Exception) {
                    failed++
                }
            }

            val message = listOfNotNull(
                "Sent $sent ${if (sent == 1) "invite" else "invites"}".takeIf { sent > 0 },
                "$alreadyInvited already invited".takeIf { alreadyInvited > 0 },
                "$failed failed".takeIf { failed > 0 }
            ).joinToString(", ")

            _uiState.update { it.copy(isWorking = false, message = message) }
            loadSentInvites()
        }
    }

    fun cancelSession() {
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, actionError = null) }
            try {
                sessionRepository.cancelSession(sessionId)
                _uiState.update {
                    it.copy(
                        isWorking = false,
                        session = it.session?.copy(status = SessionStatus.CANCELLED),
                        sentInvites = emptyList(),
                        message = "Session cancelled"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isWorking = false, actionError = errorMessage(e, "Couldn't cancel session"))
                }
            }
        }
    }

    fun removePlayer(user: User) {
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, actionError = null) }
            try {
                sessionRepository.removeParticipant(sessionId, user.id)
                _uiState.update { state ->
                    state.copy(
                        isWorking = false,
                        participants = state.participants.filterNot { it.user.id == user.id },
                        session = state.session?.let { it.copy(player_count = it.player_count - 1) },
                        message = "Removed ${user.username}"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isWorking = false, actionError = errorMessage(e, "Couldn't remove ${user.username}"))
                }
            }
        }
    }

    fun leaveSession() {
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, actionError = null) }
            try {
                sessionRepository.leaveSession(sessionId)
                _uiState.update {
                    it.copy(isWorking = false, hasLeft = true, message = "You left the session")
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isWorking = false, actionError = errorMessage(e, "Couldn't leave session"))
                }
            }
        }
    }

    fun messageShown() {
        _uiState.update { it.copy(message = null) }
    }

    private fun errorMessage(e: Exception, fallback: String): String {
        return apiErrorDetail(e) ?: e.message ?: fallback
    }
}
