package com.webclass.plus.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.webclass.plus.data.models.Bookmark
import com.webclass.plus.data.repository.BookmarkRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(
    onItemClick: (String, String) -> Unit = { _, _ -> },
    onDetailClick: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember { BookmarkRepository(context) }
    val allBookmarks by repository.bookmarks.collectAsState()
    val bookmarks = remember(allBookmarks) {
        allBookmarks.filter { it.category != "HIDDEN_COURSE" }
    }
    
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.displayCutout.union(WindowInsets.statusBars),
                title = { Text("ブックマーク", fontWeight = FontWeight.Bold) },
                colors = appTopBarColors(),
                scrollBehavior = scrollBehavior,
                // windowInsets = WindowInsets(0.dp)
            )
        },
        // contentWindowInsets = WindowInsets(0.dp)
    ) { padding ->
        if (bookmarks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.BookmarkBorder,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "ブックマークがありません",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(
                    start = 0.dp,
                    top = 16.dp,
                    end = 0.dp,
                    bottom = 16.dp,
                )
            ) {
                items(bookmarks, key = { it.id }) { bookmark ->
                    BookmarkCard(
                        modifier = Modifier.animateItem(),
                        bookmark = bookmark,
                        onClick = { onItemClick(bookmark.url, bookmark.title) },
                        onDetailClick = { bookmark.detailUrl?.let { onDetailClick(it) } },
                        onDelete = { repository.removeBookmark(bookmark.id) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
fun BookmarkCard(
    modifier: Modifier = Modifier,
    bookmark: Bookmark,
    onClick: () -> Unit,
    onDetailClick: () -> Unit,
    onDelete: () -> Unit
) {
    val isTest = bookmark.category.contains("テスト") || bookmark.category.contains("Test")
    val isReport = bookmark.category.contains("レポート") || bookmark.category.contains("Report") || bookmark.category.contains("課題")
    val isMaterial = bookmark.category.contains("教材") || bookmark.category.contains("Material")
    
    val icon = when {
        isTest -> Icons.Outlined.Quiz
        isReport -> Icons.Outlined.Assignment
        isMaterial -> Icons.Outlined.MenuBook
        else -> Icons.Outlined.Description
    }
    
    ElevatedCard(
        onClick = onClick,
        enabled = !bookmark.isDisabled,
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (bookmark.isDisabled) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (bookmark.isDisabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = bookmark.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (bookmark.isDisabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    )
                    if (bookmark.category.isNotEmpty()) {
                        Text(
                            text = bookmark.category,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = bookmark.courseTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "削除",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Metadata
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!bookmark.availablePeriod.isNullOrEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Event, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = bookmark.availablePeriod, 
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                if (bookmark.isDisabled) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Block, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "現在利用できません", 
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onClick,
                    enabled = !bookmark.isDisabled,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow, 
                        contentDescription = null, 
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("開始", fontSize = 14.sp)
                }
                
                if (bookmark.detailUrl != null) {
                    OutlinedButton(
                        onClick = onDetailClick,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Outlined.Info, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("詳細", fontSize = 14.sp)
                    }
                }
            }
        }
    }
}
