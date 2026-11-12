package com.webclass.plus.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.webclass.plus.util.ThemeSource

enum class AppTheme {
    LIGHT,
    DARK,
    PASTEL,
    CUSTOM,
    SYSTEM,
    COUNTRY
}

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = Color(0xFFF1F6FA),
    onSurfaceVariant = Color(0xFF40515D),
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    surfaceDim = Color(0xFFE3E9ED),
    surfaceBright = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF6FAFD),
    surfaceContainer = Color(0xFFF0F6FA),
    surfaceContainerHigh = Color(0xFFEAF2F7),
    surfaceContainerHighest = Color(0xFFE3ECF2)
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = Color(0xFF233044),
    onSurfaceVariant = Color(0xFFC0CAD3),
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    surfaceDim = Color(0xFF050914),
    surfaceBright = Color(0xFF253247),
    surfaceContainerLowest = Color(0xFF03060C),
    surfaceContainerLow = Color(0xFF0A1020),
    surfaceContainer = Color(0xFF0F172A),
    surfaceContainerHigh = Color(0xFF162137),
    surfaceContainerHighest = Color(0xFF1D2B40)
)

private val PastelColorScheme = lightColorScheme(
    primary = PastelPrimary,
    onPrimary = PastelOnPrimary,
    primaryContainer = PastelPrimaryContainer,
    onPrimaryContainer = PastelOnPrimaryContainer,
    secondary = PastelSecondary,
    onSecondary = PastelOnSecondary,
    secondaryContainer = PastelSecondaryContainer,
    onSecondaryContainer = PastelOnSecondaryContainer,
    background = PastelBackground,
    onBackground = PastelOnBackground,
    surface = PastelSurface,
    onSurface = PastelOnSurface,
    outline = PastelOutline,
    outlineVariant = PastelOutlineVariant
)

private val CountryColorScheme = lightColorScheme(
    primary = CountryPrimary,
    onPrimary = CountryOnPrimary,
    primaryContainer = CountryPrimaryContainer,
    onPrimaryContainer = CountryOnPrimaryContainer,
    secondary = CountrySecondary,
    onSecondary = CountryOnSecondary,
    secondaryContainer = CountrySecondaryContainer,
    onSecondaryContainer = CountryOnSecondaryContainer,
    background = CountryBackground,
    onBackground = CountryOnBackground,
    surface = CountrySurface,
    onSurface = CountryOnSurface,
    outline = CountryOutline,
    outlineVariant = CountryOutlineVariant
)

data class CustomThemeColors(
    val primary: androidx.compose.ui.graphics.Color = LightPrimary,
    val secondary: androidx.compose.ui.graphics.Color = LightSecondary,
    val background: androidx.compose.ui.graphics.Color = LightBackground,
    val surface: androidx.compose.ui.graphics.Color = LightSurface,
    val isDarkMode: Boolean = false
)

/**
 * シードカラー (ARGB Int) から Material3 スタイルのカラースキームを生成する。
 * HSL 変換を使って primary / secondary / tertiary / background / surface などを導出。
 *
 * @param seedArgb  シードとなる ARGB 整数
 * @param isDark    ダークモードで生成するか
 */
