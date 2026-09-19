package com.divium.ide

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.divium.core.model.LanguagePack
import com.divium.core.model.WorkspaceMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "divium_setup")

data class SetupState(
    val loaded: Boolean = false,
    val completed: Boolean = false,
    val languages: Set<LanguagePack> = emptySet(),
    val workspaceMode: WorkspaceMode = WorkspaceMode.DIVIUM,
    val externalWorkspaceUri: Uri? = null,
)

class SetupStore(private val context: Context) {
    private object Keys {
        val completed = booleanPreferencesKey("completed")
        val languages = stringSetPreferencesKey("languages")
        val workspaceMode = stringPreferencesKey("workspace_mode")
        val externalUri = stringPreferencesKey("external_uri")
    }

    val state: Flow<SetupState> = context.dataStore.data.map { prefs ->
        SetupState(
            loaded = true,
            completed = prefs[Keys.completed] ?: false,
            languages = prefs[Keys.languages]
                ?.mapNotNull(LanguagePack::fromId)
                ?.toSet()
                .orEmpty(),
            workspaceMode = prefs[Keys.workspaceMode]
                ?.let { runCatching { WorkspaceMode.valueOf(it) }.getOrNull() }
                ?: WorkspaceMode.DIVIUM,
            externalWorkspaceUri = prefs[Keys.externalUri]?.let(Uri::parse),
        )
    }

    suspend fun complete(
        languages: Set<LanguagePack>,
        workspaceMode: WorkspaceMode,
        externalUri: Uri?,
    ) {
        if (workspaceMode == WorkspaceMode.DIVIUM) {
            context.filesDir.resolve("workspaces").mkdirs()
        }
        context.dataStore.edit { prefs ->
            prefs[Keys.languages] = languages.mapTo(mutableSetOf()) { it.id }
            prefs[Keys.workspaceMode] = workspaceMode.name
            if (externalUri != null) prefs[Keys.externalUri] = externalUri.toString()
            else prefs.remove(Keys.externalUri)
            prefs[Keys.completed] = true
        }
    }

    suspend fun reset() {
        context.dataStore.edit { it.clear() }
    }
}
