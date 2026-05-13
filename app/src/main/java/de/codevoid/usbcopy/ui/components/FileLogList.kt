package de.codevoid.usbcopy.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import de.codevoid.usbcopy.model.FileEvent

@Composable
fun FileLogList(events: List<FileEvent>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()

    LaunchedEffect(events.size) {
        if (events.isNotEmpty()) listState.animateScrollToItem(events.lastIndex)
    }

    LazyColumn(state = listState, modifier = modifier) {
        items(events, key = { it.hashCode() }) { event ->
            Text(
                text = formatEvent(event),
                color = eventColor(event),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 1.dp),
            )
        }
    }
}

private fun formatEvent(event: FileEvent): String = when (event) {
    is FileEvent.Copied -> "✓ ${event.name}  (${event.sizeBytes.toHuman()})"
    is FileEvent.Skipped -> "→ ${event.name}  skipped"
    is FileEvent.InProgress -> {
        val pct = if (event.totalBytes > 0) event.bytesWritten * 100 / event.totalBytes else 0
        "… ${event.name}  $pct%  ${(event.speedBytesPerSec / 1_048_576.0).let { "%.1f MB/s".format(it) }}"
    }
    is FileEvent.Error -> "✗ ${event.name}  ${event.message}"
}

private fun eventColor(event: FileEvent): Color = when (event) {
    is FileEvent.Copied, is FileEvent.Skipped -> Color(0xFF2E7D32)
    is FileEvent.InProgress -> Color(0xFFE65100)
    is FileEvent.Error -> Color(0xFFC62828)
}

private fun Long.toHuman(): String = when {
    this >= 1_073_741_824 -> "%.1f GB".format(this / 1_073_741_824.0)
    this >= 1_048_576 -> "%.1f MB".format(this / 1_048_576.0)
    this >= 1_024 -> "%.0f KB".format(this / 1_024.0)
    else -> "${this} B"
}
