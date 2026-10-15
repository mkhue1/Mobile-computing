package com.example.gamercalendar.data.model

data class User(
    val id: String,
    val username: String,
    val email: String,
    val steam_linked: Boolean = false,
    val avatar_updated_at: String? = null
)

data class UserCreate(
    val email: String,
    val username: String,
    val password: String
)

data class LoginRequest(
    val identifier: String,
    val password: String
)

data class TokenResponse(
    val access_token: String,
    val token_type: String,
    val user: User
)

/** The phone's Firebase Cloud Messaging (FCM token), allows the server to send push notifications. */
data class DeviceTokenRegister(
    val token: String
)
