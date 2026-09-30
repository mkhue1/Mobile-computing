package com.example.gamercalendar.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gamercalendar.data.model.GroupDetailResponse
import com.example.gamercalendar.data.model.User
import com.example.gamercalendar.data.repository.FriendRepository
import com.example.gamercalendar.data.repository.GroupRepository
import com.example.gamercalendar.data.repository.UserRepository
import com.example.gamercalendar.ui.navigation.Routes
import com.example.gamercalendar.util.apiErrorDetail
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GroupDetailUiState(
    val group: GroupDetailResponse? = null,
    val currentUserId: String? = null,
    val addableFriends: List<User> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isWorking: Boolean = false,
    val actionError: String? = null,
    val hasLeft: Boolean = false,
    val hasDeleted: Boolean = false
) {
    val isOwner: Boolean
        get() = group != null && group.owner_id == currentUserId
}

class GroupDetailViewModel(
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val groupId: String = checkNotNull(savedStateHandle[Routes.ARG_GROUP_ID])

    private val groupRepository = GroupRepository()
    private val userRepository = UserRepository()
    private val friendRepository = FriendRepository()

    private val _uiState = MutableStateFlow(GroupDetailUiState(isLoading = true))
    val uiState: StateFlow<GroupDetailUiState> = _uiState.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                coroutineScope {
                    val groupDeferred = async { groupRepository.getGroup(groupId) }
                    val meDeferred = async { userRepository.getCurrentUser() }
                    val friendsDeferred = async { friendRepository.getFriends() }

                    val group = groupDeferred.await()
                    val me = meDeferred.await()
                    val friends = friendsDeferred.await()

                    val memberIds = group.members.map { it.user_id }.toSet()

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            group = group,
                            currentUserId = me.id,
                            addableFriends = friends.filter { friend -> friend.id !in memberIds }
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = errorMessage(e, "Couldn't load group"))
                }
            }
        }
    }

    fun addMember(userId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, actionError = null) }
            try {
                groupRepository.addMember(groupId, userId)
                load()
                _uiState.update { it.copy(isWorking = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isWorking = false, actionError = errorMessage(e, "Couldn't add member"))
                }
            }
        }
    }

    fun removeMember(userId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, actionError = null) }
            try {
                groupRepository.removeMember(groupId, userId)
                load()
                _uiState.update { it.copy(isWorking = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isWorking = false, actionError = errorMessage(e, "Couldn't remove member"))
                }
            }
        }
    }

    fun leave() {
        val currentUserId = _uiState.value.currentUserId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, actionError = null) }
            try {
                groupRepository.removeMember(groupId, currentUserId)
                _uiState.update { it.copy(isWorking = false, hasLeft = true) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isWorking = false, actionError = errorMessage(e, "Couldn't leave group"))
                }
            }
        }
    }

    fun rename(newName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, actionError = null) }
            try {
                groupRepository.updateGroup(groupId, newName)
                load()
                _uiState.update { it.copy(isWorking = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isWorking = false, actionError = errorMessage(e, "Couldn't rename group"))
                }
            }
        }
    }

    fun deleteGroup() {
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, actionError = null) }
            try {
                groupRepository.deleteGroup(groupId)
                _uiState.update { it.copy(isWorking = false, hasDeleted = true) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isWorking = false, actionError = errorMessage(e, "Couldn't delete group"))
                }
            }
        }
    }

    private fun errorMessage(e: Exception, fallback: String): String {
        return apiErrorDetail(e) ?: e.message ?: fallback
    }
}
