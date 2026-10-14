package com.example.gamercalendar.data.model

data class SteamStatus(
    val linked: Boolean,
    val steam_id: String? = null,
    val persona_name: String? = null
)

data class SteamLinkStart(
    val auth_url: String
)
