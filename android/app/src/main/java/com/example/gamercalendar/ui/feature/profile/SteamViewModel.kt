package com.example.gamercalendar.ui.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gamercalendar.data.repository.SteamRepository
import com.example.gamercalendar.util.SteamLinkEvent
import com.example.gamercalendar.util.SteamLinkEvents
import com.example.gamercalendar.util.apiErrorDetail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SteamUiState(
    val linked: Boolean = false,
    val steamId: String? = null,
    val isLoading: Boolean = false,
    val isConnecting: Boolean = false,
    val authUrl: String? = null,
    val error: String? = null,
    val message: String? = null
)

class SteamViewModel(
    private val steamRepository: SteamRepository = SteamRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(SteamUiState(isLoading = true))
    val uiState: StateFlow<SteamUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            SteamLinkEvents.events.collect { event ->
                when (event) {
                    is SteamLinkEvent.Success -> {
                        _uiState.update {
                            it.copy(message = "Steam account connected", error = null)
                        }
                        refresh()
                    }
                    is SteamLinkEvent.Error -> {
                        _uiState.update {
                            it.copy(
                                error = steamErrorMessage(event.reason),
                                isConnecting = false
                            )
                        }
                    }
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val status = steamRepository.getStatus()
                _uiState.update {
                    it.copy(
                        linked = status.linked,
                        steamId = status.steam_id,
                        isLoading = false,
                        isConnecting = false,
                        authUrl = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isConnecting = false,
                        error = apiErrorDetail(e) ?: e.message ?: "Couldn't load Steam status"
                    )
                }
            }
        }
    }

    fun connectSteam() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(isConnecting = true, error = null, message = null, authUrl = null)
            }
            try {
                val start = steamRepository.startLink()
                _uiState.update {
                    it.copy(authUrl = start.auth_url, isConnecting = false)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        error = apiErrorDetail(e) ?: e.message ?: "Couldn't start Steam linking"
                    )
                }
            }
        }
    }

    fun consumeAuthUrl() {
        _uiState.update { it.copy(authUrl = null) }
    }

    fun disconnectSteam() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, message = null) }
            try {
                steamRepository.unlink()
                _uiState.update {
                    it.copy(
                        linked = false,
                        steamId = null,
                        isLoading = false,
                        message = "Steam account disconnected"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = apiErrorDetail(e) ?: e.message ?: "Couldn't disconnect Steam"
                    )
                }
            }
        }
    }

    private fun steamErrorMessage(reason: String?): String = when (reason) {
        "cancelled" -> "Steam linking was cancelled"
        "steam_already_linked" -> "That Steam account is already linked to another user"
        "verification_failed" -> "Steam verification failed"
        "invalid_state", "missing_state" -> "Steam linking session expired. Try again."
        else -> "Couldn't link Steam account"
    }
}
