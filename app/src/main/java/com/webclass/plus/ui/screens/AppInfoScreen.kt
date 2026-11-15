package com.webclass.plus.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.webclass.plus.util.UpdateChecker
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppInfoScreen(
    showBackIcon: Boolean = true,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val officialPageUrl = "https://kosenplus-kisarazu.netlify.app/"
    
    // Update check state
    var updateInfo by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var hasChecked by remember { mutableStateOf(false) }
    var updateCheckFailed by remember { mutableStateOf(false) }
    var pendingInstall by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }
    var showThirdPartyNotices by remember { mutableStateOf(false) }
    
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.displayCutout.union(WindowInsets.statusBars),
                title = { Text("情報", fontWeight = FontWeight.Bold) },
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
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // ── Update Status ──
            item {
                Spacer(modifier = Modifier.height(0.dp))
            }

            item {
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    UpdateStatusCard(
                        updateInfo = updateInfo,
                        isChecking = isCheckingUpdate,
                        hasChecked = hasChecked,
                        checkFailed = updateCheckFailed,
                        onCheck = {
                            isCheckingUpdate = true
                            updateCheckFailed = false
                            scope.launch {
                                updateInfo = UpdateChecker.checkForUpdates()
                                updateCheckFailed = updateInfo == null
                                isCheckingUpdate = false
                                hasChecked = true
                            }
                        },
                        onUpdate = { pendingInstall = it }
                    )
                }
            }

            // ── App Info Section ──
            item { SettingsSectionHeader("アプリ情報") }

            item {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = MaterialTheme.shapes.large
                ) {
                    MetaListItem(
                        headline = "バージョン",
                        supporting = "${UpdateChecker.CURRENT_VERSION}",
                        leadingIcon = Icons.Outlined.Info
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    MetaListItem(
                        headline = "ホスト",
                        supporting = "webclass.kosen-k.go.jp",
                        leadingIcon = Icons.Outlined.Dns
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    MetaListItem(
                        headline = "開発者",
                        supporting = "稲穂",
                        leadingIcon = Icons.Outlined.Person
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    MetaListItem(
                        headline = "デバッガー",
                        supporting = "表記準備中",
                        leadingIcon = Icons.Outlined.BugReport
                    )
                }
            }

            // ── Data Sources ──
            item { SettingsSectionHeader("情報の取得元") }

            item {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = MaterialTheme.shapes.large
                ) {
                    DataSourceListItem(
                        name = "WebClass",
                        detail = "国立高等専門学校機構 WebClass",
                        url = "https://webclass.kosen-k.go.jp/"
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    DataSourceListItem(
                        name = "シラバス",
                        detail = "国立高等専門学校機構 Webシラバス",
                        url = "https://syllabus.kosen-k.go.jp/"
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    DataSourceListItem(
                        name = "どこトレ",
                        detail = "JR東日本 列車運行情報サービス",
                        url = "https://doko-train.jp/sp/"
                    )
                }
            }

            // ── Official Page ──
            item {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = MaterialTheme.shapes.large,
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(officialPageUrl))
                        context.startActivity(intent)
                    }
                ) {
                    ListItem(
                        leadingContent = { SettingsIconBadge(icon = Icons.Default.OpenInNew) },
                        headlineContent = { Text("公式ページ", fontWeight = FontWeight.Medium) },
                        supportingContent = { Text("最新情報やお知らせを確認") },
                        trailingContent = {
                            FilledTonalButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(officialPageUrl))
                                    context.startActivity(intent)
                                }
                            ) {
                                Text("開く")
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }

            // ── Update History ──
            item { SettingsSectionHeader("アップデート履歴") }
            
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    com.webclass.plus.data.VersionHistory.entries.forEach { entry ->
                        UpdateHistoryCard(entry.version, entry.date, entry.changes)
                    }
                }
            }

            // ── Open Source Licenses ──
            item { SettingsSectionHeader("オープンソースライセンス") }
            
            item {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Text(
                            text = "このアプリは以下のオープンソースソフトウェアを使用しています。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        LicenseListItem("AndroidX（Compose・CameraX・Media3を含む）", "Apache License 2.0", "The Android Open Source Project")
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        LicenseListItem("Kotlin・Kotlin Coroutines", "Apache License 2.0", "JetBrains s.r.o. and contributors")
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        LicenseListItem("Gson・Guava・AutoValue annotations", "Apache License 2.0", "Google LLC and contributors")
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        LicenseListItem("Okio", "Apache License 2.0", "Square, Inc. and contributors")
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        LicenseListItem("PDFBox-Android", "Apache License 2.0", "Tom Roush and Apache PDFBox contributors")
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        LicenseListItem("Dark Reader", "MIT License", "Copyright © 2026 Dark Reader Ltd.")
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        LicenseListItem("jsoup", "MIT License", "Copyright © 2009–2024 Jonathan Hedley")
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        LicenseListItem("Bouncy Castle", "MIT License", "Copyright © 2000–2021 The Legion of the Bouncy Castle Inc.")

                        OutlinedButton(
                            onClick = { showThirdPartyNotices = true },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text("ライセンス本文を表示")
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
            
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }

    pendingInstall?.let { update ->
        UpdateInstallDialog(update = update, onDismiss = { pendingInstall = null })
    }
    if (showThirdPartyNotices) {
        ThirdPartyNoticesDialog(onDismiss = { showThirdPartyNotices = false })
    }
}

@Composable
private fun ThirdPartyNoticesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val notices = remember {
        runCatching {
            context.assets.open("licenses/THIRD_PARTY_NOTICES.txt").bufferedReader().use { it.readText() } +
                "\n\n" + context.assets.open("licenses/APACHE-2.0.txt").bufferedReader().use { it.readText() }
        }.getOrElse { "ライセンス本文を読み込めませんでした。" }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("サードパーティーライセンス") },
        text = {
            Text(
                notices,
                modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                style = MaterialTheme.typography.bodySmall
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } }
    )
}

