package com.example.gamercalendar.data.model

data class UserGroup(
    val id: String,
    val name: String,
    val owner_id: String,
    val created_at: String
)

data class GroupCreate(
    val name: String
)

data class GroupUpdate(
    val name: String
)

data class GroupMemberAdd(
    val user_id: String
)

data class GroupMemberResponse(
    val group_id: String,
    val user_id: String,
    val role: String,
    val joined_at: String,
    val user: User?
)

data class GroupDetailResponse(
    val id: String,
    val name: String,
    val owner_id: String,
    val created_at: String,
    val members: List<GroupMemberResponse>
)
