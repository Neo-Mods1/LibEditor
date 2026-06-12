package com.neomods.libeditor.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.neomods.libeditor.domain.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsManager(private val context: Context) {

    private val dataStore = context.dataStore
    private val localePrefs: SharedPreferences =
        context.getSharedPreferences("locale_prefs", Context.MODE_PRIVATE)

    companion object {
        private val THEME_MODE = stringPreferencesKey("theme_mode")
        private val LANGUAGE = stringPreferencesKey("language")
        private val EDIT_LOCATION = stringPreferencesKey("edit_location")
        private val DYNAMIC_COLORS = booleanPreferencesKey("dynamic_colors")
    }

    private val scope = CoroutineScope(Dispatchers.Main)

    val language: StateFlow<String> = dataStore.data.map { preferences ->
        preferences[LANGUAGE] ?: "en"
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = localePrefs.getString("lang", "en") ?: "en"
    )

    val themeMode: StateFlow<ThemeMode> = dataStore.data.map { preferences ->
        ThemeMode.fromValue(preferences[THEME_MODE] ?: "system")
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ThemeMode.SYSTEM
    )

    val editLocation: StateFlow<String> = dataStore.data.map { preferences ->
        preferences[EDIT_LOCATION] ?: "/storage/emulated/0/Editor"
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "/storage/emulated/0/Editor"
    )

    val dynamicColors: StateFlow<Boolean> = dataStore.data.map { preferences ->
        preferences[DYNAMIC_COLORS] ?: true
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { preferences ->
            preferences[THEME_MODE] = mode.value
        }
    }

    suspend fun setLanguage(lang: String) {
        dataStore.edit { preferences ->
            preferences[LANGUAGE] = lang
        }
        localePrefs.edit().putString("lang", lang).apply()
    }

    suspend fun setEditLocation(location: String) {
        dataStore.edit { preferences ->
            preferences[EDIT_LOCATION] = location
        }
    }

    suspend fun setDynamicColors(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[DYNAMIC_COLORS] = enabled
        }
    }

    fun getLocaleCodeSync(): String {
        return localePrefs.getString("lang", "") ?: ""
    }
}
