package com.example.gamercalendar.ui.feature.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gamercalendar.data.model.User
import com.example.gamercalendar.data.repository.FriendRepository
import com.example.gamercalendar.data.repository.UserRepository
import com.example.gamercalendar.util.apiErrorDetail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

data class AddFriendUiState(
    val query: String = "",
    val results: List<User> = emptyList(),
    val sentTo: Set<String> = emptySet(),
    val pendingFromUserIds: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val isSearching: Boolean = false,
    val error: String? = null
)

class AddFriendViewModel : ViewModel() {

    private val friendRepository = FriendRepository()
    private val userRepository = UserRepository()

    private var searchResults: List<User> = emptyList()
    private var friendIds: Set<String> = emptySet()
    private var searchJob: Job? = null

    private val _uiState = MutableStateFlow(AddFriendUiState(isLoading = true))
    val uiState: StateFlow<AddFriendUiState> = _uiState.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                coroutineScope {
                    val friendsDeferred = async { friendRepository.getFriends() }
                    val outgoingDeferred = async { friendRepository.getFriendRequests("outgoing") }
                    val incomingDeferred = async { friendRepository.getFriendRequests("incoming") }

                    friendIds = friendsDeferred.await().map { it.id }.toSet()
                    val outgoing = outgoingDeferred.await()
                    val incoming = incomingDeferred.await()

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            results = excludeFriends(searchResults),
                            sentTo = outgoing.map { req -> req.receiver_id }.toSet(),
                            pendingFromUserIds = incoming.map { req -> req.sender_id }.toSet()
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = apiErrorDetail(e) ?: e.message ?: "Couldn't load friends"
                    )
                }
            }
        }
    }

    fun onQueryChange(query: String) {
        searchJob?.cancel()
        val trimmed = query.trim()

        if (trimmed.length < MIN_QUERY_LENGTH) {
            searchResults = emptyList()
            _uiState.update { it.copy(query = query, results = emptyList(), isSearching = false) }
            return
        }

        _uiState.update { it.copy(query = query, isSearching = true, error = null) }
        searchJob = viewModelScope.launch {
            try {
                delay(SEARCH_DEBOUNCE_MS)
                searchResults = userRepository.getUsers(search = trimmed)
                _uiState.update { it.copy(results = excludeFriends(searchResults)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = apiErrorDetail(e) ?: e.message ?: "Couldn't search users")
                }
            } finally {
                // Skip if a newer search replaced this one; it owns the flag now.
                if (searchJob === coroutineContext.job) {
                    _uiState.update { it.copy(isSearching = false) }
                }
            }
        }
    }

    fun sendRequest(user: User) {
        viewModelScope.launch {
            try {
                friendRepository.sendFriendRequest(user.id)
                _uiState.update { it.copy(sentTo = it.sentTo + user.id) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = apiErrorDetail(e) ?: e.message ?: "Couldn't send request")
                }
            }
        }
    }

    // The backend already excludes the current user from search results.
    private fun excludeFriends(users: List<User>): List<User> =
        users.filter { it.id !in friendIds }

    companion object {
        const val MIN_QUERY_LENGTH = 2
        private const val SEARCH_DEBOUNCE_MS = 300L
    }
}
