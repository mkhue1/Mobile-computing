package com.example.gamercalendar.data.model

import com.google.gson.annotations.SerializedName

enum class SessionType(val label: String) {
    @SerializedName("online")
    ONLINE("Online"),

    @SerializedName("in_person")
    IN_PERSON("In person")
}

enum class SessionVisibility(val label: String) {
    @SerializedName("private")
    PRIVATE("Private"),

    @SerializedName("friends")
    FRIENDS("Friends"),

    @SerializedName("group")
    GROUP("Group"),

    @SerializedName("public")
    PUBLIC("Public")
}

enum class SessionStatus {
    @SerializedName("open")
    OPEN,

    @SerializedName("cancelled")
    CANCELLED,

    @SerializedName("completed")
    COMPLETED
}

enum class InviteStatus {
    @SerializedName("pending")
    PENDING,

    @SerializedName("accepted")
    ACCEPTED,

    @SerializedName("declined")
    DECLINED,

    @SerializedName("cancelled")
    CANCELLED
}

data class SessionCreate(
    val game_id: String,
    val group_id: String? = null,
    val title: String?,
    val description: String?,
    val start_at: String,
    val end_at: String,
    val session_type: SessionType,
    val visibility: SessionVisibility,
    val location_name: String?,
    val player_limit: Int?
)

data class GamingSession(
    val id: String,
    val organiser_id: String,
    val game_id: String,
    val group_id: String?,
    val title: String?,
    val description: String?,
    val start_at: String,
    val end_at: String,
    val session_type: SessionType,
    val visibility: SessionVisibility,
    val status: SessionStatus,
    val location_name: String?,
    val player_limit: Int?,
    val player_count: Int,
    val created_at: String,
    val updated_at: String
)

data class SessionParticipant(
    val user: User,
    val joined_at: String
)

data class InviteCreate(
    val session_id: String,
    val receiver_id: String
)

data class SessionInvite(
    val id: String,
    val session_id: String,
    val sender_id: String,
    val receiver_id: String,
    val status: InviteStatus,
    val created_at: String,
    val responded_at: String?
)

data class InviteAction(
    val invite_id: String
)

data class InviteActionResponse(
    val status: Boolean,
    val message: String,
    val id: String
)

data class SessionCancelResponse(
    val status: Boolean,
    val message: String,
    val id: String
)
