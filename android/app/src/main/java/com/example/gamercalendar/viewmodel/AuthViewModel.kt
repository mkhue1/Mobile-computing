package com.example.gamercalendar.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.gamercalendar.data.repository.AuthRepository
import com.example.gamercalendar.data.session.SessionManager
import com.example.gamercalendar.util.AuthValidation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import retrofit2.HttpException

data class AuthUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val isCheckingSession: Boolean = true
)

class AuthViewModel(
    private val sessionManager: SessionManager,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    val accessToken: StateFlow<String?> = sessionManager.accessToken
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val username: StateFlow<String?> = sessionManager.username
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val email: StateFlow<String?> = sessionManager.email
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        restoreSession()
    }

    private fun restoreSession() {
        viewModelScope.launch {
            val token = sessionManager.getToken()
            if (token.isNullOrBlank()) {
                _uiState.value = AuthUiState(isCheckingSession = false)
                return@launch
            }
            try {
                val user = authRepository.me()
                sessionManager.saveSession(token, user.username, user.email)
                _uiState.value = AuthUiState(isCheckingSession = false)
            } catch (_: Exception) {
                authRepository.logout()
                _uiState.value = AuthUiState(isCheckingSession = false)
            }
        }
    }

    fun login(identifier: String, password: String) {
        if (identifier.isBlank() || password.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Enter email/username and password")
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState(isLoading = true)
            try {
                authRepository.login(identifier.trim(), password)
                _uiState.value = AuthUiState(isLoading = false)
            } catch (e: Exception) {
                _uiState.value = AuthUiState(
                    isLoading = false,
                    error = httpErrorMessage(e, "Login failed")
                )
            }
        }
    }

    fun register(email: String, username: String, password: String) {
        val trimmedEmail = email.trim()
        val trimmedUsername = username.trim()

        when {
            !AuthValidation.isValidEmail(trimmedEmail) -> {
                _uiState.value = _uiState.value.copy(error = "Enter a valid email address")
                return
            }
            !AuthValidation.isValidUsername(trimmedUsername) -> {
                _uiState.value = _uiState.value.copy(
                    error = "Username must be 3–120 characters (letters, numbers, _ or -)"
                )
                return
            }
            !AuthValidation.isValidPassword(password) -> {
                _uiState.value = _uiState.value.copy(
                    error = AuthValidation.passwordError(password)
                        ?: "Password does not meet requirements"
                )
                return
            }
        }

        viewModelScope.launch {
            _uiState.value = AuthUiState(isLoading = true)
            try {
                authRepository.register(trimmedEmail, trimmedUsername, password)
                _uiState.value = AuthUiState(isLoading = false)
            } catch (e: Exception) {
                _uiState.value = AuthUiState(
                    isLoading = false,
                    error = httpErrorMessage(e, "Sign up failed")
                )
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _uiState.value = AuthUiState(isCheckingSession = false)
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private fun httpErrorMessage(e: Exception, fallback: String): String {
        if (e is HttpException) {
            val body = try {
                e.response()?.errorBody()?.string()
            } catch (_: Exception) {
                null
            }
            if (!body.isNullOrBlank()) {
                val detail = Regex("\"detail\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
                if (!detail.isNullOrBlank()) return detail
                val listDetail = Regex("\"msg\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
                if (!listDetail.isNullOrBlank()) return listDetail
            }
            return when (e.code()) {
                401 -> "Invalid credentials"
                409 -> "Email or username already registered"
                else -> fallback
            }
        }
        return e.message ?: fallback
    }

    class Factory(
        private val sessionManager: SessionManager,
        private val authRepository: AuthRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AuthViewModel(sessionManager, authRepository) as T
        }
    }
}
