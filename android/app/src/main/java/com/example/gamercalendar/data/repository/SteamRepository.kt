package com.example.gamercalendar.data.repository

import com.example.gamercalendar.data.api.ApiClient
import com.example.gamercalendar.data.model.SteamLinkStart
import com.example.gamercalendar.data.model.SteamStatus

class SteamRepository {

    suspend fun getStatus(): SteamStatus {
        return ApiClient.api.getSteamStatus()
    }

    suspend fun startLink(): SteamLinkStart {
        return ApiClient.api.startSteamLink()
    }

    suspend fun unlink() {
        ApiClient.api.unlinkSteam()
    }
}
