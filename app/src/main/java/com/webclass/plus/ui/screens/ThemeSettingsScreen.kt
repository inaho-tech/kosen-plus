package com.webclass.plus.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.webclass.plus.ui.theme.AppTheme
import com.webclass.plus.util.ThemePreferences
import com.webclass.plus.util.ThemeSource
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.withContext
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale

// Preset colors for seed color picker
private val seedPresetColors = listOf(
    0xFF4FC3F7.toInt() to "スカイ",
    0xFF7C3AED.toInt() to "バイオレット",
    0xFFEC4899.toInt() to "ピンク",
    0xFFEF4444.toInt() to "レッド",
    0xFFF97316.toInt() to "オレンジ",
    0xFFEAB308.toInt() to "イエロー",
    0xFF22C55E.toInt() to "グリーン",
    0xFF14B8A6.toInt() to "ティール",
    0xFF6366F1.toInt() to "インディゴ",
    0xFFF43F5E.toInt() to "ローズ",
    0xFF84CC16.toInt() to "ライム",
    0xFF0EA5E9.toInt() to "サファイア",
    0xFFD946EF.toInt() to "フクシア",
    0xFF78716C.toInt() to "ストーン",
    0xFF1D4ED8.toInt() to "ブルー",
    0xFF065F46.toInt() to "エメラルド",
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ThemeSettingsScreen(
    onAppThemeEditorClick: () -> Unit = {},
    showBackIcon: Boolean = true,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val themePreferences = remember { ThemePreferences(context) }
    val currentTheme by themePreferences.themeFlow.collectAsState(initial = AppTheme.SYSTEM)
    val themeSource by themePreferences.themeSourceFlow.collectAsState(initial = ThemeSource.DYNAMIC_COLOR)
    val seedColor by themePreferences.seedColorFlow.collectAsState(initial = 0xFF4FC3F7.toInt())
    val useWallpaper by themePreferences.useWallpaperFlow.collectAsState(initial = false)
    val currentFont by themePreferences.fontFlow.collectAsState(initial = ThemePreferences.AppFont.DEFAULT)
    val customFontPath by themePreferences.customFontPathFlow.collectAsState(initial = "")
    val fontTimestamp by themePreferences.fontTimestampFlow.collectAsState(initial = 0L)
    val wallpaperPath by themePreferences.wallpaperPathFlow.collectAsState(initial = "")
    val wallpaperTimestamp by themePreferences.wallpaperTimestampFlow.collectAsState(initial = 0L)
    val wallpaperBackgroundAlpha by themePreferences.wallpaperBackgroundAlphaFlow.collectAsState(initial = 0.55f)
    val scope = rememberCoroutineScope()
    
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.displayCutout.union(WindowInsets.statusBars),
                title = { Text("テーマ設定", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (showBackIcon) {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                        }
                    }
                },
                colors = appTopBarColors(),
                scrollBehavior = scrollBehavior
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(modifier = Modifier.height(0.dp))

            SettingsSectionHeader("外観")

            OutlinedCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "フォント設定",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    FlowRow(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        data class FontOption(val font: ThemePreferences.AppFont, val label: String, val icon: @Composable () -> Unit)
                        val options = listOf(
                            FontOption(ThemePreferences.AppFont.DEFAULT, "デフォルト") { Icon(Icons.Outlined.TextFields, null, modifier = Modifier.size(18.dp)) },
                            FontOption(ThemePreferences.AppFont.SERIF, "明朝体") { Icon(Icons.Outlined.FormatSize, null, modifier = Modifier.size(18.dp)) },
                            FontOption(ThemePreferences.AppFont.SANS_SERIF, "ゴシック体") { Icon(Icons.Outlined.TextFormat, null, modifier = Modifier.size(18.dp)) },
                            FontOption(ThemePreferences.AppFont.MONOSPACE, "等幅") { Icon(Icons.Outlined.Terminal, null, modifier = Modifier.size(18.dp)) },
                            FontOption(ThemePreferences.AppFont.CURSIVE, "筆記体") { Icon(Icons.Outlined.Gesture, null, modifier = Modifier.size(18.dp)) },
                            FontOption(ThemePreferences.AppFont.CUSTOM, "カスタム") { Icon(Icons.Outlined.FontDownload, null, modifier = Modifier.size(18.dp)) },
                        )
                        options.forEach { option ->
                            FilterChip(
                                selected = currentFont == option.font,
                                onClick = { scope.launch { themePreferences.setFont(option.font) } },
                                label = {
                                    Text(
                                        text = option.label,
                                        style = androidx.compose.ui.text.TextStyle(
                                            fontFamily = when(option.font) {
                                                ThemePreferences.AppFont.SERIF -> androidx.compose.ui.text.font.FontFamily.Serif
                                                ThemePreferences.AppFont.SANS_SERIF -> androidx.compose.ui.text.font.FontFamily.SansSerif
                                                ThemePreferences.AppFont.MONOSPACE -> androidx.compose.ui.text.font.FontFamily.Monospace
                                                ThemePreferences.AppFont.CURSIVE -> androidx.compose.ui.text.font.FontFamily.Cursive
                                                ThemePreferences.AppFont.CUSTOM -> {
                                                    if (customFontPath.isNotEmpty()) {
                                                        try {
                                                            val file = java.io.File(customFontPath)
                                                            if (file.exists()) {
                                                                androidx.compose.ui.text.font.FontFamily(android.graphics.Typeface.createFromFile(file))
                                                            } else androidx.compose.ui.text.font.FontFamily.Default
                                                        } catch (e: Exception) {
                                                            androidx.compose.ui.text.font.FontFamily.Default
                                                        }
                                                    } else androidx.compose.ui.text.font.FontFamily.Default
                                                }
                                                else -> androidx.compose.ui.text.font.FontFamily.Default
                                            }
                                        )
                                    )
                                },
                                leadingIcon = option.icon
                            )
                        }
                    }

                AnimatedVisibility(
                    visible = currentFont == ThemePreferences.AppFont.CUSTOM,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    val fontLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
                    ) { uri: android.net.Uri? ->
                        if (uri != null) {
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                try {
                                    val destFile = java.io.File(context.filesDir, "custom_font.ttf")
                                    context.contentResolver.openInputStream(uri)?.use { input ->
                                        destFile.outputStream().use { output ->
                                            input.copyTo(output)
                                        }
                                    }
                                    themePreferences.setCustomFontPath(destFile.absolutePath)
                                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        android.widget.Toast.makeText(context, "フォントを設定しました", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        android.widget.Toast.makeText(context, "フォントの設定に失敗しました: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                    }

                    Column {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        ListItem(
                            leadingContent = {
                                SettingsIconBadge(icon = Icons.Outlined.FontDownload)
                            },
                            headlineContent = { Text("カスタムフォントファイルを選択", fontWeight = FontWeight.Medium) },
                            supportingContent = {
                                Text(
                                    text = if (customFontPath.isNotEmpty()) "フォントファイルをアップロード済み" else "ファイルが選択されていません (.ttf または .otf)",
                                    color = if (customFontPath.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            modifier = Modifier.clickable { fontLauncher.launch("*/*") },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // ── Wallpaper Toggle & Picker ──
                SettingsSwitchItem(
                    icon = Icons.Outlined.Wallpaper,
                    title = "カスタム壁紙",
                    subtitle = "アプリの背景にオリジナルの壁紙を適用します",
                    checked = useWallpaper,
                    onCheckedChange = { scope.launch { themePreferences.setUseWallpaper(it) } }
                )

                AnimatedVisibility(
                    visible = useWallpaper,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    val wallpaperLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
                    ) { uri: android.net.Uri? ->
                        if (uri != null) {
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                try {
                                    val destFile = java.io.File(context.filesDir, "wallpaper.jpg")
                                    val success = com.webclass.plus.util.ImageUtils.saveResizedImage(context, uri, destFile)
                                    if (success) {
                                        themePreferences.setWallpaperPath(destFile.absolutePath)
                                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                                            android.widget.Toast.makeText(context, "壁紙を設定しました", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        throw Exception("画像の縮小保存に失敗しました")
                                    }
                                } catch (e: Exception) {
                                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        android.widget.Toast.makeText(context, "壁紙の設定に失敗しました: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                    }

                    Column {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        ListItem(
                            leadingContent = {
                                SettingsIconBadge(icon = Icons.Outlined.Image)
                            },
                            headlineContent = { Text("壁紙画像を選択", fontWeight = FontWeight.Medium) },
                            supportingContent = {
                                Text(
                                    text = if (wallpaperPath.isNotEmpty()) "画像をアップロード済み" else "画像が選択されていません",
                                    color = if (wallpaperPath.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            trailingContent = {
                                if (wallpaperPath.isNotEmpty()) {
                                    val previewBitmap = remember(wallpaperTimestamp) {
                                        try {
                                            val file = java.io.File(wallpaperPath)
                                            if (file.exists()) {
                                                com.webclass.plus.util.ImageUtils.decodeSampledBitmapFromFile(file.absolutePath, 150, 150)?.asImageBitmap()
                                            } else null
                                        } catch (e: Exception) { null }
                                    }
                                    if (previewBitmap != null) {
                                        Image(
                                            bitmap = previewBitmap,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(MaterialTheme.shapes.small)
                                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.clickable { wallpaperLauncher.launch("image/*") },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                        
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        
                        // 背景の透明度調整スライダー
                        val transparencyPercent = ((1f - wallpaperBackgroundAlpha) * 100).toInt().coerceIn(0, 100)

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    SettingsIconBadge(icon = Icons.Outlined.Opacity)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "背景の透明度",
                                            fontWeight = FontWeight.Medium,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "値を大きくすると壁紙が見やすくなります",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Text(
                                    text = "$transparencyPercent%",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Slider(
                                value = transparencyPercent.toFloat(),
                                onValueChange = { percent ->
                                    val alpha = (100f - percent) / 100f
                                    scope.launch { themePreferences.setWallpaperBackgroundAlpha(alpha) }
                                },
                                valueRange = 0f..100f,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                        }
                    }
                }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            SettingsSectionHeader("テーマ設定")

            OutlinedCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "テーマのタイプ",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    FlowRow(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        data class ThemeOption(val theme: AppTheme, val label: String, val icon: @Composable () -> Unit)
                        val options = listOf(
                            ThemeOption(AppTheme.SYSTEM, "システム") { Icon(Icons.Outlined.BrightnessAuto, null, modifier = Modifier.size(18.dp)) },
                            ThemeOption(AppTheme.LIGHT, "ライト") { Icon(Icons.Outlined.LightMode, null, modifier = Modifier.size(18.dp)) },
                            ThemeOption(AppTheme.DARK, "ダーク") { Icon(Icons.Outlined.DarkMode, null, modifier = Modifier.size(18.dp)) },
                            ThemeOption(AppTheme.PASTEL, "パステル") { Icon(Icons.Outlined.FilterVintage, null, modifier = Modifier.size(18.dp)) },
                            ThemeOption(AppTheme.COUNTRY, "カントリー") { Icon(Icons.Outlined.Park, null, modifier = Modifier.size(18.dp)) },
                            ThemeOption(AppTheme.CUSTOM, "カスタム") { Icon(Icons.Outlined.Tune, null, modifier = Modifier.size(18.dp)) },
                        )
                        options.forEach { option ->
                            val isChipEnabled = when (themeSource) {
                                ThemeSource.COLOR_SEED, ThemeSource.DYNAMIC_COLOR -> {
                                    option.theme != AppTheme.PASTEL &&
                                    option.theme != AppTheme.COUNTRY &&
                                    option.theme != AppTheme.CUSTOM
                                }
                                ThemeSource.WALLPAPER -> {
                                    false
                                }
                                else -> true
                            }
                            FilterChip(
                                selected = currentTheme == option.theme,
                                onClick = { scope.launch { themePreferences.setTheme(option.theme) } },
                                label = { Text(option.label) },
                                leadingIcon = option.icon,
                                enabled = isChipEnabled
                            )
                        }
                    }

                    val showColorSources = (currentTheme == AppTheme.SYSTEM || currentTheme == AppTheme.LIGHT || currentTheme == AppTheme.DARK) ||
                            (themeSource == ThemeSource.WALLPAPER)

                    AnimatedVisibility(
                        visible = showColorSources,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Column {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                            
                            Text(
                                text = "カラーソース (オプション)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                            )

                            ThemeSourceOption(
                                title = "なし",
                                subtitle = "選択中のテーマカラーをそのまま使用します",
                                icon = Icons.Outlined.ColorLens,
                                selected = themeSource == ThemeSource.NONE,
                                onClick = { scope.launch { themePreferences.setThemeSource(ThemeSource.NONE) } }
                            )

                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                ThemeSourceOption(
                                    title = "ダイナミックカラー",
                                    subtitle = "壁紙の色に合わせてテーマカラーを変更します（Android 12+）",
                                    icon = Icons.Outlined.AutoAwesome,
                                    selected = themeSource == ThemeSource.DYNAMIC_COLOR,
                                    onClick = { scope.launch { themePreferences.setThemeSource(ThemeSource.DYNAMIC_COLOR) } }
                                )
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            }

                            ThemeSourceOption(
                                title = "壁紙からテーマを適用",
                                subtitle = if (useWallpaper)
                                    "壁紙から色を抽出してアプリのテーマに反映します"
                                else
                                    "有効にするには「カスタム壁紙」をオンにしてください",
                                icon = Icons.Outlined.Wallpaper,
                                selected = themeSource == ThemeSource.WALLPAPER,
                                enabled = useWallpaper,
                                onClick = { scope.launch { themePreferences.setThemeSource(ThemeSource.WALLPAPER) } }
                            )

                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                            ThemeSourceOption(
                                title = "色からテーマを作成",
                                subtitle = "一色を選ぶだけでアプリ全体のテーマを自動生成します",
                                icon = Icons.Outlined.Palette,
                                selected = themeSource == ThemeSource.COLOR_SEED,
                                onClick = { scope.launch { themePreferences.setThemeSource(ThemeSource.COLOR_SEED) } }
                            )

                            AnimatedVisibility(
                                visible = themeSource == ThemeSource.COLOR_SEED,
                                enter = expandVertically(),
                                exit = shrinkVertically()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(Color(seedColor))
                                                .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                        )
                                        Column {
                                            Text(
                                                "選択中のカスタムカラー",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                "#%06X".format(seedColor and 0xFFFFFF),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }

                                    Text(
                                        "おすすめカラー",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState())
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        seedPresetColors.forEach { (argb, label) ->
                                            val isSelected = seedColor == argb
                                            Surface(
                                                shape = CircleShape,
                                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                                                border = BorderStroke(
                                                    width = 1.dp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                                ),
                                                onClick = {
                                                    scope.launch { themePreferences.setSeedColor(argb) }
                                                },
                                                modifier = Modifier.height(36.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(16.dp)
                                                            .clip(CircleShape)
                                                            .background(Color(argb))
                                                    )
                                                    Text(
                                                        text = label,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Text(
                                        "カスタムカラー調整 (HSV)",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    
                                    val hsv = remember(seedColor) {
                                        val arr = FloatArray(3)
                                        android.graphics.Color.colorToHSV(seedColor, arr)
                                        arr
                                    }
                                    var hue by remember(seedColor) { mutableStateOf(hsv[0]) }
                                    var sat by remember(seedColor) { mutableStateOf(hsv[1]) }
                                    var value by remember(seedColor) { mutableStateOf(hsv[2]) }

                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Column {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("色相 (Hue)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text("${hue.toInt()}°", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                            }
                                            Box(
                                                modifier = Modifier.fillMaxWidth().height(36.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(10.dp)
                                                        .background(
                                                            brush = Brush.horizontalGradient(
                                                                listOf(
                                                                    Color.Red, Color.Yellow, Color.Green, Color.Cyan,
                                                                    Color.Blue, Color.Magenta, Color.Red
                                                                )
                                                            ),
                                                            shape = RoundedCornerShape(5.dp)
                                                        )
                                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(5.dp))
                                                )
                                                Slider(
                                                    value = hue,
                                                    onValueChange = { h ->
                                                        hue = h
                                                        val newColor = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))
                                                        scope.launch { themePreferences.setSeedColor(newColor) }
                                                    },
                                                    valueRange = 0f..360f,
                                                    colors = SliderDefaults.colors(
                                                        activeTrackColor = Color.Transparent,
                                                        inactiveTrackColor = Color.Transparent,
                                                        thumbColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
                                                    ),
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }

                                        Column {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("鮮やかさ (Saturation)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text("${(sat * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                            }
                                            val satBrush = remember(hue, value) {
                                                Brush.horizontalGradient(
                                                    listOf(
                                                        Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0f, value))),
                                                        Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, value)))
                                                    )
                                                )
                                            }
                                            Box(
                                                modifier = Modifier.fillMaxWidth().height(36.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(10.dp)
                                                        .background(satBrush, shape = RoundedCornerShape(5.dp))
                                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(5.dp))
                                                )
                                                Slider(
                                                    value = sat,
                                                    onValueChange = { s ->
                                                        sat = s
                                                        val newColor = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))
                                                        scope.launch { themePreferences.setSeedColor(newColor) }
                                                    },
                                                    valueRange = 0f..1f,
                                                    colors = SliderDefaults.colors(
                                                        activeTrackColor = Color.Transparent,
                                                        inactiveTrackColor = Color.Transparent,
                                                        thumbColor = Color(seedColor)
                                                    ),
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }

                                        Column {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("明るさ (Brightness)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text("${(value * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                            }
                                            val valBrush = remember(hue, sat) {
                                                Brush.horizontalGradient(
                                                    listOf(
                                                        Color.Black,
                                                        Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, 1f)))
                                                    )
                                                )
                                            }
                                            Box(
                                                modifier = Modifier.fillMaxWidth().height(36.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(10.dp)
                                                        .background(valBrush, shape = RoundedCornerShape(5.dp))
                                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(5.dp))
                                                )
                                                Slider(
                                                    value = value,
                                                    onValueChange = { v ->
                                                        value = v
                                                        val newColor = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))
                                                        scope.launch { themePreferences.setSeedColor(newColor) }
                                                    },
                                                    valueRange = 0f..1f,
                                                    colors = SliderDefaults.colors(
                                                        activeTrackColor = Color.Transparent,
                                                        inactiveTrackColor = Color.Transparent,
                                                        thumbColor = Color(seedColor)
                                                    ),
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }
                                    }

                                    SeedColorHexInput(
                                        currentSeedColor = seedColor,
                                        onColorConfirmed = { color ->
                                            scope.launch { themePreferences.setSeedColor(color) }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    val showCustomEditorButton = currentTheme == AppTheme.CUSTOM && themeSource != ThemeSource.COLOR_SEED && themeSource != ThemeSource.WALLPAPER

                    AnimatedVisibility(
                        visible = showCustomEditorButton,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                            OutlinedButton(
                                onClick = { onAppThemeEditorClick() },
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("カラーテーマを編集")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ThemeSourceOption(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val contentAlpha = if (enabled) 1f else 0.38f
    ListItem(
        leadingContent = {
            Box(modifier = Modifier.alpha(contentAlpha)) {
                SettingsIconBadge(icon = icon)
            }
        },
        headlineContent = {
            Text(
                title,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
            )
        },
        supportingContent = {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
            )
        },
        trailingContent = {
            RadioButton(selected = selected, onClick = null, enabled = enabled)
        },
        modifier = if (enabled) Modifier.clickable(onClick = onClick) else Modifier,
        colors = ListItemDefaults.colors(containerColor = if (selected && enabled)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        else Color.Transparent)
    )
}

@Composable
fun SeedColorHexInput(
    currentSeedColor: Int,
    onColorConfirmed: (Int) -> Unit
) {
    var hexText by remember(currentSeedColor) {
        mutableStateOf("#%06X".format(currentSeedColor and 0xFFFFFF))
    }
    var isError by remember { mutableStateOf(false) }

    val parsed = remember(hexText) {
        val clean = hexText.trimStart('#')
        if (clean.length == 6) {
            try {
                val argb = (0xFF000000.toInt()) or clean.toInt(16)
                argb to false
            } catch (_: NumberFormatException) { null to true }
        } else null to (clean.isNotEmpty() && clean.length != 6)
    }
    isError = parsed.second == true

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = hexText,
            onValueChange = { v ->
                val clean = v.trimStart('#').take(6).uppercase()
                hexText = "#$clean"
            },
            label = { Text("HEXカラーコード") },
            placeholder = { Text("#4FC3F7") },
            isError = isError,
            singleLine = true,
            modifier = Modifier.weight(1f),
            leadingIcon = {
                val previewColor = parsed.first?.let { Color(it) } ?: Color(currentSeedColor)
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(previewColor)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                )
            },
            supportingText = if (isError) {
                { Text("有効な6桁のHEXコードを入力してください") }
            } else null
        )
        FilledTonalButton(
            onClick = {
                parsed.first?.let { onColorConfirmed(it) }
            },
            enabled = !isError && parsed.first != null,
        ) {
            Text("適用")
        }
    }
}
