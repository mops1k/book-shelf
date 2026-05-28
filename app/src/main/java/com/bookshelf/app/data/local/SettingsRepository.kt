package com.bookshelf.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bookshelf.app.data.model.OpdsCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    
    companion object {
        val STORAGE_PATH_KEY = stringPreferencesKey("storage_path")
        val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        val SORT_BY_KEY = stringPreferencesKey("sort_by")
        val SORT_ASCENDING_KEY = booleanPreferencesKey("sort_ascending")
        val OPDS_CATALOGS_KEY = stringPreferencesKey("opds_catalogs")
        val DEFAULT_STORAGE_PATH = "/Books"
    }
    
    private val json = Json { ignoreUnknownKeys = true }
    
    val storagePath: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[STORAGE_PATH_KEY] ?: DEFAULT_STORAGE_PATH
    }
    
    val themeMode: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[THEME_MODE_KEY] ?: "system"
    }
    
    val sortBy: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[SORT_BY_KEY] ?: "dateAdded"
    }
    
    val sortAscending: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SORT_ASCENDING_KEY] ?: false
    }
    
    val opdsCatalogs: Flow<List<OpdsCatalog>> = context.dataStore.data.map { preferences ->
        val jsonStr = preferences[OPDS_CATALOGS_KEY]
        if (jsonStr != null) {
            try {
                json.decodeFromString(jsonStr)
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            getDefaultCatalogs()
        }
    }
    
    private fun getDefaultCatalogs(): List<OpdsCatalog> = listOf(
        OpdsCatalog(
            name = "Flibusta",
            url = "http://flibusta.is/opds",
            isEnabled = true
        ),
        OpdsCatalog(
            name = "Librusec",
            url = "http://librusec.com/opds",
            isEnabled = false
        )
    )
    
    suspend fun setStoragePath(path: String) {
        context.dataStore.edit { preferences ->
            preferences[STORAGE_PATH_KEY] = path
        }
    }
    
    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = mode
        }
    }
    
    suspend fun setSortBy(sortBy: String) {
        context.dataStore.edit { preferences ->
            preferences[SORT_BY_KEY] = sortBy
        }
    }
    
    suspend fun setSortAscending(ascending: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SORT_ASCENDING_KEY] = ascending
        }
    }
    
    suspend fun saveOpdsCatalogs(catalogs: List<OpdsCatalog>) {
        context.dataStore.edit { preferences ->
            preferences[OPDS_CATALOGS_KEY] = json.encodeToString(catalogs)
        }
    }
    
    suspend fun addOpdsCatalog(catalog: OpdsCatalog) {
        val current = opdsCatalogs.first()
        saveOpdsCatalogs(current + catalog)
    }
    
    suspend fun removeOpdsCatalog(id: Long) {
        val current = opdsCatalogs.first()
        saveOpdsCatalogs(current.filter { it.id != id })
    }
}
