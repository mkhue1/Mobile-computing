package com.example.gamercalendar.ui.components.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.gamercalendar.data.model.GamingSession
import com.example.gamercalendar.data.model.SessionStatus
import com.example.gamercalendar.data.model.SessionType
import com.example.gamercalendar.ui.components.labels.Tag
import com.example.gamercalendar.util.SessionTime

/**
 * Summary card for a gaming session, shown in session lists.
 * Every card has the same rows, whether or not the session has a title,
 * so lists stay visually consistent.
 */
@Composable
fun SessionCard(
    session: GamingSession,
    gameName: String?,
    startEpochMillis: Long,
    endEpochMillis: Long,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current

    val headline = session.title ?: gameName ?: "Gaming session"
    val where = if (session.session_type == SessionType.IN_PERSON && session.location_name != null) {
        "In person · ${session.location_name}"
    } else {
        session.session_type.label
    }
    val details = listOfNotNull(gameName.takeIf { session.title != null }, where)
        .joinToString(" · ")
    val players = if (session.player_limit != null) {
        "${session.player_count}/${session.player_limit} players"
    } else {
        "${session.player_count} ${if (session.player_count == 1) "player" else "players"}"
    }

    AppCard(
        modifier = modifier,
        onClick = onClick,
        contentPadding = PaddingValues(12.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GameCoverPlaceholder()

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = SessionTime.formatRange(context, startEpochMillis, endEpochMillis),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = headline,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = details,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (session.status == SessionStatus.CANCELLED) {
                        Tag(text = "Cancelled", color = MaterialTheme.colorScheme.error)
                    }
                    Tag(text = session.visibility.label)
                    Tag(text = players)
                }
            }
        }
    }
}

/** Portrait slot sized like game box art, for when covers are available. */
@Composable
private fun GameCoverPlaceholder() {
    Box(
        modifier = Modifier
            .width(66.dp)
            .aspectRatio(3f / 4f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.SportsEsports,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        )
    }
}