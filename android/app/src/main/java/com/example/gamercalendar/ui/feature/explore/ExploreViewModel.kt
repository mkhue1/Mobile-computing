package com.example.gamercalendar.ui.feature.explore

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.gamercalendar.data.model.SessionStatus
import com.example.gamercalendar.data.repository.SessionRepository
import com.example.gamercalendar.geofence.NearbyStore
import com.example.gamercalendar.geofence.SessionGeofences
import com.example.gamercalendar.ui.feature.home.SessionListItem
import com.example.gamercalendar.util.SessionTime
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

data class ExploreUiState(
    val nearby: List<SessionListItem> = emptyList(),
    /** Null until the saved setting has been read, so the switch doesn't start off and then flip on. */
    val nearbyEnabled: Boolean? = null,
    val nearbyError: String? = null,
    val isRefreshing: Boolean = false
)

class ExploreViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SessionRepository()
    private val nearbyStore = NearbyStore(application)
    private var nearbyJob: Job? = null

    private val _uiState = MutableStateFlow(ExploreUiState())
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

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

    /** Picks up sessions near a phone that has moved since the circles were last registered. */
    fun onShown() {
        viewModelScope.launch {
            try {
                SessionGeofences.refreshIfEnabled(getApplication())
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    /** Pull to refresh replaces the circles from the phone's current location. */
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, nearbyError = null) }
            try {
                if (nearbyStore.isEnabled()) {
                    val foundLocation = SessionGeofences.refresh(getApplication())
                    if (!foundLocation) {
                        _uiState.update {
                            it.copy(nearbyError = "Couldn't find this phone's location. Check that location is on.")
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(nearbyError = "Couldn't watch nearby sessions. Check that location is on.")
                }
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

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

    private fun com.example.gamercalendar.data.model.GamingSession.toListItem(): SessionListItem? {
        val start = SessionTime.parseIso(start_at) ?: return null
        val end = SessionTime.parseIso(end_at) ?: return null
        return SessionListItem(this, start, end)
    }
}
