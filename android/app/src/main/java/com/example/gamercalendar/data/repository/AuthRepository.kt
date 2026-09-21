package com.example.gamercalendar.data.repository

import com.example.gamercalendar.data.api.ApiClient
import com.example.gamercalendar.data.model.LoginRequest
import com.example.gamercalendar.data.model.TokenResponse
import com.example.gamercalendar.data.model.User
import com.example.gamercalendar.data.model.UserCreate
import com.example.gamercalendar.data.session.SessionManager

class AuthRepository(
    private val sessionManager: SessionManager
) {

    suspend fun register(email: String, username: String, password: String): TokenResponse {
        ApiClient.api.createUser(
            UserCreate(
                email = email,
                username = username,
                password = password
            )
        )
        return login(email, password)
    }

    suspend fun login(identifier: String, password: String): TokenResponse {
        val response = ApiClient.api.login(
            LoginRequest(
                identifier = identifier,
                password = password
            )
        )
        sessionManager.saveSession(
            token = response.access_token,
            username = response.user.username,
            email = response.user.email
        )
        return response
    }

    suspend fun me(): User {
        return ApiClient.api.me()
    }

    suspend fun logout() {
        sessionManager.clearSession()
    }
}
