package com.example.gamercalendar.data.model

data class User(
    val id: Int,
    val name: String,
    val email: String
)

data class UserCreate(
    val name: String,
    val email: String
)