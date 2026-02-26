package com.example.fmogeoapp.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.fmogeoapp.data.model.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// DataStore 扩展
val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * 设置 DataStore 持久化管理类
 */
class SettingsDataStore(private val context: Context) {
    private object PreferencesKeys {
        val HOST = stringPreferencesKey("host")
        val DISCOVERY_ENABLED = booleanPreferencesKey("discovery_enabled")
        val SYNC_INTERVAL_MINUTES = intPreferencesKey("sync_interval_minutes")
        val PRECISE_LOCATION_MODE = booleanPreferencesKey("precise_location_mode")
    }

    /**
     * 获取设置数据流
     */
    val settingsFlow: Flow<AppSettings> = context.settingsDataStore.data.map { preferences ->
        AppSettings(
            host = preferences[PreferencesKeys.HOST] ?: "",
            discoveryEnabled = preferences[PreferencesKeys.DISCOVERY_ENABLED] ?: false,
            syncIntervalMinutes = preferences[PreferencesKeys.SYNC_INTERVAL_MINUTES]
                ?: AppSettings.DEFAULT_SYNC_INTERVAL,
            preciseLocationMode = preferences[PreferencesKeys.PRECISE_LOCATION_MODE] ?: false
        )
    }

    /**
     * 保存设置
     */
    suspend fun saveSettings(settings: AppSettings) {
        context.settingsDataStore.edit { preferences ->
            preferences[PreferencesKeys.HOST] = settings.host
            preferences[PreferencesKeys.DISCOVERY_ENABLED] = settings.discoveryEnabled
            preferences[PreferencesKeys.SYNC_INTERVAL_MINUTES] = settings.syncIntervalMinutes
            preferences[PreferencesKeys.PRECISE_LOCATION_MODE] = settings.preciseLocationMode
        }
    }

    /**
     * 保存 Host
     */
    suspend fun saveHost(host: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[PreferencesKeys.HOST] = host
        }
    }

    /**
     * 保存发现开关状态
     */
    suspend fun saveDiscoveryEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[PreferencesKeys.DISCOVERY_ENABLED] = enabled
        }
    }

    /**
     * 保存同步频率
     */
    suspend fun saveSyncIntervalMinutes(minutes: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[PreferencesKeys.SYNC_INTERVAL_MINUTES] = minutes
        }
    }

    suspend fun savePreciseLocationMode(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[PreferencesKeys.PRECISE_LOCATION_MODE] = enabled
        }
    }
}
