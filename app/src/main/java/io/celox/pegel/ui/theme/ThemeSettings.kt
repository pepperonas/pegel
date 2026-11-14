package io.celox.pegel.ui.theme

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.themeDataStore by preferencesDataStore(name = "theme_settings")

/**
 * Persisted theme preferences. Default is the Pegel brand scheme; Material You dynamic color is a
 * deliberate opt-in (API 31+).
 */
object ThemeSettings {
    private val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    private val THEME_MODE = stringPreferencesKey("theme_mode")

    fun mode(context: Context): Flow<ThemeMode> =
        context.themeDataStore.data
            .catch { emit(emptyPreferences()) }
            .map { ThemeMode.fromName(it[THEME_MODE]) }

    suspend fun setMode(context: Context, mode: ThemeMode) {
        context.themeDataStore.edit { it[THEME_MODE] = mode.name }
    }

    fun dynamicColor(context: Context): Flow<Boolean> =
        context.themeDataStore.data
            .catch { emit(emptyPreferences()) }
            .map { it[DYNAMIC_COLOR] ?: false }

    suspend fun setDynamicColor(context: Context, enabled: Boolean) {
        context.themeDataStore.edit { it[DYNAMIC_COLOR] = enabled }
    }
}
