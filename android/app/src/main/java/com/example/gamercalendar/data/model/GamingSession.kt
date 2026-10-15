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
    val location_address: String? = null,
    val location_place_id: String? = null,
    val location_lat: Double? = null,
    val location_lng: Double? = null,
    val player_limit: Int?
)

/** A Google Maps place chosen as an in-person session's location. */
data class SessionPlace(
    val name: String,
    val address: String?,
    val placeId: String?,
    val lat: Double,
    val lng: Double
)

data class GamingSession(
    val id: String,
    val organiser_id: String,
    val game_id: String,
    val game: Game,
    val group_id: String?,
    val title: String?,
    val description: String?,
    val start_at: String,
    val end_at: String,
    val session_type: SessionType,
    val visibility: SessionVisibility,
    val status: SessionStatus,
    val location_name: String?,
    val location_address: String?,
    val location_place_id: String?,
    val location_lat: Double?,
    val location_lng: Double?,
    val player_limit: Int?,
    val player_count: Int,
    val created_at: String,
    val updated_at: String
)

/** Null when the session has no location or only a typed name without coordinates. */
val GamingSession.place: SessionPlace?
    get() {
        val name = location_name ?: return null
        val lat = location_lat ?: return null
        val lng = location_lng ?: return null
        return SessionPlace(name, location_address, location_place_id, lat, lng)
    }

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

data class SentSessionInvite(
    val id: String,
    val session_id: String,
    val receiver: User,
    val status: InviteStatus,
    val created_at: String
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
