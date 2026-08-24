package com.trustarx.pitgo.core.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appearanceDataStore by preferencesDataStore(name = "appearance")

/**
 * Manual theme override (FR-008): SYSTEM is the default until the user picks Light/Dark
 * explicitly, matching the product constitution ("respeitar o sistema por padrão e persistir
 * seleção manual").
 */
enum class AppearanceMode { LIGHT, DARK, SYSTEM }

class AppearancePreferences(context: Context) {
    private val store = context.applicationContext.appearanceDataStore

    val mode: Flow<AppearanceMode> = store.data.map { preferences ->
        preferences[ModeKey]
            ?.let { raw -> runCatching { AppearanceMode.valueOf(raw) }.getOrNull() }
            ?: AppearanceMode.SYSTEM
    }

    suspend fun setMode(mode: AppearanceMode) {
        store.edit { it[ModeKey] = mode.name }
    }

    private companion object {
        val ModeKey = stringPreferencesKey("mode")
    }
}
