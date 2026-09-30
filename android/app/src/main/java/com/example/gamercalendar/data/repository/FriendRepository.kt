package com.example.gamercalendar.data.repository

import com.example.gamercalendar.data.api.ApiClient
import com.example.gamercalendar.data.model.User

class FriendRepository {

    suspend fun getFriends(): List<User> {
        return ApiClient.api.getFriends()
    }
}
