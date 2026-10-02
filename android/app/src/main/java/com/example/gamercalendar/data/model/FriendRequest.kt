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
    val receiver: User? = null
)

data class FriendshipResponse(
    val user_id: String,
    val friend_id: String,
    val created_at: String
)
