package de.codevoid.usbcopy.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.codevoid.usbcopy.data.PreferencesRepository
import de.codevoid.usbcopy.data.extractDeviceId
import de.codevoid.usbcopy.model.ErrorStrategy
import de.codevoid.usbcopy.model.OverwriteStrategy
import de.codevoid.usbcopy.model.SourceFolder
import de.codevoid.usbcopy.service.TransferService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SetupUiState(
    val sources: List<SourceFolder> = emptyList(),
    val destination: Uri? = null,
    val overwriteStrategy: OverwriteStrategy = OverwriteStrategy.SKIP,
    val errorStrategy: ErrorStrategy = ErrorStrategy.SKIP_AND_CONTINUE,
    val sequential: Boolean = false,
) {
    val canStart: Boolean get() = sources.isNotEmpty() && destination != null
}

class SetupViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = PreferencesRepository(app)

    val uiState: StateFlow<SetupUiState> = combine(
        prefs.sourceFolders,
        prefs.destinationUri,
        prefs.overwriteStrategy,
        prefs.errorStrategy,
        prefs.sequential,
    ) { sources, dest, overwrite, error, seq ->
        SetupUiState(sources, dest, overwrite, error, seq)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SetupUiState())

    fun addSource(uri: Uri) {
        viewModelScope.launch {
            getApplication<Application>().contentResolver
                .takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)

            val doc = DocumentFile.fromTreeUri(getApplication(), uri)
            val name = doc?.name ?: uri.lastPathSegment ?: uri.toString()
            val deviceId = uri.extractDeviceId(name)

            val current = uiState.value.sources.toMutableList()
            if (current.none { it.uri == uri }) {
                current += SourceFolder(uri, name, deviceId)
                prefs.saveSourceFolders(current)
            }
        }
    }

    fun removeSource(uri: Uri) {
        viewModelScope.launch {
            val updated = uiState.value.sources.filter { it.uri != uri }
            prefs.saveSourceFolders(updated)
        }
    }

    fun setDestination(uri: Uri) {
        viewModelScope.launch {
            getApplication<Application>().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            prefs.saveDestination(uri)
        }
    }

    fun setOverwriteStrategy(s: OverwriteStrategy) { viewModelScope.launch { prefs.saveOverwriteStrategy(s) } }
    fun setErrorStrategy(s: ErrorStrategy) { viewModelScope.launch { prefs.saveErrorStrategy(s) } }
    fun setSequential(v: Boolean) { viewModelScope.launch { prefs.saveSequential(v) } }

    fun buildStartIntent(): Intent {
        val state = uiState.value
        return Intent(getApplication(), TransferService::class.java).apply {
            putExtra(TransferService.EXTRA_SOURCES, state.sources.map { it.uri.toString() }.toTypedArray())
            putExtra(TransferService.EXTRA_DEST, state.destination.toString())
            putExtra(TransferService.EXTRA_OVERWRITE, state.overwriteStrategy.name)
            putExtra(TransferService.EXTRA_ERROR, state.errorStrategy.name)
            putExtra(TransferService.EXTRA_SEQUENTIAL, state.sequential)
        }
    }
}
