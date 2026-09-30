package com.example.gamercalendar.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gamercalendar.data.model.User
import com.example.gamercalendar.data.repository.FriendRepository
import com.example.gamercalendar.util.apiErrorDetail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FriendsHubUiState(
    val friends: List<User> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val isWorking: Boolean = false,
    val actionError: String? = null
)

class FriendsHubViewModel : ViewModel() {

    private val repository = FriendRepository()

    private val _uiState = MutableStateFlow(FriendsHubUiState(isLoading = true))
    val uiState: StateFlow<FriendsHubUiState> = _uiState.asStateFlow()

    /** Background load, e.g. when the screen is shown. */
    fun load() = load(isRefresh = false)

    /** User-initiated pull to refresh. */
    fun refresh() = load(isRefresh = true)

    private fun load(isRefresh: Boolean) {
        viewModelScope.launch {
            _uiState.update {
                if (isRefresh) it.copy(isRefreshing = true, error = null)
                else it.copy(isLoading = true, error = null)
            }
            try {
                val friends = repository.getFriends()
                _uiState.update {
                    it.copy(friends = friends, isLoading = false, isRefreshing = false)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = apiErrorDetail(e) ?: e.message ?: "Couldn't load friends"
                    )
                }
            }
        }
    }

    fun removeFriend(friendId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, actionError = null) }
            try {
                repository.removeFriend(friendId)
                load(isRefresh = true)
                _uiState.update { it.copy(isWorking = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isWorking = false,
                        actionError = apiErrorDetail(e) ?: e.message ?: "Couldn't remove friend"
                    )
                }
            }
        }
    }
}
