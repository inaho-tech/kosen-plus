package com.webclass.plus.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

data class WelcomePage(
    val title: String,
    val description: String,
    val icon: ImageVector
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WelcomeScreen(
    themePreferences: com.webclass.plus.util.ThemePreferences,
    onFinish: () -> Unit
) {
    var isFinishing by remember { mutableStateOf(false) }
    
    val currentTheme by themePreferences.themeFlow.collectAsState(initial = com.webclass.plus.ui.theme.AppTheme.SYSTEM)
    var selectedTheme by remember { mutableStateOf(currentTheme) }
    
    LaunchedEffect(selectedTheme) {
        if (selectedTheme != currentTheme) {
            themePreferences.setTheme(selectedTheme)
        }
    }
    
    val pages = listOf(
        WelcomePage(
            title = "Kosen+ へようこそ",
            description = "高専生活をもっと便利に、もっと快適に。\nKosen+はあなたの学習をサポートします。",
            icon = Icons.Outlined.School
        ),
        WelcomePage(
            title = "WebClassを快適に",
            description = "モバイル向けに最適化されたUIで、\n課題の確認や資料の閲覧がスムーズに行えます。",
            icon = Icons.Outlined.Book
        ),
        WelcomePage(
            title = "シラバスを素早く検索",
            description = "気になる科目のシラバスを\nアプリ内でいつでも簡単に検索・閲覧できます。",
            icon = Icons.Outlined.Search
        ),
        WelcomePage(
            title = "バスの時刻も一目で",
            description = "キャンパスまでのバス時刻表を内蔵。\n次のバスがすぐに分かります。",
            icon = Icons.Outlined.DirectionsBus
        ),
        WelcomePage(
            title = "テーマを選択",
            description = "お好みのカラーテーマを選んでください。\n後から設定で変更することもできます。",
            icon = Icons.Outlined.ColorLens
        ),
        WelcomePage(
            title = "自分好みにカスタマイズ",
            description = "先ほど選んだテーマに加えて、ホーム画面の壁紙設定や、テーマカラーの完全自由なブレンドにも対応。あなただけの特別なKosen+を作成できます。",
            icon = Icons.Outlined.Palette
        )
    )

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    LaunchedEffect(isFinishing) {
        if (isFinishing) {
            delay(500)
            onFinish()
        }
    }

    val exitAlpha by animateFloatAsState(
        targetValue = if (isFinishing) 0f else 1f,
        animationSpec = tween(durationMillis = 500),
        label = "exitAlpha"
    )

    val exitScale by animateFloatAsState(
        targetValue = if (isFinishing) 0.9f else 1f,
        animationSpec = tween(durationMillis = 500),
        label = "exitScale"
    )

    // Subtle background gradient animation
    val infiniteTransition = rememberInfiniteTransition(label = "bg")
    val gradientShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gradientShift"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val surfaceColor = MaterialTheme.colorScheme.surface

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .graphicsLayer {
                    alpha = exitAlpha
                    scaleX = exitScale
                    scaleY = exitScale
                }
        ) {
            // Animated gradient background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                surfaceColor,
                                primaryColor.copy(alpha = 0.03f + 0.02f * gradientShift),
                                tertiaryColor.copy(alpha = 0.02f * (1f - gradientShift)),
                                surfaceColor
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) { page ->
                    val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                // Smooth parallax + fade per page
                                alpha = 1f - (pageOffset * 0.4f).coerceIn(0f, 1f)
                                translationY = pageOffset * 40f
                            }
                    ) {
                        if (pages[page].title == "テーマを選択") {
                            WelcomeThemeSelectionPage(
                                page = pages[page],
                                selectedTheme = selectedTheme,
                                onThemeSelected = { selectedTheme = it }
                            )
                        } else {
                            WelcomePageContent(page = pages[page])
                        }
                    }
                }

                // ── Bottom Section: Indicator + Buttons ──
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.Transparent
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(bottom = 24.dp)
                    ) {
                        // Page indicator
                        Row(
                            modifier = Modifier.padding(vertical = 20.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            repeat(pages.size) { index ->
                                val isSelected = pagerState.currentPage == index
                                val width by animateDpAsState(
                                    targetValue = if (isSelected) 28.dp else 8.dp,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessLow
                                    ),
                                    label = "indicator_width"
                                )
                                val indicatorColor by animateColorAsState(
                                    targetValue = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                                    animationSpec = tween(300),
                                    label = "indicator_color"
                                )

                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 3.dp)
                                        .height(8.dp)
                                        .width(width)
                                        .clip(CircleShape)
                                        .background(indicatorColor)
                                )
                            }
                        }

                        // Navigation buttons
                        Row(
                            modifier = Modifier
                                .widthIn(max = 600.dp)
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (pagerState.currentPage > 0) {
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                        }
                                    },
                                    shape = MaterialTheme.shapes.medium
                                ) {
                                    Text("戻る")
                                }
                            } else {
                                // Skip button on first page
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            pagerState.animateScrollToPage(pages.lastIndex)
                                        }
                                    }
                                ) {
                                    Text("スキップ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            if (pagerState.currentPage < pages.lastIndex) {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                        }
                                    },
                                    shape = MaterialTheme.shapes.medium,
                                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                                ) {
                                    Text("次へ")
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, modifier = Modifier.size(18.dp))
                                }
                            } else {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            themePreferences.setHasSeenWelcomeScreen(true)
                                        }
                                        isFinishing = true
                                    },
                                    shape = MaterialTheme.shapes.medium,
                                    contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                ) {
                                    Text("はじめる", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WelcomePageContent(page: WelcomePage) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = 600.dp)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Icon with layered background
            Box(contentAlignment = Alignment.Center) {
                // Outer glow ring
                Surface(
                    modifier = Modifier.size(180.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ) {}
                // Inner circle
                Surface(
                    modifier = Modifier.size(140.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = page.icon,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(48.dp))
            
            Text(
                text = page.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                letterSpacing = (-0.5).sp
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = page.description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )
        }
    }
}

@Composable
fun WelcomeThemeSelectionPage(
    page: WelcomePage,
    selectedTheme: com.webclass.plus.ui.theme.AppTheme,
    onThemeSelected: (com.webclass.plus.ui.theme.AppTheme) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val isTablet = maxWidth >= 600.dp

        if (isTablet) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 1000.dp)
                    .padding(32.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(48.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Surface(
                            modifier = Modifier.size(180.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        ) {}
                        Surface(
                            modifier = Modifier.size(140.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = page.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Text(
                        text = page.title,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        letterSpacing = (-0.5).sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = page.description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 24.sp
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Center
                ) {
                    ThemeSelectionCards(
                        selectedTheme = selectedTheme,
                        onThemeSelected = onThemeSelected
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 600.dp)
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                Box(contentAlignment = Alignment.Center) {
                    Surface(
                        modifier = Modifier.size(120.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    ) {}
                    Surface(
                        modifier = Modifier.size(88.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = page.icon,
                                contentDescription = null,
                                modifier = Modifier.size(44.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = page.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    letterSpacing = (-0.5).sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = page.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                ThemeSelectionCards(
                    selectedTheme = selectedTheme,
                    onThemeSelected = onThemeSelected
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemeSelectionCards(
    selectedTheme: com.webclass.plus.ui.theme.AppTheme,
    onThemeSelected: (com.webclass.plus.ui.theme.AppTheme) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // System option - full width
        ThemeOptionCard(
            title = "システム設定に従う",
            description = "端末のダークモード設定に同期します",
            icon = Icons.Outlined.BrightnessAuto,
            isSelected = selectedTheme == com.webclass.plus.ui.theme.AppTheme.SYSTEM,
            onClick = { onThemeSelected(com.webclass.plus.ui.theme.AppTheme.SYSTEM) }
        )
        
        // Light / Dark row
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            ThemeOptionCard(
                title = "ライト",
                description = "明るいテーマ",
                icon = Icons.Outlined.LightMode,
                isSelected = selectedTheme == com.webclass.plus.ui.theme.AppTheme.LIGHT,
                onClick = { onThemeSelected(com.webclass.plus.ui.theme.AppTheme.LIGHT) },
                modifier = Modifier.weight(1f)
            )
            
            ThemeOptionCard(
                title = "ダーク",
                description = "暗いテーマ",
                icon = Icons.Outlined.DarkMode,
                isSelected = selectedTheme == com.webclass.plus.ui.theme.AppTheme.DARK,
                onClick = { onThemeSelected(com.webclass.plus.ui.theme.AppTheme.DARK) },
                modifier = Modifier.weight(1f)
            )
        }
        
        // Pastel / Country row
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            ThemeOptionCard(
                title = "パステル",
                description = "柔らかい色合い",
                icon = Icons.Outlined.FilterVintage,
                isSelected = selectedTheme == com.webclass.plus.ui.theme.AppTheme.PASTEL,
                onClick = { onThemeSelected(com.webclass.plus.ui.theme.AppTheme.PASTEL) },
                modifier = Modifier.weight(1f)
            )
            
            ThemeOptionCard(
                title = "カントリー",
                description = "落ち着いた色合い",
                icon = Icons.Outlined.Park,
                isSelected = selectedTheme == com.webclass.plus.ui.theme.AppTheme.COUNTRY,
                onClick = { onThemeSelected(com.webclass.plus.ui.theme.AppTheme.COUNTRY) },
                modifier = Modifier.weight(1f)
            )
        }

        // Custom theme hint
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Column {
                    Text(
                        text = "カスタムテーマ",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "設定画面から自分好みの色を細かくカスタマイズできます。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
fun ThemeOptionCard(
    title: String,
    description: String,
    icon: ImageVector? = null,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = tween(durationMillis = 300),
        label = "cardColor"
    )

    val animatedBorderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = tween(durationMillis = 300),
        label = "borderColor"
    )

    val contentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = 300),
        label = "contentColor"
    )

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = animatedColor,
        border = androidx.compose.foundation.BorderStroke(2.dp, animatedBorderColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = contentColor
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                textAlign = TextAlign.Center
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}


