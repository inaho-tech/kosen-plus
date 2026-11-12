package com.webclass.plus.ui.screens

import android.app.DatePickerDialog
import java.util.Calendar
import android.util.Log
import android.webkit.WebView
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.webclass.plus.data.models.*
import com.webclass.plus.ui.components.WebClassWebView
import com.webclass.plus.ui.components.shimmerEffect
import com.webclass.plus.util.JavaScriptBridge
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseScoresScreen(
    courseId: String,
    courseName: String = "成績",
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scoresViewModel: com.webclass.plus.ui.viewmodels.CourseScoresViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    
    val developerPreferences = remember { com.webclass.plus.util.DeveloperPreferences(context) }
    val isMockMode by developerPreferences.mockModeEnabledFlow.collectAsState(initial = false)
    
    val cachedScores = scoresViewModel.getScoresForCourse(courseId)
    val hasCache = cachedScores != null
    
    var scoresData by remember { mutableStateOf<ScorePageData?>(cachedScores) }
    var isLoading by remember { mutableStateOf(!hasCache) }
    var loadingMessage by remember { mutableStateOf("成績データを取得中...") }
    var retryCount by remember { mutableIntStateOf(0) }
    
    val scoresUrl = "https://webclass.kosen-k.go.jp/webclass/course.php/$courseId/scores"
    var currentUrl by remember { mutableStateOf(if (hasCache) "about:blank" else scoresUrl) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    
    val gson = remember { Gson() }
    val scope = rememberCoroutineScope()

    val onSwitchType: (String) -> Unit = { targetType ->
        if (!isMockMode) {
            isLoading = true
            loadingMessage = "集計データを切り替え中..."
            webViewRef?.evaluateJavascript(JavaScriptBridge.switchScoreType(targetType)) { result ->
                Log.d("WebClassScores", "Switch type result: $result")
            }
        } else {
            isLoading = true
            loadingMessage = "集計データを切り替え中(モック)..."
            scope.launch {
                delay(800)
                scoresData = getMockScores(courseId, targetType)
                isLoading = false
            }
        }
    }

    val onSwitchDateRange: (String, String) -> Unit = { startDate, endDate ->
        if (!isMockMode) {
            isLoading = true
            loadingMessage = "集計期間を変更中..."
            webViewRef?.evaluateJavascript(JavaScriptBridge.switchScoreDateRange(startDate, endDate)) { result ->
                Log.d("WebClassScores", "Switch date range result: $result")
            }
        } else {
            isLoading = true
            loadingMessage = "集計期間を変更中(モック)..."
            scope.launch {
                delay(800)
                val currentMeta = scoresData?.page ?: ScorePageMeta(
                    courseId = courseId,
                    summaryTitle = null,
                    dateRange = DateRange(startDate, endDate),
                    showdata = scoresData?.page?.showdata ?: "scoreave"
                )
                val mockScores = getMockScores(courseId, currentMeta.showdata ?: "scoreave")
                scoresData = mockScores.copy(
                    page = mockScores.page.copy(
                        dateRange = DateRange(startDate, endDate)
                    )
                )
                isLoading = false
            }
        }
    }

    // Safety timeout
    LaunchedEffect(isLoading) {
        if (isLoading) {
            delay(15000)
            if (isLoading) {
                isLoading = false
            }
        }
    }

    // Force mock data when enabled
    LaunchedEffect(isMockMode) {
        if (isMockMode) {
            scoresData = getMockScores(courseId)
            isLoading = false
        } else if (!hasCache && scoresData?.page?.courseId == courseId) {
            scoresData = null
            isLoading = true
            currentUrl = scoresUrl
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        topBar = {
            TopAppBar(
                title = { Text(text = "成績・得点詳細", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (!isMockMode) {
                                isLoading = true
                                loadingMessage = "セッションを更新中..."
                                retryCount = 0
                                currentUrl = "https://webclass.kosen-k.go.jp/webclass/logout.php"
                                webViewRef?.loadUrl(currentUrl)
                            }
                        },
                        enabled = !isLoading
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "再読み込み")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Background WebView for scraping
            if (!isMockMode) {
                WebClassWebView(
                    url = currentUrl,
                    modifier = Modifier.height(1.dp).alpha(0f),
                    lightweight = true,
                    onWebViewCreated = { webViewRef = it },
                    onPageFinished = { webView, url ->
                        scope.launch {
                            // "Simultaneous access" check
                            webView.evaluateJavascript("(function(){ return document.body.innerText.indexOf('同時') !== -1; })();") { result ->
                                if (result == "true") {
                                    isLoading = true
                                    webView.reload()
                                    return@evaluateJavascript
                                }

                                if (url.contains("logout.php")) {
                                    loadingMessage = "再ログイン中..."
                                    currentUrl = "https://webclass.kosen-k.go.jp/webclass/login.php?auth_mode=SAML"
                                    retryCount = 1
                                    return@evaluateJavascript
                                }

                                if (url.contains("login.php")) {
                                    loadingMessage = "ログイン処理中..."
                                    if (!url.contains("auth_mode=SAML") && retryCount == 0) {
                                        currentUrl = "https://webclass.kosen-k.go.jp/webclass/login.php?auth_mode=SAML"
                                        retryCount = 1
                                    } else if (retryCount == 1) {
                                        loadingMessage = "成績ページへ移動中..."
                                        currentUrl = scoresUrl
                                        retryCount = 2
                                    } else if (retryCount >= 2) {
                                        isLoading = false
                                    }
                                    return@evaluateJavascript
                                }

                                if (url.contains("index.php") || url.endsWith("/webclass/") || url.endsWith("/webclass")) {
                                    loadingMessage = "成績ページへ移動中..."
                                    currentUrl = scoresUrl
                                    return@evaluateJavascript
                                }

                                if (url.contains("/scores")) {
                                    loadingMessage = "成績データを解析中..."
                                    webView.evaluateJavascript(JavaScriptBridge.extractCourseScores()) { jsonResult ->
                                        Log.d("WebClassScores", "Raw JSON result: $jsonResult")
                                        try {
                                            if (jsonResult != null && jsonResult != "null" && !jsonResult.contains("error")) {
                                                val cleanJson = if (jsonResult.startsWith("\"") && jsonResult.endsWith("\"")) {
                                                    jsonResult.substring(1, jsonResult.length - 1)
                                                        .replace("\\\"", "\"")
                                                        .replace("\\\\", "\\")
                                                } else {
                                                    jsonResult
                                                }
                                                Log.d("WebClassScores", "Clean JSON result: $cleanJson")
                                                val parsedData = gson.fromJson(cleanJson, ScorePageData::class.java)
                                                if (parsedData != null && parsedData.categories.isNotEmpty()) {
                                                    scoresData = parsedData
                                                    scoresViewModel.updateScores(courseId, parsedData)
                                                    retryCount = 0
                                                } else {
                                                    Log.e("WebClassScores", "Parsed data is null or empty categories")
                                                }
                                            } else {
                                                 Log.e("WebClassScores", "Invalid jsonResult: $jsonResult")
                                            }
                                        } catch (e: Exception) {
                                            Log.e("WebClassScores", "Error parsing scores: ${e.message}")
                                        }
                                        isLoading = false
                                    }
                                }
                            }
                        }
                    }
                )
            }

            if (isLoading) {
                LoadingLayout()
            } else {
                val data = scoresData
                if (data == null || data.categories.isEmpty()) {
                    EmptyLayout(
                        onRetry = {
                            if (!isMockMode) {
                                isLoading = true
                                loadingMessage = "セッションを更新中..."
                                retryCount = 0
                                currentUrl = "https://webclass.kosen-k.go.jp/webclass/logout.php"
                                webViewRef?.loadUrl(currentUrl)
                            }
                        }
                    )
                } else {
                    ScoresContent(
                        data = data,
                        onTypeSelected = onSwitchType,
                        onDateRangeSelected = onSwitchDateRange
                    )
                }
            }
        }
    }
}

private fun getUnitForShowData(showdata: String?): String {
    return when (showdata) {
        "execnum" -> "回"
        "time" -> "分"
        else -> "点"
    }
}

@Composable
private fun ScoresContent(
    data: ScorePageData,
    onTypeSelected: (String) -> Unit,
    onDateRangeSelected: (String, String) -> Unit
) {
    val unit = getUnitForShowData(data.page.showdata)
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val developerPreferences = remember { com.webclass.plus.util.DeveloperPreferences(context) }
    val forceTabletMode by developerPreferences.forceTabletModeFlow.collectAsState(initial = false)
    val forceSmartphoneMode by developerPreferences.forceSmartphoneModeFlow.collectAsState(initial = false)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = when {
            forceTabletMode -> true
            forceSmartphoneMode -> false
            else -> maxWidth >= 600.dp
        }
        val columns = if (isWideScreen) GridCells.Adaptive(350.dp) else GridCells.Fixed(1)
        
        LazyVerticalGrid(
            columns = columns,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 0.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Page Info Header Card
            item(span = { GridItemSpan(maxLineSpan) }) {
                PageMetaHeaderCard(data.page, onTypeSelected, onDateRangeSelected)
            }

            // Render Categories
            data.categories.forEach { category ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    CategoryHeader(category.name)
                }

                // Category Total Card
                category.total?.let { total ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        CategoryTotalCard(total, unit)
                    }
                }

                items(category.items, key = { it.title + "_" + (it.contentUrl ?: "") }) { item ->
                    ScoreItemCard(item, unit)
                }
            }

            // Bottom spacer to add comfortable breathing room inside the scroll content
            item(span = { GridItemSpan(maxLineSpan) }) {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageMetaHeaderCard(
    meta: ScorePageMeta,
    onTypeSelected: (String) -> Unit,
    onDateRangeSelected: (String, String) -> Unit
) {
    val context = LocalContext.current
    var showEditDialog by remember { mutableStateOf(false) }

    val displayType = when (meta.showdata) {
        "scoremax" -> "最大得点"
        "scoreave" -> "平均得点"
        "scoremin" -> "最少得点"
        "scoresum" -> "合計得点"
        "execnum" -> "実施回数"
        "time" -> "利用時間"
        else -> meta.summaryTitle ?: "得点集計"
    }

    OutlinedCard(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Outlined.Analytics,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(6.dp)
                    )
                }
                Text(
                    text = "集計データ: $displayType",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Horizontal scrolling FilterChips
            val scrollState = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val scoreTypes = listOf(
                    "scoremax" to "最大得点",
                    "scoreave" to "平均得点",
                    "scoremin" to "最少得点",
                    "scoresum" to "合計得点",
                    "execnum" to "実施回数",
                    "time" to "利用時間"
                )
                scoreTypes.forEach { (typeKey, label) ->
                    val isSelected = meta.showdata == typeKey
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            if (!isSelected) {
                                onTypeSelected(typeKey)
                            }
                        },
                        label = {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = MaterialTheme.colorScheme.outlineVariant,
                            selectedBorderColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            val hasDateRange = meta.dateRange != null && (!meta.dateRange.start.isNullOrEmpty() || !meta.dateRange.end.isNullOrEmpty())
            val start = meta.dateRange?.start?.replace("-", "/") ?: ""
            val end = meta.dateRange?.end?.replace("-", "/") ?: ""
            
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                onClick = { showEditDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Outlined.DateRange,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (hasDateRange) "集計期間: $start 〜 $end" else "集計期間: 全期間 (タップして変更)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = "期間変更",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }

    if (showEditDialog) {
        var tempStart by remember { mutableStateOf(meta.dateRange?.start ?: "") }
        var tempEnd by remember { mutableStateOf(meta.dateRange?.end ?: "") }
        val calendar = Calendar.getInstance()
        
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = {
                Text(
                    text = "集計期間を変更",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    // Start Date Card
                    OutlinedCard(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val parts = tempStart.split("-")
                                val y = parts.getOrNull(0)?.toIntOrNull() ?: calendar.get(Calendar.YEAR)
                                val m = (parts.getOrNull(1)?.toIntOrNull() ?: (calendar.get(Calendar.MONTH) + 1)) - 1
                                val d = parts.getOrNull(2)?.toIntOrNull() ?: calendar.get(Calendar.DAY_OF_MONTH)
                                
                                DatePickerDialog(
                                    context,
                                    { _, year, month, dayOfMonth ->
                                        tempStart = String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth)
                                    },
                                    y, m, d
                                ).show()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "開始日",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = tempStart.replace("-", "/").ifEmpty { "指定なし (全期間)" },
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (tempStart.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Icon(
                                imageVector = Icons.Outlined.CalendarToday,
                                contentDescription = "開始日を選択",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // End Date Card
                    OutlinedCard(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val parts = tempEnd.split("-")
                                val y = parts.getOrNull(0)?.toIntOrNull() ?: calendar.get(Calendar.YEAR)
                                val m = (parts.getOrNull(1)?.toIntOrNull() ?: (calendar.get(Calendar.MONTH) + 1)) - 1
                                val d = parts.getOrNull(2)?.toIntOrNull() ?: calendar.get(Calendar.DAY_OF_MONTH)
                                
                                DatePickerDialog(
                                    context,
                                    { _, year, month, dayOfMonth ->
                                        tempEnd = String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth)
                                    },
                                    y, m, d
                                ).show()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "終了日",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = tempEnd.replace("-", "/").ifEmpty { "指定なし (全期間)" },
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (tempEnd.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Icon(
                                imageVector = Icons.Outlined.CalendarToday,
                                contentDescription = "終了日を選択",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEditDialog = false
                        onDateRangeSelected(tempStart, tempEnd)
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("適用")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showEditDialog = false }
                ) {
                    Text("キャンセル")
                }
            }
        )
    }
}

@Composable
private fun CategoryHeader(name: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(4.dp).height(20.dp)
            ) {}
            Text(
                text = name.ifEmpty { "未分類" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ScoreItemCard(item: ScoreItem, unit: String) {
    val hasValidScore = item.score != null
    val isUnsubmitted = item.scoreTextRaw == "未"
    val isUnrated = item.scoreTextRaw.contains("*") || item.scoreTextRaw.contains("[")
    
    val isAboveAverage = hasValidScore && item.courseAvg != null && item.score!! >= item.courseAvg
    val isPerfectScore = hasValidScore && item.score == 100.0

    // Determine the accent color for the left strip
    val accentColor = when {
        isUnsubmitted -> MaterialTheme.colorScheme.outlineVariant
        isUnrated -> MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
        isPerfectScore -> MaterialTheme.colorScheme.primary
        isAboveAverage -> MaterialTheme.colorScheme.secondary // Material secondary color for above average
        hasValidScore -> MaterialTheme.colorScheme.tertiary // Something else for below average
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    val badgeColor = when {
        isUnsubmitted -> MaterialTheme.colorScheme.surfaceContainerHigh
        isUnrated -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        isPerfectScore -> MaterialTheme.colorScheme.primaryContainer
        isAboveAverage -> MaterialTheme.colorScheme.secondaryContainer // Material container color
        hasValidScore -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val badgeTextColor = when {
        isUnsubmitted -> MaterialTheme.colorScheme.onSurfaceVariant
        isUnrated -> MaterialTheme.colorScheme.onErrorContainer
        isPerfectScore -> MaterialTheme.colorScheme.onPrimaryContainer
        isAboveAverage -> MaterialTheme.colorScheme.onSecondaryContainer // Material content color
        hasValidScore -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val scoreText = if (hasValidScore) {
        val scoreVal = item.score!!
        if (scoreVal % 1 == 0.0) "${scoreVal.toInt()}$unit" else "${scoreVal}$unit"
    } else {
        item.scoreTextRaw
    }

    OutlinedCard(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Accent strip
            Surface(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .defaultMinSize(minHeight = 80.dp), // Ensure strip has some height
                color = accentColor
            ) {}
            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Title & Score Badge Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f).padding(end = 12.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = badgeColor
                    ) {
                        Text(
                            text = scoreText,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = badgeTextColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                // Comparison Row
                if (hasValidScore && item.courseAvg != null) {
                    val score = item.score!!
                    val avg = item.courseAvg
                    val diff = score - avg
                    val diffText = if (diff >= 0) "+${String.format("%.1f", diff)}" else String.format("%.1f", diff)
                    val diffColor = if (diff >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (isAboveAverage) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                                contentDescription = null,
                                tint = diffColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (isAboveAverage) "平均以上 ($diffText$unit)" else "平均未満 ($diffText$unit)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = diffColor
                            )
                        }

                        Text(
                            text = "コース平均: ${String.format("%.1f", avg)}$unit",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (item.courseAvg != null) {
                    // If user hasn't submitted yet but average is available
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "未受験 / 未提出",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "コース平均: ${String.format("%.1f", item.courseAvg)}$unit",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryTotalCard(total: ScoreTotal, unit: String) {
    val myLabel = when (unit) {
        "点" -> "得点"
        "回" -> "回数"
        else -> "時間"
    }

    Spacer(modifier = Modifier.height(4.dp))
    
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "カテゴリ合計",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                
                if (total.my != null && total.course != null) {
                    val diff = total.my - total.course
                    val formattedDiff = if (diff >= 0) "+${String.format("%.1f", diff)}" else String.format("%.1f", diff)
                    val diffColor = if (diff >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = diffColor.copy(alpha = 0.15f),
                    ) {
                        Text(
                            text = "平均差: $formattedDiff$unit",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = diffColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                total.course?.let { course ->
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "コース平均",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val courseText = if (course % 1 == 0.0) "${course.toInt()}$unit" else "${course}$unit"
                        Text(
                            text = courseText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                total.my?.let { my ->
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = myLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        val myText = if (my % 1 == 0.0) "${my.toInt()}$unit" else "${my}$unit"
                        Text(
                            text = myText,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingLayout() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val developerPreferences = remember { com.webclass.plus.util.DeveloperPreferences(context) }
    val forceTabletMode by developerPreferences.forceTabletModeFlow.collectAsState(initial = false)
    val forceSmartphoneMode by developerPreferences.forceSmartphoneModeFlow.collectAsState(initial = false)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = when {
            forceTabletMode -> true
            forceSmartphoneMode -> false
            else -> maxWidth >= 600.dp
        }
        val columns = if (isWideScreen) GridCells.Adaptive(350.dp) else GridCells.Fixed(1)
        
        LazyVerticalGrid(
            columns = columns,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Meta header skeleton
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .shimmerEffect()
                )
            }

            // Title skeleton
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(modifier = Modifier.width(4.dp).height(18.dp).clip(CircleShape).shimmerEffect())
                    Box(modifier = Modifier.width(120.dp).height(18.dp).clip(CircleShape).shimmerEffect())
                }
            }

            // List skeletons
            items(3) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .shimmerEffect()
                )
            }
        }
    }
}

@Composable
private fun EmptyLayout(onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Outlined.Leaderboard,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            )
            
            Text(
                text = "成績データがありません",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "データを読み込むには再読み込みを行ってください。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alpha(0.7f)
            )

            Button(onClick = onRetry) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("再表示")
            }
        }
    }
}

private fun getMockScores(courseId: String, showdata: String = "scoremax"): ScorePageData {
    val summaryTitle = when (showdata) {
        "scoremax" -> "最大得点"
        "scoreave" -> "平均得点"
        "scoremin" -> "最少得点"
        "scoresum" -> "合計得点"
        "execnum" -> "実施回数"
        "time" -> "利用時間"
        else -> "得点集計"
    }

    val scoreMultiplier = when (showdata) {
        "scoremax" -> 1.0
        "scoreave" -> 0.85
        "scoremin" -> 0.4
        "scoresum" -> 2.5
        "execnum" -> 0.05
        "time" -> 1.2
        else -> 1.0
    }

    val unit = when (showdata) {
        "execnum" -> "回"
        "time" -> "分"
        else -> "点"
    }

    val myTotal = if (showdata == "execnum") 5.0 else if (showdata == "time") 155.0 else 280.0 * scoreMultiplier
    val courseTotal = if (showdata == "execnum") 4.2 else if (showdata == "time") 122.5 else 253.0 * scoreMultiplier

    val items = listOf(
        ScoreItem("260414 flowchart（紙提出）", null, null, "未", null, null),
        ScoreItem(
            title = "260421 UNIX（入門編）【4/30(木)22:00まで】",
            contentUrl = null,
            score = if (showdata == "execnum") 1.0 else if (showdata == "time") 45.0 else (80.0 * scoreMultiplier).coerceIn(0.0, 100.0),
            scoreTextRaw = if (showdata == "execnum") "1回" else if (showdata == "time") "45分" else "${(80.0 * scoreMultiplier).toInt()}点",
            scoreUrl = null,
            courseAvg = if (showdata == "execnum") 0.9 else if (showdata == "time") 38.2 else 94.9 * scoreMultiplier
        ),
        ScoreItem(
            title = "260428 jikosho.c【5/14(木)22:00まで】",
            contentUrl = null,
            score = if (showdata == "execnum") 2.0 else if (showdata == "time") 60.0 else (100.0 * scoreMultiplier).coerceIn(0.0, 100.0),
            scoreTextRaw = if (showdata == "execnum") "2回" else if (showdata == "time") "60分" else "${(100.0 * scoreMultiplier).toInt()}点",
            scoreUrl = null,
            courseAvg = if (showdata == "execnum") 1.5 else if (showdata == "time") 52.4 else 96.6 * scoreMultiplier
        ),
        ScoreItem(
            title = "260512 cal_bytes.c【5/21(木)22:00まで】",
            contentUrl = null,
            score = if (showdata == "execnum") 2.0 else if (showdata == "time") 50.0 else (100.0 * scoreMultiplier).coerceIn(0.0, 100.0),
            scoreTextRaw = if (showdata == "execnum") "2回" else if (showdata == "time") "50分" else "${(100.0 * scoreMultiplier).toInt()}点",
            scoreUrl = null,
            courseAvg = if (showdata == "execnum") 1.8 else if (showdata == "time") 31.9 else 61.5 * scoreMultiplier
        )
    )

    return ScorePageData(
        page = ScorePageMeta(
            courseId = courseId,
            summaryTitle = summaryTitle,
            dateRange = DateRange(start = "2026-03-20", end = "2026-05-21"),
            showdata = showdata
        ),
        categories = listOf(
            ScoreCategory(
                name = "テスト/アンケート",
                items = items,
                total = ScoreTotal(my = myTotal, course = courseTotal)
            )
        )
    )
}
