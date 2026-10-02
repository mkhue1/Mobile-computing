package com.example.gamercalendar.data.repository

import com.example.gamercalendar.data.api.ApiClient
import com.example.gamercalendar.data.model.Game
import com.example.gamercalendar.data.model.GamingSession
import com.example.gamercalendar.data.model.InviteAction
import com.example.gamercalendar.data.model.InviteActionResponse
import com.example.gamercalendar.data.model.InviteCreate
import com.example.gamercalendar.data.model.SessionCancelResponse
import com.example.gamercalendar.data.model.SessionCreate
import com.example.gamercalendar.data.model.SessionInvite
import com.example.gamercalendar.data.model.SessionParticipant

class SessionRepository {

    suspend fun getGames(): List<Game> {
        return ApiClient.api.getGames()
    }

    suspend fun searchGames(query: String): List<Game> {
        return ApiClient.api.getGames().filter { it.name.contains(query, ignoreCase = true) }
    }

    suspend fun getSessions(): List<GamingSession> {
        return ApiClient.api.getSessions()
    }

    suspend fun getSession(sessionId: String): GamingSession {
        return ApiClient.api.getSession(sessionId)
    }

    suspend fun createSession(session: SessionCreate): GamingSession {
        return ApiClient.api.createSession(session)
    }

    suspend fun updateSession(sessionId: String, session: SessionCreate): GamingSession {
        return ApiClient.api.updateSession(sessionId, session)
    }

    suspend fun cancelSession(sessionId: String): SessionCancelResponse {
        return ApiClient.api.cancelSession(sessionId)
    }

    suspend fun leaveSession(sessionId: String) {
        ApiClient.api.leaveSession(sessionId)
    }

    suspend fun getParticipants(sessionId: String): List<SessionParticipant> {
        return ApiClient.api.getSessionParticipants(sessionId)
    }

    suspend fun inviteToSession(sessionId: String, userId: String): SessionInvite {
        return ApiClient.api.inviteToSession(
            sessionId,
            InviteCreate(session_id = sessionId, receiver_id = userId)
        )
    }

    suspend fun getSessionInvites(): List<SessionInvite> {
        return ApiClient.api.getSessionInvites()
    }

    suspend fun acceptInvite(invite: SessionInvite): InviteActionResponse {
        return ApiClient.api.acceptSessionInvite(invite.session_id, InviteAction(invite_id = invite.id))
    }

    suspend fun declineInvite(invite: SessionInvite): InviteActionResponse {
        return ApiClient.api.declineSessionInvite(invite.session_id, InviteAction(invite_id = invite.id))
    }
}
