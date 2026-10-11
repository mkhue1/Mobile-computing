package com.example.gamercalendar.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed class SteamLinkEvent {
    data object Success : SteamLinkEvent()
    data class Error(val reason: String?) : SteamLinkEvent()
}

object SteamLinkEvents {
    private val _events = MutableSharedFlow<SteamLinkEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<SteamLinkEvent> = _events.asSharedFlow()

    fun emit(event: SteamLinkEvent) {
        _events.tryEmit(event)
    }
}
