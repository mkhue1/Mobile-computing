package com.example.gamercalendar.ui.feature.auth

import android.content.Context
import androidx.fragment.app.FragmentActivity
import com.example.gamercalendar.biometric.findFragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.gamercalendar.biometric.BiometricCancelled
import com.example.gamercalendar.biometric.BiometricCredentialInvalid
import com.example.gamercalendar.biometric.BiometricLogin
import com.example.gamercalendar.biometric.BiometricSession
import com.example.gamercalendar.biometric.BiometricStatus
import com.example.gamercalendar.data.repository.AuthRepository
import com.example.gamercalendar.data.session.CurrentUserStore
import com.example.gamercalendar.data.session.SessionManager
import com.example.gamercalendar.util.AuthValidation
import com.example.gamercalendar.util.apiErrorDetail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import retrofit2.HttpException

data class AuthUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val isCheckingSession: Boolean = false,
    val biometricStatus: BiometricStatus = BiometricStatus.Unavailable,
    val biometricEnabled: Boolean = false,
    val needsUnlock: Boolean = false,
    val offerBiometricSetup: Boolean = false,
    val biometricError: String? = null,
    val biometricBusy: Boolean = false,
)

class AuthViewModel(
    private val sessionManager: SessionManager,
    private val authRepository: AuthRepository,
    private val biometricLogin: BiometricLogin,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState(isCheckingSession = true))
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
            val status = biometricLogin.status()
            val token = sessionManager.getToken()
            if (token.isNullOrBlank()) {
                _uiState.value = AuthUiState(
                    isCheckingSession = false,
                    biometricEnabled = biometricLogin.hasCredential(),
                    biometricStatus = status,
                )
                return@launch
            }
            val enabled = biometricLogin.hasCredential()
            // The splash screen hides once isCheckingSession is false, so the token flow must
            // already hold the token or the login screen flashes for a frame.
            accessToken.first { it == token }
            _uiState.value = AuthUiState(
                isCheckingSession = false,
                biometricEnabled = enabled,
                biometricStatus = status,
                needsUnlock = enabled,
            )
            verifyRestoredSession(token)
        }
    }

    private suspend fun verifyRestoredSession(token: String) {
        try {
            val user = authRepository.me()
            if (sessionManager.getToken() != token) return
            sessionManager.saveSession(token, user.username, user.email)
            CurrentUserStore.set(user)
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            if (e.code() != 401 || sessionManager.getToken() != token) return
            val hadBiometric = biometricLogin.hasCredential()
            biometricLogin.disable()
            authRepository.logout()
            _uiState.value = AuthUiState(
                biometricStatus = biometricLogin.status(),
                error = if (hadBiometric) "Your login expired. Sign in with your password." else null,
            )
        } catch (_: Exception) {
            // Offline or a transient server error. Keep the saved session.
        }
    }

    fun login(identifier: String, password: String) {
        if (identifier.isBlank() || password.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Enter email/username and password")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, biometricError = null)
            try {
                val response = authRepository.login(identifier.trim(), password)
                finishPasswordLogin(response.user.username)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
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
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, biometricError = null)
            try {
                val response = authRepository.register(trimmedEmail, trimmedUsername, password)
                finishPasswordLogin(response.user.username)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = httpErrorMessage(e, "Sign up failed")
                )
            }
        }
    }

    fun refreshBiometricStatus() {
        if (_uiState.value.isCheckingSession || _uiState.value.biometricBusy) return
        val status = biometricLogin.status()
        if (_uiState.value.biometricStatus == status) return
        _uiState.value = _uiState.value.copy(biometricStatus = status)
    }

    fun signInWithBiometric(activity: FragmentActivity) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(biometricBusy = true, error = null, biometricError = null)
            try {
                val session = biometricLogin.decrypt(activity)
                sessionManager.saveSession(session.token, session.username, session.email)
                try {
                    val user = authRepository.me()
                    sessionManager.saveSession(session.token, user.username, user.email)
                    CurrentUserStore.set(user)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: HttpException) {
                    if (e.code() == 401) {
                        biometricLogin.disable()
                        authRepository.logout()
                        _uiState.value = AuthUiState(
                            biometricStatus = biometricLogin.status(),
                            error = "Biometric sign-in expired. Sign in with your password.",
                        )
                        return@launch
                    }
                } catch (_: Exception) {
                    // Offline or a transient server error. The saved token is enough to enter.
                }
                _uiState.value = AuthUiState(
                    biometricEnabled = true,
                    biometricStatus = biometricLogin.status(),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: BiometricCancelled) {
                _uiState.value = _uiState.value.copy(biometricBusy = false)
            } catch (e: BiometricCredentialInvalid) {
                _uiState.value = _uiState.value.copy(
                    biometricBusy = false,
                    biometricEnabled = false,
                    biometricError = e.message,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    biometricBusy = false,
                    biometricError = e.message ?: "Biometric sign-in failed",
                )
            }
        }
    }

    fun unlockWithBiometric(activity: FragmentActivity) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(biometricBusy = true, biometricError = null)
            try {
                val session = biometricLogin.decrypt(activity)
                val current = sessionManager.readSession()
                if (current != null && current.username != session.username) {
                    _uiState.value = _uiState.value.copy(
                        biometricBusy = false,
                        biometricError = "Biometric sign-in doesn't match this account. Use your password.",
                    )
                    return@launch
                }
                if (current == null) {
                    // The background session check logged the user out while the prompt was up.
                    _uiState.value = _uiState.value.copy(biometricBusy = false)
                    return@launch
                }
                _uiState.value = _uiState.value.copy(
                    biometricBusy = false,
                    needsUnlock = false,
                    biometricEnabled = true,
                    biometricError = null,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: BiometricCancelled) {
                _uiState.value = _uiState.value.copy(biometricBusy = false)
            } catch (e: BiometricCredentialInvalid) {
                _uiState.value = _uiState.value.copy(
                    biometricBusy = false,
                    biometricEnabled = false,
                    needsUnlock = true,
                    biometricError = e.message,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    biometricBusy = false,
                    biometricError = e.message ?: "Biometric sign-in failed",
                )
            }
        }
    }

    fun runWithBiometricHost(context: Context, block: (FragmentActivity) -> Unit) {
        val activity = context.findFragmentActivity()
        if (activity == null) {
            _uiState.value = _uiState.value.copy(
                biometricBusy = false,
                biometricError = "Couldn't start biometric sign-in. Close the app and try again.",
            )
            return
        }
        block(activity)
    }

    fun enableBiometric(activity: FragmentActivity) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                offerBiometricSetup = false,
                biometricError = null,
                biometricBusy = true,
            )
            val current = sessionManager.readSession()
            if (current == null) {
                _uiState.value = _uiState.value.copy(
                    biometricBusy = false,
                    biometricError = "Log in with your password before enabling biometrics"
                )
                return@launch
            }
            try {
                biometricLogin.enable(
                    activity,
                    BiometricSession(current.token, current.username, current.email)
                )
                _uiState.value = _uiState.value.copy(
                    biometricBusy = false,
                    biometricEnabled = true,
                    biometricStatus = biometricLogin.status(),
                    offerBiometricSetup = false,
                    biometricError = null,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: BiometricCancelled) {
                _uiState.value = _uiState.value.copy(biometricBusy = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    biometricBusy = false,
                    biometricError = e.message ?: "Couldn't enable biometric sign-in"
                )
            }
        }
    }

    fun disableBiometric() {
        viewModelScope.launch {
            biometricLogin.disable()
            biometricLogin.setSetupDeclined(true)
            _uiState.value = _uiState.value.copy(
                biometricEnabled = false,
                offerBiometricSetup = false,
                biometricError = null,
            )
        }
    }

    fun declineBiometricOffer() {
        viewModelScope.launch {
            biometricLogin.setSetupDeclined(true)
            _uiState.value = _uiState.value.copy(offerBiometricSetup = false)
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _uiState.value = AuthUiState(
                biometricEnabled = biometricLogin.hasCredential(),
                biometricStatus = biometricLogin.status(),
            )
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null, biometricError = null)
    }

    private suspend fun finishPasswordLogin(username: String) {
        val storedName = biometricLogin.storedUsername()
        if (biometricLogin.hasCredential() && storedName != null && storedName != username) {
            biometricLogin.disable()
            biometricLogin.setSetupDeclined(false)
        }
        val enabled = biometricLogin.hasCredential()
        val status = biometricLogin.status()
        _uiState.value = AuthUiState(
            biometricEnabled = enabled,
            biometricStatus = status,
            offerBiometricSetup = !enabled &&
                status == BiometricStatus.Ready &&
                !biometricLogin.isSetupDeclined(),
        )
    }

    private fun httpErrorMessage(e: Exception, fallback: String): String {
        if (e is HttpException) {
            apiErrorDetail(e)?.let { return it }
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
        private val authRepository: AuthRepository,
        private val biometricLogin: BiometricLogin,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AuthViewModel(sessionManager, authRepository, biometricLogin) as T
        }
    }
}
