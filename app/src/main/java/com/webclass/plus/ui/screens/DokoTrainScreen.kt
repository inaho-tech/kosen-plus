package com.webclass.plus.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Train
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.webclass.plus.data.models.*
import com.webclass.plus.ui.viewmodels.DokoTrainUiState
import com.webclass.plus.ui.viewmodels.DokoTrainViewModel
import com.webclass.plus.ui.components.shimmerEffect
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DokoTrainScreen(
    useWallpaper: Boolean = false,
    forceTwoPane: Boolean = false,
    onDetailVisibilityChanged: (Boolean) -> Unit = {},
    viewModel: DokoTrainViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val filtered = state.stations.filterForDokoTrain(state.query)
    val favorites = state.stations.filter { it.favoriteKey in state.favoriteStationKeys }
    BackHandler(enabled = state.selectedStation != null) { viewModel.clearSelection() }
    LaunchedEffect(state.selectedStation != null) {
        onDetailVisibilityChanged(state.selectedStation != null)
    }
    DisposableEffect(Unit) {
        onDispose { onDetailVisibilityChanged(false) }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val twoPane = forceTwoPane || maxWidth >= 760.dp
        if (twoPane) {
            Row(modifier = Modifier.fillMaxSize()) {
                StationSearchPane(state, filtered, favorites, viewModel::setQuery, viewModel::selectStation, viewModel::toggleFavorite, viewModel::refresh, useWallpaper, Modifier.width(380.dp))
                VerticalDivider()
                DokoTrainDetailPane(state, viewModel::setDirection, viewModel::toggleFavorite, viewModel::refresh, useWallpaper, Modifier.weight(1f))
            }
        } else {
            AnimatedContent(
                targetState = state.selectedStation != null,
                transitionSpec = {
                    if (targetState) {
                        (slideInHorizontally(tween(320)) { it } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(tween(320)) { -it / 4 } + fadeOut(tween(180)))
                    } else {
                        (slideInHorizontally(tween(320)) { -it / 4 } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(tween(320)) { it } + fadeOut(tween(180)))
                    }
                },
                label = "doko_station_detail",
                modifier = Modifier.fillMaxSize()
            ) { showDetail ->
                if (showDetail) {
                    DokoTrainDetailPane(state, viewModel::setDirection, viewModel::toggleFavorite, viewModel::refresh, useWallpaper, Modifier.fillMaxSize(), viewModel::clearSelection)
                } else {
                    StationSearchPane(state, filtered, favorites, viewModel::setQuery, viewModel::selectStation, viewModel::toggleFavorite, viewModel::refresh, useWallpaper, Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun StationSearchPane(
    state: DokoTrainUiState,
    stations: List<DokoTrainStation>,
    favorites: List<DokoTrainStation>,
    onQueryChanged: (String) -> Unit,
    onStationSelected: (DokoTrainStation) -> Unit,
    onToggleFavorite: (DokoTrainStation) -> Unit,
    onRefresh: () -> Unit,
    useWallpaper: Boolean,
    modifier: Modifier = Modifier
) {
    var pendingFavoriteRemoval by remember { mutableStateOf<DokoTrainStation?>(null) }
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChanged,
                modifier = Modifier.weight(1f),
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                placeholder = { Text("駅名・路線名で検索") },
                shape = RoundedCornerShape(16.dp)
            )
            IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, contentDescription = "駅情報を更新") }
        }

        state.error?.takeIf { state.selectedStation == null }?.let { ErrorMessage(it, onRefresh) }

        if (state.isLoadingStations) {
            StationListPlaceholder(modifier = Modifier.fillMaxSize())
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    top = 4.dp,
                    end = 12.dp,
                    bottom = 4.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (favorites.isNotEmpty() && state.query.isBlank()) {
                    item(key = "favorites_header") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                            Spacer(Modifier.width(8.dp))
                            Text("お気に入り", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            Text("${favorites.size}駅", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    items(
                        items = favorites.chunked(2),
                        key = { row -> "favorite_row_${row.joinToString("_") { it.favoriteKey }}" }
                    ) { rowStations ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowStations.forEach { station ->
                                ElevatedCard(
                                    onClick = { onStationSelected(station) },
                                    colors = CardDefaults.elevatedCardColors(
                                        containerColor = if (useWallpaper) MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                                        else MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f)
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(station.name, fontWeight = FontWeight.Bold, maxLines = 1)
                                            Text(
                                                station.dokoLineName.ifBlank { station.lineName },
                                                style = MaterialTheme.typography.labelSmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        IconButton(onClick = { pendingFavoriteRemoval = station }) {
                                            Icon(Icons.Default.Star, contentDescription = "お気に入りから削除", tint = MaterialTheme.colorScheme.tertiary)
                                        }
                                    }
                                }
                            }
                            if (rowStations.size == 1) Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                    item(key = "favorites_bottom_space") { Spacer(Modifier.height(4.dp)) }
                }

                item(key = "all_stations_header") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(if (state.query.isBlank()) "すべての駅" else "検索結果", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text("${stations.size}件", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                if (stations.isEmpty()) {
                    item {
                        Text(
                            if (state.query.isBlank()) "駅情報がありません" else "「${state.query}」に一致する駅がありません",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                }
                items(stations, key = { "${it.dokoLineId}:${it.stationId}" }) { station ->
                    val selected = state.selectedStation?.let { it.stationId == station.stationId && it.dokoLineId == station.dokoLineId } == true
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth().clickable { onStationSelected(station) },
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = when {
                                selected -> MaterialTheme.colorScheme.secondaryContainer
                                useWallpaper -> MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)
                                else -> MaterialTheme.colorScheme.surface
                            }
                        )
                    ) {
                        ListItem(
                            headlineContent = { Text(station.name, fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text(station.dokoLineName.ifBlank { station.lineName }) },
                            leadingContent = { Icon(Icons.Outlined.Train, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            trailingContent = {
                                IconButton(onClick = { onToggleFavorite(station) }) {
                                    if (station.favoriteKey in state.favoriteStationKeys) {
                                        Icon(Icons.Default.Star, contentDescription = "お気に入りから削除", tint = MaterialTheme.colorScheme.tertiary)
                                    } else {
                                        Icon(Icons.Outlined.StarBorder, contentDescription = "お気に入りに追加")
                                    }
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
            }
        }
    }

    pendingFavoriteRemoval?.let { station ->
        AlertDialog(
            onDismissRequest = { pendingFavoriteRemoval = null },
            icon = { Icon(Icons.Default.Star, contentDescription = null) },
            title = { Text("お気に入りを解除") },
            text = {
                Text("${station.name}（${station.dokoLineName.ifBlank { station.lineName }}）をお気に入りから解除しますか？")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onToggleFavorite(station)
                        pendingFavoriteRemoval = null
                    }
                ) { Text("解除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingFavoriteRemoval = null }) { Text("キャンセル") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DokoTrainDetailPane(
    state: DokoTrainUiState,
    onDirectionChanged: (DokoTrainDirection) -> Unit,
    onToggleFavorite: (DokoTrainStation) -> Unit,
    onRefresh: () -> Unit,
    useWallpaper: Boolean,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null
) {
    val station = state.selectedStation
    if (station == null) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.Train, contentDescription = null, modifier = Modifier.size(52.dp), tint = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(12.dp))
                Text("駅を選択すると時刻表を表示します", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    val directionPager = rememberPagerState(
        initialPage = if (state.direction == DokoTrainDirection.UP) 0 else 1,
        pageCount = { 2 }
    )
    val directionScope = rememberCoroutineScope()

    LaunchedEffect(directionPager.currentPage) {
        val direction = if (directionPager.currentPage == 0) DokoTrainDirection.UP else DokoTrainDirection.DOWN
        if (state.direction != direction) onDirectionChanged(direction)
    }
    LaunchedEffect(state.direction) {
        val target = if (state.direction == DokoTrainDirection.UP) 0 else 1
        if (directionPager.currentPage != target) directionPager.scrollToPage(target)
    }

    Column(modifier = modifier) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = if (useWallpaper) MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "駅検索へ戻る") }
                Icon(
                    Icons.Outlined.Train,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(10.dp).size(25.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(station.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(station.dokoLineName.ifBlank { station.lineName }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { onToggleFavorite(station) }) {
                    if (station.favoriteKey in state.favoriteStationKeys) {
                        Icon(Icons.Default.Star, contentDescription = "お気に入りから削除", tint = MaterialTheme.colorScheme.tertiary)
                    } else {
                        Icon(Icons.Outlined.StarBorder, contentDescription = "お気に入りに追加")
                    }
                }
                IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, contentDescription = "最新情報に更新") }
            }
        }

        state.lineStatus?.let { status ->
            val attention = status.maxDelayMinutes > 0 || status.statusCode != 0
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                color = when {
                    attention -> MaterialTheme.colorScheme.errorContainer
                    useWallpaper -> MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
                    else -> MaterialTheme.colorScheme.primaryContainer
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Schedule, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(status.label, fontWeight = FontWeight.Bold)
                        if (status.announcedAt.isNotBlank()) Text("更新 ${formatDokoDate(status.announcedAt)}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        PrimaryTabRow(selectedTabIndex = if (state.direction == DokoTrainDirection.UP) 0 else 1) {
            Tab(
                selected = state.direction == DokoTrainDirection.UP,
                enabled = state.upTimetable != null || state.isLoadingDetails,
                onClick = {
                    onDirectionChanged(DokoTrainDirection.UP)
                    directionScope.launch { directionPager.animateScrollToPage(0) }
                },
                text = { Text("上り") }
            )
            Tab(
                selected = state.direction == DokoTrainDirection.DOWN,
                enabled = state.downTimetable != null || state.isLoadingDetails,
                onClick = {
                    onDirectionChanged(DokoTrainDirection.DOWN)
                    directionScope.launch { directionPager.animateScrollToPage(1) }
                },
                text = { Text("下り") }
            )
        }

        if (state.isLoadingDetails) {
            TimetablePlaceholder(modifier = Modifier.fillMaxSize())
        } else {
            state.error?.let { ErrorMessage(it, onRefresh) }
            HorizontalPager(
                state = directionPager,
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) { page ->
                TimetableList(
                    timetable = if (page == 0) state.upTimetable else state.downTimetable,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun TimetableList(timetable: DokoTrainTimetable?, modifier: Modifier = Modifier) {
    if (timetable == null || timetable.departures.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("この方面の時刻表はありません", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val grouped = timetable.departures.groupBy { it.departureTime.substringBefore(':') }
    val listState = rememberLazyListState()
    LaunchedEffect(timetable.departures) {
        val nowSeconds = System.currentTimeMillis() / 1000
        val nearest = timetable.departures.minByOrNull { kotlin.math.abs(it.departureEpoch - nowSeconds) }
        val nearestHour = nearest?.departureTime?.substringBefore(':')
        val hourIndex = grouped.keys.indexOf(nearestHour)
        if (hourIndex >= 0) listState.animateScrollToItem(hourIndex + 1)
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = 12.dp,
            top = 12.dp,
            end = 12.dp,
            bottom = 12.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column {
                if (timetable.message.isNotBlank()) Text(timetable.message, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                if (timetable.announcedAt.isNotBlank()) Text("時刻表更新 ${formatDokoDate(timetable.announcedAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        grouped.forEach { (hour, trains) ->
            item(key = "hour_$hour") {
                Row(verticalAlignment = Alignment.Top) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.width(54.dp)
                    ) {
                        Text("${hour}時", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 12.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        trains.forEach { DepartureRow(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DepartureRow(item: DokoTrainDeparture) {
    val delay = item.liveDelay?.toIntOrNull() ?: 0
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                item.departureTime.substringAfter(':'),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = when (item.typeLabel) {
                    "特急" -> MaterialTheme.colorScheme.error
                    "快速" -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(listOf(item.typeLabel, item.trainName).filter { it.isNotBlank() }.joinToString(" "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${item.destination.ifBlank { "行先未定" }} 行", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val details = buildList {
                    if (item.cars.isNotBlank() && item.cars != "0") add("${item.cars}両")
                    if (item.trainId.isNotBlank()) add(item.trainId)
                }.joinToString("・")
                if (details.isNotBlank()) Text(details, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when {
                item.isCancelled -> AssistChip(onClick = {}, label = { Text("運休") }, colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.errorContainer, labelColor = MaterialTheme.colorScheme.onErrorContainer))
                delay > 0 -> AssistChip(onClick = {}, label = { Text("${delay}分遅れ") }, colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.errorContainer, labelColor = MaterialTheme.colorScheme.onErrorContainer))
            }
        }
    }
}

@Composable
private fun StationListPlaceholder(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(6) {
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).shimmerEffect())
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.fillMaxWidth(0.42f).height(18.dp).clip(RoundedCornerShape(6.dp)).shimmerEffect())
                        Box(Modifier.fillMaxWidth(0.7f).height(12.dp).clip(RoundedCornerShape(6.dp)).shimmerEffect())
                    }
                    Box(Modifier.size(28.dp).clip(RoundedCornerShape(14.dp)).shimmerEffect())
                }
            }
        }
    }
}

@Composable
private fun TimetablePlaceholder(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.fillMaxWidth(0.55f).height(16.dp).clip(RoundedCornerShape(6.dp)).shimmerEffect())
        Box(Modifier.fillMaxWidth(0.35f).height(11.dp).clip(RoundedCornerShape(6.dp)).shimmerEffect())
        repeat(5) {
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.width(54.dp).height(48.dp).clip(RoundedCornerShape(10.dp)).shimmerEffect())
                Spacer(Modifier.width(10.dp))
                OutlinedCard(modifier = Modifier.weight(1f)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(36.dp).height(28.dp).clip(RoundedCornerShape(6.dp)).shimmerEffect())
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.fillMaxWidth(0.35f).height(11.dp).clip(RoundedCornerShape(5.dp)).shimmerEffect())
                            Box(Modifier.fillMaxWidth(0.65f).height(16.dp).clip(RoundedCornerShape(5.dp)).shimmerEffect())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorMessage(message: String, onRetry: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(12.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
            TextButton(onClick = onRetry) { Text("再試行") }
        }
    }
}

private fun List<DokoTrainStation>.filterForDokoTrain(query: String): List<DokoTrainStation> {
    val keyword = query.trim()
    if (keyword.isBlank()) return take(250)
    return filter {
        it.name.contains(keyword, ignoreCase = true) || it.kana.contains(keyword, ignoreCase = true) ||
            it.lineName.contains(keyword, ignoreCase = true) || it.dokoLineName.contains(keyword, ignoreCase = true)
    }.take(250)
}

private fun formatDokoDate(value: String): String = value.removeSuffix(" GMT").replace('/', '-')
