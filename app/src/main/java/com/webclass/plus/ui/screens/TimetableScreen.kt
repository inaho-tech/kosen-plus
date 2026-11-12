package com.webclass.plus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.webclass.plus.data.models.TimetableEntry
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(
    timetable: List<TimetableEntry>,
    onBack: () -> Unit,
    onCourseClick: (courseId: String, courseTitle: String) -> Unit
) {
    val activeDays = remember(timetable) {
        val allDays = listOf("月", "火", "水", "木", "金", "土")
        if (timetable.isEmpty()) {
            allDays
        } else {
            val daysWithClasses = timetable.map { it.day }.toSet()
            val filtered = allDays.filter { it in daysWithClasses }
            if (filtered.isEmpty()) allDays else filtered
        }
    }
    
    // Default selected tab based on current day of the week, or the first active day
    val initialDayIndex = remember(activeDays) {
        val calendar = Calendar.getInstance()
        val currentDayStr = when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "月"
            Calendar.TUESDAY -> "火"
            Calendar.WEDNESDAY -> "水"
            Calendar.THURSDAY -> "木"
            Calendar.FRIDAY -> "金"
            Calendar.SATURDAY -> "土"
            else -> "月"
        }
        val index = activeDays.indexOf(currentDayStr)
        if (index != -1) index else 0
    }
    
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = if (initialDayIndex in activeDays.indices) initialDayIndex else 0
    ) {
        activeDays.size
    }
    
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("時間割", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "戻る")
                    }
                },
                colors = appTopBarColors(),
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Days of the week tab row
            TabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                activeDays.forEachIndexed { index, day ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        },
                        text = { 
                            Text(
                                text = "${day}曜", 
                                fontWeight = if (pagerState.currentPage == index) FontWeight.Bold else FontWeight.Normal
                            ) 
                        }
                    )
                }
            }

            if (timetable.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "時間割情報がありません",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "WebClassにログインして情報を取得してください",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) { page ->
                    val pageDay = activeDays.getOrNull(page) ?: "月"
                    val dayEntries = remember(timetable, pageDay) {
                        timetable.filter { it.day == pageDay }
                    }
                    val maxPeriod = remember(dayEntries) {
                        dayEntries.maxOfOrNull { it.period } ?: 0
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items((1..maxPeriod).toList()) { period ->
                            val entriesForPeriod = dayEntries.filter { it.period == period }
                            
                            if (entriesForPeriod.isNotEmpty()) {
                                entriesForPeriod.forEach { entry ->
                                    TimetableCard(
                                        entry = entry,
                                        onClick = { onCourseClick(entry.courseCode, entry.courseTitle) }
                                    )
                                }
                            } else {
                                EmptyPeriodCard(period = period)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableCard(
    entry: TimetableEntry,
    onClick: () -> Unit
) {
    val periodTime = getPeriodTime(entry.period)
    
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Accent strip matching CourseCard
            Surface(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight(),
                color = MaterialTheme.colorScheme.primary
            ) {}

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "${entry.period}限",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                        if (periodTime.isNotEmpty()) {
                            Text(
                                text = periodTime,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        if (entry.courseCode.isNotEmpty()) {
                            Text(
                                text = entry.courseCode,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = entry.courseTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyPeriodCard(period: Int) {
    val periodTime = getPeriodTime(period)
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(0.55f)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = MaterialTheme.shapes.medium
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "${period}限",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                if (periodTime.isNotEmpty()) {
                    Text(
                        text = periodTime,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = "空きコマ",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

fun getPeriodTime(period: Int): String {
    return when (period) {
        1 -> "09:00 - 10:30"
        2 -> "10:40 - 12:10"
        3 -> "13:00 - 14:30"
        4 -> "14:40 - 15:30"
        else -> ""
    }
}
