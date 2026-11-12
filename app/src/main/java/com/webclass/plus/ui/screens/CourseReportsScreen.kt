package com.webclass.plus.ui.screens

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.webclass.plus.data.models.ReportItem
import com.webclass.plus.ui.components.WebClassWebView
import com.webclass.plus.ui.components.shimmerEffect
import com.webclass.plus.util.JavaScriptBridge
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseReportsScreen(
    courseId: String,
    courseName: String = "マイレポート",
    onNavigateBack: () -> Unit,
    onTaskClick: (String, String) -> Unit = { _, _ -> },
    onScoreDetailClick: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val reportsViewModel: com.webclass.plus.ui.viewmodels.CourseReportsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    
    val developerPreferences = remember { com.webclass.plus.util.DeveloperPreferences(context) }
    val isMockMode by developerPreferences.mockModeEnabledFlow.collectAsState(initial = false)
    val forceTabletMode by developerPreferences.forceTabletModeFlow.collectAsState(initial = false)
    val forceSmartphoneMode by developerPreferences.forceSmartphoneModeFlow.collectAsState(initial = false)
    
    val cachedReports = reportsViewModel.getReportsForCourse(courseId)
    val hasCache = cachedReports != null
    
    var reportsData by remember { mutableStateOf<List<ReportItem>?>(cachedReports) }
    var selectedReport by remember { mutableStateOf<ReportItem?>(null) }
    var isLoading by remember { mutableStateOf(!hasCache) }
    var loadingMessage by remember { mutableStateOf("レポート情報を取得中...") }
    var retryCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(reportsData) {
        if (selectedReport == null || reportsData?.contains(selectedReport) != true) {
            selectedReport = reportsData?.firstOrNull()
        }
    }
    
    val reportsUrl = "https://webclass.kosen-k.go.jp/webclass/course.php/$courseId/my-reports"
    var currentUrl by remember { mutableStateOf(if (hasCache) "about:blank" else reportsUrl) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    
    val gson = remember { Gson() }
    val scope = rememberCoroutineScope()

    // Safety timeout
    LaunchedEffect(isLoading) {
        if (isLoading) {
            delay(15000)
            if (isLoading) {
                isLoading = false
            }
        }
    }

    // Handle Mock Data
    LaunchedEffect(isMockMode) {
        if (isMockMode) {
            reportsData = getMockReports()
            isLoading = false
        } else if (!hasCache && reportsData != null) {
            reportsData = null
            isLoading = true
            currentUrl = reportsUrl
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        topBar = {
            TopAppBar(
                title = { Text(text = "マイレポート", maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
                            } else {
                                isLoading = true
                                loadingMessage = "モックデータを再読み込み中..."
                                scope.launch {
                                    delay(500)
                                    reportsData = getMockReports()
                                    isLoading = false
                                }
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
                                        loadingMessage = "レポートページへ移動中..."
                                        currentUrl = reportsUrl
                                        retryCount = 2
                                    } else if (retryCount >= 2) {
                                        isLoading = false
                                    }
                                    return@evaluateJavascript
                                }

                                if (url.contains("index.php") || url.endsWith("/webclass/") || url.endsWith("/webclass")) {
                                    loadingMessage = "レポートページへ移動中..."
                                    currentUrl = reportsUrl
                                    return@evaluateJavascript
                                }

                                if (url.contains("/my-reports")) {
                                    loadingMessage = "レポートデータを解析中..."
                                    webView.evaluateJavascript(JavaScriptBridge.extractMyReports()) { jsonResult ->
                                        Log.d("WebClassReports", "Raw JSON: $jsonResult")
                                        try {
                                            if (jsonResult != null && jsonResult != "null" && !jsonResult.contains("error")) {
                                                val cleanJson = if (jsonResult.startsWith("\"") && jsonResult.endsWith("\"")) {
                                                    jsonResult.substring(1, jsonResult.length - 1)
                                                        .replace("\\\"", "\"")
                                                        .replace("\\\\", "\\")
                                                } else {
                                                    jsonResult
                                                }
                                                Log.d("WebClassReports", "Clean JSON: $cleanJson")
                                                val parsedData = gson.fromJson(cleanJson, Array<ReportItem>::class.java).toList()
                                                reportsData = parsedData
                                                reportsViewModel.updateReports(courseId, parsedData)
                                                retryCount = 0
                                            } else {
                                                Log.e("WebClassReports", "Invalid JSON or error returned: $jsonResult")
                                            }
                                        } catch (e: Exception) {
                                            Log.e("WebClassReports", "Error parsing reports: ${e.message}")
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
                ReportsLoadingLayout()
            } else {
                val reports = reportsData
                if (reports == null || reports.isEmpty()) {
                    ReportsEmptyLayout(
                        onRetry = {
                            if (!isMockMode) {
                                isLoading = true
                                loadingMessage = "セッションを更新中..."
                                retryCount = 0
                                currentUrl = "https://webclass.kosen-k.go.jp/webclass/logout.php"
                                webViewRef?.loadUrl(currentUrl)
                            } else {
                                isLoading = true
                                scope.launch {
                                    delay(500)
                                    reportsData = getMockReports()
                                    isLoading = false
                                }
                            }
                        }
                    )
                } else {
                    BoxWithConstraints {
                        val isWideScreen = when {
                            forceTabletMode -> true
                            forceSmartphoneMode -> false
                            else -> maxWidth >= 600.dp
                        }
                        
                        if (isWideScreen) {
                            ReportsTabletLayout(
                                reports = reports,
                                selectedReport = selectedReport,
                                onReportSelected = { selectedReport = it },
                                onTaskClick = onTaskClick,
                                onScoreDetailClick = onScoreDetailClick
                            )
                        } else {
                            ReportsContent(
                                reports = reports,
                                onTaskClick = onTaskClick,
                                onScoreDetailClick = onScoreDetailClick
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportsContent(
    reports: List<ReportItem>,
    onTaskClick: (String, String) -> Unit,
    onScoreDetailClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(reports, key = { it.taskName + "_" + it.submissionDate }) { report ->
            ReportItemCard(
                report = report,
                onTaskClick = onTaskClick,
                onScoreDetailClick = onScoreDetailClick
            )
        }
    }
}

@Composable
private fun ReportItemCard(
    report: ReportItem,
    onTaskClick: (String, String) -> Unit,
    onScoreDetailClick: (String) -> Unit
) {
    val context = LocalContext.current
    val hasResult = report.result == "○" || report.result == "OK"
    val accentColor = if (hasResult) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val badgeContainerColor = if (hasResult) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val badgeTextColor = if (hasResult) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

    OutlinedCard(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Left strip indicator
            Surface(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .defaultMinSize(minHeight = 110.dp),
                color = accentColor
            ) {}

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header: Task link + Result badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = report.taskName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (report.taskUrl != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable(enabled = report.taskUrl != null) {
                                report.taskUrl?.let { onTaskClick(it, report.taskName) }
                            }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Outlined.HelpOutline, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)
                            Text(
                                text = "設問 No. ${report.questionNo}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Result Status Badge
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = badgeContainerColor
                    ) {
                        Text(
                            text = when (report.result) {
                                "未" -> "未採点"
                                "○", "OK" -> "採点済み"
                                else -> report.result
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = badgeTextColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Mid section: Report Contents (File or Text)
                if (report.reportFileName != null && report.reportFileUrl != null) {
                    // Submitted File Item
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .clickable {
                                    report.reportFileUrl.let {
                                        downloadReportFile(context, it, report.reportFileName)
                                    }
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Outlined.InsertDriveFile,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = report.reportFileName,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                Icons.Default.Download,
                                contentDescription = "ダウンロード",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                } else if (report.reportText != null) {
                    // Text Response
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "提出されたレポートテキスト:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = report.reportText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Corrected File (添削ファイル)
                if (report.correctedFileName != null && report.correctedFileUrl != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .clickable {
                                    downloadReportFile(context, report.correctedFileUrl, report.correctedFileName)
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Outlined.RateReview,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "添削: ${report.correctedFileName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                Icons.Default.Download,
                                contentDescription = "ダウンロード",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Comment Section (コメント)
                if (report.comment != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f))
                            .padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Outlined.Comment, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.tertiary)
                            Text(
                                text = "教員からのコメント:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = report.comment,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Bottom row: Date & Score Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Outlined.CalendarToday, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)
                        Text(
                            text = "提出日: ${report.submissionDate}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Score Badge / Link
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.clickable(enabled = report.scoreUrl != null) {
                            report.scoreUrl?.let { onScoreDetailClick(it) }
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "得点/配点: ${report.scoreText}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            if (report.scoreUrl != null) {
                                Icon(
                                    Icons.Outlined.OpenInNew,
                                    contentDescription = "詳細",
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun downloadReportFile(context: Context, downloadUrl: String, fileName: String) {
    try {
        val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
            // Retrieve cookies for authenticated download
            val cookies = CookieManager.getInstance().getCookie(downloadUrl)
            addRequestHeader("Cookie", cookies)
            addRequestHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.0.0 Mobile Safari/537.36")
            
            setDescription("レポートファイルをダウンロード中...")
            setTitle(fileName)
            
            // Set visibility of notifications
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            
            // Set destination to Downloads directory
            setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                fileName
            )
        }
        
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadManager.enqueue(request)
        
        Toast.makeText(context, "ダウンロードを開始しました", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "ダウンロードに失敗しました: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

@Composable
private fun ReportsLoadingLayout() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .shimmerEffect()
            )
        }
    }
}

@Composable
private fun ReportsEmptyLayout(onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Outlined.AssignmentLate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = "レポートデータが見つかりません",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onRetry) {
                Text("再試行")
            }
        }
    }
}

// Generate premium mock data based on the specific HTML examples provided in user request
private fun getMockReports(): List<ReportItem> {
    return listOf(
        ReportItem(
            taskName = "260519 sphere_density.c【5/28(木)22:00まで】",
            taskUrl = "https://webclass.kosen-k.go.jp/webclass/course.php/14_26_a23d0040_2D/contents/6ab09a11652e25aa05d2318b357875bd/?acs_=adb74f42",
            questionNo = "1",
            reportText = null,
            reportFileName = "sphare_density.c",
            reportFileUrl = "https://webclass.kosen-k.go.jp/webclass/course.php/14_26_a23d0040_2D/my-reports/download?filename=sphare_density.c&file=14_稲穂-260520-144500-6ab09a-09c290&acs_=adb74f42",
            correctedFileName = null,
            correctedFileUrl = null,
            comment = null,
            submissionDate = "2026-05-20 14:45:00",
            result = "未",
            scoreText = "未/100",
            scoreUrl = "https://webclass.kosen-k.go.jp/webclass/mbl.php/question_results/answer?answer_id=372a855f3e05942473e2ed8bf83244b3&set_contents_id=6ab09a11652e25aa05d2318b357875bd&current_page=1&prev=myreports&acs_=adb74f42"
        ),
        ReportItem(
            taskName = "260512 cal_bytes.c【5/21(木)22:00まで】",
            taskUrl = "https://webclass.kosen-k.go.jp/webclass/course.php/14_26_a23d0040_2D/contents/750845dd13e05af5baaae4677917f2e9/?acs_=adb74f42",
            questionNo = "1",
            reportText = null,
            reportFileName = "cal_bytes.c",
            reportFileUrl = "https://webclass.kosen-k.go.jp/webclass/course.php/14_26_a23d0040_2D/my-reports/download?filename=cal_bytes.c&file=14_稲穂-260512-121423-750845-32521e&acs_=adb74f42",
            correctedFileName = null,
            correctedFileUrl = null,
            comment = "よくできています！",
            submissionDate = "2026-05-12 12:14:23",
            result = "○",
            scoreText = "100/100",
            scoreUrl = "https://webclass.kosen-k.go.jp/webclass/mbl.php/question_results/answer?answer_id=b6c988192bfac5a53fa1273e1c1c77d3&set_contents_id=750845dd13e05af5baaae4677917f2e9&current_page=1&prev=myreports&acs_=adb74f42"
        ),
        ReportItem(
            taskName = "260428 jikosho.c【5/14(木)22:00まで】",
            taskUrl = "https://webclass.kosen-k.go.jp/webclass/course.php/14_26_a23d0040_2D/contents/02fd5a8c6b9d03cc60e80f80fcc47646/?acs_=adb74f42",
            questionNo = "1",
            reportText = null,
            reportFileName = "jikosho.c",
            reportFileUrl = "https://webclass.kosen-k.go.jp/webclass/course.php/14_26_a23d0040_2D/my-reports/download?filename=jikosho.c&file=14_稲穂-260428-100838-02fd5a-0318ee&acs_=adb74f42",
            correctedFileName = "jikosho_corrected.c",
            correctedFileUrl = "https://webclass.kosen-k.go.jp/webclass/course.php/14_26_a23d0040_2D/my-reports/download?filename=jikosho_corrected.c&file=14_稲穂-260428-100838-02fd5a-corrected",
            comment = "インデントとコメントが非常に丁寧です。",
            submissionDate = "2026-04-28 10:08:38",
            result = "○",
            scoreText = "100/100",
            scoreUrl = "https://webclass.kosen-k.go.jp/webclass/mbl.php/question_results/answer?answer_id=93f924c1738fa041ca4bba26810aae85&set_contents_id=02fd5a8c6b9d03cc60e80f80fcc47646&current_page=1&prev=myreports&acs_=adb74f42"
        ),
        ReportItem(
            taskName = "Lesson 1  Make Your Own",
            taskUrl = "https://webclass.kosen-k.go.jp/webclass/course.php/14_26_a23ga220_2D/contents/5aeeba03500e9a57a61cb2130f7464ae/?acs_=7447f2ad",
            questionNo = "2",
            reportText = "Shopping in stores is better than online shopping.\nI have two reasons.\nFirst, you can see and try products in real life.\nSecond, you can buy them right away.",
            reportFileName = null,
            reportFileUrl = null,
            correctedFileName = null,
            correctedFileUrl = null,
            comment = null,
            submissionDate = "2026-04-22 10:28:28",
            result = "未",
            scoreText = "未/0",
            scoreUrl = "https://webclass.kosen-k.go.jp/webclass/mbl.php/question_results/answer?answer_id=51c3b4d8fc12dc1c8c78ff52d09d0447&set_contents_id=5aeeba03500e9a57a61cb2130f7464ae&current_page=2&prev=myreports&acs_=7447f2ad"
        ),
        ReportItem(
            taskName = "Lesson 1  Make Your Own",
            taskUrl = "https://webclass.kosen-k.go.jp/webclass/course.php/14_26_a23ga220_2D/contents/5aeeba03500e9a57a61cb2130f7464ae/?acs_=7447f2ad",
            questionNo = "1",
            reportText = "Online shopping is better than shopping in stores.\nI have two reasons.\nFirst, you can buy products from other countries online.\nSecond, online shopping is convenient because you can shop anytime.",
            reportFileName = null,
            reportFileUrl = null,
            correctedFileName = null,
            correctedFileUrl = null,
            comment = null,
            submissionDate = "2026-04-22 10:27:49",
            result = "未",
            scoreText = "未/0",
            scoreUrl = "https://webclass.kosen-k.go.jp/webclass/mbl.php/question_results/answer?answer_id=51c3b4d8fc12dc1c8c78ff52d09d0447&set_contents_id=5aeeba03500e9a57a61cb2130f7464ae&current_page=1&prev=myreports&acs_=7447f2ad"
        )
    )
}

@Composable
private fun ReportsTabletLayout(
    reports: List<ReportItem>,
    selectedReport: ReportItem?,
    onReportSelected: (ReportItem) -> Unit,
    onTaskClick: (String, String) -> Unit,
    onScoreDetailClick: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Left Column: Simplified List
        Box(
            modifier = Modifier
                .weight(0.4f)
                .fillMaxHeight()
                .padding(start = 16.dp, top = 16.dp, bottom = 16.dp)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(reports, key = { it.taskName + "_" + it.submissionDate }) { report ->
                    SimplifiedReportItemCard(
                        report = report,
                        isSelected = report == selectedReport,
                        onClick = { onReportSelected(report) }
                    )
                }
            }
        }

        // Vertical Divider
        VerticalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxHeight().padding(vertical = 16.dp)
        )

        // Right Column: Details Pane
        Box(
            modifier = Modifier
                .weight(0.6f)
                .fillMaxHeight()
                .padding(end = 16.dp, top = 16.dp, bottom = 16.dp)
        ) {
            if (selectedReport != null) {
                ReportDetailPane(
                    report = selectedReport,
                    onTaskClick = onTaskClick,
                    onScoreDetailClick = onScoreDetailClick
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "レポートを選択してください",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SimplifiedReportItemCard(
    report: ReportItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val hasResult = report.result == "○" || report.result == "OK"
    val accentColor = if (hasResult) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val badgeContainerColor = if (hasResult) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val badgeTextColor = if (hasResult) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

    val cardBorder = if (isSelected) {
        CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
            width = 2.dp
        )
    } else {
        CardDefaults.outlinedCardBorder()
    }
    
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    OutlinedCard(
        shape = MaterialTheme.shapes.medium,
        border = cardBorder,
        colors = CardDefaults.outlinedCardColors(containerColor = containerColor),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Left strip indicator
            Surface(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .defaultMinSize(minHeight = 88.dp),
                color = accentColor
            ) {}

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Header: Title + Result Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = report.taskName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = badgeContainerColor
                    ) {
                        Text(
                            text = when (report.result) {
                                "未" -> "未採点"
                                "○", "OK" -> "採点"
                                else -> report.result
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = badgeTextColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Score and Date
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Outlined.CalendarToday, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.outline)
                        Text(
                            text = report.submissionDate.split(" ").firstOrNull() ?: report.submissionDate,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        text = report.scoreText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun ReportDetailPane(
    report: ReportItem,
    onTaskClick: (String, String) -> Unit,
    onScoreDetailClick: (String) -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    
    val hasResult = report.result == "○" || report.result == "OK"
    val badgeContainerColor = if (hasResult) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val badgeTextColor = if (hasResult) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Detailed Card holding all details
        ElevatedCard(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text(
                            text = report.taskName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (report.taskUrl != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.clickable(enabled = report.taskUrl != null) {
                                report.taskUrl?.let { onTaskClick(it, report.taskName) }
                            }
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Outlined.HelpOutline, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
                            Text(
                                text = "設問 No. ${report.questionNo}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = badgeContainerColor
                    ) {
                        Text(
                            text = when (report.result) {
                                "未" -> "未採点"
                                "○", "OK" -> "採点済み"
                                else -> report.result
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = badgeTextColor,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Mid section: Report Contents (File or Text)
                if (report.reportFileName != null && report.reportFileUrl != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "提出ファイル",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            fontWeight = FontWeight.SemiBold
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .clickable {
                                        report.reportFileUrl?.let {
                                            downloadReportFile(context, it, report.reportFileName)
                                        }
                                    }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        Icons.Outlined.InsertDriveFile,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        text = report.reportFileName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Icon(
                                    Icons.Default.Download,
                                    contentDescription = "ダウンロード",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                } else if (report.reportText != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "提出されたレポートテキスト:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = report.reportText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Corrected File (添削ファイル)
                if (report.correctedFileName != null && report.correctedFileUrl != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "添削ファイル",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            fontWeight = FontWeight.SemiBold
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .clickable {
                                        downloadReportFile(context, report.correctedFileUrl, report.correctedFileName)
                                    }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        Icons.Outlined.RateReview,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        text = "添削: ${report.correctedFileName}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Icon(
                                    Icons.Default.Download,
                                    contentDescription = "ダウンロード",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // Comment Section (コメント)
                if (report.comment != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f))
                            .border(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Outlined.Comment, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
                            Text(
                                text = "教員からのコメント:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = report.comment,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Bottom row: Date & Score Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Outlined.CalendarToday, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
                        Text(
                            text = "提出日: ${report.submissionDate}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Score Badge / Link
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.clickable(enabled = report.scoreUrl != null) {
                            report.scoreUrl?.let { onScoreDetailClick(it) }
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "得点/配点: ${report.scoreText}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            if (report.scoreUrl != null) {
                                Icon(
                                    Icons.Outlined.OpenInNew,
                                    contentDescription = "詳細",
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
