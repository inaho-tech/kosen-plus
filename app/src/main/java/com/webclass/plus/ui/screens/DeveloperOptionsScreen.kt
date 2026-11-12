package com.webclass.plus.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.webclass.plus.util.DeveloperPreferences
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperOptionsScreen(
    showBackIcon: Boolean = true,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val preferences = remember { DeveloperPreferences(context) }
    val mockMode by preferences.mockModeEnabledFlow.collectAsState(initial = false)
    val customUpdateEnabled by preferences.customUpdateSourceEnabledFlow.collectAsState(initial = false)
    val customUpdateUrl by preferences.customUpdateUrlFlow.collectAsState(initial = "")
    val forceTabletMode by preferences.forceTabletModeFlow.collectAsState(initial = false)
    val forceSmartphoneMode by preferences.forceSmartphoneModeFlow.collectAsState(initial = false)
    val spoofVersionEnabled by preferences.spoofVersionEnabledFlow.collectAsState(initial = false)
    val spoofVersion by preferences.spoofVersionFlow.collectAsState(initial = "")
    val showNetworkLog by preferences.showNetworkLogFlow.collectAsState(initial = false)
    val alwaysShowWelcomeScreen by preferences.alwaysShowWelcomeScreenFlow.collectAsState(initial = false)
    val alwaysShowWhatsNew by preferences.alwaysShowWhatsNewFlow.collectAsState(initial = false)
    val applyCustomContentUi by preferences.applyCustomContentUiFlow.collectAsState(initial = false)
    val spoofNameEnabled by preferences.spoofNameEnabledFlow.collectAsState(initial = false)
    val spoofName by preferences.spoofNameFlow.collectAsState(initial = "")
    val scope = rememberCoroutineScope()

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.displayCutout.union(WindowInsets.statusBars),
                title = { Text("開発者向けオプション", fontWeight = FontWeight.Bold) },
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

            // Warning banner
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                ),
                shape = MaterialTheme.shapes.large
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Outlined.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            "開発者専用",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "これらの設定は開発・デバッグ目的です。不適切な変更はアプリの動作に影響を与える可能性があります。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // ── Section: データ ──
            SettingsSectionHeader("データ")

            OutlinedCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = MaterialTheme.shapes.large
            ) {
                SettingsSwitchItem(
                    icon = Icons.Outlined.DataObject,
                    title = "モックモード",
                    subtitle = "WebClassタブとマイページの情報取得をモックデータに置換",
                    checked = mockMode,
                    onCheckedChange = { scope.launch { preferences.setMockModeEnabled(it) } }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                SettingsSwitchItem(
                    icon = Icons.Outlined.Person,
                    title = "ユーザー名を偽造",
                    subtitle = "マイページ等で表示されるユーザー名を任意の文字列に変更",
                    checked = spoofNameEnabled,
                    onCheckedChange = { scope.launch { preferences.setSpoofNameEnabled(it) } }
                )

                AnimatedVisibility(
                    visible = spoofNameEnabled,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    OutlinedTextField(
                        value = spoofName,
                        onValueChange = { scope.launch { preferences.setSpoofName(it) } },
                        label = { Text("偽造ユーザー名 (例: Developer)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 12.dp),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium
                    )
                }
            }

            // ── Section: レイアウト ──
            SettingsSectionHeader("レイアウト")

            OutlinedCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = MaterialTheme.shapes.large
            ) {
                SettingsSwitchItem(
                    icon = Icons.Outlined.Tablet,
                    title = "タブレットモードを強制",
                    subtitle = "画面サイズに関わらずタブレットUIを使用",
                    checked = forceTabletMode,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            preferences.setForceTabletMode(enabled)
                            if (enabled) preferences.setForceSmartphoneMode(false)
                        }
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                
                SettingsSwitchItem(
                    icon = Icons.Outlined.PhoneAndroid,
                    title = "スマートフォンモードを強制",
                    subtitle = "画面サイズに関わらずスマートフォンUIを使用",
                    checked = forceSmartphoneMode,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            preferences.setForceSmartphoneMode(enabled)
                            if (enabled) preferences.setForceTabletMode(false)
                        }
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                SettingsSwitchItem(
                    icon = Icons.Outlined.Celebration,
                    title = "常にようこそ画面を表示",
                    subtitle = "アプリ起動時に必ずようこそ画面を表示",
                    checked = alwaysShowWelcomeScreen,
                    onCheckedChange = { scope.launch { preferences.setAlwaysShowWelcomeScreen(it) } }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                SettingsSwitchItem(
                    icon = Icons.Outlined.NewReleases,
                    title = "常に新しい機能を表示",
                    subtitle = "アプリ起動時に毎回、新しい機能のポップアップを表示",
                    checked = alwaysShowWhatsNew,
                    onCheckedChange = { scope.launch { preferences.setAlwaysShowWhatsNew(it) } }
                )
            }

            // ── Section: 危険なエリア ──
            SettingsSectionHeader("危険なエリア")

            OutlinedCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.32f)
                )
            ) {
                SettingsSwitchItem(
                    icon = Icons.Outlined.Science,
                    title = "独自UIを反映",
                    subtitle = "教材実行画面をスマートフォン版から取得し、アプリ独自のUIで表示します",
                    checked = applyCustomContentUi,
                    onCheckedChange = { scope.launch { preferences.setApplyCustomContentUi(it) } }
                )
            }

            // ── Section: アップデート ──
            SettingsSectionHeader("アップデート")

            OutlinedCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = MaterialTheme.shapes.large
            ) {
                SettingsSwitchItem(
                    icon = Icons.Outlined.Link,
                    title = "アップデート元URL設定",
                    subtitle = "アップデート確認を任意のURLで実行",
                    checked = customUpdateEnabled,
                    onCheckedChange = { scope.launch { preferences.setCustomUpdateSourceEnabled(it) } }
                )

                AnimatedVisibility(
                    visible = customUpdateEnabled,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    OutlinedTextField(
                        value = customUpdateUrl,
                        onValueChange = { scope.launch { preferences.setCustomUpdateUrl(it) } },
                        label = { Text("アップデート確認URL") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 12.dp),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                SettingsSwitchItem(
                    icon = Icons.Outlined.NewReleases,
                    title = "バージョンを偽造",
                    subtitle = "アップデートチェックを任意のバージョンで実行",
                    checked = spoofVersionEnabled,
                    onCheckedChange = { scope.launch { preferences.setSpoofVersionEnabled(it) } }
                )

                AnimatedVisibility(
                    visible = spoofVersionEnabled,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    OutlinedTextField(
                        value = spoofVersion,
                        onValueChange = { scope.launch { preferences.setSpoofVersion(it) } },
                        label = { Text("偽造バージョン (例: 1.0.0)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 12.dp),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium
                    )
                }
            }

            // ── Section: デバッグ ──
            SettingsSectionHeader("デバッグ")

            OutlinedCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = MaterialTheme.shapes.large
            ) {
                SettingsSwitchItem(
                    icon = Icons.Outlined.NetworkCheck,
                    title = "通信を表示",
                    subtitle = "WebViewなどの通信を画面左上にオーバーレイ表示",
                    checked = showNetworkLog,
                    onCheckedChange = { scope.launch { preferences.setShowNetworkLog(it) } }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
