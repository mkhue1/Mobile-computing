package com.example.gamercalendar.data.model

data class SteamStatus(
    val linked: Boolean,
    val steam_id: String? = null
)

data class SteamLinkStart(
    val auth_url: String
)
