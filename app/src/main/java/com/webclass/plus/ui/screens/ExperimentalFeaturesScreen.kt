package com.webclass.plus.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.webclass.plus.util.ThemePreferences
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExperimentalFeaturesScreen(
    onWebThemeEditorClick: () -> Unit = {},
    showBackIcon: Boolean = true,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val themePreferences = remember { ThemePreferences(context) }
    val scope = rememberCoroutineScope()
    
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.displayCutout.union(WindowInsets.statusBars),
                title = { Text("試験的機能", fontWeight = FontWeight.Bold) },
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
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(modifier = Modifier.height(0.dp))

            // Info banner
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                ),
                shape = MaterialTheme.shapes.large
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Outlined.Science,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            "実験的な機能",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "これらの機能は開発中であり、将来変更または削除される可能性があります。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // ── Section: UI ──
            SettingsSectionHeader("インターフェース")

            OutlinedCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = MaterialTheme.shapes.large
            ) {
                val hideSplashScreen by themePreferences.hideSplashScreenFlow.collectAsState(initial = false)

                SettingsSwitchItem(
                    icon = Icons.Outlined.SkipNext,
                    title = "スプラッシュスクリーンを非表示",
                    subtitle = "起動時のアニメーションを省略します",
                    checked = hideSplashScreen,
                    onCheckedChange = { scope.launch { themePreferences.setHideSplashScreen(it) } }
                )
                
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                val enableHyperBootAnimation by themePreferences.enableHyperBootAnimationFlow.collectAsState(initial = false)

                SettingsSwitchItem(
                    icon = Icons.Outlined.AutoAwesome,
                    title = "ハイパー起動アニメーション",
                    subtitle = "起動アニメーションを全く違う、派手でおしゃれなものにします",
                    checked = enableHyperBootAnimation,
                    onCheckedChange = { scope.launch { themePreferences.setEnableHyperBootAnimation(it) } }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                val enableLightweightMode by themePreferences.enableLightweightModeFlow.collectAsState(initial = false)

                SettingsSwitchItem(
                    icon = Icons.Outlined.Speed,
                    title = "軽量モード",
                    subtitle = "有効にすると、すべてのアニメーション（画面遷移、起動時の演出など）を無効化します",
                    checked = enableLightweightMode,
                    onCheckedChange = { scope.launch { themePreferences.setEnableLightweightMode(it) } }
                )

            }

            // ── Section: Features ──
            SettingsSectionHeader("機能拡張")

            OutlinedCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = MaterialTheme.shapes.large
            ) {
                val enableSpeedUp by themePreferences.enableSpeedUpFlow.collectAsState(initial = false)

                SettingsSwitchItem(
                    icon = Icons.Outlined.FlashOn,
                    title = "高速化 (通信)",
                    subtitle = "WebViewを使用せず、直接URLをフェッチして通信を高速化します",
                    checked = enableSpeedUp,
                    onCheckedChange = { scope.launch { themePreferences.setEnableSpeedUp(it) } }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                val enableBookmarks by themePreferences.enableBookmarksFlow.collectAsState(initial = false)

                SettingsSwitchItem(
                    icon = Icons.Outlined.BookmarkBorder,
                    title = "ブックマーク機能",
                    subtitle = "コースをブックマークして素早くアクセスします",
                    checked = enableBookmarks,
                    onCheckedChange = { scope.launch { themePreferences.setEnableBookmarks(it) } }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                val enableThemeEditor by themePreferences.enableThemeEditorFlow.collectAsState(initial = false)

                SettingsSwitchItem(
                    icon = Icons.Outlined.Code,
                    title = "Webテーマエディタ (CSS)",
                    subtitle = "WebClassにカスタムCSSを適用します",
                    checked = enableThemeEditor,
                    onCheckedChange = { scope.launch { themePreferences.setEnableThemeEditor(it) } }
                )

                if (enableThemeEditor) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ListItem(
                        leadingContent = { SettingsIconBadge(icon = Icons.Default.Edit) },
                        headlineContent = { Text("テーマを編集", fontWeight = FontWeight.Medium) },
                        supportingContent = { Text("CSSエディタを開く", color = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.clickable { onWebThemeEditorClick() },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                    )
                }

            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
