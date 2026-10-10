package com.example.gamercalendar.ui.feature.profile

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gamercalendar.data.model.User
import com.example.gamercalendar.data.repository.UserRepository
import com.example.gamercalendar.data.session.CurrentUserStore
import com.example.gamercalendar.util.apiErrorDetail
import com.example.gamercalendar.util.resizeToJpeg
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val user: User? = null,
    val isLoading: Boolean = false,
    val isUploading: Boolean = false,
    val error: String? = null
)

class UserViewModel : ViewModel() {

    private val repository = UserRepository()

    private val _uiState = MutableStateFlow(ProfileUiState(user = CurrentUserStore.user.value))
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        // The repository writes every fresh user into CurrentUserStore, so the store is the source of truth.
        viewModelScope.launch {
            CurrentUserStore.user.collect { user ->
                _uiState.update { it.copy(user = user) }
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                repository.getCurrentUser()
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = apiErrorDetail(e) ?: "Couldn't load your profile")
                }
            }
        }
    }

    fun uploadPhoto(context: Context, uri: Uri) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true, error = null) }
            try {
                val jpeg = resizeToJpeg(appContext, uri)
                repository.uploadAvatar(jpeg)
                _uiState.update { it.copy(isUploading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isUploading = false, error = apiErrorDetail(e) ?: "Couldn't upload photo")
                }
            }
        }
    }

    fun removePhoto() {
        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true, error = null) }
            try {
                repository.deleteAvatar()
                _uiState.update { it.copy(isUploading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isUploading = false, error = apiErrorDetail(e) ?: "Couldn't remove photo")
                }
            }
        }
    }
}
