package com.example.gamercalendar.data.repository

import com.example.gamercalendar.data.api.ApiClient
import com.example.gamercalendar.data.model.User

class UserRepository {

    suspend fun getUsers(): List<User> {
        return ApiClient.api.getUsers()
    }
}
