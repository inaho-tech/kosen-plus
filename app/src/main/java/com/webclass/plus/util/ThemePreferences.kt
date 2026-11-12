package com.webclass.plus.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.webclass.plus.ui.theme.AppTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "theme_preferences")

/** テーマの色ソースを選択する enum */
enum class ThemeSource {
    NONE,          // テーマカラーをそのまま使用
    DYNAMIC_COLOR, // Android 12+ ダイナミックカラー
    WALLPAPER,     // 壁紙から色を抽出してテーマに反映
    COLOR_SEED     // 単一色を選んでテーマ全体を自動生成
}

class ThemePreferences(private val context: Context) {
    
    private val THEME_KEY = stringPreferencesKey("app_theme")
    
    private val DYNAMIC_COLOR_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("dynamic_color")
    private val USE_BROWSER_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("use_external_browser")
    private val USE_WALLPAPER_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("use_wallpaper")
    private val WALLPAPER_PATH_KEY = stringPreferencesKey("wallpaper_path")
    private val WALLPAPER_TIMESTAMP_KEY = androidx.datastore.preferences.core.longPreferencesKey("wallpaper_timestamp")
    private val USE_WALLPAPER_COLOR_EXTRACTION_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("use_wallpaper_color_extraction")
    private val WALLPAPER_BACKGROUND_ALPHA_KEY = androidx.datastore.preferences.core.floatPreferencesKey("wallpaper_background_alpha")
    private val THEME_SOURCE_KEY = stringPreferencesKey("theme_source")
    private val SEED_COLOR_KEY = androidx.datastore.preferences.core.intPreferencesKey("seed_color")
    
    val themeFlow: Flow<AppTheme> = context.dataStore.data.map { preferences ->
        val themeName = preferences[THEME_KEY] ?: "SYSTEM"
        try {
            AppTheme.valueOf(themeName)
        } catch (e: IllegalArgumentException) {
            AppTheme.SYSTEM
        }
    }.distinctUntilChanged()

