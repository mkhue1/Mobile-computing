package com.example.gamercalendar.ui.feature.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gamercalendar.data.model.User
import com.example.gamercalendar.data.repository.FriendRepository
import com.example.gamercalendar.data.repository.SteamRepository
import com.example.gamercalendar.data.repository.UserRepository
import com.example.gamercalendar.nfc.NfcFriendPayload
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
import retrofit2.HttpException
import kotlin.time.Duration.Companion.milliseconds

data class AddFriendUiState(
    val query: String = "",
    val results: List<User> = emptyList(),
    val steamSuggestions: List<User> = emptyList(),
    val steamLinked: Boolean = false,
    val steamSuggestionsMessage: String? = null,
    val sentTo: Set<String> = emptySet(),
    val pendingFromUserIds: Set<String> = emptySet(),
    val currentUserId: String? = null,
    val currentUsername: String? = null,
    val pendingConfirmUser: User? = null,
    val isResolvingDiscoveredUser: Boolean = false,
    val isLoading: Boolean = false,
    val isSearching: Boolean = false,
    val isLoadingSteamSuggestions: Boolean = false,
    val error: String? = null,
    val message: String? = null
)

class AddFriendViewModel : ViewModel() {

    private val friendRepository = FriendRepository()
    private val userRepository = UserRepository()
    private val steamRepository = SteamRepository()

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
                    val steamStatusDeferred = async { steamRepository.getStatus() }
                    val meDeferred = async { userRepository.getCurrentUser() }

                    friendIds = friendsDeferred.await().map { it.id }.toSet()
                    val outgoing = outgoingDeferred.await()
                    val incoming = incomingDeferred.await()
                    val steamStatus = steamStatusDeferred.await()
                    val me = meDeferred.await()

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            results = excludeFriends(searchResults),
                            sentTo = outgoing.map { req -> req.receiver_id }.toSet(),
                            pendingFromUserIds = incoming.map { req -> req.sender_id }.toSet(),
                            steamLinked = steamStatus.linked,
                            currentUserId = me.id,
                            currentUsername = me.username
                        )
                    }

                    if (steamStatus.linked) {
                        loadSteamSuggestions()
                    } else {
                        _uiState.update {
                            it.copy(
                                steamSuggestions = emptyList(),
                                steamSuggestionsMessage = null,
                                isLoadingSteamSuggestions = false
                            )
                        }
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

    private fun loadSteamSuggestions() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(isLoadingSteamSuggestions = true, steamSuggestionsMessage = null)
            }
            try {
                val suggestions = friendRepository.getSteamFriendSuggestions()
                _uiState.update {
                    it.copy(
                        steamSuggestions = suggestions,
                        isLoadingSteamSuggestions = false,
                        steamSuggestionsMessage = if (suggestions.isEmpty()) {
                            "No Steam friends found on Roundtable yet."
                        } else {
                            null
                        }
                    )
                }
            } catch (e: Exception) {
                val detail = apiErrorDetail(e)
                val isUnlinked = e is HttpException && e.code() == 400
                _uiState.update {
                    it.copy(
                        isLoadingSteamSuggestions = false,
                        steamSuggestions = emptyList(),
                        steamSuggestionsMessage = detail
                            ?: e.message
                            ?: "Couldn't load Steam suggestions",
                        steamLinked = if (isUnlinked && detail?.contains("Connect", ignoreCase = true) == true) {
                            false
                        } else {
                            it.steamLinked
                        }
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
                delay(SEARCH_DEBOUNCE_MS.milliseconds)
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
        resolveDiscoveredUser(userId, DiscoverySource.Qr)
    }

    fun onNfcPayload(rawContent: String) {
        val userId = NfcFriendPayload.parse(rawContent)
        if (userId == null) {
            _uiState.update {
                it.copy(error = "That NFC tap isn't a Roundtable profile")
            }
            return
        }
        resolveDiscoveredUser(userId, DiscoverySource.Nfc)
    }

    fun confirmDiscoveredFriend() {
        val user = _uiState.value.pendingConfirmUser ?: return
        _uiState.update { it.copy(pendingConfirmUser = null) }
        sendRequest(user, announceSent = true)
    }

    fun dismissDiscoveredFriend() {
        _uiState.update { it.copy(pendingConfirmUser = null) }
    }

    private fun resolveDiscoveredUser(userId: String, source: DiscoverySource) {
        val state = _uiState.value
        when (userId) {
            state.currentUserId -> {
                _uiState.update { it.copy(error = source.ownError) }
                return
            }
            in friendIds -> {
                _uiState.update { it.copy(error = "You're already friends with this user") }
                return
            }
            in state.sentTo -> {
                _uiState.update { it.copy(error = "Friend request already sent") }
                return
            }
            in state.pendingFromUserIds -> {
                _uiState.update {
                    it.copy(error = "They already sent you a request — check Requests")
                }
                return
            }
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(isResolvingDiscoveredUser = true, error = null, pendingConfirmUser = null)
            }
            try {
                val user = userRepository.getUser(userId)
                _uiState.update {
                    it.copy(isResolvingDiscoveredUser = false, pendingConfirmUser = user)
                }
            } catch (e: Exception) {
                val notFound = e is HttpException && e.code() == 404
                _uiState.update {
                    it.copy(
                        isResolvingDiscoveredUser = false,
                        error = when {
                            notFound -> source.missingError
                            else -> apiErrorDetail(e) ?: e.message ?: "Couldn't look up that user"
                        }
                    )
                }
            }
        }
    }

    fun messageShown() {
        _uiState.update { it.copy(message = null) }
    }

    fun sendRequest(user: User, announceSent: Boolean = false) {
        viewModelScope.launch {
            try {
                friendRepository.sendFriendRequest(user.id)
                _uiState.update {
                    it.copy(
                        sentTo = it.sentTo + user.id,
                        steamSuggestions = it.steamSuggestions.filter { suggestion ->
                            suggestion.id != user.id
                        },
                        message = if (announceSent) {
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

private enum class DiscoverySource(
    val ownError: String,
    val missingError: String
) {
    Qr(
        ownError = "That's your own QR code",
        missingError = "No Roundtable user matches that QR code"
    ),
    Nfc(
        ownError = "That's your own NFC profile",
        missingError = "No Roundtable user matches that NFC profile"
    )
}
