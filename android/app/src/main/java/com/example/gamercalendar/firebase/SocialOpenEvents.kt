package com.example.gamercalendar.firebase

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Where a tapped social notification should take the user.
 * Routes a notifcation tap from main activity to app navigation*/
internal sealed interface SocialOpenRequest {
    /** An invite opens its session, where it can be accepted or declined. */
    data class SessionInvite(val sessionId: String) : SocialOpenRequest

    /** A friend request opens the friends hub, where requests are listed. */
    data object FriendRequests : SocialOpenRequest
}

internal object SocialOpenEvents {
    private val _requests = MutableSharedFlow<SocialOpenRequest>(replay = 1)
    val requests: SharedFlow<SocialOpenRequest> = _requests.asSharedFlow()

    fun emit(request: SocialOpenRequest) {
        _requests.tryEmit(request)
    }

    fun clear() {
        _requests.resetReplayCache()
    }
}
