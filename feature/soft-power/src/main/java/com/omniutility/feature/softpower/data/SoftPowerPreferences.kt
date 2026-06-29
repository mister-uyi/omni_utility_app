package com.omniutility.feature.softpower.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class SoftPowerPreferences(
    val buttonOpacity: Float = 0.6f,
    val buttonSize: Int = 56,
    val isServiceEnabled: Boolean = false
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "soft_power_settings")

class SoftPowerSettingsRepository(private val context: Context) {
    private object Keys {
        val OPACITY = floatPreferencesKey("button_opacity")
        val SIZE = intPreferencesKey("button_size")
        val ENABLED = booleanPreferencesKey("service_enabled")
    }

    val preferencesFlow: Flow<SoftPowerPreferences> = context.dataStore.data
        .map { preferences ->
            SoftPowerPreferences(
                buttonOpacity = preferences[Keys.OPACITY] ?: 0.6f,
                buttonSize = preferences[Keys.SIZE] ?: 56,
                isServiceEnabled = preferences[Keys.ENABLED] ?: false
            )
        }

    suspend fun updateOpacity(opacity: Float) {
        context.dataStore.edit { preferences ->
            preferences[Keys.OPACITY] = opacity
        }
    }

    suspend fun updateSize(size: Int) {
        context.dataStore.edit { preferences ->
            preferences[Keys.SIZE] = size
        }
    }

    suspend fun updateServiceEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[Keys.ENABLED] = enabled
        }
    }
}
