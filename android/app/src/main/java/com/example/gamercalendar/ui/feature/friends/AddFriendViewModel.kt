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
    val currentUserId: String? = null,
    val currentUsername: String? = null,
    val pendingQrConfirmUser: User? = null,
    val isResolvingQr: Boolean = false,
    val isLoading: Boolean = false,
    val isSearching: Boolean = false,
    val error: String? = null,
    val message: String? = null
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
                    val meDeferred = async { userRepository.getCurrentUser() }

                    friendIds = friendsDeferred.await().map { it.id }.toSet()
                    val outgoing = outgoingDeferred.await()
                    val incoming = incomingDeferred.await()
                    val me = meDeferred.await()

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            results = excludeFriends(searchResults),
                            sentTo = outgoing.map { req -> req.receiver_id }.toSet(),
                            pendingFromUserIds = incoming.map { req -> req.sender_id }.toSet(),
                            currentUserId = me.id,
                            currentUsername = me.username
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

    fun onQrScanned(rawContent: String) {
        val userId = rawContent.trim()
        if (!UUID_PATTERN.matches(userId)) {
            _uiState.update {
                it.copy(error = "That QR code isn't a Roundtable user ID")
            }
            return
        }

        val state = _uiState.value
        when {
            userId == state.currentUserId -> {
                _uiState.update { it.copy(error = "That's your own QR code") }
                return
            }
            userId in friendIds -> {
                _uiState.update { it.copy(error = "You're already friends with this user") }
                return
            }
            userId in state.sentTo -> {
                _uiState.update { it.copy(error = "Friend request already sent") }
                return
            }
            userId in state.pendingFromUserIds -> {
                _uiState.update {
                    it.copy(error = "They already sent you a request — check Requests")
                }
                return
            }
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(isResolvingQr = true, error = null, pendingQrConfirmUser = null)
            }
            try {
                val user = userRepository.getUser(userId)
                _uiState.update {
                    it.copy(isResolvingQr = false, pendingQrConfirmUser = user)
                }
            } catch (e: Exception) {
                val notFound = e is HttpException && e.code() == 404
                _uiState.update {
                    it.copy(
                        isResolvingQr = false,
                        error = when {
                            notFound -> "No Roundtable user matches that QR code"
                            else -> apiErrorDetail(e) ?: e.message ?: "Couldn't look up that user"
                        }
                    )
                }
            }
        }
    }

    fun confirmQrFriendRequest() {
        val user = _uiState.value.pendingQrConfirmUser ?: return
        _uiState.update { it.copy(pendingQrConfirmUser = null) }
        sendRequest(user, fromQr = true)
    }

    fun dismissQrConfirm() {
        _uiState.update { it.copy(pendingQrConfirmUser = null) }
    }

    fun messageShown() {
        _uiState.update { it.copy(message = null) }
    }

    fun sendRequest(user: User, fromQr: Boolean = false) {
        viewModelScope.launch {
            try {
                friendRepository.sendFriendRequest(user.id)
                _uiState.update {
                    it.copy(
                        sentTo = it.sentTo + user.id,
                        message = if (fromQr) {
                            "Friend request sent to ${user.username}"
                        } else {
                            it.message
                        }
                    )
                }
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
        private val UUID_PATTERN = Regex(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
        )
    }
}
