package com.webclass.plus.ui.screens

import android.util.Log
import android.webkit.WebView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.webclass.plus.data.models.ContentHistory
import com.webclass.plus.data.models.ContentResults
import com.webclass.plus.ui.components.WebClassWebView
import com.webclass.plus.util.JavaScriptBridge
import com.webclass.plus.util.WebViewStyles
import com.webclass.plus.util.ThemePreferences
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.lifecycle.viewmodel.compose.viewModel
import com.webclass.plus.ui.viewmodels.ContentHistoryViewModel
import com.webclass.plus.ui.components.shimmerEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentHistoryScreen(
    url: String,
    onBackClick: () -> Unit = {},
    onLinkClick: (String) -> Unit = {}
) {
    val viewModel: ContentHistoryViewModel = viewModel()
    
    // Initialize ViewModel — skips load if we already have data for this URL
    LaunchedEffect(url) {
        viewModel.initIfNeeded(url)
    }
    
    val historyData by viewModel.historyData.collectAsState()
    val resultsData by viewModel.resultsData.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val currentUrl by viewModel.currentUrl.collectAsState()
    val pageType by viewModel.pageType.collectAsState()
    
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    
    val needsLoad = !viewModel.hasCachedDataFor(url) || isLoading
    
    val context = LocalContext.current
    val themePreferences = remember { ThemePreferences(context) }
    val currentTheme by themePreferences.themeFlow.collectAsState(initial = com.webclass.plus.ui.theme.AppTheme.SYSTEM)
    val enableLightweightMode by themePreferences.enableLightweightModeFlow.collectAsState(initial = false)
    
    val gson = remember { Gson() }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.displayCutout.union(WindowInsets.statusBars),
                title = { Text(text = "履歴・結果", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { 
                        viewModel.setLoading(true)
                        webViewRef?.reload() 
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Invisible WebView to fetch data — only when no cached data
            if (needsLoad) {
                Box(modifier = Modifier.height(0.dp)) {
                    WebClassWebView(
                        url = currentUrl,
                        modifier = Modifier.fillMaxWidth().height(1.dp).graphicsLayer { alpha = 0f },
                        lightweight = true,
                        onWebViewCreated = { webViewRef = it },
                        onPageFinished = { webView, pageUrl ->
                            val css = WebViewStyles.getThemeCss(currentTheme)
                            val jsCode = WebViewStyles.injectCss(css)
                            webView.evaluateJavascript(jsCode, null)
                            
                            webView.evaluateJavascript(JavaScriptBridge.detectPageType()) { typeResult ->
                                 val type = typeResult?.replace("\"", "") ?: "UNKNOWN"
                                 viewModel.setPageType(type)
                                 
                                 if (type == "RESULTS") {
                                     webView.evaluateJavascript(JavaScriptBridge.extractContentResults()) { jsonResult ->
                                         if (jsonResult != null && jsonResult != "null") {
                                             try {
                                                 val cleanJson = if (jsonResult.startsWith("\"") && jsonResult.endsWith("\"")) {
                                                     jsonResult.substring(1, jsonResult.length - 1).replace("\\\"", "\"").replace("\\\\", "\\")
                                                 } else {
                                                     jsonResult
                                                 }
                                                 val data = gson.fromJson(cleanJson, ContentResults::class.java)
                                                 viewModel.setResultsData(data)
                                                 viewModel.setLoading(false)
                                             } catch (e: Exception) {
                                                 android.util.Log.e("ContentHistory", "Error parsing results JSON", e)
                                                 viewModel.setLoading(false)
                                             }
                                         }
                                     }
                                 } else {
                                     webView.evaluateJavascript(JavaScriptBridge.extractContentHistory()) { jsonResult ->
                                         if (jsonResult != null && jsonResult != "null") {
                                             try {
                                                 val cleanJson = if (jsonResult.startsWith("\"") && jsonResult.endsWith("\"")) {
                                                     jsonResult.substring(1, jsonResult.length - 1).replace("\\\"", "\"").replace("\\\\", "\\")
                                                 } else {
                                                     jsonResult
                                                 }
                                                 val data = gson.fromJson(cleanJson, ContentHistory::class.java)
                                                 viewModel.setHistoryData(data)
                                                 viewModel.setLoading(false)
                                             } catch (e: Exception) {
                                                 android.util.Log.e("ContentHistory", "Error parsing history JSON", e)
                                                 viewModel.setLoading(false)
                                             }
                                         } else {
                                             viewModel.setLoading(false)
                                         }
                                     }
                                 }
                            }
                        }
                    )
                }
            }

            if (isLoading) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Skeleton header card
                    item {
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Surface(modifier = Modifier.width(4.dp).height(76.dp), color = MaterialTheme.colorScheme.surfaceVariant) {}
                                Row(
                                    modifier = Modifier.weight(1f).padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Box(modifier = Modifier.size(44.dp).clip(MaterialTheme.shapes.small).shimmerEffect())
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Box(modifier = Modifier.fillMaxWidth(0.7f).height(18.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                                        Box(modifier = Modifier.fillMaxWidth(0.4f).height(14.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                                    }
                                }
                            }
                        }
                    }
                    // Skeleton info card
                    item {
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Surface(modifier = Modifier.width(4.dp).height(48.dp), color = MaterialTheme.colorScheme.surfaceVariant) {}
                                Row(modifier = Modifier.weight(1f).padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Box(modifier = Modifier.size(20.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                                    Box(modifier = Modifier.fillMaxWidth(0.8f).height(14.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                                }
                            }
                        }
                    }
                    // Skeleton section header
                    item {
                        Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.width(4.dp).height(18.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                            Box(modifier = Modifier.width(80.dp).height(16.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                        }
                    }
                    // Skeleton row cards
                    items(4) {
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Surface(modifier = Modifier.width(4.dp).height(120.dp), color = MaterialTheme.colorScheme.surfaceVariant) {}
                                Column(modifier = Modifier.weight(1f).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(modifier = Modifier.width(40.dp).height(18.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                                    repeat(3) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Box(modifier = Modifier.width(80.dp).height(14.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                                            Box(modifier = Modifier.width(120.dp).height(14.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                val rawTabs = if (pageType == "RESULTS") resultsData?.tabs else historyData?.tabs
                val tabs = rawTabs?.filter { 
                    !it.label.contains("Menu", ignoreCase = true) && 
                    !it.label.contains("メニュー") 
                }
                
                // Tab Row with modern styling
                if (tabs != null && tabs.isNotEmpty()) {
                    SecondaryTabRow(
                        selectedTabIndex = tabs.indexOfFirst { it.isActive }.takeIf { it != -1 } ?: 0
                    ) {
                        tabs.forEachIndexed { index, tab ->
                            Tab(
                                selected = tab.isActive,
                                onClick = {
                                    if (!tab.isActive && tab.href.isNotEmpty()) {
                                        viewModel.setLoading(true)
                                        viewModel.setCurrentUrl(tab.href)
                                    }
                                },
                                text = { 
                                    Text(
                                        text = tab.label,
                                        fontWeight = if (tab.isActive) FontWeight.Bold else FontWeight.Normal
                                    ) 
                                },
                                icon = {
                                    val icon = when {
                                        tab.label.contains("利用", ignoreCase = true) -> Icons.Outlined.History
                                        tab.label.contains("結果", ignoreCase = true) -> Icons.Outlined.Assessment
                                        else -> Icons.Outlined.Article
                                    }
                                    Icon(icon, null, modifier = Modifier.size(20.dp))
                                }
                            )
                        }
                    }
                }
                
                // Content
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (tabs != null && tabs.isNotEmpty()) {
                        val pagerState = rememberPagerState(
                            initialPage = tabs.indexOfFirst { it.isActive }.takeIf { it != -1 } ?: 0,
                            pageCount = { tabs.size }
                        )

                        // Sync Tab -> Pager
                        LaunchedEffect(tabs) {
                            val activeIndex = tabs.indexOfFirst { it.isActive }
                            if (activeIndex != -1 && pagerState.currentPage != activeIndex) {
                                if (enableLightweightMode) {
                                    pagerState.scrollToPage(activeIndex)
                                } else {
                                    pagerState.animateScrollToPage(activeIndex)
                                }
                            }
                        }

                        // Sync Pager -> URL
                        LaunchedEffect(pagerState.currentPage) {
                            if (!pagerState.isScrollInProgress) return@LaunchedEffect
                            
                            val tab = tabs.getOrNull(pagerState.currentPage)
                            if (tab != null && !tab.isActive && tab.href.isNotEmpty() && tab.href != currentUrl) {
                                viewModel.setLoading(true)
                                viewModel.setCurrentUrl(tab.href)
                            }
                        }
                        
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            userScrollEnabled = true
                        ) { page ->
                            if (page == tabs.indexOfFirst { it.isActive }) {
                                if (pageType == "RESULTS") {
                                    resultsData?.let { data ->
                                        ContentResultsScreen(
                                            results = data,
                                            onLinkClick = onLinkClick
                                        )
                                    }
                                } else {
                                    historyData?.let { data ->
                                        HistoryContent(data = data, enableLightweightMode = enableLightweightMode)
                                    } ?: run {
                                        EmptyHistoryState()
                                    }
                                }
                            } else {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                    } else {
                        if (pageType == "RESULTS") {
                            resultsData?.let { data ->
                                ContentResultsScreen(
                                    results = data,
                                    onLinkClick = onLinkClick
                                )
                            }
                        } else {
                            historyData?.let { data ->
                                HistoryContent(data = data, enableLightweightMode = enableLightweightMode)
                            } ?: run {
                                EmptyHistoryState()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryContent(data: ContentHistory, enableLightweightMode: Boolean) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header Card
        item {
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        modifier = Modifier.width(4.dp).fillMaxHeight(),
                        color = MaterialTheme.colorScheme.secondary
                    ) {}
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(44.dp),
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = data.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                shape = MaterialTheme.shapes.extraSmall,
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = data.type,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // Usage Summary Card
        if (data.usageSummary.isNotEmpty()) {
            item {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            modifier = Modifier.width(4.dp).fillMaxHeight(),
                            color = MaterialTheme.colorScheme.tertiary
                        ) {}
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = data.usageSummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
        
        // History Records Section Title
        if (data.rows.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(4.dp).height(18.dp)
                    ) {}
                    Text(
                        text = "アクセス履歴",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "${data.rows.size}件",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            
            // History Rows
            itemsIndexed(data.rows, key = { index, _ -> "row_$index" }) { index, row ->
                HistoryRowCard(
                    modifier = if (enableLightweightMode) Modifier else Modifier.animateItem(),
                    rowIndex = index + 1,
                    columns = data.columns,
                    cells = row
                )
            }
        } else {
            item {
                EmptyHistoryState()
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun HistoryRowCard(
    modifier: Modifier = Modifier,
    rowIndex: Int,
    columns: List<String>,
    cells: List<String>
) {
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Surface(
                modifier = Modifier.width(4.dp).fillMaxHeight(),
                color = MaterialTheme.colorScheme.primary
            ) {}
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Row number
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "#$rowIndex",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                
                // Data grid
                cells.forEachIndexed { index, cell ->
                    if (index < columns.size && columns[index].isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = columns[index],
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(0.4f)
                            )
                            Text(
                                text = cell,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(0.6f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHistoryState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(72.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.HistoryToggleOff,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "履歴はありません",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "まだこのコンテンツにアクセスした記録がありません",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}
