package com.webclass.plus.ui.screens


import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.webclass.plus.data.models.ContentResults
import com.webclass.plus.data.models.PreStartInfo
import com.webclass.plus.data.models.NativeQuestionPage
import com.webclass.plus.ui.components.WebClassWebView
import com.webclass.plus.ui.components.PdfViewer
import androidx.compose.material.icons.filled.PictureAsPdf
import com.webclass.plus.util.JavaScriptBridge
import com.webclass.plus.util.ThemePreferences
import com.webclass.plus.util.DeveloperPreferences
import com.webclass.plus.ui.theme.AppTheme
import com.webclass.plus.util.WebViewStyles
import com.webclass.plus.ui.components.VideoPlayer
import com.webclass.plus.ui.components.shimmerEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Splitscreen
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

data class TabInfo(
    val id: Int,
    var url: String,
    var title: String,
    val isMainContent: Boolean,
    var webViewRef: WebView? = null
)

data class Shortcut(val name: String, val url: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

const val NEW_TAB_URL = "webclass://newtab"




@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentViewerScreen(
    url: String,
    title: String = "コンテンツ",
    onBackClick: () -> Unit = {},
    onFullScreenChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    val themePreferences = remember { ThemePreferences(context) }
    val developerPreferences = remember { DeveloperPreferences(context) }
    val currentTheme by themePreferences.themeFlow.collectAsState(initial = AppTheme.SYSTEM)
    val useWallpaper by themePreferences.useWallpaperFlow.collectAsState(initial = false)
    val enableNativeVideoPlayer by themePreferences.enableNativeVideoPlayerFlow.collectAsState(initial = true)
    val enableNativePdfViewer by themePreferences.enableNativePdfViewerFlow.collectAsState(initial = true)
    val customContentUiPreference: Boolean? by developerPreferences.applyCustomContentUiFlow.collectAsState(initial = null)
    val applyCustomContentUi = customContentUiPreference == true
    
    var tabIdCounter by remember { mutableIntStateOf(1) }
    
    // Tab State
    val tabs = remember {
        mutableStateListOf(
            TabInfo(
                id = 0,
                url = if (applyCustomContentUi) JavaScriptBridge.toMobileContentUrl(url) else url,
                title = title,
                isMainContent = true
            )
        )
    }
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    
    // Split Screen States
    var isSplitMode by remember { mutableStateOf(false) }
    var splitTabIndex by remember { mutableIntStateOf(-1) }
    var activePane by remember { mutableIntStateOf(0) } // 0 = primary/left/top, 1 = split/right/bottom
    
    val activeTab = tabs.getOrNull(selectedTabIndex) ?: return
    val focusedTab = if (isSplitMode && activePane == 1) (tabs.getOrNull(splitTabIndex) ?: activeTab) else activeTab
    
    // Native Screen States (Only applicable to Main Content Tab)
    var contentResults by remember { mutableStateOf<ContentResults?>(null) }
    var preStartInfo by remember { mutableStateOf<PreStartInfo?>(null) }
    var nativeQuestionPage by remember { mutableStateOf<NativeQuestionPage?>(null) }
    var isNativeQuestionLoading by remember { mutableStateOf(false) }
    var isCustomContentDiscoveryPending by remember { mutableStateOf(false) }
    var videoUrl by remember { mutableStateOf<String?>(null) }
    var isFullScreen by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    var hasResolvedMessageInbox by remember(url, title) { mutableStateOf(false) }
    
    // PDF Viewer States
    var nativePdfUrl by remember { mutableStateOf<String?>(null) }
    var detectedPdfUrls by remember { mutableStateOf<List<String>>(emptyList()) }
    
    val scope = rememberCoroutineScope()
    val gson = remember { Gson() }

    val hasExtraTabs = tabs.size > 1

    fun decodeJsString(value: String?): String {
        if (value.isNullOrBlank() || value == "null") return ""
        return try {
            gson.fromJson(value, String::class.java).orEmpty()
        } catch (_: Exception) {
            value.trim().trim('"')
        }
    }

    fun isPdfFile(fileUrl: String, mimeType: String?, contentDisposition: String?): Boolean {
        val combined = "${fileUrl.lowercase()} ${mimeType.orEmpty().lowercase()} ${contentDisposition.orEmpty().lowercase()}"
        return combined.contains("application/pdf") ||
            combined.contains(".pdf") ||
            Regex("filename\\*?=[^;]*pdf", RegexOption.IGNORE_CASE).containsMatchIn(combined)
    }

    fun isVideoFile(fileUrl: String, mimeType: String?, contentDisposition: String?): Boolean {
        val combined = "${fileUrl.lowercase()} ${mimeType.orEmpty().lowercase()} ${contentDisposition.orEmpty().lowercase()}"
        return mimeType?.startsWith("video/", ignoreCase = true) == true ||
            listOf(".mp4", ".webm", ".m3u8", ".mov", ".m4v").any { combined.contains(it) }
    }

    fun extractNativeQuestion(webView: WebView, retries: Int = 0, previousKey: String? = null) {
        webView.evaluateJavascript(JavaScriptBridge.extractNativeQuestionPage()) { result ->
            val parsed = runCatching {
                gson.fromJson(decodeJsString(result), NativeQuestionPage::class.java)
            }.getOrNull()
            if (parsed == null) {
                if (retries > 0) webView.postDelayed({ extractNativeQuestion(webView, retries - 1, previousKey) }, 300L)
                else isNativeQuestionLoading = false
                return@evaluateJavascript
            }
            val parsedKey = "${parsed.mode}|${parsed.page}|${parsed.sectionTitle}|${parsed.warning}"
            if (previousKey != null && parsedKey == previousKey && retries > 0) {
                webView.postDelayed({ extractNativeQuestion(webView, retries - 1, previousKey) }, 300L)
            } else {
                nativeQuestionPage = parsed
                preStartInfo = null
                isNativeQuestionLoading = false
            }
        }
    }

    fun discoverCustomContentTarget(webView: WebView, tabId: Int, retries: Int = 6) {
        webView.evaluateJavascript(JavaScriptBridge.detectCustomContentTarget()) { result ->
            val target = decodeJsString(result)
            if (target.isNotBlank()) {
                val mainIndex = tabs.indexOfFirst { it.id == tabId && it.isMainContent }
                if (mainIndex >= 0) {
                    val mainTab = tabs[mainIndex]
                    val mobileUrl = JavaScriptBridge.toMobileContentUrl(target)
                    if (mobileUrl != webView.url) {
                        isCustomContentDiscoveryPending = true
                        isNativeQuestionLoading = true
                        tabs[mainIndex] = mainTab.copy(url = mobileUrl, title = "読み込み中...")
                        webView.loadUrl(mobileUrl)
                    }
                }
            } else if (retries > 0) {
                webView.postDelayed({ discoverCustomContentTarget(webView, tabId, retries - 1) }, 350L)
            } else {
                isCustomContentDiscoveryPending = false
            }
        }
    }

    LaunchedEffect(applyCustomContentUi) {
        if (applyCustomContentUi) {
            val mainIndex = tabs.indexOfFirst { it.isMainContent }
            if (mainIndex >= 0) {
                val current = tabs[mainIndex]
                val mobileUrl = JavaScriptBridge.toMobileContentUrl(current.url)
                if (mobileUrl != current.url) {
                    nativeQuestionPage = null
                    preStartInfo = null
                    isCustomContentDiscoveryPending = true
                    tabs[mainIndex] = current.copy(url = mobileUrl)
                    current.webViewRef?.loadUrl(mobileUrl)
                } else {
                    current.webViewRef?.let { webView ->
                        isCustomContentDiscoveryPending = true
                        discoverCustomContentTarget(webView, current.id)
                    }
                }
            }
        } else {
            nativeQuestionPage = null
            preStartInfo = null
            isNativeQuestionLoading = false
            isCustomContentDiscoveryPending = false
        }
    }


    
    val handleBack = {
        if (isFullScreen) {
            isFullScreen = false
            onFullScreenChange(false)
        } else if (nativePdfUrl != null) {
            nativePdfUrl = null
            detectedPdfUrls = emptyList()
        } else if (isSplitMode) {
            val targetTab = if (activePane == 1) tabs.getOrNull(splitTabIndex) else tabs.getOrNull(selectedTabIndex)
            if (targetTab != null && targetTab.webViewRef != null && targetTab.webViewRef!!.canGoBack()) {
                targetTab.webViewRef!!.goBack()
            } else {
                isSplitMode = false
                splitTabIndex = -1
            }
        } else if (!activeTab.isMainContent && activeTab.webViewRef != null && activeTab.webViewRef!!.canGoBack()) {
             activeTab.webViewRef!!.goBack()
        } else if (!activeTab.isMainContent) {
            // Close non-main tab on back
            val idx = selectedTabIndex
            tabs.removeAt(idx)
            selectedTabIndex = if (idx >= tabs.size) tabs.size - 1 else idx
        } else {
            showExitDialog = true
        }
    }

    BackHandler(onBack = handleBack)

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("コンテンツを終了しますか？") },
            text = { Text("実行中のコンテンツを終了します。学習状況が保存されない可能性があります。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitDialog = false
                        val mainTab = tabs.find { it.isMainContent }
                        mainTab?.webViewRef?.evaluateJavascript(JavaScriptBridge.cleanupSession()) {
                            // Proceed to exit after cleanup attempt
                            onBackClick()
                        } ?: run {
                            onBackClick()
                        }
                    }
                ) {
                    Text("終了して戻る", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text("キャンセル")
                }
            }
        )
    }

    Scaffold(
        contentWindowInsets = if (isFullScreen) WindowInsets(0.dp) else WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        topBar = {
            if (!isFullScreen) {
                Column {
                    TopAppBar(
                        windowInsets = WindowInsets.displayCutout.union(WindowInsets.statusBars),
                        title = { 
                            Text(
                                text = if (focusedTab.isMainContent) {
                                    nativeQuestionPage?.title ?: preStartInfo?.title ?: contentResults?.title ?: focusedTab.title
                                } else {
                                    focusedTab.title
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            ) 
                        },
                        navigationIcon = {
                            IconButton(onClick = handleBack) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = {
                                    isSplitMode = !isSplitMode
                                    if (isSplitMode) {
                                        val mainContentIndex = tabs.indexOfFirst { it.isMainContent }.coerceAtLeast(0)
                                        selectedTabIndex = mainContentIndex
                                        
                                        val firstSecondaryIndex = tabs.indexOfFirst { !it.isMainContent }
                                        if (firstSecondaryIndex != -1) {
                                            splitTabIndex = firstSecondaryIndex
                                        } else {
                                            tabs.add(TabInfo(id = tabIdCounter++, url = NEW_TAB_URL, title = "新しいタブ", isMainContent = false))
                                            splitTabIndex = tabs.size - 1
                                        }
                                        activePane = 0
                                    } else {
                                        splitTabIndex = -1
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Splitscreen,
                                    contentDescription = "画面分割",
                                    tint = if (isSplitMode) MaterialTheme.colorScheme.primary else LocalContentColor.current
                                )
                            }
                            IconButton(
                                onClick = {
                                    tabs.add(TabInfo(id = tabIdCounter++, url = NEW_TAB_URL, title = "新しいタブ", isMainContent = false))
                                    if (isSplitMode) {
                                        splitTabIndex = tabs.size - 1
                                        activePane = 1
                                    } else {
                                        selectedTabIndex = tabs.size - 1
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "新しいタブ")
                            }
                        }
                    )
                    
                    // M3 Tab Bar - only show when extra tabs exist
                    AnimatedVisibility(
                        visible = hasExtraTabs,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    itemsIndexed(tabs, key = { _, tab -> tab.id }) { index, tab ->
                                        val isFocused = if (isSplitMode) {
                                            (tab.isMainContent && activePane == 0) || (index == splitTabIndex && activePane == 1)
                                        } else {
                                            index == selectedTabIndex
                                        }
                                        val isVisible = if (isSplitMode) {
                                            tab.isMainContent || index == splitTabIndex
                                        } else {
                                            index == selectedTabIndex
                                        }
                                        
                                        val containerColor = when {
                                            isFocused -> MaterialTheme.colorScheme.primaryContainer
                                            isVisible -> MaterialTheme.colorScheme.secondaryContainer
                                            else -> MaterialTheme.colorScheme.surfaceContainerHigh
                                        }
                                        val contentColor = when {
                                            isFocused -> MaterialTheme.colorScheme.onPrimaryContainer
                                            isVisible -> MaterialTheme.colorScheme.onSecondaryContainer
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                        
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = containerColor,
                                            contentColor = contentColor,
                                            modifier = Modifier
                                                .height(40.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                                .clickable {
                                                    if (isSplitMode) {
                                                        if (tab.isMainContent) {
                                                            activePane = 0
                                                        } else {
                                                            splitTabIndex = index
                                                            activePane = 1
                                                        }
                                                    } else {
                                                        selectedTabIndex = index
                                                    }
                                                }
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(start = 16.dp, end = if (!tab.isMainContent) 8.dp else 16.dp)
                                            ) {
                                                Text(
                                                    text = if (tab.url == NEW_TAB_URL) "新しいタブ" else tab.title,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = if (isFocused) FontWeight.Bold else if (isVisible) FontWeight.SemiBold else FontWeight.Medium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.widthIn(max = 140.dp)
                                                )
                                                if (!tab.isMainContent) {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Box(
                                                        modifier = Modifier
                                                            .size(24.dp)
                                                            .clip(CircleShape)
                                                            .clickable {
                                                                val removedIndex = index
                                                                tabs.removeAt(removedIndex)
                                                                
                                                                val mainContentIndex = tabs.indexOfFirst { it.isMainContent }.coerceAtLeast(0)
                                                                
                                                                if (isSplitMode) {
                                                                    val secondaryTabsExist = tabs.any { !it.isMainContent }
                                                                    if (!secondaryTabsExist) {
                                                                        isSplitMode = false
                                                                        splitTabIndex = -1
                                                                        selectedTabIndex = mainContentIndex
                                                                        activePane = 0
                                                                    } else {
                                                                        selectedTabIndex = mainContentIndex
                                                                        if (splitTabIndex == removedIndex || splitTabIndex >= tabs.size) {
                                                                            splitTabIndex = tabs.indexOfFirst { !it.isMainContent }.coerceAtLeast(0)
                                                                        } else if (splitTabIndex > removedIndex) {
                                                                            splitTabIndex--
                                                                        }
                                                                        if (splitTabIndex == selectedTabIndex) {
                                                                            splitTabIndex = tabs.indexOfFirst { !it.isMainContent }.coerceAtLeast(0)
                                                                        }
                                                                    }
                                                                } else {
                                                                    if (selectedTabIndex >= tabs.size) {
                                                                        selectedTabIndex = tabs.size - 1
                                                                    } else if (selectedTabIndex > removedIndex) {
                                                                        selectedTabIndex--
                                                                    }
                                                                }
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Close,
                                                            contentDescription = "Close Tab",
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            if (!isFullScreen) {
                val containerColor = if (useWallpaper) {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                }
                Surface(
                    color = containerColor,
                    modifier = Modifier.fillMaxWidth(),
                    tonalElevation = 0.dp
                ) {
                    Column {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { focusedTab?.webViewRef?.goBack() },
                                enabled = focusedTab?.webViewRef?.canGoBack() == true
                            ) {
                                Icon(
                                    Icons.Default.ArrowBack,
                                    contentDescription = "戻る",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = { focusedTab?.webViewRef?.goForward() },
                                enabled = focusedTab?.webViewRef?.canGoForward() == true
                            ) {
                                Icon(
                                    Icons.Default.ArrowForward,
                                    contentDescription = "進む",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = { focusedTab?.webViewRef?.reload() }
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "再読み込み",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        BoxWithConstraints(modifier = Modifier.padding(if (isFullScreen) PaddingValues(0.dp) else padding)) {
            val containerWidth = maxWidth
            val containerHeight = maxHeight
            val isWideScreen = containerWidth > 600.dp
            
            // Helper to find index by tab id safely
            fun findTabIndex(tabId: Int): Int? = tabs.indexOfFirst { it.id == tabId }.takeIf { it >= 0 }
            
            // Render WebViews
            tabs.forEachIndexed { index, tab ->
                key(tab.id) {
                    val isPrimaryActive = index == selectedTabIndex
                    val isSplitActive = isSplitMode && index == splitTabIndex
                    val isVisible = isPrimaryActive || isSplitActive
                    
                    val showOverlays = tab.isMainContent && (
                        contentResults != null || preStartInfo != null || nativeQuestionPage != null || videoUrl != null ||
                            customContentUiPreference == null ||
                            isCustomContentDiscoveryPending ||
                            (applyCustomContentUi && JavaScriptBridge.isCustomContentUrl(tab.url))
                        )
                    val tabId = tab.id
                    
                    val isVisibleAndActive = isVisible && !showOverlays
                    
                    val paneWidth: androidx.compose.ui.unit.Dp
                    val paneHeight: androidx.compose.ui.unit.Dp
                    val offsetX: androidx.compose.ui.unit.Dp
                    val offsetY: androidx.compose.ui.unit.Dp
                    val paneIndex: Int
                    
                    if (isVisibleAndActive) {
                        if (isSplitMode) {
                            paneIndex = if (isSplitActive) 1 else 0
                            if (isWideScreen) {
                                // Tablet: horizontal split (left and right)
                                paneWidth = containerWidth / 2
                                paneHeight = containerHeight
                                offsetX = if (paneIndex == 1) containerWidth / 2 else 0.dp
                                offsetY = 0.dp
                            } else {
                                // Phone: vertical split (top and bottom)
                                paneWidth = containerWidth
                                paneHeight = containerHeight / 2
                                offsetX = 0.dp
                                offsetY = if (paneIndex == 1) containerHeight / 2 else 0.dp
                            }
                        } else {
                            paneIndex = 0
                            paneWidth = containerWidth
                            paneHeight = containerHeight
                            offsetX = 0.dp
                            offsetY = 0.dp
                        }
                    } else {
                        paneIndex = -1
                        paneWidth = containerWidth
                        paneHeight = containerHeight
                        offsetX = 0.dp
                        offsetY = 10000.dp // Hide off-screen to keep the Composable alive
                    }
                    
                    val isFocused = isSplitMode && activePane == paneIndex
                    
                    Card(
                        shape = RoundedCornerShape(0.dp),
                        border = if (isSplitMode && isVisibleAndActive && isFocused) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
                        modifier = Modifier
                            .size(width = paneWidth, height = paneHeight)
                            .offset(x = offsetX, y = offsetY)
                            .clickable(enabled = isSplitMode && isVisibleAndActive) { activePane = paneIndex }
                            .then(if (isVisibleAndActive) Modifier else Modifier.alpha(0f)) // Hide visually when not active
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            val showTitleBar = isSplitMode && isVisibleAndActive
                            Surface(
                                color = if (isFocused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(if (showTitleBar) 36.dp else 0.dp)
                                    .clickable(enabled = showTitleBar) { activePane = paneIndex }
                            ) {
                                if (showTitleBar) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(if (isFocused) MaterialTheme.colorScheme.primary else Color.Gray)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (tab.url == NEW_TAB_URL) "新しいタブ" else tab.title,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (paneIndex == 1) {
                                            IconButton(
                                                onClick = { 
                                                    isSplitMode = false
                                                    splitTabIndex = -1
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = "分割を閉じる",
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            
                            Box(modifier = Modifier.weight(1f)) {
                                if (tab.url == NEW_TAB_URL) {
                                    NewTabPage(
                                        showShortcuts = (!isSplitMode || isWideScreen) && isVisibleAndActive,
                                        onUrlSubmit = { newUrl ->
                                            val processedUrl = if (newUrl.startsWith("http://") || newUrl.startsWith("https://")) {
                                                newUrl
                                            } else {
                                                "https://www.google.com/search?q=${java.net.URLEncoder.encode(newUrl, "UTF-8")}"
                                            }
                                            findTabIndex(tabId)?.let { idx -> tabs[idx] = tabs[idx].copy(url = processedUrl) }
                                        }
                                    )
                                } else {
                                    var retryCount by remember { mutableIntStateOf(0) }
                                    
                                    WebClassWebView(
                                        url = tab.url,
                                        modifier = Modifier.fillMaxSize(),
                                        onWebViewCreated = { wv ->
                                            findTabIndex(tabId)?.let { idx -> tabs[idx] = tabs[idx].copy(webViewRef = wv) }
                                        },
                                        onNewTabRequested = { newUrl ->
                                            if (applyCustomContentUi && JavaScriptBridge.isCustomContentUrl(newUrl)) {
                                                val mainIndex = tabs.indexOfFirst { it.isMainContent }
                                                if (mainIndex >= 0) {
                                                    val mainTab = tabs[mainIndex]
                                                    val mobileUrl = JavaScriptBridge.toMobileContentUrl(newUrl)
                                                    nativeQuestionPage = null
                                                    preStartInfo = null
                                                    isNativeQuestionLoading = true
                                                    tabs[mainIndex] = mainTab.copy(url = mobileUrl, title = "読み込み中...")
                                                    selectedTabIndex = mainIndex
                                                    activePane = 0
                                                    mainTab.webViewRef?.loadUrl(mobileUrl)
                                                }
                                            } else {
                                                tabs.add(TabInfo(id = tabIdCounter++, url = newUrl, title = "読み込み中...", isMainContent = false))
                                                if (isSplitMode) {
                                                    splitTabIndex = tabs.size - 1
                                                    activePane = 1
                                                } else {
                                                    selectedTabIndex = tabs.size - 1
                                                }
                                            }
                                        },
                                        onViewableFileRequested = { fileUrl, mimeType, contentDisposition ->
                                            if (!isVisibleAndActive) {
                                                false
                                            } else if (enableNativePdfViewer && isPdfFile(fileUrl, mimeType, contentDisposition)) {
                                                videoUrl = null
                                                nativePdfUrl = fileUrl
                                                detectedPdfUrls = listOf(fileUrl)
                                                true
                                            } else if (enableNativeVideoPlayer && isVideoFile(fileUrl, mimeType, contentDisposition)) {
                                                nativePdfUrl = null
                                                detectedPdfUrls = emptyList()
                                                videoUrl = fileUrl
                                                true
                                            } else {
                                                false
                                            }
                                        },
                                        onPageStarted = { pageUrl ->
                                            if (tab.isMainContent) {
                                                nativePdfUrl = null
                                                detectedPdfUrls = emptyList()
                                                if (applyCustomContentUi && JavaScriptBridge.isCustomContentUrl(pageUrl)) {
                                                    isNativeQuestionLoading = true
                                                }
                                                if (applyCustomContentUi) isCustomContentDiscoveryPending = true
                                            }
                                        },
                                        onPageFinished = { webView, pageUrl ->
                                            scope.launch {
                                                val currentIdx = findTabIndex(tabId) ?: return@launch
                                                val currentTab = tabs[currentIdx]
                                                
                                                // Update tab state
                                                val newTitle = webView.title ?: "コンテンツ"
                                                tabs[currentIdx] = currentTab.copy(url = pageUrl, title = newTitle)

                                                if (currentTab.isMainContent && title == "メッセージ") {
                                                    val isInboxPage = pageUrl.contains("/messages.php/inbox") ||
                                                        pageUrl.contains("/mbl.php/messages") ||
                                                        (pageUrl.contains("msg_editor.php") && pageUrl.contains("msgappmode=inbox"))
                                                    if (isInboxPage) {
                                                        hasResolvedMessageInbox = true
                                                    } else if (!hasResolvedMessageInbox && pageUrl.contains("webclass.kosen-k.go.jp")) {
                                                        hasResolvedMessageInbox = true
                                                        webView.evaluateJavascript(JavaScriptBridge.extractMessageInboxUrl()) { result ->
                                                            val inboxUrl = decodeJsString(result).ifBlank {
                                                                "https://webclass.kosen-k.go.jp/webclass/mbl.php/messages/"
                                                            }
                                                            val i = findTabIndex(tabId) ?: return@evaluateJavascript
                                                            tabs[i] = tabs[i].copy(url = inboxUrl, title = "メッセージ")
                                                            webView.loadUrl(inboxUrl)
                                                        }
                                                    }
                                                }
                                                
                                                if (currentTab.isMainContent) {
                                                    webView.evaluateJavascript(JavaScriptBridge.detectPageType()) { typeResult ->
                                                        val type = typeResult?.replace("\"", "") ?: "OTHER"
                                                        
                                                        when (type) {
                                                            "RESULTS" -> {
                                                                webView.evaluateJavascript(JavaScriptBridge.extractContentResults()) { json ->
                                                                    try {
                                                                        val data = gson.fromJson(json, ContentResults::class.java)
                                                                        contentResults = data
                                                                    } catch (e: Exception) {
                                                                        e.printStackTrace()
                                                                    }
                                                                }
                                                            }
                                                            "PRE_START" -> if (applyCustomContentUi) {
                                                                webView.evaluateJavascript(JavaScriptBridge.extractPreStartInfo()) { json ->
                                                                    try {
                                                                        val data = gson.fromJson(decodeJsString(json), PreStartInfo::class.java)
                                                                        preStartInfo = data
                                                                        isNativeQuestionLoading = false
                                                                        isCustomContentDiscoveryPending = false
                                                                    } catch (e: Exception) {
                                                                        e.printStackTrace()
                                                                    }
                                                                }
                                                            } else preStartInfo = null
                                                            else -> {
                                                                contentResults = null
                                                                preStartInfo = null
                                                            }
                                                        }
                                                    }

                                                    if (applyCustomContentUi && JavaScriptBridge.isNativeQuestionUrl(pageUrl)) {
                                                        isCustomContentDiscoveryPending = false
                                                        extractNativeQuestion(webView)
                                                    } else if (applyCustomContentUi && !JavaScriptBridge.isCustomContentUrl(pageUrl)) {
                                                        discoverCustomContentTarget(webView, tabId)
                                                    } else if (!JavaScriptBridge.isCustomContentUrl(pageUrl)) {
                                                        nativeQuestionPage = null
                                                        isNativeQuestionLoading = false
                                                    }
                                                }

                                                // Handle redirects (Login/Logout) logic
                                                if (pageUrl.contains("logout.php")) {
                                                    val i = findTabIndex(tabId) ?: return@launch
                                                    tabs[i] = tabs[i].copy(url = "https://webclass.kosen-k.go.jp/webclass/login.php?auth_mode=SAML")
                                                    retryCount = 1
                                                    webView.loadUrl(tabs[i].url)
                                                    return@launch
                                                }

                                                if (pageUrl.contains("login.php")) {
                                                    val i = findTabIndex(tabId) ?: return@launch
                                                    if (!pageUrl.contains("auth_mode=SAML") && retryCount == 0) {
                                                        tabs[i] = tabs[i].copy(url = "https://webclass.kosen-k.go.jp/webclass/login.php?auth_mode=SAML")
                                                        retryCount = 1
                                                        webView.loadUrl(tabs[i].url)
                                                    } else if (retryCount == 1) {
                                                        tabs[i] = tabs[i].copy(url = url)
                                                        retryCount = 2
                                                        webView.loadUrl(tabs[i].url)
                                                    }
                                                    return@launch
                                                }
                                                
                                                if (retryCount > 0 && !pageUrl.contains("login.php") && !pageUrl.contains("logout.php")) {
                                                    retryCount = 0
                                                }
                                                
                                                // Inject custom CSS based on theme
                                                val css = WebViewStyles.getThemeCss(currentTheme)
                                                val jsCode = WebViewStyles.injectCss(css)
                                                webView.evaluateJavascript(jsCode, null)

                                                // Check for textbooks content (Video and/or PDF)
                                                if (currentTab.isMainContent) {
                                                    if (pageUrl.contains("txtbk_frame.php") || pageUrl.contains("mbl.php/textbooks/")) {
                                                        if (enableNativeVideoPlayer) {
                                                            webView.evaluateJavascript(JavaScriptBridge.detectVideo()) { result ->
                                                                val detectedUrl = result?.replace("\"", "")
                                                                if (!detectedUrl.isNullOrEmpty() && detectedUrl != "NOT_FOUND") {
                                                                    videoUrl = detectedUrl
                                                                }
                                                            }
                                                        }
                                                        // Check for PDFs in textbooks pages automatically if enabled
                                                        if (enableNativePdfViewer) {
                                                            webView.evaluateJavascript(JavaScriptBridge.detectPdfs()) { result ->
                                                                try {
                                                                    val decoded = decodeJsString(result)
                                                                    if (!decoded.isNullOrEmpty() && decoded != "[]") {
                                                                        val parsedUrls = gson.fromJson(decoded, Array<String>::class.java).toList()
                                                                        // Filter out parent PHP wrapper pages, allowing only direct PDFs or file.php gateways
                                                                        val filtered = parsedUrls.filter { url ->
                                                                            val lower = url.lowercase()
                                                                            lower.contains(".pdf") && (!lower.contains(".php") || lower.contains("file.php"))
                                                                        }
                                                                        if (filtered.isNotEmpty()) {
                                                                            detectedPdfUrls = filtered
                                                                            if (filtered.size == 1) {
                                                                                nativePdfUrl = filtered.first()
                                                                            }
                                                                        }
                                                                    }
                                                                } catch (e: Exception) {
                                                                    e.printStackTrace()
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                    }
                }
            }
        }
            
            // Overlays (Only for main content when it's the active tab)
            val activeTab = tabs[selectedTabIndex]
            if (!activeTab.isMainContent && videoUrl != null) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    VideoPlayer(
                        videoUrl = videoUrl!!,
                        referer = activeTab.url,
                        isFullScreen = isFullScreen,
                        onFullScreenChange = {
                            isFullScreen = it
                            onFullScreenChange(it)
                        },
                        onClose = {
                            videoUrl = null
                            isFullScreen = false
                            onFullScreenChange(false)
                        }
                    )
                }
            } else if (!activeTab.isMainContent && nativePdfUrl != null) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    PdfViewer(
                        pdfUrl = nativePdfUrl!!,
                        detectedPdfUrls = detectedPdfUrls,
                        onPdfUrlSelected = { selectedUrl -> nativePdfUrl = selectedUrl.ifEmpty { null } },
                        title = "PDFプレビュー"
                    )
                }
            } else if (activeTab.isMainContent) {
                if (videoUrl != null) {
                    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                        VideoPlayer(
                            videoUrl = videoUrl!!,
                            referer = activeTab.url,
                            isFullScreen = isFullScreen,
                            onFullScreenChange = { 
                                isFullScreen = it 
                                onFullScreenChange(it)
                            },
                            onClose = { 
                                videoUrl = null 
                                isFullScreen = false
                                onFullScreenChange(false)
                            }
                        )
                    }
                } else if (contentResults != null) {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        ContentResultsScreen(
                            results = contentResults!!,
                            onLinkClick = { linkUrl ->
                                contentResults = null
                                preStartInfo = null
                                tabs[selectedTabIndex] = activeTab.copy(url = linkUrl)
                                activeTab.webViewRef?.loadUrl(linkUrl)
                            }
                        )
                    }
                } else if (preStartInfo != null) {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        PreStartScreen(
                            info = preStartInfo!!,
                            onStart = {
                                activeTab.webViewRef?.evaluateJavascript(JavaScriptBridge.triggerStart()) { _ ->
                                    preStartInfo = null 
                                }
                            },
                            onEnd = {
                                activeTab.webViewRef?.evaluateJavascript(JavaScriptBridge.triggerEnd()) { _ ->
                                    preStartInfo = null
                                    onBackClick() 
                                }
                            }
                        )
                    }
                } else if (nativeQuestionPage != null) {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        NativeQuestionScreen(
                            page = nativeQuestionPage!!,
                            isLoading = isNativeQuestionLoading,
                            onAction = { action, targetPage, answers ->
                                val webView = activeTab.webViewRef ?: return@NativeQuestionScreen
                                val payload = answers.map { (name, values) ->
                                    mapOf("name" to name, "values" to values)
                                }
                                val previous = nativeQuestionPage?.let {
                                    "${it.mode}|${it.page}|${it.sectionTitle}|${it.warning}"
                                }
                                isNativeQuestionLoading = true
                                webView.evaluateJavascript(
                                    JavaScriptBridge.runNativeQuestionAction(gson.toJson(payload), action, targetPage)
                                ) {
                                    webView.postDelayed({ extractNativeQuestion(webView, 8, previous) }, 250L)
                                }
                            }
                        )
                    }
                } else if (customContentUiPreference == null ||
                    isCustomContentDiscoveryPending ||
                    (applyCustomContentUi && JavaScriptBridge.isCustomContentUrl(activeTab.url))) {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            repeat(5) { index ->
                                Spacer(
                                    modifier = Modifier
                                        .fillMaxWidth(if (index == 1) 0.72f else 1f)
                                        .height(if (index == 2) 120.dp else 24.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .shimmerEffect()
                                )
                            }
                        }
                    }
                } else if (nativePdfUrl != null) {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        PdfViewer(
                            pdfUrl = nativePdfUrl!!,
                            detectedPdfUrls = detectedPdfUrls,
                            onPdfUrlSelected = { selectedUrl ->
                                nativePdfUrl = selectedUrl.ifEmpty { null }
                            },
                            title = "PDFプレビュー"
                        )
                    }
                } else if (detectedPdfUrls.size > 1 && nativePdfUrl == null) {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PictureAsPdf,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "デジタル教科書を選択",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "複数のPDFファイルが検出されました。表示するファイルを選択してください。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            
                            Card(
                                modifier = Modifier.fillMaxWidth().widthIn(max = 400.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    detectedPdfUrls.forEachIndexed { index, pdfUrl ->
                                        val fileName = pdfUrl.substringAfterLast("/").substringBefore("?").ifEmpty { "ファイル ${index + 1}" }
                                        Button(
                                            onClick = {
                                                nativePdfUrl = pdfUrl
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.PictureAsPdf,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = fileName,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
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
fun NewTabPage(
    showShortcuts: Boolean = true,
    onUrlSubmit: (String) -> Unit
) {
    var searchText by remember { mutableStateOf("") }
    
    val shortcuts = listOf(
        Shortcut("検索", "https://www.google.com/", Icons.Outlined.Search),
        Shortcut("WebClass", "https://webclass.kosen-k.go.jp/webclass/", Icons.Outlined.School),
        Shortcut("電卓", "https://www.google.com/search?q=%E9%9B%BB%E5%8D%93", Icons.Outlined.Calculate),
        Shortcut("翻訳", "https://translate.google.co.jp/", Icons.Outlined.Language)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))
        
        Text(
            text = "Kosen+",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "何をお探しですか？",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
        )
        
        // Search bar
        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it },
            placeholder = { Text("検索またはURLを入力") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (searchText.isNotBlank()) {
                    IconButton(onClick = { searchText = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "クリア")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = androidx.compose.ui.text.input.ImeAction.Go
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onGo = {
                    if (searchText.isNotBlank()) {
                        onUrlSubmit(searchText)
                    }
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 400.dp)
                .padding(bottom = if (showShortcuts) 32.dp else 0.dp)
        )
        
        if (showShortcuts) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth().widthIn(max = 400.dp)
        ) {
            items(shortcuts) { shortcut ->
                ElevatedCard(
                    onClick = { onUrlSubmit(shortcut.url) },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier.height(110.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(shortcut.icon, contentDescription = null, modifier = Modifier.size(22.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = shortcut.name,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
        }
        
        Spacer(modifier = Modifier.weight(2f))
    }
}
