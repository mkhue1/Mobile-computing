package com.example.gamercalendar.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gamercalendar.data.model.User
import com.example.gamercalendar.data.repository.FriendRepository
import com.example.gamercalendar.data.repository.UserRepository
import com.example.gamercalendar.util.apiErrorDetail
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddFriendUiState(
    val query: String = "",
    val results: List<User> = emptyList(),
    val sentTo: Set<String> = emptySet(),
    val pendingFromUserIds: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class AddFriendViewModel : ViewModel() {

    private val friendRepository = FriendRepository()
    private val userRepository = UserRepository()

    private var allUsers: List<User> = emptyList()
    private var selfId: String? = null
    private var friendIds: Set<String> = emptySet()

    private val _uiState = MutableStateFlow(AddFriendUiState(isLoading = true))
    val uiState: StateFlow<AddFriendUiState> = _uiState.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                coroutineScope {
                    val usersDeferred = async { userRepository.getUsers() }
                    val meDeferred = async { userRepository.getCurrentUser() }
                    val friendsDeferred = async { friendRepository.getFriends() }
                    val outgoingDeferred = async { friendRepository.getFriendRequests("outgoing") }
                    val incomingDeferred = async { friendRepository.getFriendRequests("incoming") }

                    allUsers = usersDeferred.await()
                    selfId = meDeferred.await().id
                    friendIds = friendsDeferred.await().map { it.id }.toSet()
                    val outgoing = outgoingDeferred.await()
                    val incoming = incomingDeferred.await()

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            results = filterResults(it.query),
                            sentTo = outgoing.map { req -> req.receiver_id }.toSet(),
                            pendingFromUserIds = incoming.map { req -> req.sender_id }.toSet()
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = apiErrorDetail(e) ?: e.message ?: "Couldn't load users"
                    )
                }
            }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query, results = filterResults(query)) }
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

    private fun filterResults(query: String): List<User> {
        val trimmed = query.trim()
        if (trimmed.length < MIN_QUERY_LENGTH) return emptyList()
        return allUsers
            .filter { it.id != selfId && it.id !in friendIds }
            .filter { it.username.contains(trimmed, ignoreCase = true) }
    }

    companion object {
        const val MIN_QUERY_LENGTH = 2
    }
}
