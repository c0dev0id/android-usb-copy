package de.codevoid.usbcopy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.codevoid.usbcopy.model.TaskState
import de.codevoid.usbcopy.ui.components.TaskCard
import de.codevoid.usbcopy.viewmodel.TransferViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferScreen(
    vm: TransferViewModel,
    onTaskClick: (String) -> Unit,
    onBack: () -> Unit,
) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    var showCancelDialog by remember { mutableStateOf(false) }

    val allDone = tasks.isNotEmpty() && tasks.all {
        it.status in setOf(TaskState.Status.DONE, TaskState.Status.ERROR, TaskState.Status.CANCELLED)
    }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text("Cancel Transfer?") },
            text = { Text("All running transfers will be stopped.") },
            confirmButton = {
                Button(
                    onClick = { vm.cancel(); showCancelDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Cancel Transfer") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showCancelDialog = false }) { Text("Keep Running") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (allDone) "Transfer Complete" else "Transferring…") },
                navigationIcon = {
                    if (allDone) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (!allDone) {
                        OutlinedButton(
                            onClick = { showCancelDialog = true },
                            modifier = Modifier.padding(end = 8.dp),
                        ) { Text("Cancel") }
                    }
                },
            )
        },
    ) { padding ->
        if (tasks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("Starting transfer…")
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
            ) {
                items(tasks, key = { it.task.id }) { state ->
                    TaskCard(state = state, onClick = { onTaskClick(state.task.id) })
                }
            }
        }
    }
}