fun colorSchemeFromSeed(seedArgb: Int, isDark: Boolean): androidx.compose.material3.ColorScheme {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(seedArgb, hsv)
    val hue = hsv[0]
    val sat = hsv[1]
    val value = hsv[2]

    // Helper: Color from HSV
    fun hsv(h: Float, s: Float, v: Float): Color {
        val argb = android.graphics.Color.HSVToColor(floatArrayOf(h.coerceIn(0f, 360f), s.coerceIn(0f, 1f), v.coerceIn(0f, 1f)))
        return Color(argb)
    }

    // Secondary: hue shifted +60°, slightly desaturated
    val secHue = (hue + 60f) % 360f
    // Tertiary: hue shifted +120°
    val terHue = (hue + 120f) % 360f

    return if (isDark) {
        darkColorScheme(
            primary            = hsv(hue,   (sat * 0.8f).coerceAtLeast(0.4f), 0.85f),
            onPrimary          = hsv(hue,   0.9f, 0.15f),
            primaryContainer   = hsv(hue,   (sat * 0.6f).coerceAtLeast(0.3f), 0.35f),
            onPrimaryContainer = hsv(hue,   0.2f, 0.95f),
            secondary          = hsv(secHue,(sat * 0.7f).coerceAtLeast(0.3f), 0.80f),
            onSecondary        = hsv(secHue,0.9f, 0.12f),
            secondaryContainer = hsv(secHue,(sat * 0.5f).coerceAtLeast(0.2f), 0.30f),
            onSecondaryContainer = hsv(secHue, 0.2f, 0.92f),
            tertiary           = hsv(terHue,(sat * 0.7f).coerceAtLeast(0.3f), 0.78f),
            onTertiary         = hsv(terHue,0.9f, 0.12f),
            tertiaryContainer  = hsv(terHue,(sat * 0.5f).coerceAtLeast(0.2f), 0.28f),
            onTertiaryContainer = hsv(terHue, 0.15f, 0.90f),
            background         = hsv(hue,   (sat * 0.15f).coerceAtLeast(0.05f), 0.08f),
            onBackground       = hsv(hue,   0.1f, 0.92f),
            surface            = hsv(hue,   (sat * 0.12f).coerceAtLeast(0.04f), 0.12f),
            onSurface          = hsv(hue,   0.08f, 0.92f),
            surfaceVariant     = hsv(hue,   (sat * 0.18f).coerceAtLeast(0.06f), 0.20f),
            onSurfaceVariant   = hsv(hue,   0.12f, 0.78f),
            outline            = hsv(hue,   (sat * 0.2f).coerceAtLeast(0.08f), 0.40f),
            outlineVariant     = hsv(hue,   (sat * 0.15f).coerceAtLeast(0.05f), 0.28f),
            surfaceDim             = hsv(hue, (sat * 0.14f).coerceAtLeast(0.05f), 0.08f),
            surfaceBright          = hsv(hue, (sat * 0.10f).coerceAtLeast(0.03f), 0.20f),
            surfaceContainerLowest = hsv(hue, (sat * 0.16f).coerceAtLeast(0.06f), 0.04f),
            surfaceContainerLow    = hsv(hue, (sat * 0.14f).coerceAtLeast(0.05f), 0.09f),
            surfaceContainer       = hsv(hue, (sat * 0.12f).coerceAtLeast(0.04f), 0.12f),
            surfaceContainerHigh   = hsv(hue, (sat * 0.10f).coerceAtLeast(0.03f), 0.16f),
            surfaceContainerHighest= hsv(hue, (sat * 0.08f).coerceAtLeast(0.02f), 0.22f)
        )
    } else {
        lightColorScheme(
            primary            = hsv(hue,   (sat * 0.85f).coerceAtLeast(0.45f), (value * 0.75f).coerceIn(0.35f, 0.65f)),
            onPrimary          = Color.White,
            primaryContainer   = hsv(hue,   (sat * 0.3f).coerceAtLeast(0.1f),  0.95f),
            onPrimaryContainer = hsv(hue,   (sat * 0.9f).coerceAtLeast(0.5f),  0.20f),
            secondary          = hsv(secHue,(sat * 0.6f).coerceAtLeast(0.3f),  (value * 0.70f).coerceIn(0.30f, 0.60f)),
            onSecondary        = Color.White,
            secondaryContainer = hsv(secHue,(sat * 0.2f).coerceAtLeast(0.08f), 0.93f),
            onSecondaryContainer = hsv(secHue, (sat * 0.8f).coerceAtLeast(0.4f), 0.18f),
            tertiary           = hsv(terHue,(sat * 0.6f).coerceAtLeast(0.3f),  (value * 0.70f).coerceIn(0.30f, 0.60f)),
            onTertiary         = Color.White,
            tertiaryContainer  = hsv(terHue,(sat * 0.2f).coerceAtLeast(0.08f), 0.93f),
            onTertiaryContainer = hsv(terHue,(sat * 0.8f).coerceAtLeast(0.4f), 0.18f),
            background         = hsv(hue,   (sat * 0.05f).coerceAtLeast(0.01f), 0.99f),
            onBackground       = hsv(hue,   (sat * 0.5f).coerceAtLeast(0.1f),   0.10f),
            surface            = hsv(hue,   (sat * 0.04f).coerceAtLeast(0.01f), 0.98f),
            onSurface          = hsv(hue,   (sat * 0.5f).coerceAtLeast(0.1f),   0.10f),
            surfaceVariant     = hsv(hue,   (sat * 0.08f).coerceAtLeast(0.02f), 0.94f),
            onSurfaceVariant   = hsv(hue,   (sat * 0.3f).coerceAtLeast(0.1f),   0.35f),
            outline            = hsv(hue,   (sat * 0.15f).coerceAtLeast(0.05f), 0.60f),
            outlineVariant     = hsv(hue,   (sat * 0.10f).coerceAtLeast(0.03f), 0.80f),
            surfaceDim             = hsv(hue, (sat * 0.06f).coerceAtLeast(0.02f), 0.94f),
            surfaceBright          = hsv(hue, (sat * 0.02f).coerceAtLeast(0.01f), 0.99f),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow    = hsv(hue, (sat * 0.04f).coerceAtLeast(0.01f), 0.96f),
            surfaceContainer       = hsv(hue, (sat * 0.06f).coerceAtLeast(0.02f), 0.94f),
            surfaceContainerHigh   = hsv(hue, (sat * 0.08f).coerceAtLeast(0.03f), 0.92f),
            surfaceContainerHighest= hsv(hue, (sat * 0.10f).coerceAtLeast(0.04f), 0.90f)
        )
    }
}

