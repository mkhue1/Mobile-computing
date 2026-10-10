package com.example.gamercalendar.ui.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.gamercalendar.data.model.ExternalCalendar

/** Dialog for letting users choose which of the exeternal calendars appear */
@Composable
internal fun CalendarPickerDialog(
    calendars: List<ExternalCalendar>,
    excludedCalendarIds: Set<Long>,
    onConfirm: (excludedCalendarIds: Set<Long>) -> Unit,
    onDismiss: () -> Unit
) {
    var excluded by remember { mutableStateOf(excludedCalendarIds) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Calendars to show") },
        text = {
            if (calendars.isEmpty()) {
                Text(
                    text = "No calendars found. Calendar permissions may be turned off.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(
                    modifier = Modifier
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Group calendars by account
                    calendars.groupBy { it.accountName }.forEach { (account, accountCalendars) ->
                        if (account.isNotBlank()) {
                            Text(
                                text = account,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        accountCalendars.forEach { calendar ->
                            CalendarRow(
                                calendar = calendar,
                                checked = calendar.id !in excluded,
                                onCheckedChange = { shown ->
                                    excluded = if (shown) excluded - calendar.id else excluded + calendar.id
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(excluded) }) {
                Text("Done")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun CalendarRow(
    calendar: ExternalCalendar,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                // apparently colors can sometimes be stored without an alpha channel
                .background(Color(calendar.color).copy(alpha = 1f))
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = calendar.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
