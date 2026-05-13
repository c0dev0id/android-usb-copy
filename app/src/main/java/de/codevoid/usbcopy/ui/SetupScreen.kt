package de.codevoid.usbcopy.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
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
import de.codevoid.usbcopy.model.ErrorStrategy
import de.codevoid.usbcopy.model.OverwriteStrategy
import de.codevoid.usbcopy.model.SourceFolder
import de.codevoid.usbcopy.viewmodel.SetupViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(vm: SetupViewModel, onStartTransfer: () -> Unit) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    val sourcePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? -> if (uri != null) vm.addSource(uri) }

    val destPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? -> if (uri != null) vm.setDestination(uri) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("USB Copy") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Sources
            Text("Source Folders", style = MaterialTheme.typography.titleMedium)
            state.sources.forEach { src ->
                SourceRow(src, onRemove = { vm.removeSource(src.uri) })
            }
            OutlinedButton(onClick = { sourcePicker.launch(null) }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("Add Source", modifier = Modifier.padding(start = 4.dp))
            }

            HorizontalDivider()

            // Destination
            Text("Destination Folder", style = MaterialTheme.typography.titleMedium)
            val destLabel = state.destination?.lastPathSegment ?: "Not selected"
            OutlinedButton(onClick = { destPicker.launch(null) }, modifier = Modifier.fillMaxWidth()) {
                Text(destLabel)
            }

            HorizontalDivider()

            // Options
            Text("Options", style = MaterialTheme.typography.titleMedium)

            EnumDropdown(
                title = "Overwrite Strategy",
                selected = state.overwriteStrategy,
                values = OverwriteStrategy.entries,
                displayName = { it.name.replace('_', ' ').lowercase().replaceFirstChar { c -> c.uppercase() } },
                onSelect = vm::setOverwriteStrategy,
            )

            EnumDropdown(
                title = "On Error",
                selected = state.errorStrategy,
                values = ErrorStrategy.entries,
                displayName = { it.name.replace('_', ' ').lowercase().replaceFirstChar { c -> c.uppercase() } },
                onSelect = vm::setErrorStrategy,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Transfer Sequentially", modifier = Modifier.weight(1f))
                Switch(checked = state.sequential, onCheckedChange = vm::setSequential)
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    onStartTransfer()
                },
                enabled = state.canStart,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Start Transfer")
            }
        }
    }
}

@Composable
private fun SourceRow(src: SourceFolder, onRemove: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(src.displayName, style = MaterialTheme.typography.bodyMedium)
            Text(src.deviceId, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Close, contentDescription = "Remove")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> EnumDropdown(
    title: String,
    selected: T,
    values: List<T>,
    displayName: (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        TextField(
            value = displayName(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(title) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            values.forEach { value ->
                DropdownMenuItem(
                    text = { Text(displayName(value)) },
                    onClick = {
                        onSelect(value)
                        expanded = false
                    },
                )
            }
        }
    }
}
