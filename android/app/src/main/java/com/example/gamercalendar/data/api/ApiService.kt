package com.example.gamercalendar.data.api

import com.example.gamercalendar.data.model.User
import com.example.gamercalendar.data.model.UserCreate
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface ApiService {

    @GET("users/")
    suspend fun getUsers(): List<User>

    @POST("users/")
    suspend fun createUser(
        @Body user: UserCreate
    ): User
}