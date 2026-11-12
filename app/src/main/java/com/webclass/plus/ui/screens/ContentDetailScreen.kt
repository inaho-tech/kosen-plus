package com.webclass.plus.ui.screens

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Info
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.webclass.plus.data.models.ContentDetail
import com.webclass.plus.ui.components.WebClassWebView
import com.webclass.plus.util.JavaScriptBridge
import com.webclass.plus.ui.viewmodels.ContentDetailViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.draw.clip
import com.webclass.plus.ui.components.shimmerEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentDetailScreen(
    url: String,
    onBackClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel: ContentDetailViewModel = viewModel()
    
    // Ensure URL is absolute
    val targetUrl = if (url.startsWith("http")) url else "https://webclass.kosen-k.go.jp/webclass/$url"
    
    // Initialize ViewModel — skips load if we already have cached data
    LaunchedEffect(targetUrl) {
        viewModel.initIfNeeded(targetUrl)
    }
    
    val data by viewModel.data.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val currentUrl by viewModel.currentUrl.collectAsState()
    
    val needsLoad = !viewModel.hasCachedData() || isLoading
    
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    
    val gson = remember { Gson() }
    val scope = rememberCoroutineScope()

    // Timeout
    LaunchedEffect(isLoading) {
        if (isLoading) {
            delay(15000)
            if (isLoading) {
                viewModel.setLoading(false)
            }
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    
    Box(modifier = Modifier.fillMaxSize()) {
        if (needsLoad) {
            WebClassWebView(
                url = currentUrl,
                modifier = Modifier.height(1.dp).alpha(0f),
                lightweight = true,
                onWebViewCreated = { webViewRef = it },
                onPageFinished = { webView, url ->
                     scope.launch {
                         if (url.contains("login.php")) {
                             if (!url.contains("auth_mode=SAML")) {
                                 viewModel.setCurrentUrl("https://webclass.kosen-k.go.jp/webclass/login.php?auth_mode=SAML")
                             }
                             return@launch
                         }

                         webView.evaluateJavascript(JavaScriptBridge.extractContentDetail()) { jsonResult ->
                             try {
                                 if (jsonResult != null && jsonResult != "null") {
                                     val cleanJson = if (jsonResult.startsWith("\"") && jsonResult.endsWith("\"")) {
                                         jsonResult.substring(1, jsonResult.length - 1).replace("\\\"", "\"").replace("\\\\", "\\")
                                     } else {
                                         jsonResult
                                     }
                                     val detail: ContentDetail = gson.fromJson(cleanJson, ContentDetail::class.java)
                                     if (detail.title.isNotEmpty()) {
                                         viewModel.setData(detail)
                                         viewModel.setLoading(false)
                                     }
                                 }
                             } catch (e: Exception) {
                                 Log.e("WebClass", "Detail Parse Error: ${e.message}")
                             }
                             if (data == null && !isLoading) {
                                 // Retry logic could go here
                             } else {
                                 viewModel.setLoading(false)
                             }
                         }
                     }
                }
            )
        }

        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
            topBar = {
                MediumTopAppBar(
                    windowInsets = WindowInsets.displayCutout.union(WindowInsets.statusBars),
                    title = { 
                        Text(
                            text = data?.title ?: "詳細情報",
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        ) 
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            viewModel.clearDataForReload()
                            webViewRef?.reload()
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = "再読み込み")
                        }
                        if (data?.execUrl?.isNotEmpty() == true) {
                            IconButton(onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(data!!.execUrl))
                                context.startActivity(intent)
                            }) {
                                Icon(Icons.Default.OpenInNew, contentDescription = "ブラウザで開く")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.mediumTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    scrollBehavior = scrollBehavior
                )
            },
        ) { padding ->
            if (isLoading) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = padding,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Skeleton chips row
                    item {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(modifier = Modifier.width(80.dp).height(32.dp).clip(MaterialTheme.shapes.small).shimmerEffect())
                            Box(modifier = Modifier.width(80.dp).height(32.dp).clip(MaterialTheme.shapes.small).shimmerEffect())
                        }
                    }
                    // Skeleton description
                    item {
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            Box(modifier = Modifier.width(40.dp).height(18.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                            Spacer(modifier = Modifier.height(8.dp))
                            ElevatedCard(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(modifier = Modifier.fillMaxWidth(0.9f).height(14.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                                    Box(modifier = Modifier.fillMaxWidth(0.7f).height(14.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                                    Box(modifier = Modifier.fillMaxWidth(0.5f).height(14.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                                }
                            }
                        }
                    }
                    // Skeleton info items
                    item {
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            Box(modifier = Modifier.width(60.dp).height(18.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedCard(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column {
                                    repeat(5) { index ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(modifier = Modifier.width(80.dp).height(14.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                                            Box(modifier = Modifier.width(120.dp).height(14.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                                        }
                                        if (index < 4) {
                                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // Skeleton button
                    item {
                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().height(56.dp).clip(MaterialTheme.shapes.medium).shimmerEffect())
                    }
                }
            } else if (data == null) {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("情報の取得に失敗しました", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "ネットワーク接続を確認してください",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        FilledTonalButton(onClick = { 
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl))
                            context.startActivity(intent)
                        }) {
                            Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("ブラウザで開く")
                        }
                    }
                }
            } else {
                val detail = data!!
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = padding,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Props and Chips
                    item {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text(detail.type) },
                                    icon = {
                                        Icon(Icons.Outlined.Info, null, modifier = Modifier.size(16.dp))
                                    }
                                )
                                
                                val (statusLabel, statusColor, _) = if (detail.isCurrentlyAvailable) {
                                    Triple("利用可能", MaterialTheme.colorScheme.primary, null)
                                } else {
                                    Triple("期間外/不可", MaterialTheme.colorScheme.error, null)
                                }
                                
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text(statusLabel) },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = androidx.compose.ui.graphics.Color.Transparent,
                                        labelColor = statusColor,
                                        iconContentColor = statusColor
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        width = 1.dp,
                                        color = statusColor.copy(alpha = 0.5f)
                                    )
                                )
                            }
                        }
                    }

                    // Description
                    if (detail.description.isNotEmpty()) {
                        item {
                            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                                Text(
                                    "説明",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                                )
                                ElevatedCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.elevatedCardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                    )
                                ) {
                                    Text(
                                        detail.description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Info Items
                    if (detail.infoItems.isNotEmpty()) {
                        item {
                            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                                Text(
                                    "詳細情報",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                                )
                                OutlinedCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.outlinedCardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    )
                                ) {
                                    Column {
                                        detail.infoItems.forEachIndexed { index, item ->
                                            ListItem(
                                                headlineContent = { 
                                                    Text(item.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) 
                                                },
                                                trailingContent = {
                                                    Text(
                                                        item.value, 
                                                        style = MaterialTheme.typography.bodyMedium, 
                                                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                                        modifier = Modifier.widthIn(max = 200.dp)
                                                    )
                                                },
                                                colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                                            )
                                            if (index < detail.infoItems.size - 1) {
                                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Actions
                    if (!detail.execUrl.isNullOrEmpty()) {
                        item {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Button(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(detail.execUrl))
                                        context.startActivity(intent)
                                    },
                                    modifier = Modifier.fillMaxWidth().height(56.dp)
                                ) {
                                    Icon(Icons.Default.OpenInNew, null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("ブラウザで開く (実行)")
                                }
                            }
                        }
                    }
                    
                    item {
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}
