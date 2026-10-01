package com.example.gamercalendar.data.repository

import com.example.gamercalendar.data.api.ApiClient
import com.example.gamercalendar.data.model.User

class UserRepository {

    suspend fun getUsers(search: String? = null, limit: Int? = null): List<User> {
        return ApiClient.api.getUsers(search = search, limit = limit)
    }

    suspend fun getCurrentUser(): User {
        return ApiClient.api.me()
    }
}
