package com.app.whisper.feature.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.app.whisper.data.model.WhisperModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore("settings_prefs")

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val SELECTED_MODEL_KEY = stringPreferencesKey("selected_model")
        private val DARK_THEME_KEY = booleanPreferencesKey("dark_theme")
        private val LANGUAGE_KEY = stringPreferencesKey("language")
        private val TRANSLATE_KEY = booleanPreferencesKey("translate")
        private val THREADS_KEY = intPreferencesKey("threads")
        private val WIFI_ONLY_DOWNLOADS_KEY = booleanPreferencesKey("wifi_only_downloads")
    }

    val selectedModelFlow: Flow<WhisperModel> = context.dataStore.data
        .map { prefs ->
            prefs[SELECTED_MODEL_KEY]?.let { WhisperModel.valueOf(it) } ?: WhisperModel.TINY
        }

    suspend fun getSelectedModel(): WhisperModel = selectedModelFlow.first()

    suspend fun setSelectedModel(model: WhisperModel) {
        context.dataStore.edit { prefs ->
            prefs[SELECTED_MODEL_KEY] = model.name
        }
    }

    val darkThemeFlow: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[DARK_THEME_KEY] ?: false }

    suspend fun getDarkTheme(): Boolean = darkThemeFlow.first()

    suspend fun setDarkTheme(isDark: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[DARK_THEME_KEY] = isDark
        }
    }

    val languageFlow: Flow<String> = context.dataStore.data
        .map { prefs -> prefs[LANGUAGE_KEY] ?: "tr" }

    suspend fun getLanguage(): String = languageFlow.first()

    suspend fun setLanguage(language: String) {
        context.dataStore.edit { prefs ->
            prefs[LANGUAGE_KEY] = language
        }
    }

    val translateFlow: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[TRANSLATE_KEY] ?: false }

    suspend fun getTranslate(): Boolean = translateFlow.first()

    suspend fun setTranslate(translate: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[TRANSLATE_KEY] = translate
        }
    }

    val threadsFlow: Flow<Int> = context.dataStore.data
        .map { prefs -> prefs[THREADS_KEY] ?: 4 }

    suspend fun getThreads(): Int = threadsFlow.first()

    suspend fun setThreads(threads: Int) {
        context.dataStore.edit { prefs ->
            prefs[THREADS_KEY] = threads
        }
    }

    val wifiOnlyDownloadsFlow: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[WIFI_ONLY_DOWNLOADS_KEY] ?: false }

    suspend fun getWifiOnlyDownloads(): Boolean = wifiOnlyDownloadsFlow.first()

    suspend fun setWifiOnlyDownloads(wifiOnly: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[WIFI_ONLY_DOWNLOADS_KEY] = wifiOnly
        }
    }
}
