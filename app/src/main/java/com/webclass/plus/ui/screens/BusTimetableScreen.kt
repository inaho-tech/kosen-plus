package com.webclass.plus.ui.screens

import androidx.compose.foundation.background

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.Schedule

import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.webclass.plus.data.models.BusDeparture
import com.webclass.plus.data.models.BusStop
import com.webclass.plus.data.models.DayType
import com.webclass.plus.data.models.Direction
import com.webclass.plus.data.repository.BusRepository

import com.webclass.plus.ui.viewmodels.BusTimetableViewModel
import com.webclass.plus.util.BusPreferences

import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusTimetableScreen(
    isWideScreen: Boolean,
    embedded: Boolean = false,
    viewModel: BusTimetableViewModel = viewModel()
) {
    val selectedDirection by viewModel.selectedDirection.collectAsState()
    val dayType by viewModel.dayType.collectAsState()
    val timetable by viewModel.timetable.collectAsState()
    val nextBus by viewModel.nextBus.collectAsState()
    val currentTime by viewModel.currentTime.collectAsState()
    val dayTypeMode by viewModel.dayTypeMode.collectAsState()
    val showOnlyUpcoming by viewModel.showOnlyUpcoming.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current
    val themePreferences = remember { com.webclass.plus.util.ThemePreferences(context) }
    val enableLightweightMode by themePreferences.enableLightweightModeFlow.collectAsState(initial = false)
    val useWallpaper by themePreferences.useWallpaperFlow.collectAsState(initial = false)

    val pagerState = rememberPagerState(pageCount = { 2 })

    // Compute timetables and next buses for both directions on tablet
    val timetableDown = remember(dayType, currentTime, showOnlyUpcoming) {
        val allBuses = BusRepository.getCombinedTimetable(Direction.DOWN, dayType)
        if (showOnlyUpcoming) {
            val currentMinutes = currentTime.get(Calendar.HOUR_OF_DAY) * 60 + currentTime.get(Calendar.MINUTE)
            allBuses.filter { it.toTimeUserId() >= currentMinutes }
        } else {
            allBuses
        }
    }
    
    val nextBusDown = remember(dayType, currentTime) {
        val hour = currentTime.get(Calendar.HOUR_OF_DAY)
        val minute = currentTime.get(Calendar.MINUTE)
        BusRepository.getNextBusForDirection(Direction.DOWN, dayType, hour, minute)
    }

    val timetableUp = remember(dayType, currentTime, showOnlyUpcoming) {
        val allBuses = BusRepository.getCombinedTimetable(Direction.UP, dayType)
        if (showOnlyUpcoming) {
            val currentMinutes = currentTime.get(Calendar.HOUR_OF_DAY) * 60 + currentTime.get(Calendar.MINUTE)
            allBuses.filter { it.toTimeUserId() >= currentMinutes }
        } else {
            allBuses
        }
    }
    
    val nextBusUp = remember(dayType, currentTime) {
        val hour = currentTime.get(Calendar.HOUR_OF_DAY)
        val minute = currentTime.get(Calendar.MINUTE)
        BusRepository.getNextBusForDirection(Direction.UP, dayType, hour, minute)
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .background(
                        if (useWallpaper) MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
                        else MaterialTheme.colorScheme.surface
                    )
                    .then(
                        if (embedded) Modifier
                        else Modifier.windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))
                    )
                    .padding(bottom = 8.dp)
            ) {
                if (!isWideScreen) {
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        SegmentedButton(
                            selected = selectedDirection == Direction.DOWN,
                            onClick = { viewModel.setDirection(Direction.DOWN) },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            modifier = Modifier.weight(1f),
                            icon = { Icon(Icons.Outlined.DirectionsBus, null, modifier = Modifier.size(18.dp)) },
                            label = { Text("木更津駅 発") }
                        )
                        SegmentedButton(
                            selected = selectedDirection == Direction.UP,
                            onClick = { viewModel.setDirection(Direction.UP) },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            modifier = Modifier.weight(1f),
                            icon = { Icon(Icons.Filled.School, null, modifier = Modifier.size(18.dp)) },
                            label = { Text("高専 発") }
                        )
                    }
                }

                // Control Bar (DayType & Filter)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    AssistChip(
                        onClick = { viewModel.toggleDayTypeMode() },
                        label = {
                            Text(
                                text = when (dayTypeMode) {
                                    BusPreferences.DayTypeMode.AUTO -> "自動・${dayType.getLabel()}"
                                    BusPreferences.DayTypeMode.MANUAL_WEEKDAY -> "手動・平日"
                                    BusPreferences.DayTypeMode.MANUAL_HOLIDAY -> "手動・休日"
                                }
                            )
                        },
                        leadingIcon = {
                        Icon(Icons.Outlined.Schedule, null, modifier = Modifier.size(18.dp))
                        }
                    )

                    FilterChip(
                        selected = showOnlyUpcoming,
                        onClick = { viewModel.toggleShowOnlyUpcoming() },
                        label = { Text("これから") },
                        leadingIcon = {
                            Icon(
                                imageVector = if (showOnlyUpcoming) Icons.Filled.Restore else Icons.Outlined.Restore,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }
        },
        contentWindowInsets = if (embedded) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets
    ) { paddingValues ->
        if (isWideScreen) {
            // Tablet Layout: Side-by-side Columns
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Column 1: Station Departures
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Outlined.DirectionsBus, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "木更津駅 発",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    BusTimetableList(
                        timetable = timetableDown,
                        nextBus = nextBusDown,
                        currentTime = currentTime,
                        paddingValues = PaddingValues(0.dp),
                        enableLightweightMode = enableLightweightMode
                    )
                }

                // Column 2: Kosen Departures
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Filled.School, null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "高専 発",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                    BusTimetableList(
                        timetable = timetableUp,
                        nextBus = nextBusUp,
                        currentTime = currentTime,
                        paddingValues = PaddingValues(0.dp),
                        enableLightweightMode = enableLightweightMode
                    )
                }
            }
        } else {
            // Smartphone Layout: Pager
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val pageTimetable = if (page == 0) timetableDown else timetableUp
                val pageNextBus = if (page == 0) nextBusDown else nextBusUp
                BusTimetableList(
                    timetable = pageTimetable,
                    nextBus = pageNextBus,
                    currentTime = currentTime,
                    paddingValues = paddingValues,
                    enableLightweightMode = enableLightweightMode
                )
            }
        }
    }

    // Sync Pager <-> Tab (Smartphone only)
    if (!isWideScreen) {
        LaunchedEffect(selectedDirection) {
            val targetPage = if (selectedDirection == Direction.DOWN) 0 else 1
            if (pagerState.currentPage != targetPage) {
                if (enableLightweightMode) {
                    pagerState.scrollToPage(targetPage)
                } else {
                    pagerState.animateScrollToPage(targetPage)
                }
            }
        }

        LaunchedEffect(pagerState.currentPage) {
            val newDirection = if (pagerState.currentPage == 0) Direction.DOWN else Direction.UP
            if (selectedDirection != newDirection) {
                viewModel.setDirection(newDirection)
            }
        }
    }
}

