package com.example.gamercalendar.geofence

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * A notification tap asks the open app to show a nearby session.
 * A null id means several sessions were in the notification, so home is enough.
 */
object NearbyOpenEvents {
    private val _requests = MutableSharedFlow<String?>(replay = 1)
    val requests: SharedFlow<String?> = _requests.asSharedFlow()

    fun emit(sessionId: String?) {
        _requests.tryEmit(sessionId)
    }

    fun clear() {
        _requests.resetReplayCache()
    }
}
