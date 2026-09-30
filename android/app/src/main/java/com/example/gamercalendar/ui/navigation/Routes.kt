package com.example.gamercalendar.ui.navigation

object Routes {
    const val ITEM_1 = "item1"
    const val ITEM_2 = "item2"
    const val USERS = "users"
    const val ITEM_4 = "item4"
    const val ITEM_5 = "item5"
    const val CREATE_SESSION = "sessions/create"
    const val FRIENDS_HUB = "friends/hub"
    const val ADD_FRIEND = "friends/add"

    const val ARG_SESSION_ID = "sessionId"
    const val MANAGE_SESSION = "session/{$ARG_SESSION_ID}"
    const val EDIT_SESSION = "session/{$ARG_SESSION_ID}/edit"

    const val ARG_GROUP_ID = "groupId"
    const val GROUP_DETAIL = "groups/{$ARG_GROUP_ID}"

    fun manageSession(sessionId: String) = "session/$sessionId"
    fun editSession(sessionId: String) = "session/$sessionId/edit"
    fun groupDetail(groupId: String) = "groups/$groupId"
}
