package com.webclass.plus.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.developerDataStore: DataStore<Preferences> by preferencesDataStore(name = "developer_settings")

class DeveloperPreferences(private val context: Context) {

    // Keys
    private val MOCK_MODE_ENABLED = booleanPreferencesKey("mock_mode_enabled")
    private val CUSTOM_UPDATE_SOURCE_ENABLED = booleanPreferencesKey("custom_update_source_enabled")
    private val CUSTOM_UPDATE_URL = stringPreferencesKey("custom_update_url")
    private val FORCE_TABLET_MODE = booleanPreferencesKey("force_tablet_mode")
    private val FORCE_SMARTPHONE_MODE = booleanPreferencesKey("force_smartphone_mode")
    private val SPOOF_VERSION_ENABLED = booleanPreferencesKey("spoof_version_enabled")
    private val SPOOF_VERSION = stringPreferencesKey("spoof_version")
    private val SHOW_NETWORK_LOG = booleanPreferencesKey("show_network_log")
    private val ALWAYS_SHOW_WELCOME_SCREEN = booleanPreferencesKey("always_show_welcome_screen")
    private val ALWAYS_SHOW_WHATS_NEW = booleanPreferencesKey("always_show_whats_new")
    private val APPLY_CUSTOM_CONTENT_UI = booleanPreferencesKey("apply_custom_content_ui")
    private val SPOOF_NAME_ENABLED = booleanPreferencesKey("spoof_name_enabled")
    private val SPOOF_NAME = stringPreferencesKey("spoof_name")

    // Flows
    val mockModeEnabledFlow: Flow<Boolean> = context.developerDataStore.data
        .map { preferences ->
            preferences[MOCK_MODE_ENABLED] ?: false
        }.distinctUntilChanged()

    val customUpdateSourceEnabledFlow: Flow<Boolean> = context.developerDataStore.data
        .map { preferences ->
            preferences[CUSTOM_UPDATE_SOURCE_ENABLED] ?: false
        }.distinctUntilChanged()

    val customUpdateUrlFlow: Flow<String> = context.developerDataStore.data
        .map { preferences ->
            preferences[CUSTOM_UPDATE_URL] ?: ""
        }.distinctUntilChanged()

    val forceTabletModeFlow: Flow<Boolean> = context.developerDataStore.data
        .map { preferences ->
            preferences[FORCE_TABLET_MODE] ?: false
        }.distinctUntilChanged()

    val forceSmartphoneModeFlow: Flow<Boolean> = context.developerDataStore.data
        .map { preferences ->
            preferences[FORCE_SMARTPHONE_MODE] ?: false
        }.distinctUntilChanged()

    val spoofVersionEnabledFlow: Flow<Boolean> = context.developerDataStore.data
        .map { preferences ->
            preferences[SPOOF_VERSION_ENABLED] ?: false
        }.distinctUntilChanged()

    val spoofVersionFlow: Flow<String> = context.developerDataStore.data
        .map { preferences ->
            preferences[SPOOF_VERSION] ?: ""
        }.distinctUntilChanged()

    val showNetworkLogFlow: Flow<Boolean> = context.developerDataStore.data
        .map { preferences ->
            preferences[SHOW_NETWORK_LOG] ?: false
        }.distinctUntilChanged()

    val alwaysShowWelcomeScreenFlow: Flow<Boolean> = context.developerDataStore.data
        .map { preferences ->
            preferences[ALWAYS_SHOW_WELCOME_SCREEN] ?: false
        }.distinctUntilChanged()

    val alwaysShowWhatsNewFlow: Flow<Boolean> = context.developerDataStore.data
        .map { preferences ->
            preferences[ALWAYS_SHOW_WHATS_NEW] ?: false
        }.distinctUntilChanged()

    val applyCustomContentUiFlow: Flow<Boolean> = context.developerDataStore.data
        .map { preferences ->
            preferences[APPLY_CUSTOM_CONTENT_UI] ?: false
        }.distinctUntilChanged()

    val spoofNameEnabledFlow: Flow<Boolean> = context.developerDataStore.data
        .map { preferences ->
            preferences[SPOOF_NAME_ENABLED] ?: false
        }.distinctUntilChanged()

    val spoofNameFlow: Flow<String> = context.developerDataStore.data
        .map { preferences ->
            preferences[SPOOF_NAME] ?: ""
        }.distinctUntilChanged()

    // Setters
    suspend fun setMockModeEnabled(enabled: Boolean) {
        context.developerDataStore.edit { preferences ->
            preferences[MOCK_MODE_ENABLED] = enabled
        }
    }

    suspend fun setCustomUpdateSourceEnabled(enabled: Boolean) {
        context.developerDataStore.edit { preferences ->
            preferences[CUSTOM_UPDATE_SOURCE_ENABLED] = enabled
        }
    }

    suspend fun setCustomUpdateUrl(url: String) {
        context.developerDataStore.edit { preferences ->
            preferences[CUSTOM_UPDATE_URL] = url
        }
    }

    suspend fun setForceTabletMode(enabled: Boolean) {
        context.developerDataStore.edit { preferences ->
            preferences[FORCE_TABLET_MODE] = enabled
        }
    }

    suspend fun setForceSmartphoneMode(enabled: Boolean) {
        context.developerDataStore.edit { preferences ->
            preferences[FORCE_SMARTPHONE_MODE] = enabled
        }
    }

    suspend fun setSpoofVersionEnabled(enabled: Boolean) {
        context.developerDataStore.edit { preferences ->
            preferences[SPOOF_VERSION_ENABLED] = enabled
        }
    }

    suspend fun setSpoofVersion(version: String) {
        context.developerDataStore.edit { preferences ->
            preferences[SPOOF_VERSION] = version
        }
    }

    suspend fun setShowNetworkLog(enabled: Boolean) {
        context.developerDataStore.edit { preferences ->
            preferences[SHOW_NETWORK_LOG] = enabled
        }
    }

    suspend fun setAlwaysShowWelcomeScreen(enabled: Boolean) {
        context.developerDataStore.edit { preferences ->
            preferences[ALWAYS_SHOW_WELCOME_SCREEN] = enabled
        }
    }

    suspend fun setAlwaysShowWhatsNew(enabled: Boolean) {
        context.developerDataStore.edit { preferences ->
            preferences[ALWAYS_SHOW_WHATS_NEW] = enabled
        }
    }

    suspend fun setApplyCustomContentUi(enabled: Boolean) {
        context.developerDataStore.edit { preferences ->
            preferences[APPLY_CUSTOM_CONTENT_UI] = enabled
        }
    }

    suspend fun setSpoofNameEnabled(enabled: Boolean) {
        context.developerDataStore.edit { preferences ->
            preferences[SPOOF_NAME_ENABLED] = enabled
        }
    }

    suspend fun setSpoofName(name: String) {
        context.developerDataStore.edit { preferences ->
            preferences[SPOOF_NAME] = name
        }
    }
}
