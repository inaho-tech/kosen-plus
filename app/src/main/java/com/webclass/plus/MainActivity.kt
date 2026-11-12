package com.webclass.plus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.webclass.plus.ui.navigation.NavGraph
import com.webclass.plus.ui.theme.AppTheme
import com.webclass.plus.ui.theme.WebClassTheme
import com.webclass.plus.util.ThemePreferences
import com.webclass.plus.util.ThemeSource
import android.content.Intent
import android.content.res.Configuration
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette

class MainActivity : ComponentActivity() {
    
    private lateinit var themePreferences: ThemePreferences
    private lateinit var developerPreferences: com.webclass.plus.util.DeveloperPreferences
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Read the saved theme synchronously BEFORE super.onCreate so we can apply
        // the correct XML theme (light/dark window background) before the first frame.
        themePreferences = ThemePreferences(applicationContext)
        developerPreferences = com.webclass.plus.util.DeveloperPreferences(applicationContext)
        
        val savedTheme = runBlocking { themePreferences.themeFlow.first() }
        val isDarkXml = when (savedTheme) {
            AppTheme.DARK -> true
            AppTheme.SYSTEM -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            else -> false
        }
        if (isDarkXml) {
            setTheme(R.style.Theme_WebClassPlus_Dark)
        } else {
            setTheme(R.style.Theme_WebClassPlus)
        }
        // else: the default Theme.WebClassPlus (light) from the manifest is overridden if needed
        
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setupAppShortcuts()
        
