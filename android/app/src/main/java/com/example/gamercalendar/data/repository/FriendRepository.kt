package com.example.gamercalendar.data.repository

import com.example.gamercalendar.data.api.ApiClient
import com.example.gamercalendar.data.model.FriendRequestCreate
import com.example.gamercalendar.data.model.FriendRequestResponse
import com.example.gamercalendar.data.model.FriendshipResponse
import com.example.gamercalendar.data.model.User

class FriendRepository {

    suspend fun getFriends(): List<User> {
        return ApiClient.api.getFriends()
    }

    suspend fun sendFriendRequest(receiverId: String): FriendRequestResponse {
        return ApiClient.api.sendFriendRequest(FriendRequestCreate(receiver_id = receiverId))
    }

    suspend fun getFriendRequests(direction: String): List<FriendRequestResponse> {
        return ApiClient.api.getFriendRequests(direction)
    }

    suspend fun acceptFriendRequest(requestId: String): FriendshipResponse {
        return ApiClient.api.acceptFriendRequest(requestId)
    }

    suspend fun declineFriendRequest(requestId: String) {
        ApiClient.api.declineFriendRequest(requestId)
    }

    suspend fun removeFriend(friendId: String) {
        ApiClient.api.removeFriend(friendId)
    }
}