    val dynamicColorFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[DYNAMIC_COLOR_KEY] ?: true // Default to true
    }.distinctUntilChanged()

    val useBrowserFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[USE_BROWSER_KEY] ?: false // Default to false
    }.distinctUntilChanged()

    val useWallpaperFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[USE_WALLPAPER_KEY] ?: false // Default to false
    }.distinctUntilChanged()

    val wallpaperPathFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[WALLPAPER_PATH_KEY] ?: ""
    }.distinctUntilChanged()

    // Timestamp changes every time a new wallpaper is saved, forcing recomposition even when path stays the same
    val wallpaperTimestampFlow: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[WALLPAPER_TIMESTAMP_KEY] ?: 0L
    }.distinctUntilChanged()

    val useWallpaperColorExtractionFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[USE_WALLPAPER_COLOR_EXTRACTION_KEY] ?: false
    }.distinctUntilChanged()

    val wallpaperBackgroundAlphaFlow: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[WALLPAPER_BACKGROUND_ALPHA_KEY] ?: 0.55f
    }.distinctUntilChanged()

    val themeSourceFlow: Flow<ThemeSource> = context.dataStore.data.map { preferences ->
        val name = preferences[THEME_SOURCE_KEY] ?: "DYNAMIC_COLOR"
        try { ThemeSource.valueOf(name) } catch (e: IllegalArgumentException) { ThemeSource.DYNAMIC_COLOR }
    }.distinctUntilChanged()

    /** シードカラー (ARGB Int)。デフォルトはアプリのライトプライマリ色 */
    val seedColorFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[SEED_COLOR_KEY] ?: 0xFF4FC3F7.toInt()
    }.distinctUntilChanged()

    private val FOCUS_MODE_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("focus_mode")
    val focusModeFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[FOCUS_MODE_KEY] ?: false
    }.distinctUntilChanged()

    private val ENABLE_NATIVE_VIDEO_PLAYER_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("enable_native_video_player")
    val enableNativeVideoPlayerFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[ENABLE_NATIVE_VIDEO_PLAYER_KEY] ?: true // Default to true
    }.distinctUntilChanged()

    private val ENABLE_NATIVE_PDF_VIEWER_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("enable_native_pdf_viewer")
    val enableNativePdfViewerFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[ENABLE_NATIVE_PDF_VIEWER_KEY] ?: true // Default to true
    }.distinctUntilChanged()

    private val HIDE_SPLASH_SCREEN_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("hide_splash_screen")
    val hideSplashScreenFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[HIDE_SPLASH_SCREEN_KEY] ?: false // Default to false
    }.distinctUntilChanged()

    private val HIDE_ANNOUNCEMENTS_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("hide_announcements")
    val hideAnnouncementsFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[HIDE_ANNOUNCEMENTS_KEY] ?: false // Default to false
    }.distinctUntilChanged()

    private val HIDE_SURVEYS_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("hide_surveys")
    val hideSurveysFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[HIDE_SURVEYS_KEY] ?: false // Default to false
    }.distinctUntilChanged()

    private val HAS_SEEN_WELCOME_SCREEN_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("has_seen_welcome_screen")
    val hasSeenWelcomeScreenFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[HAS_SEEN_WELCOME_SCREEN_KEY] ?: false // Default to false
    }.distinctUntilChanged()

    private val LAST_SEEN_VERSION_KEY = stringPreferencesKey("last_seen_version")
    val lastSeenVersionFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[LAST_SEEN_VERSION_KEY] ?: ""
    }.distinctUntilChanged()

    suspend fun setLastSeenVersion(version: String) {
        context.dataStore.edit { preferences ->
            preferences[LAST_SEEN_VERSION_KEY] = version
        }
    }
    
    suspend fun setTheme(theme: AppTheme) {
        context.dataStore.edit { preferences ->
            preferences[THEME_KEY] = theme.name
            
            // パステル、カントリー、カスタム選択時はダイナミックカラー等の色ソースに対応していないため、NONEにする
            if (theme == AppTheme.PASTEL || theme == AppTheme.COUNTRY || theme == AppTheme.CUSTOM) {
                preferences[THEME_SOURCE_KEY] = ThemeSource.NONE.name
                preferences[DYNAMIC_COLOR_KEY] = false
                preferences[USE_WALLPAPER_COLOR_EXTRACTION_KEY] = false
            }
        }
        notifyWidgetUpdated()
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[DYNAMIC_COLOR_KEY] = enabled
        }
        notifyWidgetUpdated()
    }

    suspend fun setUseBrowser(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[USE_BROWSER_KEY] = enabled
        }
    }

    suspend fun setUseWallpaper(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[USE_WALLPAPER_KEY] = enabled
        }
    }

    suspend fun setWallpaperPath(path: String) {
        context.dataStore.edit { preferences ->
            preferences[WALLPAPER_PATH_KEY] = path
            preferences[WALLPAPER_TIMESTAMP_KEY] = System.currentTimeMillis()
        }
    }

    suspend fun setUseWallpaperColorExtraction(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[USE_WALLPAPER_COLOR_EXTRACTION_KEY] = enabled
        }
    }

    suspend fun setWallpaperBackgroundAlpha(alpha: Float) {
        context.dataStore.edit { preferences ->
            preferences[WALLPAPER_BACKGROUND_ALPHA_KEY] = alpha
        }
        notifyWidgetUpdated()
    }

    suspend fun setThemeSource(source: ThemeSource) {
        context.dataStore.edit { preferences ->
            preferences[THEME_SOURCE_KEY] = source.name
            // 既存フラグと同期して互換性を維持
            preferences[DYNAMIC_COLOR_KEY] = (source == ThemeSource.DYNAMIC_COLOR)
            preferences[USE_WALLPAPER_COLOR_EXTRACTION_KEY] = (source == ThemeSource.WALLPAPER)
            
            // ダイナミックカラー、色シード、壁紙抽出時はパステル、カントリー、カスタムは無効なためシステムにフォールバックする
            if (source != ThemeSource.NONE) {
                val currentThemeName = preferences[THEME_KEY] ?: "SYSTEM"
                if (currentThemeName == AppTheme.PASTEL.name || 
                    currentThemeName == AppTheme.COUNTRY.name || 
                    currentThemeName == AppTheme.CUSTOM.name) {
                    preferences[THEME_KEY] = AppTheme.SYSTEM.name
                }
            }
        }
        notifyWidgetUpdated()
    }

    suspend fun setSeedColor(color: Int) {
        context.dataStore.edit { preferences ->
            preferences[SEED_COLOR_KEY] = color
        }
        notifyWidgetUpdated()
    }

    suspend fun setFocusMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[FOCUS_MODE_KEY] = enabled
        }
    }

    suspend fun setEnableNativeVideoPlayer(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ENABLE_NATIVE_VIDEO_PLAYER_KEY] = enabled
        }
    }

    suspend fun setEnableNativePdfViewer(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ENABLE_NATIVE_PDF_VIEWER_KEY] = enabled
        }
    }

    suspend fun setHideSplashScreen(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[HIDE_SPLASH_SCREEN_KEY] = enabled
        }
    }

    suspend fun setHideAnnouncements(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[HIDE_ANNOUNCEMENTS_KEY] = enabled
        }
    }

    suspend fun setHideSurveys(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[HIDE_SURVEYS_KEY] = enabled
        }
    }

    suspend fun setHasSeenWelcomeScreen(seen: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[HAS_SEEN_WELCOME_SCREEN_KEY] = seen
        }
    }

    // Experimental Features
    private val ENABLE_SPEED_UP_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("enable_speed_up")
    val enableSpeedUpFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[ENABLE_SPEED_UP_KEY] ?: false
    }.distinctUntilChanged()

    private val ENABLE_BOOKMARKS_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("enable_bookmarks")
    val enableBookmarksFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[ENABLE_BOOKMARKS_KEY] ?: false
    }.distinctUntilChanged()

    private val ENABLE_THEME_EDITOR_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("enable_theme_editor")
    val enableThemeEditorFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[ENABLE_THEME_EDITOR_KEY] ?: false
    }.distinctUntilChanged()

    private val ENABLE_WEBVIEW_DARK_MODE_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("enable_webview_dark_mode")
    val enableWebViewDarkModeFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[ENABLE_WEBVIEW_DARK_MODE_KEY] ?: true
    }.distinctUntilChanged()

    private val ENABLE_HYPER_ANIMATION_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("enable_hyper_animation")
    val enableHyperAnimationFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[ENABLE_HYPER_ANIMATION_KEY] ?: false
    }.distinctUntilChanged()

    private val ENABLE_HYPER_BOOT_ANIMATION_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("enable_hyper_boot_animation")
    val enableHyperBootAnimationFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[ENABLE_HYPER_BOOT_ANIMATION_KEY] ?: false
    }.distinctUntilChanged()

    private val ENABLE_LIGHTWEIGHT_MODE_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("enable_lightweight_mode")
    val enableLightweightModeFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[ENABLE_LIGHTWEIGHT_MODE_KEY] ?: false
    }.distinctUntilChanged()

    private val HYPER_ANIMATION_STRENGTH_KEY = androidx.datastore.preferences.core.floatPreferencesKey("hyper_animation_strength")
    val hyperAnimationStrengthFlow: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[HYPER_ANIMATION_STRENGTH_KEY] ?: 1.0f
    }.distinctUntilChanged()

    private val CUSTOM_THEME_CSS_KEY = stringPreferencesKey("custom_theme_css")
    val customThemeCssFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[CUSTOM_THEME_CSS_KEY] ?: ""
    }.distinctUntilChanged()
    
    // Custom App Theme Colors
    private val CUSTOM_PRIMARY_KEY = androidx.datastore.preferences.core.intPreferencesKey("custom_primary")
    private val CUSTOM_SECONDARY_KEY = androidx.datastore.preferences.core.intPreferencesKey("custom_secondary")
    private val CUSTOM_BACKGROUND_KEY = androidx.datastore.preferences.core.intPreferencesKey("custom_background")
    private val CUSTOM_SURFACE_KEY = androidx.datastore.preferences.core.intPreferencesKey("custom_surface")

    val customColorsFlow: Flow<Map<String, Int>> = context.dataStore.data.map { preferences ->
        mapOf(
            "primary" to (preferences[CUSTOM_PRIMARY_KEY] ?: 0xFF4FC3F7.toInt()),
            "secondary" to (preferences[CUSTOM_SECONDARY_KEY] ?: 0xFF81D4FA.toInt()),
            "background" to (preferences[CUSTOM_BACKGROUND_KEY] ?: 0xFFFFFFFF.toInt()),
            "surface" to (preferences[CUSTOM_SURFACE_KEY] ?: 0xFFFAFBFD.toInt())
        )
    }.distinctUntilChanged()

    suspend fun setCustomColor(key: String, color: Int) {
        context.dataStore.edit { preferences ->
            when(key) {
                "primary" -> preferences[CUSTOM_PRIMARY_KEY] = color
                "secondary" -> preferences[CUSTOM_SECONDARY_KEY] = color
                "background" -> preferences[CUSTOM_BACKGROUND_KEY] = color
                "surface" -> preferences[CUSTOM_SURFACE_KEY] = color
            }
        }
        notifyWidgetUpdated()
    }

    // Custom Dark Mode
    private val CUSTOM_DARK_MODE_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("custom_dark_mode")
    val customDarkModeFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[CUSTOM_DARK_MODE_KEY] ?: false
    }.distinctUntilChanged()

    suspend fun setCustomDarkMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[CUSTOM_DARK_MODE_KEY] = enabled
        }
        notifyWidgetUpdated()
    }


    suspend fun setEnableSpeedUp(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ENABLE_SPEED_UP_KEY] = enabled
        }
    }

    suspend fun setEnableBookmarks(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ENABLE_BOOKMARKS_KEY] = enabled
        }
    }

    suspend fun setEnableThemeEditor(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ENABLE_THEME_EDITOR_KEY] = enabled
        }
    }

    suspend fun setEnableWebViewDarkMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ENABLE_WEBVIEW_DARK_MODE_KEY] = enabled
        }
    }

    suspend fun setEnableHyperAnimation(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ENABLE_HYPER_ANIMATION_KEY] = enabled
        }
    }

    suspend fun setEnableHyperBootAnimation(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ENABLE_HYPER_BOOT_ANIMATION_KEY] = enabled
        }
    }

    suspend fun setEnableLightweightMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ENABLE_LIGHTWEIGHT_MODE_KEY] = enabled
        }
    }

    suspend fun setHyperAnimationStrength(strength: Float) {
        context.dataStore.edit { preferences ->
            preferences[HYPER_ANIMATION_STRENGTH_KEY] = strength
        }
    }

    suspend fun setCustomThemeCss(css: String) {
        context.dataStore.edit { preferences ->
            preferences[CUSTOM_THEME_CSS_KEY] = css
        }
    }
    // Font Family
    private val FONT_FAMILY_KEY = stringPreferencesKey("font_family")
    private val CUSTOM_FONT_PATH_KEY = stringPreferencesKey("custom_font_path")
    private val FONT_TIMESTAMP_KEY = androidx.datastore.preferences.core.longPreferencesKey("font_timestamp")
    
    enum class AppFont {
        DEFAULT,
        SERIF,
        SANS_SERIF,
        MONOSPACE,
        CURSIVE,
        CUSTOM
    }

    val fontFlow: Flow<AppFont> = context.dataStore.data.map { preferences ->
        val fontName = preferences[FONT_FAMILY_KEY] ?: "DEFAULT"
        try {
            AppFont.valueOf(fontName)
        } catch (e: IllegalArgumentException) {
            AppFont.DEFAULT
        }
    }.distinctUntilChanged()

    val customFontPathFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[CUSTOM_FONT_PATH_KEY] ?: ""
    }.distinctUntilChanged()

    val fontTimestampFlow: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[FONT_TIMESTAMP_KEY] ?: 0L
    }.distinctUntilChanged()

    suspend fun setFont(font: AppFont) {
        context.dataStore.edit { preferences ->
            preferences[FONT_FAMILY_KEY] = font.name
        }
    }

    suspend fun setCustomFontPath(path: String) {
        context.dataStore.edit { preferences ->
            preferences[CUSTOM_FONT_PATH_KEY] = path
            preferences[FONT_TIMESTAMP_KEY] = System.currentTimeMillis()
        }
    }

    private fun notifyWidgetUpdated() {
        val intent = android.content.Intent(context, com.webclass.plus.widget.BusWidgetProvider::class.java).apply {
            action = com.webclass.plus.widget.BusWidgetProvider.ACTION_FORCE_UPDATE_THEME
        }
        context.sendBroadcast(intent)
    }
}
