package com.example.gamercalendar.data.api

import com.example.gamercalendar.data.model.Game
import com.example.gamercalendar.data.model.GamingSession
import com.example.gamercalendar.data.model.InviteCreate
import com.example.gamercalendar.data.model.LoginRequest
import com.example.gamercalendar.data.model.SessionCreate
import com.example.gamercalendar.data.model.SessionInvite
import com.example.gamercalendar.data.model.SessionParticipant
import com.example.gamercalendar.data.model.TokenResponse
import com.example.gamercalendar.data.model.User
import com.example.gamercalendar.data.model.UserCreate
import com.example.gamercalendar.data.model.UserGroup
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface ApiService {

    @GET("users/")
    suspend fun getUsers(): List<User>

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

    @GET("games/")
    suspend fun getGames(): List<Game>

    @GET("groups/")
    suspend fun getGroups(): List<UserGroup>

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

    @PUT("sessions/{sessionId}")
    suspend fun updateSession(
        @Path("sessionId") sessionId: String,
        @Body session: SessionCreate
    ): GamingSession

    @POST("sessions/{sessionId}/cancel")
    suspend fun cancelSession(
        @Path("sessionId") sessionId: String
    ): GamingSession

    @POST("sessions/{sessionId}/leave")
    suspend fun leaveSession(
        @Path("sessionId") sessionId: String
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
}
