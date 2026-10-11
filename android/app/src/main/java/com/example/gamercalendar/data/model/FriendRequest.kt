package com.example.gamercalendar.data.model

data class FriendRequestCreate(
    val receiver_id: String
)

data class FriendRequestResponse(
    val id: String,
    val sender_id: String,
    val receiver_id: String,
    val created_at: String,
    val sender: User? = null,
    val receiver: User? = null,
    val steam_relation: String? = null,
    val steam_persona_name: String? = null
)

data class FriendshipResponse(
    val user_id: String,
    val friend_id: String,
    val created_at: String
)
