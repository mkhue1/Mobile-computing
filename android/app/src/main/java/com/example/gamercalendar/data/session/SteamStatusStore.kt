package com.example.gamercalendar.data.session

import com.example.gamercalendar.data.model.SteamStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// In-memory copy of the logged-in user's Steam link, so the profile page doesn't re-check it on every visit.
object SteamStatusStore {

    private val _status = MutableStateFlow<SteamStatus?>(null)
    val status: StateFlow<SteamStatus?> = _status.asStateFlow()

    fun set(status: SteamStatus) {
        _status.value = status
        CurrentUserStore.user.value?.let { user ->
            if (user.steam_linked != status.linked) {
                CurrentUserStore.set(user.copy(steam_linked = status.linked))
            }
        }
    }

    fun clear() {
        _status.value = null
    }
}
