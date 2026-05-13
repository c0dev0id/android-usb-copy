package de.codevoid.usbcopy.ui.components

import android.text.format.Formatter
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import de.codevoid.usbcopy.model.FileEvent

@Composable
fun FileLogList(events: List<FileEvent>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val context = LocalContext.current

    val isPinnedToBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
            last.index >= info.totalItemsCount - 2
        }
    }

    LaunchedEffect(events.size) {
        if (events.isNotEmpty() && isPinnedToBottom) {
            listState.animateScrollToItem(events.lastIndex)
        }
    }

    LazyColumn(state = listState, modifier = modifier) {
        items(events) { event ->
            Text(
                text = formatEvent(event, context),
                color = eventColor(event),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 1.dp),
            )
        }
    }
}

private fun formatEvent(event: FileEvent, context: android.content.Context): String = when (event) {
    is FileEvent.Copied -> "✓ ${event.name}  (${Formatter.formatShortFileSize(context, event.sizeBytes)})"
    is FileEvent.Skipped -> "→ ${event.name}  skipped"
    is FileEvent.Error -> "✗ ${event.name}  ${event.message}"
}

private fun eventColor(event: FileEvent): Color = when (event) {
    is FileEvent.Copied, is FileEvent.Skipped -> Color(0xFF2E7D32)
    is FileEvent.Error -> Color(0xFFC62828)
}
