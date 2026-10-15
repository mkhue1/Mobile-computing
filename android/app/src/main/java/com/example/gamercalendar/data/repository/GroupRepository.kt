package com.example.gamercalendar.data.repository

import com.example.gamercalendar.data.api.ApiClient
import com.example.gamercalendar.data.model.GroupCreate
import com.example.gamercalendar.data.model.GroupDetailResponse
import com.example.gamercalendar.data.model.GroupMemberAdd
import com.example.gamercalendar.data.model.GroupMemberResponse
import com.example.gamercalendar.data.model.GroupUpdate
import com.example.gamercalendar.data.model.UserGroup

class GroupRepository {

    suspend fun getGroups(): List<UserGroup> {
        return ApiClient.api.getGroups()
    }

    suspend fun createGroup(name: String): GroupDetailResponse {
        return ApiClient.api.createGroup(GroupCreate(name = name))
    }

    suspend fun getGroup(groupId: String): GroupDetailResponse {
        return ApiClient.api.getGroup(groupId)
    }

    suspend fun updateGroup(groupId: String, name: String): UserGroup {
        return ApiClient.api.updateGroup(groupId, GroupUpdate(name = name))
    }

    suspend fun deleteGroup(groupId: String) {
        ApiClient.api.deleteGroup(groupId)
    }

    suspend fun addMember(groupId: String, userId: String): GroupMemberResponse {
        return ApiClient.api.addGroupMember(groupId, GroupMemberAdd(user_id = userId))
    }

    suspend fun removeMember(groupId: String, memberId: String) {
        ApiClient.api.removeGroupMember(groupId, memberId)
    }
}
