package com.webclass.plus.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.BorderStroke
import com.webclass.plus.ui.theme.colorSchemeFromSeed
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.webclass.plus.ui.theme.AppTheme
import com.webclass.plus.util.ThemePreferences
import com.webclass.plus.util.ThemeSource
import kotlinx.coroutines.launch
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.alpha


// ─────────────────────────────────────────────────────────────────────────────
// Preset colors for seed color picker
// ─────────────────────────────────────────────────────────────────────────────
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
fun SettingsScreen(
    onWebThemeEditorClick: () -> Unit = {},
    showBackIcon: Boolean = true,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val themePreferences = remember { ThemePreferences(context) }
    val enableHyperAnimation by themePreferences.enableHyperAnimationFlow.collectAsState(initial = false)
    val hyperAnimationStrength by themePreferences.hyperAnimationStrengthFlow.collectAsState(initial = 1.0f)
    val enableWebViewDarkMode by themePreferences.enableWebViewDarkModeFlow.collectAsState(initial = true)
    val scope = rememberCoroutineScope()
    
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.displayCutout.union(WindowInsets.statusBars),
                title = { Text("設定", fontWeight = FontWeight.Bold) },
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

            // ── Section: 機能 ──
            SettingsSectionHeader("機能")

            OutlinedCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = MaterialTheme.shapes.large
            ) {
                val useBrowser by themePreferences.useBrowserFlow.collectAsState(initial = false)
                val hideAnnouncements by themePreferences.hideAnnouncementsFlow.collectAsState(initial = false)
                val hideSurveys by themePreferences.hideSurveysFlow.collectAsState(initial = false)
                val focusMode by themePreferences.focusModeFlow.collectAsState(initial = false)
                val enableNativeVideoPlayer by themePreferences.enableNativeVideoPlayerFlow.collectAsState(initial = true)
                val enableNativePdfViewer by themePreferences.enableNativePdfViewerFlow.collectAsState(initial = true)

                SettingsSwitchItem(
                    icon = Icons.Outlined.OpenInBrowser,
                    title = "ブラウザで開く",
                    subtitle = "コンテンツを外部ブラウザで開きます",
                    checked = useBrowser,
                    onCheckedChange = { scope.launch { themePreferences.setUseBrowser(it) } }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                SettingsSwitchItem(
                    icon = Icons.Outlined.NotificationsOff,
                    title = "管理者からのお知らせを非表示",
                    subtitle = "ホーム画面のお知らせカードを非表示にします",
                    checked = hideAnnouncements,
                    onCheckedChange = { scope.launch { themePreferences.setHideAnnouncements(it) } }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                SettingsSwitchItem(
                    icon = Icons.Outlined.CommentsDisabled,
                    title = "アンケートを非表示",
                    subtitle = "ホーム画面のアンケートカードを非表示にします",
                    checked = hideSurveys,
                    onCheckedChange = { scope.launch { themePreferences.setHideSurveys(it) } }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                SettingsSwitchItem(
                    icon = Icons.Outlined.CenterFocusStrong,
                    title = "フォーカスモード",
                    subtitle = "システムバーを非表示にし、学習に集中します",
                    checked = focusMode,
                    onCheckedChange = { scope.launch { themePreferences.setFocusMode(it) } }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                SettingsSwitchItem(
                    icon = Icons.Outlined.PlayCircle,
                    title = "動画プレイヤー",
                    subtitle = "教材内の動画をネイティブプレイヤーで再生します",
                    checked = enableNativeVideoPlayer,
                    onCheckedChange = { scope.launch { themePreferences.setEnableNativeVideoPlayer(it) } }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                SettingsSwitchItem(
                    icon = Icons.Outlined.PictureAsPdf,
                    title = "PDFビューワー",
                    subtitle = "教材内のPDFをネイティブプレイヤーで開きます",
                    checked = enableNativePdfViewer,
                    onCheckedChange = { scope.launch { themePreferences.setEnableNativePdfViewer(it) } }
                )
            }

            // ── Section: 外観 ──
            SettingsSectionHeader("外観")

            OutlinedCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = MaterialTheme.shapes.large
            ) {
                SettingsSwitchItem(
                    icon = Icons.Outlined.Language,
                    title = "Webページもダークモードにする",
                    subtitle = "アプリがダークモードのとき、WebClassなどのWebページも暗く表示します",
                    checked = enableWebViewDarkMode,
                    onCheckedChange = { scope.launch { themePreferences.setEnableWebViewDarkMode(it) } }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // ── Hyper Animation ──
                SettingsSwitchItem(
                    icon = Icons.Outlined.Animation,
                    title = "ハイパーアニメーション",
                    subtitle = "アプリ全体のアニメーションを非常に派手にします",
                    checked = enableHyperAnimation,
                    onCheckedChange = { scope.launch { themePreferences.setEnableHyperAnimation(it) } }
                )

                if (enableHyperAnimation) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "アニメーション強化度",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val percentage = (hyperAnimationStrength * 100).toInt()
                            val label = when {
                                hyperAnimationStrength <= 0.6f -> "控えめ ($percentage%)"
                                hyperAnimationStrength <= 1.1f -> "標準 ($percentage%)"
                                hyperAnimationStrength <= 1.6f -> "ダイナミック ($percentage%)"
                                else -> "超ハイパー ($percentage%)"
                            }
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Slider(
                            value = hyperAnimationStrength,
                            onValueChange = { scope.launch { themePreferences.setHyperAnimationStrength(it) } },
                            valueRange = 0.5f..2.0f,
                            steps = 5,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        val dynamicDampingRatio = (0.8f - hyperAnimationStrength * 0.4f).coerceIn(0.15f, 0.8f)
                        val dynamicStiffness = (200f / hyperAnimationStrength).coerceIn(50f, 800f)
                        
                        var isBouncing by remember { mutableStateOf(false) }
                        val previewOffset by androidx.compose.animation.core.animateFloatAsState(
                            targetValue = if (isBouncing) 1f else 0f,
                            animationSpec = androidx.compose.animation.core.spring(
                                dampingRatio = dynamicDampingRatio,
                                stiffness = dynamicStiffness
                            ),
                            label = "previewOffset"
                        )
                        
                        LaunchedEffect(hyperAnimationStrength) {
                            while (true) {
                                kotlinx.coroutines.delay(1800)
                                isBouncing = !isBouncing
                            }
                        }
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                                    shape = MaterialTheme.shapes.small
                                )
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f),
                                        shape = CircleShape
                                    )
                            )
                            
                            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                                val maxOffset = maxWidth - 32.dp
                                Box(
                                    modifier = Modifier
                                        .offset(x = maxOffset * previewOffset)
                                        .size(32.dp)
                                        .background(
                                            brush = Brush.linearGradient(
                                                colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
                                            ),
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Animation,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}



// ─────────────────────────────────────────────────────────────────────────────
// Reusable components
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SettingsSectionHeader(title: String) {
    Row(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(4.dp).height(18.dp)
        ) {}
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun SettingsIconBadge(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(
        modifier = Modifier.size(40.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon, null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun SettingsSwitchItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        leadingContent = { SettingsIconBadge(icon = icon) },
        headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
        supportingContent = { Text(subtitle) },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
        modifier = Modifier.clickable { onCheckedChange(!checked) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun ThemeOptionItem(
    name: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(name) },
        supportingContent = { Text(description) },
        leadingContent = {
             RadioButton(
                selected = isSelected,
                onClick = null
            )
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
