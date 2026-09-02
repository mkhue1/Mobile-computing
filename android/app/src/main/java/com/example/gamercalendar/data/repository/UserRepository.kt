package com.example.gamercalendar.data.repository

import com.example.gamercalendar.data.api.ApiClient
import com.example.gamercalendar.data.model.User
import com.example.gamercalendar.data.model.UserCreate

class UserRepository {

    suspend fun getUsers(): List<User> {
        return ApiClient.api.getUsers()
    }

    suspend fun createUser(user: UserCreate): User {
        return ApiClient.api.createUser(user)
    }
}