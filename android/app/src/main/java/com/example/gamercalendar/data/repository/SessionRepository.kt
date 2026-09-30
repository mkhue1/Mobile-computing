package com.example.gamercalendar.data.repository

import com.example.gamercalendar.data.api.ApiClient
import com.example.gamercalendar.data.model.Game
import com.example.gamercalendar.data.model.GamingSession
import com.example.gamercalendar.data.model.SessionCreate

class SessionRepository {

    suspend fun getGames(): List<Game> {
        return ApiClient.api.getGames()
    }

    suspend fun getSessions(): List<GamingSession> {
        return ApiClient.api.getSessions()
    }

    suspend fun createSession(session: SessionCreate): GamingSession {
        return ApiClient.api.createSession(session)
    }
}
