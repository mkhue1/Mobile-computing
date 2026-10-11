package com.example.gamercalendar.ui.feature.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.gamercalendar.data.model.GamingSession
import com.example.gamercalendar.data.model.SessionInvite
import com.example.gamercalendar.data.model.SessionStatus
import com.example.gamercalendar.data.repository.SessionRepository
import com.example.gamercalendar.geofence.NearbyStore
import com.example.gamercalendar.geofence.SessionGeofences
import com.example.gamercalendar.util.SessionTime
import com.example.gamercalendar.util.apiErrorDetail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException

data class SessionListItem(
    val session: GamingSession,
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
    val nearby: List<SessionListItem> = emptyList(),
    val nearbyEnabled: Boolean = false,
    val nearbyError: String? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SessionRepository()
    private val nearbyStore = NearbyStore(application)
    private var nearbyJob: Job? = null

    private val _uiState = MutableStateFlow(HomeUiState(isLoading = true))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            nearbyStore.enabled.collect { enabled ->
                _uiState.update { it.copy(nearbyEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            nearbyStore.insideIds.distinctUntilChanged().collect { ids ->
                nearbyJob?.cancel()
                nearbyJob = viewModelScope.launch { publishNearby(ids) }
            }
        }
    }

    /** Background load, e.g. when the screen is shown. */
    fun loadSessions() = load(isRefresh = false)

    /** User-initiated pull to refresh. Also picks up sessions that are now nearby. */
    fun refresh() = load(isRefresh = true)

    fun enableNearby() {
        viewModelScope.launch {
            _uiState.update { it.copy(nearbyError = null) }
            nearbyStore.setEnabled(true)
            try {
                val foundLocation = SessionGeofences.refresh(getApplication())
                if (!foundLocation) {
                    nearbyStore.setEnabled(false)
                    _uiState.update {
                        it.copy(nearbyError = "Couldn't find this phone's location. Check that location is on.")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                nearbyStore.setEnabled(false)
                _uiState.update {
                    it.copy(nearbyError = "Couldn't watch nearby sessions. Check that location is on.")
                }
            }
        }
    }

    fun disableNearby() {
        viewModelScope.launch {
            _uiState.update { it.copy(nearbyError = null) }
            SessionGeofences.disable(getApplication())
        }
    }

    private fun load(isRefresh: Boolean) {
        viewModelScope.launch {
            _uiState.update {
                if (isRefresh) it.copy(isRefreshing = true, error = null)
                else it.copy(isLoading = true, error = null)
            }
            try {
                val (sessions, invites) = coroutineScope {
                    val sessions = async { repository.getSessions() }
                    val invites = async { loadInvites() }
                    if (isRefresh && nearbyStore.isEnabled()) {
                        async {
                            try {
                                SessionGeofences.refresh(getApplication())
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                            }
                        }
                    }
                    sessions.await() to invites.await()
                }
                SessionGeofences.dropJoined(getApplication(), sessions.map { it.id }.toSet())
                val now = System.currentTimeMillis()

                val upcoming = sessions
                    .mapNotNull { it.toListItem() }
                    .filter { it.endEpochMillis > now }
                    .sortedBy { it.startEpochMillis }

                val openInvites = invites
                    .mapNotNull { (invite, session) ->
                        session.toListItem()?.let { InviteListItem(invite, it) }
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

    private suspend fun publishNearby(ids: Set<String>) {
        val joinedIds = try {
            repository.getSessions().map { it.id }.toSet()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptySet()
        }
        SessionGeofences.dropJoined(getApplication(), joinedIds)
        val watching = ids - joinedIds
        if (watching.isEmpty()) {
            _uiState.update { it.copy(nearby = emptyList()) }
            return
        }
        val now = System.currentTimeMillis()
        val items = coroutineScope {
            watching.map { id ->
                async {
                    try {
                        val session = repository.getSession(id)
                        val item = session.toListItem()
                        if (item == null || session.status != SessionStatus.OPEN || item.endEpochMillis <= now) {
                            nearbyStore.removeInside(setOf(id))
                            null
                        } else {
                            item
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: HttpException) {
                        if (e.code() == 404) nearbyStore.removeInside(setOf(id))
                        null
                    } catch (_: Exception) {
                        null
                    }
                }
            }.awaitAll().filterNotNull().sortedBy { it.startEpochMillis }
        }
        _uiState.update { it.copy(nearby = items) }
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

    private fun GamingSession.toListItem(): SessionListItem? {
        val start = SessionTime.parseIso(start_at) ?: return null
        val end = SessionTime.parseIso(end_at) ?: return null
        return SessionListItem(this, start, end)
    }
}
