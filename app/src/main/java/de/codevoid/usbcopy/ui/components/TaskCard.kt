package de.codevoid.usbcopy.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import de.codevoid.usbcopy.model.TaskState

@Composable
fun TaskCard(state: TaskState, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = state.task.deviceId,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = state.status.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor(state.status),
                )
            }

            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { state.progressFraction },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))

            Row {
                val speedMb = state.speedBytesPerSec / 1_048_576.0
                Text(
                    text = "%.1f MB/s".format(speedMb),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "%.0f%%".format(state.progressFraction * 100),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (state.currentFile.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = state.currentFile,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun statusColor(status: TaskState.Status) = when (status) {
    TaskState.Status.RUNNING -> MaterialTheme.colorScheme.primary
    TaskState.Status.DONE -> MaterialTheme.colorScheme.secondary
    TaskState.Status.ERROR -> MaterialTheme.colorScheme.error
    TaskState.Status.CANCELLED -> MaterialTheme.colorScheme.outline
    TaskState.Status.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
}
