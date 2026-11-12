package com.webclass.plus.ui.screens

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.webclass.plus.data.models.Bookmark
import com.webclass.plus.data.models.CourseFolder
import com.webclass.plus.data.models.CourseItem
import com.webclass.plus.data.repository.BookmarkRepository
import com.webclass.plus.ui.components.WebClassWebView
import com.webclass.plus.util.JavaScriptBridge
import com.webclass.plus.util.HtmlFetcher
import com.webclass.plus.util.WebClassHtmlParser
import kotlinx.coroutines.flow.first
import com.webclass.plus.ui.components.shimmerEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseContentsScreen(
    courseId: String,
    courseTitle: String = "コース",
    autoOpenUrl: String? = null,
    autoOpenTitle: String? = null,
    onBackClick: () -> Unit = {},
    onDetailClick: (String) -> Unit = {},
    onHistoryClick: (String) -> Unit = {},
    onContentClick: (String, String) -> Unit = { _, _ -> },
    onScoresClick: (String) -> Unit = {},
    onReportsClick: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val bookmarkRepository = remember { BookmarkRepository(context) }
    val themePreferences = remember { com.webclass.plus.util.ThemePreferences(context) }
    val enableBookmarks by themePreferences.enableBookmarksFlow.collectAsState(initial = false)
    val bookmarkedIds by bookmarkRepository.bookmarks.collectAsState()
    
    val courseViewModel: com.webclass.plus.ui.viewmodels.CourseViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val cachedFolders = courseViewModel.getCachedFolders(courseId)
    val hasCache = cachedFolders != null && cachedFolders.isNotEmpty()
    
    var folders by remember { mutableStateOf<List<CourseFolder>>(cachedFolders ?: emptyList()) }
    var isLoading by remember { mutableStateOf(!hasCache) }
    var loadingMessage by remember { mutableStateOf("セッションを更新中...") }
    var showWebView by remember { mutableStateOf(false) }
    var retryCount by remember { mutableIntStateOf(0) }
    var autoOpenConsumed by remember { mutableStateOf(false) }
    
    // Filtering State
    val searchQuery by courseViewModel.searchQuery.collectAsState()
    val selectedCategory by courseViewModel.selectedCategory.collectAsState() // null = All
    val showOnlyValid by courseViewModel.showOnlyValid.collectAsState()
    
    // Filter Logic
    val allCategories = remember(folders) {
        folders.flatMap { it.items }
            .map { it.category }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }

    val filteredFolders = remember(folders, searchQuery, selectedCategory, showOnlyValid) {
        if (searchQuery.isEmpty() && selectedCategory == null && !showOnlyValid) {
            folders
        } else {
            folders.mapNotNull { folder ->
                val filteredItems = folder.items.filter { item ->
                    val matchesSearch = item.title.contains(searchQuery, ignoreCase = true)
                    val matchesCategory = selectedCategory == null || item.category == selectedCategory
                    val matchesValidity = !showOnlyValid || !item.isDisabled
                    matchesSearch && matchesCategory && matchesValidity
                }
                if (filteredItems.isNotEmpty()) {
                    folder.copy(items = filteredItems)
                } else {
                    null
                }
            }
        }
    }
    
    val enableSpeedUp by themePreferences.enableSpeedUpFlow.collectAsState(initial = false)
    val courseUrl = "https://webclass.kosen-k.go.jp/webclass/course.php/$courseId/"
    val logoutUrl = "https://webclass.kosen-k.go.jp/webclass/logout.php"
    val samlLoginUrl = "https://webclass.kosen-k.go.jp/webclass/login.php?auth_mode=SAML"
    var currentUrl by remember { mutableStateOf(if (hasCache || enableSpeedUp) "about:blank" else logoutUrl) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var directWebViewFallbackActive by remember { mutableStateOf(false) }
    
    val gson = remember { Gson() }
    val scope = rememberCoroutineScope()

    fun openAutoContentIfNeeded() {
        if (autoOpenUrl != null && autoOpenTitle != null && !autoOpenConsumed) {
            autoOpenConsumed = true
            onContentClick(autoOpenUrl, autoOpenTitle)
        }
    }

    fun completeCourseContentsLoad(extractedFolders: List<CourseFolder>) {
        folders = extractedFolders
        courseViewModel.setCachedFolders(courseId, extractedFolders)
        retryCount = 0
        directWebViewFallbackActive = false
        isLoading = false
        openAutoContentIfNeeded()
    }

    fun startLogoutFallback(webView: WebView? = webViewRef) {
        directWebViewFallbackActive = false
        retryCount = 0
        loadingMessage = "セッションを更新中..."
        currentUrl = logoutUrl
        webView?.loadUrl(logoutUrl)
    }

    fun startDirectWebViewFallback() {
        directWebViewFallbackActive = true
        retryCount = 0
        loadingMessage = "コースへ移動中..."
        currentUrl = courseUrl
        webViewRef?.loadUrl(courseUrl)
    }

    fun performCourseContentsFetch() {
        scope.launch {
            isLoading = true
            directWebViewFallbackActive = false
            loadingMessage = "データをフェッチ中..."
            val html = HtmlFetcher.fetchHtml(courseUrl)
            if (html != null) {
                try {
                    val extractedFolders = WebClassHtmlParser.parseCourseFolders(html, "https://webclass.kosen-k.go.jp")
                    if (extractedFolders.isNotEmpty()) {
                        completeCourseContentsLoad(extractedFolders)
                        return@launch
                    }
                } catch (e: Exception) {
                    Log.e("WebClass", "Fetch parse error for course contents: $e")
                }
            }
            
            // Fallback to WebView
            Log.d("WebClass", "Course contents fetch failed, trying course URL before logout fallback")
            loadingMessage = "セッションを更新中..."
            startDirectWebViewFallback()
        }
    }

    // Safety timeout: after 15 seconds of loading, show login WebView
    LaunchedEffect(isLoading) {
        if (isLoading) {
            delay(15000)
            if (isLoading) {
                isLoading = false
                showWebView = true
                directWebViewFallbackActive = false
                retryCount = 0
                currentUrl = logoutUrl
            }
        }
    }

    LaunchedEffect(Unit) {
        val speedUp = themePreferences.enableSpeedUpFlow.first()
        if (speedUp && !hasCache) {
            performCourseContentsFetch()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (showWebView) {
             WebClassWebView(
                url = currentUrl,
                modifier = Modifier.fillMaxSize(),
                onWebViewCreated = { webViewRef = it },
                onPageFinished = { webView, url ->
                    scope.launch {
                        if (url.contains("logout.php")) {
                            currentUrl = samlLoginUrl
                            retryCount = 1
                            return@launch
                        }

                        if (url.contains("login.php")) {
                            if (!url.contains("auth_mode=SAML") && retryCount == 0) {
                                currentUrl = samlLoginUrl
                                retryCount = 1
                            } else if (retryCount == 1) {
                                currentUrl = courseUrl
                                retryCount = 2
                            } else {
                                isLoading = false
                            }
                            return@launch
                        }

                        webView.evaluateJavascript(JavaScriptBridge.detectPageType()) { typeResult ->
                             val pageType = typeResult?.replace("\"", "") ?: "UNKNOWN"
                             if (pageType == "HOME") {
                                 // Redirect back to course if we ended up home
                                 currentUrl = courseUrl
                             }
                        }
                    }
                }
            )
        } else {
            // Background WebView
            WebClassWebView(
                url = currentUrl,
                modifier = Modifier.height(1.dp).alpha(0f),
                lightweight = true,
                blockPostRequests = enableSpeedUp,
                onWebViewCreated = { webViewRef = it },
                onPageFinished = { webView, url ->
                    scope.launch {
                        // "Simultaneous access" error check
                        webView.evaluateJavascript("(function(){ return document.body.innerText.indexOf('同時') !== -1; })();") { result ->
                             if (result == "true") {
                                 isLoading = true
                                 webView.reload()
                                 return@evaluateJavascript
                             }
                             
                             if (url.contains("logout.php")) {
                                 loadingMessage = "再ログイン中..."
                                 directWebViewFallbackActive = false
                                 currentUrl = samlLoginUrl
                                 retryCount = 1
                                 return@evaluateJavascript
                             }

                             if (url.contains("login.php")) {
                                 if (directWebViewFallbackActive && retryCount == 0) {
                                     startLogoutFallback(webView)
                                     return@evaluateJavascript
                                 }
                                 loadingMessage = "ログイン処理中..."
                                 if (!url.contains("auth_mode=SAML") && retryCount == 0) {
                                     currentUrl = samlLoginUrl
                                     retryCount = 1
                                 } else if (retryCount == 1) {
                                     loadingMessage = "コースへ移動中..."
                                     currentUrl = courseUrl
                                     retryCount = 2
                                 } else if (retryCount >= 2) {
                                     showWebView = true
                                     isLoading = false
                                 }
                                 return@evaluateJavascript
                             }

                             webView.evaluateJavascript(JavaScriptBridge.detectPageType()) { typeResult ->
                                 val pageType = typeResult?.replace("\"", "") ?: "UNKNOWN"
                                 if (pageType == "COURSE" || pageType == "COURSE_LIST") { // Accept LIST if redirected
                                     loadingMessage = "データを解析中..."
                                     webView.evaluateJavascript(JavaScriptBridge.extractCourseFolders()) { jsonResult ->
                                         var didLoadFolders = false
                                         try {
                                             if (jsonResult != null && jsonResult != "null" && jsonResult != "[]") {
                                                val cleanJson = if (jsonResult.startsWith("\"") && jsonResult.endsWith("\"")) {
                                                    jsonResult.substring(1, jsonResult.length - 1).replace("\\\"", "\"").replace("\\\\", "\\")
                                                } else {
                                                    jsonResult
                                                }
                                                val extractedFolders: List<CourseFolder> = gson.fromJson(cleanJson, Array<CourseFolder>::class.java).toList()
                                                if (extractedFolders.isNotEmpty()) {
                                                    completeCourseContentsLoad(extractedFolders)
                                                    didLoadFolders = true
                                                }
                                             }
                                         } catch (e: Exception) {
                                             Log.e("WebClass", "Error parsing folders: ${e.message}")
                                         }
                                         if (!didLoadFolders) {
                                             if (directWebViewFallbackActive) {
                                                 startLogoutFallback(webView)
                                             } else {
                                                 isLoading = false
                                                 openAutoContentIfNeeded()
                                             }
                                         }
                                     }
                                 } else if (pageType == "HOME" && retryCount > 0) {
                                     currentUrl = courseUrl
                                 } else if (directWebViewFallbackActive) {
                                     startLogoutFallback(webView)
                                 }
                             }
                        }
                    }
                }
            )
        }

        // Native UI
        if (!showWebView) {
            val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
            
            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
                modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                topBar = {
                    TopAppBar(
                        windowInsets = WindowInsets.displayCutout.union(WindowInsets.statusBars),
                        title = { 
                            Text(
                                text = courseTitle,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            ) 
                        },
                        navigationIcon = {
                            IconButton(onClick = onBackClick) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        val speedUp = themePreferences.enableSpeedUpFlow.first()
                                        if (speedUp) {
                                            performCourseContentsFetch()
                                        } else {
                                            isLoading = true
                                            loadingMessage = "セッションを更新中..."
                                            retryCount = 0
                                            directWebViewFallbackActive = false
                                            currentUrl = logoutUrl
                                            webViewRef?.loadUrl(currentUrl)
                                        }
                                    }
                                },
                                enabled = !isLoading
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "再読み込み")
                            }
                        },
                        scrollBehavior = scrollBehavior
                    )
                },
                floatingActionButton = {
                    val contentsLoaded = folders.isNotEmpty()

                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        val reportsContainerColor = if (contentsLoaded) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        }
                        val reportsContentColor = if (contentsLoaded) {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        }

                        // Reports FAB
                        FloatingActionButton(
                            onClick = {
                                if (contentsLoaded) {
                                    onReportsClick(courseId)
                                }
                            },
                            containerColor = reportsContainerColor,
                            contentColor = reportsContentColor
                        ) {
                            Icon(Icons.Outlined.Assignment, contentDescription = "マイレポート")
                        }

                        val gradesContainerColor = if (contentsLoaded) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        }
                        val gradesContentColor = if (contentsLoaded) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        }

                        // Grades FAB
                        FloatingActionButton(
                            onClick = {
                                if (contentsLoaded) {
                                    onScoresClick(courseId)
                                }
                            },
                            containerColor = gradesContainerColor,
                            contentColor = gradesContentColor
                        ) {
                            Icon(Icons.Outlined.Leaderboard, contentDescription = "成績")
                        }
                    }
                }
            ) { padding ->
                if (isLoading) {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(padding)) {
                        val isWideScreen = maxWidth > 600.dp
                        val columns = if (isWideScreen) androidx.compose.foundation.lazy.grid.GridCells.Adaptive(350.dp) else androidx.compose.foundation.lazy.grid.GridCells.Fixed(1)
                        
                        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                            columns = columns,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Search & filter skeleton (full span)
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                Column(modifier = Modifier.padding(bottom = 16.dp)) {
                                    // Search bar
                                    Box(modifier = Modifier.fillMaxWidth().height(56.dp).clip(MaterialTheme.shapes.medium).shimmerEffect())
                                    Spacer(modifier = Modifier.height(12.dp))
                                    // Filter chips row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(modifier = Modifier.width(56.dp).height(32.dp).clip(MaterialTheme.shapes.small).shimmerEffect())
                                        Box(modifier = Modifier.width(64.dp).height(32.dp).clip(MaterialTheme.shapes.small).shimmerEffect())
                                        Box(modifier = Modifier.width(72.dp).height(32.dp).clip(MaterialTheme.shapes.small).shimmerEffect())
                                        Box(modifier = Modifier.width(52.dp).height(32.dp).clip(MaterialTheme.shapes.small).shimmerEffect())
                                        Spacer(modifier = Modifier.weight(1f))
                                        Box(modifier = Modifier.width(64.dp).height(32.dp).clip(MaterialTheme.shapes.small).shimmerEffect())
                                    }
                                }
                            }
                            // First folder header skeleton (full span)
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(modifier = Modifier.width(4.dp).height(20.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                                        Box(modifier = Modifier.width(120.dp).height(18.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                                    }
                                    Box(modifier = Modifier.width(36.dp).height(20.dp).clip(MaterialTheme.shapes.small).shimmerEffect())
                                }
                            }
                            items(3) {
                                SkeletonCourseContentCard()
                            }
                            // Second folder header skeleton (full span)
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(modifier = Modifier.width(4.dp).height(20.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                                        Box(modifier = Modifier.width(100.dp).height(18.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                                    }
                                    Box(modifier = Modifier.width(36.dp).height(20.dp).clip(MaterialTheme.shapes.small).shimmerEffect())
                                }
                            }
                            items(2) {
                                SkeletonCourseContentCard()
                            }
                        }
                    }
                } else if (folders.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                         Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("コンテンツが見つかりません")
                        }
                    }

                } else {
                    BoxWithConstraints {
                        val isWideScreen = maxWidth > 600.dp
                        val columns = if (isWideScreen) androidx.compose.foundation.lazy.grid.GridCells.Adaptive(350.dp) else androidx.compose.foundation.lazy.grid.GridCells.Fixed(1)
                        
                        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                            columns = columns,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Search & Filter Header (Full Span)
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                Column(modifier = Modifier.padding(bottom = 16.dp)) {
                                    OutlinedTextField(
                                        value = searchQuery,
                                        onValueChange = { courseViewModel.setSearchQuery(it) },
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = { Text("コンテンツを検索") },
                                        leadingIcon = { Icon(Icons.Default.Search, null) },
                                        trailingIcon = {
                                            if (searchQuery.isNotEmpty()) {
                                                IconButton(onClick = { courseViewModel.setSearchQuery("") }) {
                                                    Icon(Icons.Default.Close, null)
                                                }
                                            }
                                        },
                                        singleLine = true,
                                        shape = MaterialTheme.shapes.medium
                                    )
                                    
                                    Spacer(modifier = Modifier.height(12.dp))
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        androidx.compose.foundation.lazy.LazyRow(
                                            modifier = Modifier.weight(1f),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            item {
                                                FilterChip(
                                                    selected = selectedCategory == null,
                                                    onClick = { courseViewModel.setSelectedCategory(null) },
                                                    label = { Text("すべて") },
                                                    leadingIcon = if (selectedCategory == null) {
                                                        { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                                                    } else null
                                                )
                                            }
                                            
                                            items(allCategories) { category ->
                                                FilterChip(
                                                    selected = selectedCategory == category,
                                                    onClick = { courseViewModel.setSelectedCategory(category) },
                                                    label = { Text(category) },
                                                    leadingIcon = if (selectedCategory == category) {
                                                        { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                                                    } else null
                                                )
                                            }
                                        }
                                        
                                        FilterChip(
                                            selected = showOnlyValid,
                                            onClick = { courseViewModel.setShowOnlyValid(!showOnlyValid) },
                                            label = { Text("有効のみ") },
                                            leadingIcon = if (showOnlyValid) {
                                                { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                                            } else null
                                        )
                                    }
                                }
                            }
                            
                            // Use filteredFolders instead of folders
                            filteredFolders.forEach { folder ->
                                // Folder Section Header (Full Span)
                                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
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
                                                text = folder.title.ifEmpty { "名称未設定のグループ" },
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Surface(
                                            shape = MaterialTheme.shapes.small,
                                            color = MaterialTheme.colorScheme.secondaryContainer
                                        ) {
                                            Text(
                                                text = "${folder.items.size}件",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }

                                // Items (1 column span on grid)
                                items(folder.items, key = { it.id }) { item ->
                                    CourseContentCard(
                                        item = item,
                                        courseTitle = courseTitle,
                                        isBookmarked = bookmarkedIds.any { it.id == item.id },
                                        showBookmark = enableBookmarks,
                                        onClick = { 
                                            if (item.url.isNotEmpty()) {
                                                onContentClick(item.url, item.title)
                                            }
                                        },
                                        onDetailClick = onDetailClick,
                                        onHistoryClick = onHistoryClick,
                                        onBookmarkClick = {
                                            bookmarkRepository.toggleBookmark(
                                                Bookmark(
                                                    id = item.id,
                                                    title = item.title,
                                                    url = item.url,
                                                    category = item.category,
                                                    courseTitle = courseTitle,
                                                    detailUrl = item.detailUrl,
                                                    availablePeriod = item.availablePeriod,
                                                    isDisabled = item.isDisabled
                                                )
                                            )
                                        }
                                    )
                                }
                                
                                // Spacer between folders (Full Span)
                                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                            
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                Spacer(modifier = Modifier.height(24.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CourseContentCard(
    item: CourseItem,
    courseTitle: String,
    isBookmarked: Boolean,
    onClick: () -> Unit,
    onDetailClick: (String) -> Unit,
    onHistoryClick: (String) -> Unit,
    showBookmark: Boolean,
    onBookmarkClick: () -> Unit
) {
    val context = LocalContext.current
    val isTest = item.category.contains("テスト") || item.category.contains("Test") || item.category.contains("試験")
    val isReport = item.category.contains("レポート") || item.category.contains("Report") || item.category.contains("課題")
    val isMaterial = item.category.contains("教材") || item.category.contains("Material") || item.category.contains("資料")
    val isSelfStudy = item.category.contains("自習") || item.category.contains("Self-study")
    val isQuestionnaire = item.category.contains("アンケート") || item.category.contains("Questionnaire")
    
    val icon = when {
        isTest -> Icons.Outlined.Quiz
        isReport -> Icons.Outlined.Assignment
        isSelfStudy -> Icons.Outlined.School
        isQuestionnaire -> Icons.Outlined.ListAlt
        isMaterial -> Icons.Outlined.MenuBook
        else -> Icons.Outlined.Description
    }

    val accentColor = when {
        isTest -> MaterialTheme.colorScheme.error
        isReport -> MaterialTheme.colorScheme.tertiary
        isSelfStudy -> MaterialTheme.colorScheme.secondary
        isQuestionnaire -> MaterialTheme.colorScheme.primary
        isMaterial -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }

    val categoryBadgeColor = when {
        isTest -> MaterialTheme.colorScheme.errorContainer
        isReport -> MaterialTheme.colorScheme.tertiaryContainer
        isSelfStudy -> MaterialTheme.colorScheme.secondaryContainer
        isQuestionnaire -> MaterialTheme.colorScheme.primaryContainer
        isMaterial -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    
    val categoryTextColor = when {
        isTest -> MaterialTheme.colorScheme.onErrorContainer
        isReport -> MaterialTheme.colorScheme.onTertiaryContainer
        isSelfStudy -> MaterialTheme.colorScheme.onSecondaryContainer
        isQuestionnaire -> MaterialTheme.colorScheme.onPrimaryContainer
        isMaterial -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val cardAlpha = if (item.isDisabled) 0.6f else 1f

    OutlinedCard(
        onClick = onClick,
        enabled = !item.isDisabled,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer(alpha = cardAlpha)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Accent strip
            Surface(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight(),
                color = if (item.isDisabled) MaterialTheme.colorScheme.outlineVariant else accentColor
            ) {}
            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, end = 16.dp, top = 14.dp, bottom = 14.dp)
            ) {
                // Top row: Category badge + NEW + actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Category badge
                        if (item.category.isNotEmpty()) {
                            Surface(
                                shape = MaterialTheme.shapes.extraSmall,
                                color = categoryBadgeColor
                            ) {
                                Text(
                                    text = item.category,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = categoryTextColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        // NEW badge
                        if (item.isNew) {
                            Surface(
                                shape = MaterialTheme.shapes.extraSmall,
                                color = MaterialTheme.colorScheme.errorContainer
                            ) {
                                Text(
                                    text = "NEW",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    
                    // Action icons
                    Row {
                        if (!item.historyUrl.isNullOrEmpty()) {
                            IconButton(
                                onClick = { onHistoryClick(item.historyUrl) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.History,
                                    contentDescription = "履歴",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                        if (showBookmark) {
                            IconButton(
                                onClick = onBookmarkClick,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    contentDescription = if (isBookmarked) "ブックマーク解除" else "ブックマーク",
                                    modifier = Modifier.size(18.dp),
                                    tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Title row with icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (item.isDisabled) MaterialTheme.colorScheme.onSurfaceVariant else accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (item.isDisabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                
                // Metadata
                val hasMetadata = !item.availablePeriod.isNullOrEmpty() || item.usageCount != null || item.isDisabled
                if (hasMetadata) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (!item.availablePeriod.isNullOrEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Event, null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.outline
                                )
                                Text(
                                    text = item.availablePeriod,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (item.usageCount != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.History, null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.outline
                                )
                                Text(
                                    text = "${item.usageCount}回利用",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (item.isDisabled) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Block, null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = "利用不可",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                // Action Buttons
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = onClick,
                        enabled = !item.isDisabled,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("開始", fontSize = 13.sp)
                    }

                    if (!item.detailUrl.isNullOrEmpty()) {
                        OutlinedButton(
                            onClick = { onDetailClick(item.detailUrl) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Icon(Icons.Outlined.Info, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("詳細", fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun Modifier.alpha(alpha: Float) = this.then(
    Modifier.graphicsLayer(alpha = alpha)
)

@Composable
fun SkeletonCourseContentCard() {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Surface(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight(),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {}
            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, end = 16.dp, top = 14.dp, bottom = 14.dp)
            ) {
                // Top row: Category badge + NEW badge + bookmark
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.width(52.dp).height(20.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                        Box(modifier = Modifier.width(36.dp).height(20.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                    }
                    Box(modifier = Modifier.size(20.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Title row with icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(modifier = Modifier.size(22.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                    Box(modifier = Modifier.weight(1f).height(16.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Metadata: period
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(modifier = Modifier.size(14.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                    Box(modifier = Modifier.width(160.dp).height(12.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Metadata: usage count
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(modifier = Modifier.size(14.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                    Box(modifier = Modifier.width(60.dp).height(12.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(modifier = Modifier.weight(1f).height(36.dp).clip(MaterialTheme.shapes.small).shimmerEffect())
                    Box(modifier = Modifier.weight(1f).height(36.dp).clip(MaterialTheme.shapes.small).shimmerEffect())
                }
            }
        }
    }
}