@Composable
fun BusTimetableList(
    timetable: List<BusDeparture>,
    nextBus: BusDeparture?,
    currentTime: java.util.Calendar,
    paddingValues: PaddingValues,
    enableLightweightMode: Boolean = false
) {
    val listState = rememberLazyListState()

    // Scroll to next bus when it changes
    LaunchedEffect(nextBus, timetable) {
        if (nextBus != null && timetable.isNotEmpty()) {
            val index = timetable.indexOf(nextBus)
            if (index >= 0) {
                if (enableLightweightMode) {
                    listState.scrollToItem(maxOf(0, index - 1))
                } else {
                    listState.animateScrollToItem(maxOf(0, index - 1))
                }
            }
        }
    }
    
    // Check if list is empty
    if (timetable.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "表示する便がありません",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(
            top = paddingValues.calculateTopPadding() + 8.dp,
            bottom = 16.dp
        ),
        modifier = Modifier.fillMaxSize()
    ) {
        items(timetable, key = { "${it.busStop?.name ?: ""}_${it.hour}:${it.minute}" }) { departure ->
            BusTimeRow(
                modifier = if (enableLightweightMode) Modifier else Modifier.animateItem(),
                departure = departure,
                isNext = departure == nextBus,
                isPast = departure.toTimeUserId() < (currentTime.get(java.util.Calendar.HOUR_OF_DAY) * 60 + currentTime.get(java.util.Calendar.MINUTE))
            )
        }
    }
}

/**
 * Returns the color for a given BusStop badge using Material 3 colorScheme.
 * East = primary, West = tertiary, Front = primary, Back = secondary
 */
@Composable
fun busStopBadgeColor(stop: BusStop?): Color {
    return when (stop) {
        BusStop.KISARAZU_EAST -> MaterialTheme.colorScheme.primary
        BusStop.KISARAZU_WEST -> MaterialTheme.colorScheme.tertiary
        BusStop.KOSEN_FRONT -> MaterialTheme.colorScheme.primary
        BusStop.KOSEN_BACK -> MaterialTheme.colorScheme.tertiary
        null -> MaterialTheme.colorScheme.outline
    }
}

@Composable
fun BusTimeRow(
    modifier: Modifier = Modifier,
    departure: BusDeparture,
    isNext: Boolean,
    isPast: Boolean
) {
    val accentColor = when {
        isNext -> MaterialTheme.colorScheme.primary
        isPast -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    val contentAlpha = if (isPast && !isNext) 0.4f else 1f

    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isNext)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Accent strip
            Surface(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight(),
                color = accentColor
            ) {}

            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Bus icon badge
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = MaterialTheme.shapes.small,
                    color = if (isNext)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = contentAlpha)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.DirectionsBus, null,
                            modifier = Modifier.size(20.dp),
                            tint = if (isNext)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
                        )
                    }
                }

                // Time
                Text(
                    text = departure.toString(),
                    style = if (isNext) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                    fontWeight = if (isNext) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )

                // Bus stop badge (東口/西口/正門/裏門)
                if (departure.busStop != null) {
                    val badgeColor = busStopBadgeColor(departure.busStop)
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = badgeColor.copy(alpha = if (isPast && !isNext) 0.2f else 0.15f)
                    ) {
                        Text(
                            text = departure.busStop.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor.copy(alpha = contentAlpha),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                // NEXT badge
                if (isNext) {
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            text = "NEXT",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}