@Composable
private fun DataSourceListItem(name: String, detail: String, url: String) {
    val context = LocalContext.current
    ListItem(
        modifier = Modifier.clickable {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        },
        leadingContent = { SettingsIconBadge(icon = Icons.Outlined.Public) },
        headlineContent = { Text(name, fontWeight = FontWeight.Medium) },
        supportingContent = {
            Column {
                Text(detail)
                Text(
                    text = url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        trailingContent = { Icon(Icons.Default.OpenInNew, contentDescription = "開く") },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun UpdateStatusCard(
    updateInfo: UpdateChecker.UpdateInfo?,
    isChecking: Boolean,
    hasChecked: Boolean,
    checkFailed: Boolean,
    onCheck: () -> Unit,
    onUpdate: (UpdateChecker.UpdateInfo) -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = when {
                updateInfo?.isUpdateAvailable == true -> MaterialTheme.colorScheme.primaryContainer
                checkFailed -> MaterialTheme.colorScheme.errorContainer
                hasChecked && updateInfo != null -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surfaceContainerHigh
            }
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            if (isChecking) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("確認中...", style = MaterialTheme.typography.labelLarge)
                }
            } else if (updateInfo?.isUpdateAvailable == true) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(44.dp),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.SystemUpdate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "新しいバージョンが利用可能です",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "v${updateInfo.latestVersion}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { updateInfo?.let(onUpdate) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("アップデートする")
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(44.dp),
                            shape = MaterialTheme.shapes.medium,
                            color = if (hasChecked) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    when {
                                        checkFailed -> Icons.Outlined.ErrorOutline
                                        hasChecked -> Icons.Outlined.CheckCircle
                                        else -> Icons.Outlined.Refresh
                                    },
                                    contentDescription = null,
                                    tint = when {
                                        checkFailed -> MaterialTheme.colorScheme.error
                                        hasChecked -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                when {
                                    checkFailed -> "更新情報を確認できません"
                                    hasChecked -> "最新のバージョンです"
                                    else -> "アップデート確認"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            if (checkFailed) {
                                Text(
                                    "リポジトリの公開後に再度お試しください",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else if (hasChecked) {
                                Text(
                                    "最終確認: 今",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    FilledTonalButton(
                        onClick = onCheck,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("確認")
                    }
                }
            }
        }
    }
}

@Composable
fun MetaListItem(
    headline: String,
    supporting: String,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector?
) {
    ListItem(
        headlineContent = { Text(headline, fontWeight = FontWeight.Medium) },
        supportingContent = { Text(supporting) },
        leadingContent = if (leadingIcon != null) {
            { SettingsIconBadge(icon = leadingIcon) }
        } else null,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun UpdateHistoryCard(version: String, date: String, changes: List<String>) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(8.dp),
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.primary
                    ) {}
                    Text(
                        text = version,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = date,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            changes.forEach { change ->
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    Text(
                        "•",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = change,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun LicenseListItem(name: String, license: String, copyright: String, url: String? = null) {
    ListItem(
        headlineContent = { Text(name, fontWeight = FontWeight.SemiBold) },
        supportingContent = { 
            Column {
                Text(license, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                Text(copyright, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (url != null) {
                    Text(url, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}
