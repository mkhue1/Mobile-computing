package com.example.gamercalendar.data.repository

import com.example.gamercalendar.data.api.ApiClient
import com.example.gamercalendar.data.model.UserGroup

class GroupRepository {

    suspend fun getGroups(): List<UserGroup> {
        return ApiClient.api.getGroups()
    }
}
