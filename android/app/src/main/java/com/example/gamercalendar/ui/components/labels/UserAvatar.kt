package com.example.gamercalendar.ui.components.labels

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.gamercalendar.BuildConfig
import com.example.gamercalendar.data.api.AvatarImageLoader
import com.example.gamercalendar.data.model.User

/**
 * Circular avatar for a user: their profile photo if they have one, otherwise the
 * first letter of their username. The initials also show while the photo loads or if it fails.
 */
@Composable
fun UserAvatar(
    user: User?,
    size: Dp = 36.dp,
    modifier: Modifier = Modifier
) {
    // Fixed ratio of the circle, converted without font scaling so large system fonts can't overflow it.
    val fontSize = with(LocalDensity.current) { (size * 0.4f).toSp() }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = user?.username?.take(1)?.uppercase() ?: "?",
            style = MaterialTheme.typography.titleSmall,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        val updatedAt = user?.avatar_updated_at
        if (updatedAt != null) {
            // ?v= changes whenever the photo does, so the long-lived cache never shows a stale one.
            AsyncImage(
                model = "${BuildConfig.API_BASE_URL}users/${user.id}/avatar?v=${Uri.encode(updatedAt)}",
                contentDescription = null,
                imageLoader = AvatarImageLoader.get(LocalContext.current),
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        }
    }
}
