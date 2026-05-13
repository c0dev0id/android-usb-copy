package de.codevoid.usbcopy.data

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.codevoid.usbcopy.model.ErrorStrategy
import de.codevoid.usbcopy.model.OverwriteStrategy
import de.codevoid.usbcopy.model.SourceFolder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "prefs")

@Serializable
private data class SourceFolderDto(val uri: String, val displayName: String, val deviceId: String)

class PreferencesRepository(private val context: Context) {

    companion object {
        private val KEY_SOURCES = stringPreferencesKey("sources")
        private val KEY_DEST = stringPreferencesKey("dest")
        private val KEY_OVERWRITE = stringPreferencesKey("overwrite")
        private val KEY_ERROR = stringPreferencesKey("error")
        private val KEY_SEQUENTIAL = booleanPreferencesKey("sequential")
    }

    val sourceFolders: Flow<List<SourceFolder>> = context.dataStore.data.map { prefs ->
        val json = prefs[KEY_SOURCES] ?: return@map emptyList()
        Json.decodeFromString<List<SourceFolderDto>>(json).map {
            SourceFolder(Uri.parse(it.uri), it.displayName, it.deviceId)
        }
    }

    val destinationUri: Flow<Uri?> = context.dataStore.data.map { prefs ->
        prefs[KEY_DEST]?.let { Uri.parse(it) }
    }

    val overwriteStrategy: Flow<OverwriteStrategy> = context.dataStore.data.map { prefs ->
        prefs[KEY_OVERWRITE].toEnumOrDefault(OverwriteStrategy.SKIP)
    }

    val errorStrategy: Flow<ErrorStrategy> = context.dataStore.data.map { prefs ->
        prefs[KEY_ERROR].toEnumOrDefault(ErrorStrategy.SKIP_AND_CONTINUE)
    }

    val sequential: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SEQUENTIAL] == true
    }

    suspend fun saveSourceFolders(folders: List<SourceFolder>) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SOURCES] = Json.encodeToString(
                folders.map { SourceFolderDto(it.uri.toString(), it.displayName, it.deviceId) }
            )
        }
    }

    suspend fun saveDestination(uri: Uri?) {
        context.dataStore.edit { prefs ->
            if (uri != null) prefs[KEY_DEST] = uri.toString() else prefs.remove(KEY_DEST)
        }
    }

    suspend fun saveOverwriteStrategy(strategy: OverwriteStrategy) {
        context.dataStore.edit { it[KEY_OVERWRITE] = strategy.name }
    }

    suspend fun saveErrorStrategy(strategy: ErrorStrategy) {
        context.dataStore.edit { it[KEY_ERROR] = strategy.name }
    }

    suspend fun saveSequential(sequential: Boolean) {
        context.dataStore.edit { it[KEY_SEQUENTIAL] = sequential }
    }
}

private inline fun <reified E : Enum<E>> String?.toEnumOrDefault(default: E): E =
    this?.let { runCatching { enumValueOf<E>(it) }.getOrNull() } ?: default
