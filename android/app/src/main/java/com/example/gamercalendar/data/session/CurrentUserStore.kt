package com.example.gamercalendar.data.session

import com.example.gamercalendar.data.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// In-memory copy of the logged-in user, so UI like the top bar updates right after an avatar change.
object CurrentUserStore {

    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user.asStateFlow()

    fun set(user: User) {
        _user.value = user
    }

    fun clear() {
        _user.value = null
    }
}
