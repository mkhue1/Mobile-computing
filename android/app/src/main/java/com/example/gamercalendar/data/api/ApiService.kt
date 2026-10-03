package com.example.gamercalendar.data.api

import com.example.gamercalendar.data.model.FriendRequestCreate
import com.example.gamercalendar.data.model.FriendRequestResponse
import com.example.gamercalendar.data.model.FriendshipResponse
import com.example.gamercalendar.data.model.Game
import com.example.gamercalendar.data.model.GamingSession
import com.example.gamercalendar.data.model.GroupCreate
import com.example.gamercalendar.data.model.GroupDetailResponse
import com.example.gamercalendar.data.model.GroupMemberAdd
import com.example.gamercalendar.data.model.GroupMemberResponse
import com.example.gamercalendar.data.model.GroupUpdate
import com.example.gamercalendar.data.model.InviteAction
import com.example.gamercalendar.data.model.InviteActionResponse
import com.example.gamercalendar.data.model.InviteCreate
import com.example.gamercalendar.data.model.LoginRequest
import com.example.gamercalendar.data.model.SentSessionInvite
import com.example.gamercalendar.data.model.SessionCancelResponse
import com.example.gamercalendar.data.model.SessionCreate
import com.example.gamercalendar.data.model.SessionInvite
import com.example.gamercalendar.data.model.SessionParticipant
import com.example.gamercalendar.data.model.TokenResponse
import com.example.gamercalendar.data.model.User
import com.example.gamercalendar.data.model.UserCreate
import com.example.gamercalendar.data.model.UserGroup
import com.example.gamercalendar.data.model.GameSearchResult
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    @GET("users/")
    suspend fun getUsers(
        @Query("search") search: String? = null,
        @Query("limit") limit: Int? = null
    ): List<User>

    @POST("users/")
    suspend fun createUser(
        @Body user: UserCreate
    ): User

    @POST("auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): TokenResponse

    @GET("auth/me")
    suspend fun me(): User

    @GET("friends/")
    suspend fun getFriends(): List<User>

    @POST("friends/requests")
    suspend fun sendFriendRequest(
        @Body request: FriendRequestCreate
    ): FriendRequestResponse

    @GET("friends/requests")
    suspend fun getFriendRequests(
        @Query("direction") direction: String
    ): List<FriendRequestResponse>

    @POST("friends/requests/{requestId}/accept")
    suspend fun acceptFriendRequest(
        @Path("requestId") requestId: String
    ): FriendshipResponse

    @POST("friends/requests/{requestId}/decline")
    suspend fun declineFriendRequest(
        @Path("requestId") requestId: String
    )

    @DELETE("friends/{friendId}")
    suspend fun removeFriend(
        @Path("friendId") friendId: String
    )

    @GET("games/igdb/{igdbId}")
    suspend fun getGameByIgdbId(
        @Path("igdbId") igdbId: Long
    ): Game

    @GET("games/search")
    suspend fun searchGames(
        @Query("q") query: String
    ): List<GameSearchResult>

    @GET("groups/")
    suspend fun getGroups(): List<UserGroup>

    @POST("groups/")
    suspend fun createGroup(
        @Body group: GroupCreate
    ): GroupDetailResponse

    @GET("groups/{groupId}")
    suspend fun getGroup(
        @Path("groupId") groupId: String
    ): GroupDetailResponse

    @PATCH("groups/{groupId}")
    suspend fun updateGroup(
        @Path("groupId") groupId: String,
        @Body group: GroupUpdate
    ): UserGroup

    @DELETE("groups/{groupId}")
    suspend fun deleteGroup(
        @Path("groupId") groupId: String
    )

    @POST("groups/{groupId}/members")
    suspend fun addGroupMember(
        @Path("groupId") groupId: String,
        @Body member: GroupMemberAdd
    ): GroupMemberResponse

    @DELETE("groups/{groupId}/members/{memberId}")
    suspend fun removeGroupMember(
        @Path("groupId") groupId: String,
        @Path("memberId") memberId: String
    )

    @GET("sessions/")
    suspend fun getSessions(): List<GamingSession>

    @GET("sessions/{sessionId}")
    suspend fun getSession(
        @Path("sessionId") sessionId: String
    ): GamingSession

    @POST("sessions/create")
    suspend fun createSession(
        @Body session: SessionCreate
    ): GamingSession

    @PATCH("sessions/{sessionId}")
    suspend fun updateSession(
        @Path("sessionId") sessionId: String,
        @Body session: SessionCreate
    ): GamingSession

    @POST("sessions/{sessionId}/cancel")
    suspend fun cancelSession(
        @Path("sessionId") sessionId: String
    ): SessionCancelResponse

    @POST("sessions/{sessionId}/leave")
    suspend fun leaveSession(
        @Path("sessionId") sessionId: String
    )

    @DELETE("sessions/{sessionId}/participants/{userId}")
    suspend fun removeSessionParticipant(
        @Path("sessionId") sessionId: String,
        @Path("userId") userId: String
    )

    @GET("sessions/{sessionId}/participants")
    suspend fun getSessionParticipants(
        @Path("sessionId") sessionId: String
    ): List<SessionParticipant>

    @POST("sessions/{sessionId}/invite")
    suspend fun inviteToSession(
        @Path("sessionId") sessionId: String,
        @Body invite: InviteCreate
    ): SessionInvite

    @GET("sessions/invites")
    suspend fun getSessionInvites(): List<SessionInvite>

    @GET("sessions/{sessionId}/invites")
    suspend fun getSentSessionInvites(
        @Path("sessionId") sessionId: String
    ): List<SentSessionInvite>

    @POST("sessions/{sessionId}/accept")
    suspend fun acceptSessionInvite(
        @Path("sessionId") sessionId: String,
        @Body invite: InviteAction
    ): InviteActionResponse

    @POST("sessions/{sessionId}/decline")
    suspend fun declineSessionInvite(
        @Path("sessionId") sessionId: String,
        @Body invite: InviteAction
    ): InviteActionResponse
}