        setContent {
            val currentTheme by themePreferences.themeFlow.collectAsState(initial = com.webclass.plus.ui.theme.AppTheme.SYSTEM)
            val dynamicColor by themePreferences.dynamicColorFlow.collectAsState(initial = true)
            val focusMode by themePreferences.focusModeFlow.collectAsState(initial = false)

            val customColorsMap by themePreferences.customColorsFlow.collectAsState(initial = emptyMap())
            val customDarkMode by themePreferences.customDarkModeFlow.collectAsState(initial = false)
            
            val appFont by themePreferences.fontFlow.collectAsState(initial = com.webclass.plus.util.ThemePreferences.AppFont.DEFAULT)
            val customFontPath by themePreferences.customFontPathFlow.collectAsState(initial = "")
            val fontTimestamp by themePreferences.fontTimestampFlow.collectAsState(initial = 0L)
            
            val fontFamily = androidx.compose.runtime.remember(appFont, customFontPath, fontTimestamp) {
                when(appFont) {
                    com.webclass.plus.util.ThemePreferences.AppFont.SERIF -> androidx.compose.ui.text.font.FontFamily.Serif
                    com.webclass.plus.util.ThemePreferences.AppFont.SANS_SERIF -> androidx.compose.ui.text.font.FontFamily.SansSerif
                    com.webclass.plus.util.ThemePreferences.AppFont.MONOSPACE -> androidx.compose.ui.text.font.FontFamily.Monospace
                    com.webclass.plus.util.ThemePreferences.AppFont.CURSIVE -> androidx.compose.ui.text.font.FontFamily.Cursive
                    com.webclass.plus.util.ThemePreferences.AppFont.CUSTOM -> {
                        if (customFontPath.isNotEmpty()) {
                            try {
                                val file = java.io.File(customFontPath)
                                if (file.exists()) {
                                    androidx.compose.ui.text.font.FontFamily(android.graphics.Typeface.createFromFile(file))
                                } else {
                                    androidx.compose.ui.text.font.FontFamily.Default
                                }
                            } catch (e: Exception) {
                                androidx.compose.ui.text.font.FontFamily.Default
                            }
                        } else {
                            androidx.compose.ui.text.font.FontFamily.Default
                        }
                    }
                    else -> androidx.compose.ui.text.font.FontFamily.Default
                }
            }
            
            val customColors = com.webclass.plus.ui.theme.CustomThemeColors(
                primary = androidx.compose.ui.graphics.Color(customColorsMap["primary"] ?: 0xFF4FC3F7.toInt()),
                secondary = androidx.compose.ui.graphics.Color(customColorsMap["secondary"] ?: 0xFF81D4FA.toInt()),
                background = androidx.compose.ui.graphics.Color(customColorsMap["background"] ?: 0xFFFFFFFF.toInt()),
                surface = androidx.compose.ui.graphics.Color(customColorsMap["surface"] ?: 0xFFFAFBFD.toInt()),
                isDarkMode = customDarkMode
            )

            androidx.compose.runtime.LaunchedEffect(focusMode) {
                val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                if (focusMode) {
                    windowInsetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                    windowInsetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                } else {
                    windowInsetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                }
            }
            
            val hideSplashScreen by themePreferences.hideSplashScreenFlow.collectAsState(initial = false)
            val hasSeenWelcomeScreen by themePreferences.hasSeenWelcomeScreenFlow.collectAsState(initial = false)
            val alwaysShowWelcomeScreen by developerPreferences.alwaysShowWelcomeScreenFlow.collectAsState(initial = false)
            
            val useWallpaper by themePreferences.useWallpaperFlow.collectAsState(initial = false)
            val wallpaperPath by themePreferences.wallpaperPathFlow.collectAsState(initial = "")
            val wallpaperTimestamp by themePreferences.wallpaperTimestampFlow.collectAsState(initial = 0L)
            val wallpaperBackgroundAlpha by themePreferences.wallpaperBackgroundAlphaFlow.collectAsState(initial = 0.55f)
            val useWallpaperColorExtraction by themePreferences.useWallpaperColorExtractionFlow.collectAsState(initial = false)
            val themeSource by themePreferences.themeSourceFlow.collectAsState(initial = ThemeSource.DYNAMIC_COLOR)
            val seedColor by themePreferences.seedColorFlow.collectAsState(initial = 0xFF4FC3F7.toInt())
            
            val shortcutRoute = intent?.getStringExtra("shortcut_route")
            
            val initialDestination = if (shortcutRoute != null) {
                shortcutRoute
            } else if (!hideSplashScreen) {
                "splash"
            } else if (!hasSeenWelcomeScreen || alwaysShowWelcomeScreen) {
                "welcome"
            } else {
                "home"
            }

            val context = androidx.compose.ui.platform.LocalContext.current
            val displayMetrics = context.resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels

            // Decode wallpaper bitmap — keyed on timestamp so it reloads whenever a new image is saved,
            // even if the file path stays the same (wallpaper.jpg is always overwritten in-place).
            val wallpaperBitmap = androidx.compose.runtime.remember(wallpaperTimestamp) {
                if (wallpaperPath.isNotEmpty()) {
                    try {
                        com.webclass.plus.util.ImageUtils.decodeSampledBitmapFromFile(
                            wallpaperPath,
                            screenWidth,
                            screenHeight
                        )
                    } catch (e: Exception) { null }
                } else null
            }

            // Extract dominant color via Palette and build a full Material color scheme when enabled.
            val wallpaperColorScheme: ColorScheme? = androidx.compose.runtime.remember(wallpaperTimestamp, useWallpaperColorExtraction) {
                if (useWallpaperColorExtraction && wallpaperBitmap != null) {
                    val palette = Palette.from(wallpaperBitmap).generate()
                    val dominant = palette.getDominantColor(0xFF4FC3F7.toInt())
                    val vibrant = palette.getVibrantColor(dominant)
                    val muted = palette.getMutedColor(dominant)
                    val seedColor = Color(vibrant)
                    val seedInt = vibrant

                    // Derive a harmonious Material3 palette from the seed color
                    val primaryColor = Color(seedInt)
                    val secondaryColor = Color(muted)
                    val isDark = android.graphics.Color.luminance(seedInt) < 0.2f

                    if (isDark) {
                        darkColorScheme(
                            primary = primaryColor,
                            secondary = secondaryColor,
                            primaryContainer = primaryColor.copy(alpha = 0.3f),
                            secondaryContainer = secondaryColor.copy(alpha = 0.3f),
                        )
                    } else {
                        lightColorScheme(
                            primary = primaryColor,
                            secondary = secondaryColor,
                            primaryContainer = primaryColor.copy(alpha = 0.2f),
                            secondaryContainer = secondaryColor.copy(alpha = 0.2f),
                        )
                    }
                } else null
            }

            // When wallpaper color extraction is active, disable dynamic color to avoid conflicts.
            val effectiveDynamicColor = if (useWallpaperColorExtraction || themeSource == ThemeSource.COLOR_SEED) false else dynamicColor
            
            WebClassTheme(
                theme = currentTheme, 
                dynamicColor = effectiveDynamicColor,
                customColors = customColors,
                fontFamily = fontFamily,
                useWallpaper = useWallpaper,
                wallpaperColorScheme = wallpaperColorScheme,
                themeSource = themeSource,
                seedColor = if (themeSource == ThemeSource.COLOR_SEED) seedColor else null,
                wallpaperBackgroundAlpha = wallpaperBackgroundAlpha
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent
                ) {
                    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
                        if (useWallpaper && wallpaperBitmap != null) {
                            androidx.compose.foundation.Image(
                                bitmap = wallpaperBitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        }
                        NavGraph(startDestination = initialDestination)
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Ensure cookies are persisted to disk when the app goes to background.
        // This helps prevent session loss if the app is subsequently task-killed.
        android.webkit.CookieManager.getInstance().flush()
    }

    private fun setupAppShortcuts() {
        val syllabusShortcut = ShortcutInfoCompat.Builder(this, "shortcut_syllabus")
            .setShortLabel("シラバス検索")
            .setLongLabel("シラバス検索を開く")
            .setIcon(IconCompat.createWithResource(this, R.drawable.ic_shortcut_syllabus))
            .setIntent(
                Intent(this, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    putExtra("shortcut_route", "syllabus")
                }
            )
            .build()

        val busShortcut = ShortcutInfoCompat.Builder(this, "shortcut_bus")
            .setShortLabel("時刻表")
            .setLongLabel("バス・電車時刻表を開く")
            .setIcon(IconCompat.createWithResource(this, R.drawable.ic_shortcut_bus))
            .setIntent(
                Intent(this, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    putExtra("shortcut_route", "bus")
                }
            )
            .build()

        ShortcutManagerCompat.setDynamicShortcuts(this, listOf(syllabusShortcut, busShortcut))
    }
}
