package com.webclass.plus.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.webclass.plus.data.repository.UserDataRepository
import com.webclass.plus.util.HtmlFetcher
import com.webclass.plus.util.UpdateChecker
import org.jsoup.Jsoup

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToThemeSettings: () -> Unit,
    onNavigateToInfo: () -> Unit,
    onNavigateToWidgetSettings: () -> Unit,
    onNavigateToDeveloperOptions: () -> Unit,
    onNavigateToExperimentalFeatures: () -> Unit,
    onWebThemeEditorClick: () -> Unit = {},
    onAppThemeEditorClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val userDataRepository = remember { UserDataRepository(context) }
    val developerPreferences = remember { com.webclass.plus.util.DeveloperPreferences(context) }
    val themePreferences = remember { com.webclass.plus.util.ThemePreferences(context) }
    val useWallpaper by themePreferences.useWallpaperFlow.collectAsState(initial = false)
    val isMockMode by developerPreferences.mockModeEnabledFlow.collectAsState(initial = false)
    val forceTabletMode by developerPreferences.forceTabletModeFlow.collectAsState(initial = false)
    val forceSmartphoneMode by developerPreferences.forceSmartphoneModeFlow.collectAsState(initial = false)

    val realUserName by userDataRepository.userName.collectAsState(initial = "WebClass ユーザー")
    val realEmail by userDataRepository.email.collectAsState(initial = "")
    val userName = if (isMockMode) "Mock User" else realUserName
    val email = if (isMockMode) "mock@example.com" else realEmail

    LaunchedEffect(isMockMode) {
        if (!isMockMode) {
            HtmlFetcher.fetchHtml(WEBCLASS_ACCOUNT_CONFIG_URL)?.let { html ->
                val fetchedEmail = Jsoup.parse(html)
                    .selectFirst("input[name=email]")
                    ?.attr("value")
                    ?.trim()
                    .orEmpty()
                if (fetchedEmail.isNotEmpty()) {
                    userDataRepository.setEmail(fetchedEmail)
                }
            }
        }
    }

    var showLogoutDialog by remember { mutableStateOf(false) }
    var showCacheClearDialog by remember { mutableStateOf(false) }

    var selectedDetailRoute by remember { mutableStateOf("settings") }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isWideScreen = when {
        forceTabletMode -> true
        forceSmartphoneMode -> false
        else -> configuration.screenWidthDp >= 600
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            if (!isWideScreen) {
                TopAppBar(
                    title = { Text("マイページ", fontWeight = FontWeight.Bold) },
                    colors = appTopBarColors(),
                    scrollBehavior = scrollBehavior
                )
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
    ) { padding ->
        val topPadding = if (isWideScreen) {
            WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        } else {
            padding.calculateTopPadding()
        }

        if (isWideScreen) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // ─── Left Pane (Master Menu List) ───
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(top = topPadding, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    // ─── Hero Profile Section ───
                    ProfileHeroSection(userName = userName, email = email)

                    Spacer(modifier = Modifier.height(24.dp))

                    // ─── Quick Status Row ───
                    QuickStatusRow(isMockMode = isMockMode, useWallpaper = useWallpaper)

                    Spacer(modifier = Modifier.height(24.dp))

                    // ─── Account Menu Group ───
                    MenuSectionHeader(title = "アカウント")
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        MenuRow(
                            icon = Icons.Outlined.Delete,
                            iconTint = MaterialTheme.colorScheme.secondary,
                            title = "キャッシュをクリア",
                            subtitle = "一時ファイルを削除して動作を軽くする",
                            onClick = { showCacheClearDialog = true }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        MenuRow(
                            icon = Icons.AutoMirrored.Filled.Logout,
                            iconTint = MaterialTheme.colorScheme.error,
                            title = "ログアウト",
                            subtitle = "ログイン情報をリセットする",
                            onClick = { showLogoutDialog = true }
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // ─── General Menu Group ───
                    MenuSectionHeader(title = "一般")
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        MenuRow(
                            icon = Icons.Outlined.Palette,
                            iconTint = MaterialTheme.colorScheme.primary,
                            title = "テーマ",
                            subtitle = "アプリの配色やダークモード",
                            isSelected = selectedDetailRoute == "theme_settings",
                            onClick = { selectedDetailRoute = "theme_settings" }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        MenuRow(
                            icon = Icons.Default.Settings,
                            iconTint = MaterialTheme.colorScheme.secondary,
                            title = "設定",
                            subtitle = "表示・動画プレイヤー・ブラウザの設定",
                            isSelected = selectedDetailRoute == "settings",
                            onClick = { selectedDetailRoute = "settings" }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        MenuRow(
                            icon = Icons.Default.Widgets,
                            iconTint = MaterialTheme.colorScheme.tertiary,
                            title = "ウィジェット",
                            subtitle = "バス時刻表をホーム画面に追加",
                            isSelected = selectedDetailRoute == "widget_settings",
                            onClick = { selectedDetailRoute = "widget_settings" }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        MenuRow(
                            icon = Icons.Default.Info,
                            iconTint = MaterialTheme.colorScheme.secondary,
                            title = "情報",
                            subtitle = "バージョン・ライセンス・更新履歴",
                            isSelected = selectedDetailRoute == "info",
                            onClick = { selectedDetailRoute = "info" }
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // ─── Advanced Menu Group ───
                    MenuSectionHeader(title = "高度な設定")
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        MenuRow(
                            icon = Icons.Default.Science,
                            iconTint = MaterialTheme.colorScheme.error,
                            title = "試験的機能",
                            subtitle = "開発中の新機能をいち早く試す",
                            isSelected = selectedDetailRoute == "experimental_features",
                            onClick = { selectedDetailRoute = "experimental_features" }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        MenuRow(
                            icon = Icons.Filled.Code,
                            iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                            title = "開発者向けオプション",
                            subtitle = "モックモード・デバッグ設定",
                            isSelected = selectedDetailRoute == "developer_options",
                            onClick = { selectedDetailRoute = "developer_options" }
                        )
                    }
                }

                VerticalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxHeight()
                )

                // ─── Right Pane (Detail Viewer) ───
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    when (selectedDetailRoute) {
                        "theme_settings" -> {
                            ThemeSettingsScreen(
                                onAppThemeEditorClick = onAppThemeEditorClick,
                                showBackIcon = false,
                                onBackClick = {}
                            )
                        }
                        "settings" -> {
                            SettingsScreen(
                                onWebThemeEditorClick = onWebThemeEditorClick,
                                showBackIcon = false,
                                onBackClick = {}
                            )
                        }
                        "widget_settings" -> {
                            WidgetSettingsScreen(
                                showBackIcon = false,
                                onBackClick = {}
                            )
                        }
                        "info" -> {
                            AppInfoScreen(
                                showBackIcon = false,
                                onBackClick = {}
                            )
                        }
                        "developer_options" -> {
                            DeveloperOptionsScreen(
                                showBackIcon = false,
                                onBackClick = {}
                            )
                        }
                        "experimental_features" -> {
                            ExperimentalFeaturesScreen(
                                onWebThemeEditorClick = onWebThemeEditorClick,
                                showBackIcon = false,
                                onBackClick = {}
                            )
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topPadding)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // ─── Hero Profile Section ───
                ProfileHeroSection(userName = userName, email = email)

                Spacer(modifier = Modifier.height(24.dp))

                // ─── Quick Status Row ───
                QuickStatusRow(isMockMode = isMockMode, useWallpaper = useWallpaper)

                Spacer(modifier = Modifier.height(24.dp))

                // ─── Account Menu Group ───
                MenuSectionHeader(title = "アカウント")
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    MenuRow(
                        icon = Icons.Outlined.Delete,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        title = "キャッシュをクリア",
                        subtitle = "一時ファイルを削除して動作を軽くする",
                        onClick = { showCacheClearDialog = true }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    MenuRow(
                        icon = Icons.AutoMirrored.Filled.Logout,
                        iconTint = MaterialTheme.colorScheme.error,
                        title = "ログアウト",
                        subtitle = "ログイン情報をリセットする",
                        onClick = { showLogoutDialog = true }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ─── General Menu Group ───
                MenuSectionHeader(title = "一般")
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    MenuRow(
                        icon = Icons.Outlined.Palette,
                        iconTint = MaterialTheme.colorScheme.primary,
                        title = "テーマ",
                        subtitle = "アプリの配色やダークモード",
                        onClick = onNavigateToThemeSettings
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    MenuRow(
                        icon = Icons.Default.Settings,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        title = "設定",
                        subtitle = "表示・動画プレイヤー・ブラウザの設定",
                        onClick = onNavigateToSettings
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    MenuRow(
                        icon = Icons.Default.Widgets,
                        iconTint = MaterialTheme.colorScheme.tertiary,
                        title = "ウィジェット",
                        subtitle = "バス時刻表をホーム画面に追加",
                        onClick = onNavigateToWidgetSettings
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    MenuRow(
                        icon = Icons.Default.Info,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        title = "情報",
                        subtitle = "バージョン・ライセンス・更新履歴",
                        onClick = onNavigateToInfo
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ─── Advanced Menu Group ───
                MenuSectionHeader(title = "高度な設定")
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    MenuRow(
                        icon = Icons.Default.Science,
                        iconTint = MaterialTheme.colorScheme.error,
                        title = "試験的機能",
                        subtitle = "開発中の新機能をいち早く試す",
                        onClick = onNavigateToExperimentalFeatures
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    MenuRow(
                        icon = Icons.Filled.Code,
                        iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                        title = "開発者向けオプション",
                        subtitle = "モックモード・デバッグ設定",
                        onClick = onNavigateToDeveloperOptions
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        if (showCacheClearDialog) {
            AlertDialog(
                onDismissRequest = { showCacheClearDialog = false },
                icon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) },
                title = { Text("キャッシュをクリアしますか？") },
                text = { Text("WebClassのページ表示や内部ブラウザが蓄積した一時データを削除します。ログイン状態や設定などは保持されます。") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showCacheClearDialog = false
                            clearAppCache(context)
                            android.widget.Toast.makeText(context, "キャッシュをクリアしました", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text("クリア", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCacheClearDialog = false }) {
                        Text("キャンセル")
                    }
                }
            )
        }

        if (showLogoutDialog) {
            AlertDialog(
                onDismissRequest = { showLogoutDialog = false },
                icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                title = { Text("本当にログアウトしますか？") },
                text = { Text("ログアウトするとログイン情報がリセットされ、次回開く際に再ログインが必要になります。") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showLogoutDialog = false
                            performLogout(context)
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("ログアウト", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLogoutDialog = false }) {
                        Text("キャンセル")
                    }
                }
            )
        }
    }
}

// ─── Hero Profile Section ───
@Composable
private fun ProfileHeroSection(userName: String, email: String) {
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val secondaryContainer = MaterialTheme.colorScheme.secondaryContainer

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Avatar circle
            Surface(
                modifier = Modifier.size(96.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 4.dp,
                shadowElevation = 8.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Name
            Text(
                text = userName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (email.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Email,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = email,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

        }
    }
}

private const val WEBCLASS_ACCOUNT_CONFIG_URL =
    "https://webclass.kosen-k.go.jp/webclass/user.php/config?acs_=a0008c6c"

// ─── Quick Status Row ───
@Composable
private fun QuickStatusRow(isMockMode: Boolean, useWallpaper: Boolean) {
    val ipAddress = produceState(initialValue = "…", key1 = isMockMode) {
        if (isMockMode) {
            value = "127.0.0.1"
        } else {
            value = com.webclass.plus.util.NetworkUtils.getIpAddress()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // IP Address chip
        StatusChip(
            icon = Icons.Default.Public,
            label = "IPアドレス",
            value = ipAddress.value,
            useWallpaper = useWallpaper,
            modifier = Modifier.weight(1f)
        )

        // Version chip
        StatusChip(
            icon = Icons.Outlined.Speed,
            label = "バージョン",
            value = "v${UpdateChecker.CURRENT_VERSION}",
            useWallpaper = useWallpaper,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatusChip(
    icon: ImageVector,
    label: String,
    value: String,
    useWallpaper: Boolean,
    modifier: Modifier = Modifier
) {
    val containerColor = if (useWallpaper) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        tonalElevation = if (useWallpaper) 0.dp else 1.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ─── Section Header ───
@Composable
private fun MenuSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 28.dp, bottom = 8.dp)
    )
}

// ─── Menu Row ───
@Composable
private fun MenuRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                text = title,
                fontWeight = FontWeight.Medium
            )
        },
        supportingContent = {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingContent = {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = MaterialTheme.shapes.small,
                color = iconTint.copy(alpha = 0.12f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = iconTint
                    )
                }
            }
        },
        trailingContent = {
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = ListItemDefaults.colors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            } else {
                Color.Transparent
            }
        )
    )
}

// ─── Helper Functions ───
private fun clearAppCache(context: android.content.Context) {
    // WebStorage caches (like IndexedDB, localStorage used by WebView)
    android.webkit.WebStorage.getInstance().deleteAllData()
    // Standard WebView cache
    android.webkit.WebView(context).clearCache(true)
}

private fun performLogout(context: android.content.Context) {
    // Clear cookies
    val cookieManager = android.webkit.CookieManager.getInstance()
    cookieManager.removeAllCookies(null)
    cookieManager.flush()

    // Clear WebView data
    clearAppCache(context)

    // Clear SharedPreferences user data
    val prefs = context.getSharedPreferences("user_data", android.content.Context.MODE_PRIVATE)
    prefs.edit().clear().apply()

    // Show toast
    android.widget.Toast.makeText(context, "ログアウトしました", android.widget.Toast.LENGTH_SHORT).show()
}


