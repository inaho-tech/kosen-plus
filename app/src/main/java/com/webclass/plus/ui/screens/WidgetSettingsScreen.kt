package com.webclass.plus.ui.screens

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.webclass.plus.widget.BusWidgetProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetSettingsScreen(
    showBackIcon: Boolean = true,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    
    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.displayCutout.union(WindowInsets.statusBars),
                title = { Text("ウィジェット", fontWeight = FontWeight.Bold) },
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

            // ── Section: Preview ──
            SettingsSectionHeader("プレビュー")

            // Widget preview centered in a subtle background
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                WidgetPreviewCard()
            }

            // ── Add Button ──
            Button(
                onClick = { pinWidget(context) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(16.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Outlined.AddCircleOutline, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("ホーム画面に追加", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }

            // ── Section: About ──
            SettingsSectionHeader("ウィジェットについて")

            OutlinedCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = MaterialTheme.shapes.large
            ) {
                WidgetInfoItem(
                    icon = Icons.Outlined.Schedule,
                    title = "自動更新",
                    description = "30秒ごとにバス時刻を自動更新します"
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                WidgetInfoItem(
                    icon = Icons.Outlined.Palette,
                    title = "テーマ連動",
                    description = "アプリのテーマ設定がウィジェットにも反映されます"
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                WidgetInfoItem(
                    icon = Icons.Outlined.TouchApp,
                    title = "タップで操作",
                    description = "ウィジェットをタップでアプリ起動、タイトルタップで更新"
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                WidgetInfoItem(
                    icon = Icons.Outlined.CalendarToday,
                    title = "曜日自動判定",
                    description = "平日・土曜・日祝の時刻表を自動で切り替えます"
                )
            }

            // ── Note ──
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                ),
                shape = MaterialTheme.shapes.large
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Android 8.0以降のデバイスで、アプリから直接ウィジェットを追加できます。ホームアプリによっては手動で配置する必要があります。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun WidgetInfoItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    ListItem(
        leadingContent = { SettingsIconBadge(icon = icon) },
        headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
        supportingContent = { Text(description) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

/**
 * Accurate preview of the bus widget matching the actual XML layout.
 * Uses the same corner radii, padding, font sizes, M3 dynamic colors, and spacing.
 */
@Composable
fun WidgetPreviewCard() {
    val currentTime = "13:30 更新" // Mock update time for preview

    // Outer card: matches widget_background.xml (28dp corners, surface color)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        // 12dp padding matches the XML LinearLayout padding
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.Top
        ) {
            // ── Header: matches LinearLayout ──
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Bus Icon
                Icon(
                    imageVector = Icons.Outlined.DirectionsBus,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))

                // Title
                Text(
                    text = "バス時刻表",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = 0.14.sp
                )

                // Update time stamp
                Text(
                    text = currentTime,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                )

                // Refresh Button
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp).padding(2.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))

                // Day type badge: matches widget_badge_neutral.xml (100dp corners)
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(100.dp)
                ) {
                    Text(
                        text = "平日",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // ── Two Column Layout ──
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Left Column: Kosen Section
                WidgetSectionPreview(
                    modifier = Modifier.weight(1f),
                    label = "高専 発",
                    items = listOf(
                        Triple("表", "あと 5分", "(16:45)"),
                        Triple("裏", "本日終了", "--:--")
                    ),
                    badgeBgColor = listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.secondaryContainer
                    ),
                    badgeTextColor = listOf(
                        MaterialTheme.colorScheme.onPrimaryContainer,
                        MaterialTheme.colorScheme.onSecondaryContainer
                    )
                )

                // Right Column: Station Section
                WidgetSectionPreview(
                    modifier = Modifier.weight(1f),
                    label = "木更津駅 発",
                    items = listOf(
                        Triple("東", "あと 25分", "(15:50)"),
                        Triple("西", "あと 40分", "(16:05)")
                    ),
                    badgeBgColor = listOf(
                        MaterialTheme.colorScheme.tertiaryContainer,
                        MaterialTheme.colorScheme.errorContainer
                    ),
                    badgeTextColor = listOf(
                        MaterialTheme.colorScheme.onTertiaryContainer,
                        MaterialTheme.colorScheme.onErrorContainer
                    )
                )
            }
        }
    }
}

@Composable
fun WidgetSectionPreview(
    modifier: Modifier = Modifier,
    label: String,
    items: List<Triple<String, String, String>>, // (badgeText, remainingTime, timeStr)
    badgeBgColor: List<Color>,
    badgeTextColor: List<Color>
) {
    // Section card: matches widget_section_background.xml (20dp, surfaceVariant)
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.fillMaxHeight()
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Section label
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.4.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            
            items.forEachIndexed { index, (badgeText, remaining, time) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(bottom = if (index < items.size - 1) 4.dp else 0.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Badge: Pill-shaped (100.dp corners), size 22dp, gravity center
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .background(color = badgeBgColor[index], shape = RoundedCornerShape(100.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = badgeText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeTextColor[index]
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(6.dp))
                    
                    // Time block
                    Column(modifier = Modifier.weight(1f)) {
                        // Remaining: matches kosen_front_remaining
                        Text(
                            text = remaining,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                        // Time: matches kosen_front_time
                        Text(
                            text = time,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}

fun pinWidget(context: Context) {
    val appWidgetManager = AppWidgetManager.getInstance(context)
    val myProvider = ComponentName(context, BusWidgetProvider::class.java)

    if (appWidgetManager.isRequestPinAppWidgetSupported) {
        appWidgetManager.requestPinAppWidget(myProvider, null, null)
    } else {
        // Fallback for older devices
    }
}