@Composable
fun WebClassTheme(
    theme: AppTheme = AppTheme.LIGHT,
    dynamicColor: Boolean = true,
    customColors: CustomThemeColors = CustomThemeColors(),
    fontFamily: androidx.compose.ui.text.font.FontFamily = androidx.compose.ui.text.font.FontFamily.Default,
    useWallpaper: Boolean = false,
    wallpaperColorScheme: androidx.compose.material3.ColorScheme? = null,
    themeSource: ThemeSource = ThemeSource.DYNAMIC_COLOR,
    seedColor: Int? = null,
    wallpaperBackgroundAlpha: Float = 0.55f,
    content: @Composable () -> Unit
) {
    // Cache Typography to prevent recreation on every recomposition
    val typography = androidx.compose.runtime.remember(fontFamily) {
        getTypography(fontFamily)
    }
    
    // Resolve SYSTEM theme to LIGHT or DARK based on device setting
    val resolvedTheme = if (theme == AppTheme.SYSTEM) {
        if (isSystemInDarkTheme()) AppTheme.DARK else AppTheme.LIGHT
    } else {
        theme
    }

    val isDarkResolved = resolvedTheme == AppTheme.DARK

    val colorScheme = when (resolvedTheme) {
        AppTheme.LIGHT -> {
            if (dynamicColor && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val context = androidx.compose.ui.platform.LocalContext.current
                androidx.compose.material3.dynamicLightColorScheme(context)
            } else {
                LightColorScheme
            }
        }
        AppTheme.DARK -> {
            if (dynamicColor && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val context = androidx.compose.ui.platform.LocalContext.current
                androidx.compose.material3.dynamicDarkColorScheme(context)
            } else {
                DarkColorScheme
            }
        }
        AppTheme.PASTEL -> PastelColorScheme
        AppTheme.COUNTRY -> CountryColorScheme
        AppTheme.CUSTOM -> {
            val onPrimary = if (customColors.isDarkMode) androidx.compose.ui.graphics.Color.Black else androidx.compose.ui.graphics.Color.White
            val onBackground = if (customColors.isDarkMode) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Black
            val onSurface = if (customColors.isDarkMode) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Black
            
            if (customColors.isDarkMode) {
                darkColorScheme(
                    primary = customColors.primary,
                    onPrimary = onPrimary,
                    primaryContainer = customColors.primary.copy(alpha = 0.3f),
                    onPrimaryContainer = androidx.compose.ui.graphics.Color.White,
                    secondary = customColors.secondary,
                    onSecondary = onPrimary,
                    secondaryContainer = customColors.secondary.copy(alpha = 0.3f),
                    onSecondaryContainer = androidx.compose.ui.graphics.Color.White,
                    background = customColors.background,
                    onBackground = onBackground,
                    surface = customColors.surface,
                    onSurface = onSurface,
                )
            } else {
                lightColorScheme(
                    primary = customColors.primary,
                    onPrimary = onPrimary,
                    primaryContainer = customColors.primary.copy(alpha = 0.3f),
                    onPrimaryContainer = androidx.compose.ui.graphics.Color.Black,
                    secondary = customColors.secondary,
                    onSecondary = onPrimary,
                    secondaryContainer = customColors.secondary.copy(alpha = 0.3f),
                    onSecondaryContainer = androidx.compose.ui.graphics.Color.Black,
                    background = customColors.background,
                    onBackground = onBackground,
                    surface = customColors.surface,
                    onSurface = onSurface,
                )
            }
        }
        AppTheme.SYSTEM -> LightColorScheme // Already resolved above, unreachable
    }

    // COLOR_SEED: シードカラーからテーマ全体を生成（他ソースより優先）
    val seedColorScheme: androidx.compose.material3.ColorScheme? = if (
        themeSource == ThemeSource.COLOR_SEED && seedColor != null
    ) {
        colorSchemeFromSeed(seedColor, isDarkResolved)
    } else null

    val adjustedColorScheme = when {
        seedColorScheme != null -> {
            // シードカラーテーマ（壁紙表示時は半透明調整）
            if (useWallpaper) {
                seedColorScheme.copy(
                    background = seedColorScheme.background.copy(alpha = wallpaperBackgroundAlpha),
                    surface = seedColorScheme.surface.copy(alpha = wallpaperBackgroundAlpha),
                    surfaceVariant = seedColorScheme.surfaceVariant.copy(alpha = wallpaperBackgroundAlpha)
                )
            } else seedColorScheme
        }
        wallpaperColorScheme != null -> {
            // 壁紙カラー抽出モード
            if (useWallpaper) {
                wallpaperColorScheme.copy(
                    background = wallpaperColorScheme.background.copy(alpha = wallpaperBackgroundAlpha),
                    surface = wallpaperColorScheme.surface.copy(alpha = wallpaperBackgroundAlpha),
                    surfaceVariant = wallpaperColorScheme.surfaceVariant.copy(alpha = wallpaperBackgroundAlpha)
                )
            } else {
                wallpaperColorScheme
            }
        }
        useWallpaper -> colorScheme.copy(
            background = colorScheme.background.copy(alpha = wallpaperBackgroundAlpha),
            surface = colorScheme.surface.copy(alpha = wallpaperBackgroundAlpha),
            surfaceVariant = colorScheme.surfaceVariant.copy(alpha = wallpaperBackgroundAlpha)
        )
        else -> colorScheme
    }

    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        androidx.compose.runtime.SideEffect {
            val window = (view.context as? android.app.Activity)?.window
            if (window != null) {
                val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !isDarkResolved
                insetsController.isAppearanceLightNavigationBars = !isDarkResolved
            }
        }
    }

    MaterialTheme(
        colorScheme = adjustedColorScheme,
        typography = typography,
        shapes = Shapes,
        content = content
    )
}
