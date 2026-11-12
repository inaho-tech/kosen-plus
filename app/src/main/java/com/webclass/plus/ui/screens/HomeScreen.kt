package com.webclass.plus.ui.screens

import android.util.Log
import android.webkit.WebView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.webclass.plus.data.models.Bookmark
import com.webclass.plus.data.models.Course
import com.webclass.plus.data.models.TimetableEntry
import com.webclass.plus.data.models.ScheduleSettings
import com.webclass.plus.data.models.ScheduleOption
import com.webclass.plus.data.models.DashboardInfo
import com.webclass.plus.data.models.AdminAnnouncement
import com.webclass.plus.data.models.SurveyBlock
import com.webclass.plus.data.repository.BookmarkRepository
import com.webclass.plus.data.repository.UserDataRepository
import com.webclass.plus.ui.components.WebClassWebView
import com.webclass.plus.util.JavaScriptBridge
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import com.webclass.plus.ui.viewmodels.BusTimetableViewModel
import com.webclass.plus.util.getCourseIcon
import com.webclass.plus.ui.components.shimmerEffect
import androidx.compose.ui.draw.alpha

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    resetTrigger: Int = 0,
    onCourseClick: (courseId: String, courseTitle: String, autoOpenUrl: String?, autoOpenTitle: String?) -> Unit = { _, _, _, _ -> },
    onContentClick: (String, String) -> Unit = { _, _ -> },
    onNavigateToTimetable: () -> Unit = {}
) {
    val activityContext = androidx.compose.ui.platform.LocalContext.current as androidx.activity.ComponentActivity
    val homeViewModel: com.webclass.plus.ui.viewmodels.HomeViewModel = viewModel(activityContext)
    
    // State management
    val courses by homeViewModel.courses.collectAsState()
    val timetable by homeViewModel.timetable.collectAsState()
    val scheduleSettings by homeViewModel.scheduleSettings.collectAsState()
    val dashboardInfo by homeViewModel.dashboardInfo.collectAsState()
    val unreadMessageCount by homeViewModel.unreadMessageCount.collectAsState()
    val searchQuery by homeViewModel.searchQuery.collectAsState()
    
    // UI Filter State
    var selectedFilter by remember { mutableStateOf(CourseFilter.ALL) }
    
    val isLoading by homeViewModel.isLoading.collectAsState()
    val showWebView by homeViewModel.showWebView.collectAsState()
    val currentUrl by homeViewModel.currentUrl.collectAsState()
    val isOnMicrosoftLogin by homeViewModel.isOnMicrosoftLogin.collectAsState()
    val hasCachedData by homeViewModel.hasCachedData.collectAsState()
    
    var loginDetectedTime by remember { mutableStateOf(0L) }

    LaunchedEffect(showWebView, hasCachedData) {
        if (!showWebView && hasCachedData) {
            homeViewModel.refreshUnreadMessageCount()
        }
    }
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val userDataRepository = remember { UserDataRepository(context) }
    val bookmarkRepository = remember { BookmarkRepository(context) }
    val bookmarks by bookmarkRepository.bookmarks.collectAsState()
    
    val themePreferences = remember { com.webclass.plus.util.ThemePreferences(context) }
    val hideAnnouncements by themePreferences.hideAnnouncementsFlow.collectAsState(initial = false)
    val hideSurveys by themePreferences.hideSurveysFlow.collectAsState(initial = false)
    val enableHyperAnimation by themePreferences.enableHyperAnimationFlow.collectAsState(initial = false)
    val enableLightweightMode by themePreferences.enableLightweightModeFlow.collectAsState(initial = false)
    val hyperAnimationStrength by themePreferences.hyperAnimationStrengthFlow.collectAsState(initial = 1.0f)
    
    val developerPreferences = remember { com.webclass.plus.util.DeveloperPreferences(context) }
    val isMockMode by developerPreferences.mockModeEnabledFlow.collectAsState(initial = false)
    
    val LOGIN_URL = "https://webclass.kosen-k.go.jp/webclass/login.php?auth_mode=SAML"
    val LOGOUT_URL = "https://webclass.kosen-k.go.jp/webclass/logout.php"
    val gson = remember { Gson() }
    val scope = rememberCoroutineScope()
    
    val bookmarkedCourseIds = remember(bookmarks) {
        bookmarks.asSequence().map { it.id }.toSet()
    }
    val visibleCourses = remember(courses, bookmarkedCourseIds) {
        courses.filter { course -> course.code !in bookmarkedCourseIds }
    }

    // Handle reset trigger
    LaunchedEffect(resetTrigger) {
        if (resetTrigger > homeViewModel.lastResetTrigger && !isMockMode) {
            homeViewModel.lastResetTrigger = resetTrigger
            Log.d("WebClass", "Home reset triggered. Logging out...")
            homeViewModel.resetState()
            homeViewModel.triggerLoadUrl(LOGOUT_URL)
        }
    }
    
    // Mock Data Logic
    LaunchedEffect(isMockMode) {
        if (isMockMode) {
            homeViewModel.setMockData()
        }
    }


    // When composable re-enters while loading (e.g. returned from another tab),
    // the old WebView is gone. Reset the URL so the new WebView starts fresh.
    LaunchedEffect(Unit) {
        if (isLoading && !hasCachedData && !isMockMode) {
            homeViewModel.setCurrentUrl("https://webclass.kosen-k.go.jp/webclass/index.php")
            homeViewModel.setOnMicrosoftLogin(false)
            homeViewModel.setShowWebView(false)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // WebView Layer for standard login
        if (showWebView) {
            val isWideScreen = context.resources.configuration.screenWidthDp > 600
            val initialUrl = if (isWideScreen) {
                "https://webclass.kosen-k.go.jp/webclass/index.php"
            } else {
                "https://webclass.kosen-k.go.jp/webclass/mbl.php"
            }
            
            WebClassWebView(
                url = currentUrl.ifEmpty { initialUrl },
                modifier = Modifier.fillMaxSize(),
                onPageFinished = { webView, url ->
                    if (url.contains("index.php") || url.contains("mbl.php/index")) {
                        // Successfully logged in / reached index!
                        val now = System.currentTimeMillis()
                        if (now - loginDetectedTime > 5000) {
                            loginDetectedTime = now
                            homeViewModel.setLoading(true)
                            homeViewModel.setShowWebView(false)
                            homeViewModel.triggerReload()
                        }
                    }
                    
                    // Microsoft Login Detection
                    if (url.contains("login.microsoftonline.com")) {
                        homeViewModel.setOnMicrosoftLogin(true)
                    } else if (url.contains("webclass.kosen-k.go.jp")) {
                        homeViewModel.setOnMicrosoftLogin(false)
                    }
                }
            )
        }


        // Native UI Layer
        if (!showWebView) {
            val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
            
            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
                modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                topBar = {
                    CenterAlignedTopAppBar(
                        windowInsets = WindowInsets.displayCutout.union(WindowInsets.statusBars),
                        navigationIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = {
                                    onContentClick(
                                        "https://webclass.kosen-k.go.jp/webclass/messages.php/inbox",
                                        "メッセージ"
                                    )
                                }) {
                                    BadgedBox(
                                        badge = {
                                            if (unreadMessageCount > 0) {
                                                Badge {
                                                    Text(
                                                        if (unreadMessageCount > 99) "99+"
                                                        else unreadMessageCount.toString()
                                                    )
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Outlined.Mail, contentDescription = "メッセージ")
                                    }
                                }
                                if (!dashboardInfo.portfolioUrl.isNullOrEmpty()) {
                                    IconButton(onClick = {
                                        onContentClick(dashboardInfo.portfolioUrl!!, "ポートフォリオ")
                                    }) {
                                        Icon(Icons.Outlined.ContactPage, contentDescription = "ポートフォリオ")
                                    }
                                }
                            }
                        },
                        title = { Text("WebClass+", fontWeight = FontWeight.Bold) },
                        actions = {
                            IconButton(onClick = onNavigateToTimetable) {
                                Icon(Icons.Default.CalendarToday, contentDescription = "時間割")
                            }
                            IconButton(onClick = { 
                                homeViewModel.setLoading(true)
                                homeViewModel.refreshUnreadMessageCount()
                                homeViewModel.triggerReload() 
                            }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Reload")
                            }
                        },
                        colors = appTopBarColors(),
                        scrollBehavior = scrollBehavior
                    )
                }
            ) { padding ->
                if (isLoading && !hasCachedData) {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(padding)) {
                        val isWideScreen = maxWidth > 600.dp
                        val columns = if (isWideScreen) androidx.compose.foundation.lazy.grid.GridCells.Adaptive(300.dp) else androidx.compose.foundation.lazy.grid.GridCells.Fixed(1)
                        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                            columns = columns,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                top = 16.dp,
                                end = 16.dp,
                                bottom = 16.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Controls skeleton (full span)
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    // Year/Semester dropdown row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(modifier = Modifier.weight(1f).height(40.dp).clip(MaterialTheme.shapes.small).shimmerEffect())
                                        Box(modifier = Modifier.weight(1f).height(40.dp).clip(MaterialTheme.shapes.small).shimmerEffect())
                                    }
                                    // Search bar
                                    Box(modifier = Modifier.fillMaxWidth().height(56.dp).clip(MaterialTheme.shapes.medium).shimmerEffect())
                                    // Filter chips
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Box(modifier = Modifier.width(64.dp).height(32.dp).clip(MaterialTheme.shapes.small).shimmerEffect())
                                        Box(modifier = Modifier.width(72.dp).height(32.dp).clip(MaterialTheme.shapes.small).shimmerEffect())
                                        Box(modifier = Modifier.width(56.dp).height(32.dp).clip(MaterialTheme.shapes.small).shimmerEffect())
                                    }
                                }
                            }
                            items(6) {
                                SkeletonCourseCard()
                            }
                        }
                    }
                } else {
                    val filteredCourses = remember(courses, bookmarkedCourseIds, searchQuery, selectedFilter) {
                        try {
                            courses.filter { course ->
                                val isHidden = course.code in bookmarkedCourseIds
                                val matchesSearch = searchQuery.isBlank() ||
                                    course.title.contains(searchQuery, ignoreCase = true) ||
                                    course.code.contains(searchQuery, ignoreCase = true)
 
                                matchesSearch && when(selectedFilter) {
                                    CourseFilter.ALL -> !isHidden
                                    CourseFilter.TASKS -> !isHidden && course.isDueSoon
                                    CourseFilter.HIDDEN -> isHidden
                                }
                            }
                        } catch(e: Exception) { emptyList() }
                    }
                    val groupedCourses = remember(filteredCourses, timetable) {
                        buildCourseDayGroups(filteredCourses, timetable)
                    }
 
                    BoxWithConstraints {
                        val isWideScreen = maxWidth > 600.dp
                        val columns = if (isWideScreen) androidx.compose.foundation.lazy.grid.GridCells.Adaptive(300.dp) else androidx.compose.foundation.lazy.grid.GridCells.Fixed(1)
 
                        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                            columns = columns,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                top = 16.dp,
                                end = 16.dp,
                                bottom = 16.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Filters Section - Full Width
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    // Dashboard Items (Announcements & Surveys)
                                    if (isWideScreen) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            if (dashboardInfo.announcements.isNotEmpty() && !hideAnnouncements) {
                                                Box(modifier = Modifier.weight(1f)) {
                                                    AdminInfoCard(announcements = dashboardInfo.announcements, onUrlClick = { url ->
                                                        onContentClick(url, "管理者からのお知らせ")
                                                    })
                                                }
                                            }
                                            if (dashboardInfo.surveys.isNotEmpty() && !hideSurveys) {
                                                Box(modifier = Modifier.weight(1f)) {
                                                    SurveyInfoCard(surveys = dashboardInfo.surveys, onUrlClick = { url ->
                                                        onContentClick(url, "アンケート")
                                                    })
                                                }
                                            }
                                        }
                                    } else {
                                        // Mobile: Vertical Stack
                                        if (dashboardInfo.announcements.isNotEmpty() && !hideAnnouncements) {
                                            AdminInfoCard(announcements = dashboardInfo.announcements, onUrlClick = { url ->
                                                // Redirect to mobile version if applicable and NOT on tablet
                                                val mobileUrl = if (!isWideScreen && url.contains("informations.php?action=show&id=")) {
                                                    url.replace("informations.php?action=show&id=", "mbl.php/information/show?id=")
                                                } else {
                                                    url
                                                }
                                                onContentClick(mobileUrl, "管理者からのお知らせ")
                                            })
                                        }
                                        
                                        if (dashboardInfo.surveys.isNotEmpty() && !hideSurveys) {
                                            SurveyInfoCard(surveys = dashboardInfo.surveys, onUrlClick = { url ->
                                                onContentClick(url, "アンケート")
                                            })
                                        }
                                    }
 
                                    // Year/Semester Selector
                                    if (scheduleSettings.years.isNotEmpty() || scheduleSettings.semesters.isNotEmpty()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            // Year Dropdown
                                            if (scheduleSettings.years.isNotEmpty()) {
                                                var expanded by remember { mutableStateOf(false) }
                                                Box(modifier = Modifier.weight(1f)) {
                                                    OutlinedButton(
                                                        onClick = { expanded = true },
                                                        modifier = Modifier.fillMaxWidth(),
                                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                                    ) {
                                                        val label = scheduleSettings.years.find { it.value == scheduleSettings.currentYear }?.label ?: scheduleSettings.currentYear
                                                        Text(label.ifEmpty { "年度" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                        Icon(Icons.Default.ArrowDropDown, null)
                                                    }
                                                    DropdownMenu(
                                                        expanded = expanded,
                                                        onDismissRequest = { expanded = false }
                                                    ) {
                                                        scheduleSettings.years.forEach { option ->
                                                            DropdownMenuItem(
                                                                text = { Text(option.label) },
                                                                onClick = { 
                                                                    expanded = false
                                                                    homeViewModel.setLoading(true)
                                                                    val newUrl = "https://webclass.kosen-k.go.jp/webclass/index.php?year=${option.value}&semester=${scheduleSettings.currentSemester}"
                                                                    homeViewModel.setCurrentUrl(newUrl)
                                                                }
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                            
                                            // Semester Dropdown
                                            if (scheduleSettings.semesters.isNotEmpty()) {
                                                var expanded by remember { mutableStateOf(false) }
                                                Box(modifier = Modifier.weight(1f)) {
                                                    OutlinedButton(
                                                        onClick = { expanded = true },
                                                        modifier = Modifier.fillMaxWidth(),
                                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                                    ) {
                                                        val label = scheduleSettings.semesters.find { it.value == scheduleSettings.currentSemester }?.label ?: scheduleSettings.currentSemester
                                                        Text(label.ifEmpty { "前期/後期" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                        Icon(Icons.Default.ArrowDropDown, null)
                                                    }
                                                    DropdownMenu(
                                                        expanded = expanded,
                                                        onDismissRequest = { expanded = false }
                                                    ) {
                                                        scheduleSettings.semesters.forEach { option ->
                                                            DropdownMenuItem(
                                                                text = { Text(option.label) },
                                                                onClick = { 
                                                                    expanded = false
                                                                    homeViewModel.setLoading(true)
                                                                    val newUrl = "https://webclass.kosen-k.go.jp/webclass/index.php?year=${scheduleSettings.currentYear}&semester=${option.value}"
                                                                    homeViewModel.setCurrentUrl(newUrl)
                                                                }
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
 
                                    // Search Bar
                                    OutlinedTextField(
                                        value = searchQuery,
                                        onValueChange = { homeViewModel.updateSearchQuery(it) },
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = { Text("コース名やIDで検索") },
                                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                        singleLine = true,
                                        shape = MaterialTheme.shapes.medium,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                        )
                                    )
                                    
                                    // Chips
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        CourseFilter.values().forEach { filter ->
                                            FilterChip(
                                                selected = selectedFilter == filter,
                                                onClick = { selectedFilter = filter },
                                                label = { Text(filter.label) },
                                                leadingIcon = if (selectedFilter == filter) {
                                                    { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                                                } else null
                                            )
                                        }
                                    }
                                }
                            }
 
                            // Course List
                            if (filteredCourses.isEmpty()) {
                                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                Icons.Default.School, 
                                                null, 
                                                tint = MaterialTheme.colorScheme.surfaceVariant,
                                                modifier = Modifier.size(64.dp)
                                            )
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Text(
                                                "コースが見つかりません",
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (courses.isEmpty() && !isLoading) {
                                                Spacer(modifier = Modifier.height(16.dp))
                                                Button(onClick = { homeViewModel.setShowWebView(true) }) {
                                                    Text("WebClassを開く (ログイン)")
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                groupedCourses.forEach { group ->
                                    item(
                                        key = "day-header-${group.day}",
                                        span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }
                                    ) {
                                        CourseDayHeader(day = group.day, courseCount = group.items.size)
                                    }

                                    items(
                                        items = group.items,
                                        key = { item ->
                                            "${group.day}-${item.period ?: 0}-${item.course.code}-${item.course.href}"
                                        }
                                    ) { item ->
                                        val course = item.course
                                        val isHidden = course.code in bookmarkedCourseIds
                                        CourseCard(
                                            modifier = if (enableLightweightMode) Modifier else Modifier.animateItem(),
                                            course = course,
                                            period = item.period,
                                            isHidden = isHidden,
                                            enableLightweightMode = enableLightweightMode,
                                            enableHyperAnimation = enableHyperAnimation,
                                            hyperAnimationStrength = hyperAnimationStrength,
                                            onHideClick = {
                                                bookmarkRepository.toggleBookmark(
                                                    Bookmark(
                                                        id = course.code,
                                                        title = course.title,
                                                        url = course.href,
                                                        category = "HIDDEN_COURSE",
                                                        courseTitle = course.title
                                                    )
                                                )
                                            },
                                            onClick = { onCourseClick(course.code, course.title, null, null) }
                                        )
                                    }
                                }
                                
                                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                    Spacer(modifier = Modifier.height(32.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

    }
}

enum class CourseFilter(val label: String) {
    ALL("すべて"),
    TASKS("期限間近"),
    HIDDEN("非表示")
}

private data class ScheduledCourseItem(
    val course: Course,
    val period: Int?
)

private data class CourseDayGroup(
    val day: String,
    val items: List<ScheduledCourseItem>
)

private fun buildCourseDayGroups(
    courses: List<Course>,
    timetable: List<TimetableEntry>
): List<CourseDayGroup> {
    val dayOrder = listOf("月", "火", "水", "木", "金", "土", "日")
    val coursesByCode = courses.filter { it.code.isNotEmpty() }.associateBy { it.code }
    val scheduledCourseCodes = mutableSetOf<String>()

    val scheduledItems = timetable.mapNotNull { entry ->
        val course = coursesByCode[entry.courseCode]
            ?: courses.firstOrNull { it.title == entry.courseTitle }
            ?: return@mapNotNull null
        scheduledCourseCodes += course.code
        val day = dayOrder.firstOrNull { entry.day.contains(it) } ?: return@mapNotNull null
        Triple(day, entry.period, course)
    }.distinctBy { (day, period, course) -> "$day-$period-${course.code}-${course.href}" }

    val groups = dayOrder.mapNotNull { day ->
        val dayItems = scheduledItems
            .filter { it.first == day }
            .sortedWith(compareBy<Triple<String, Int, Course>> { it.second }.thenBy { it.third.title })
            .map { ScheduledCourseItem(course = it.third, period = it.second) }
        dayItems.takeIf { it.isNotEmpty() }?.let {
            CourseDayGroup(day = "${day}曜日", items = it)
        }
    }.toMutableList()

    val unscheduledItems = courses
        .filter { it.code !in scheduledCourseCodes }
        .map { ScheduledCourseItem(course = it, period = null) }
    if (unscheduledItems.isNotEmpty()) {
        groups += CourseDayGroup(day = "その他", items = unscheduledItems)
    }
    return groups
}

@Composable
private fun CourseDayHeader(day: String, courseCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            modifier = Modifier.size(36.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (day == "その他") Icons.Outlined.Apps else Icons.Outlined.CalendarToday,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Text(
            text = day,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "${courseCount}コース",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseCard(
    modifier: Modifier = Modifier,
    course: Course,
    period: Int? = null,
    isHidden: Boolean,
    enableLightweightMode: Boolean = false,
    enableHyperAnimation: Boolean = false,
    hyperAnimationStrength: Float = 1.0f,
    onHideClick: () -> Unit,
    onClick: () -> Unit
) {
    val accentColor = when {
        course.isDueSoon -> MaterialTheme.colorScheme.error
        course.unreadCount > 0 -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    val icon = getCourseIcon(course.title)

    OutlinedCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().animateContentSize(
            animationSpec = if (enableLightweightMode) {
                androidx.compose.animation.core.snap()
            } else if (enableHyperAnimation) {
                val springDamping = (0.8f - hyperAnimationStrength * 0.4f).coerceIn(0.15f, 0.8f)
                val springStiffness = (200f / hyperAnimationStrength).coerceIn(50f, 800f)
                androidx.compose.animation.core.spring(dampingRatio = springDamping, stiffness = springStiffness)
            } else {
                androidx.compose.animation.core.spring()
            }
        ),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Accent strip
            Surface(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight(),
                color = accentColor
            ) {}

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 14.dp)
            ) {
                // Top row: Code badge + Status badges + Hide button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Code + badges
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (period != null) {
                            Surface(
                                shape = MaterialTheme.shapes.extraSmall,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "${period}限",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        // Course code badge
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Text(
                                text = course.code,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        
                        if (course.messageCount > 0) {
                            Surface(
                                shape = MaterialTheme.shapes.extraSmall,
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.Mail, null,
                                        modifier = Modifier.size(12.dp),
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        text = "${course.messageCount}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }

                        if (course.unreadCount > 0) {
                            Surface(
                                shape = MaterialTheme.shapes.extraSmall,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "未読 ${course.unreadCount}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        if (course.isDueSoon) {
                            Surface(
                                shape = MaterialTheme.shapes.extraSmall,
                                color = MaterialTheme.colorScheme.errorContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Warning, null,
                                        modifier = Modifier.size(12.dp),
                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                        "期限間近",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }

                    // Hide/Show button
                    IconButton(
                        onClick = onHideClick,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (isHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (isHidden) "表示" else "非表示",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Title row with icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Tinted icon circle
                    Surface(
                        modifier = Modifier.size(36.dp),
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Text(
                        text = course.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )

                    Icon(
                        Icons.Outlined.ChevronRight, null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}

// Private helper
private fun Modifier.alpha(alpha: Float) = this.then(
    Modifier.graphicsLayer(alpha = alpha)
)

@Composable
fun SkeletonCourseCard() {
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
                    .padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 14.dp)
            ) {
                // Top row: Code badge + hide button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.width(56.dp).height(20.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                        Box(modifier = Modifier.width(48.dp).height(20.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                    }
                    Box(modifier = Modifier.size(16.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Title row with icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(modifier = Modifier.size(36.dp).clip(MaterialTheme.shapes.small).shimmerEffect())
                    Box(modifier = Modifier.weight(1f).height(16.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                    Box(modifier = Modifier.size(20.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                }
            }
        }
    }
}

@Composable
fun AdminInfoCard(announcements: List<AdminAnnouncement>, onUrlClick: (String) -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "管理者からのお知らせ",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            announcements.take(3).forEachIndexed { index, item -> // Limit to 3
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.5f))
                Column(modifier = Modifier.clickable { onUrlClick(item.url) }) {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            item.sender,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            item.date,
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
fun SurveyInfoCard(surveys: List<SurveyBlock>, onUrlClick: (String) -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Assignment, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "アンケート",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            surveys.forEachIndexed { index, item ->
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha=0.2f))
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onUrlClick(item.url) },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    if (item.count > 0) {
                        Surface(
                            color = MaterialTheme.colorScheme.error,
                            shape = androidx.compose.foundation.shape.CircleShape
                        ) {
                            Text(
                                item.count.toString(),
                                color = MaterialTheme.colorScheme.onError,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
