package com.webclass.plus.ui.screens

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.DirectionsBoat
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PrecisionManufacturing
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Science
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.webclass.plus.data.models.*
import com.webclass.plus.util.SyllabusJsBridge
import com.webclass.plus.util.getCourseIcon
import com.webclass.plus.ui.components.shimmerEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlin.math.roundToInt


private const val BASE_URL = "https://syllabus.kosen-k.go.jp"
private const val DEPARTMENTS_URL = "$BASE_URL/Pages/PublicDepartments?school_id=14&lang=ja"

enum class SyllabusPage {
    DEPARTMENTS, SUBJECTS, DETAIL, SIMULATOR
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SyllabusScreen(resetTrigger: Int = 0) {
    val activityContext = androidx.compose.ui.platform.LocalContext.current as androidx.activity.ComponentActivity
    val viewModel: com.webclass.plus.ui.viewmodels.SyllabusViewModel = androidx.lifecycle.viewmodel.compose.viewModel(activityContext)
    
    val detailListState = rememberLazyListState()
    
    val currentPage by viewModel.currentPage.collectAsState()
    val departments by viewModel.departments.collectAsState()
    val subjects by viewModel.subjects.collectAsState()
    val syllabusDetail by viewModel.syllabusDetail.collectAsState()
    val selectedDepartment by viewModel.selectedDepartment.collectAsState()
    val selectedSubjectName by viewModel.selectedSubjectName.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val hasError by viewModel.hasError.collectAsState()
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val themePreferences = remember { com.webclass.plus.util.ThemePreferences(context) }
    val enableHyperAnimation by themePreferences.enableHyperAnimationFlow.collectAsState(initial = false)
    val enableLightweightMode by themePreferences.enableLightweightModeFlow.collectAsState(initial = false)
    val hyperAnimationStrength by themePreferences.hyperAnimationStrengthFlow.collectAsState(initial = 1.0f)

    val springDampingEnter = (0.8f - hyperAnimationStrength * 0.4f).coerceIn(0.15f, 0.8f)
    val springStiffnessEnter = (200f / hyperAnimationStrength).coerceIn(50f, 800f)
    val springDampingExit = (0.9f - hyperAnimationStrength * 0.4f).coerceIn(0.2f, 0.9f)
    val springStiffnessExit = (400f / hyperAnimationStrength).coerceIn(100f, 1500f)
    
    androidx.activity.compose.BackHandler(enabled = currentPage != SyllabusPage.DEPARTMENTS) {
        when (currentPage) {
            SyllabusPage.SIMULATOR -> viewModel.goBackFromSimulator()
            SyllabusPage.DETAIL -> viewModel.goBackFromDetail()
            SyllabusPage.SUBJECTS -> viewModel.goBackFromSubjects()
            else -> {}
        }
    }

    LaunchedEffect(resetTrigger) {
        if (resetTrigger > viewModel.lastResetTrigger) {
            viewModel.lastResetTrigger = resetTrigger
            viewModel.resetToDepartments()
        }
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            when (currentPage) {
                                SyllabusPage.DEPARTMENTS -> "シラバス"
                                SyllabusPage.SUBJECTS -> selectedDepartment
                                SyllabusPage.DETAIL -> selectedSubjectName
                                SyllabusPage.SIMULATOR -> "評価シミュレーター"
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                },
                navigationIcon = {
                    if (currentPage != SyllabusPage.DEPARTMENTS) {
                        IconButton(onClick = {
                            when (currentPage) {
                                SyllabusPage.SIMULATOR -> viewModel.goBackFromSimulator()
                                SyllabusPage.DETAIL -> viewModel.goBackFromDetail()
                                SyllabusPage.SUBJECTS -> viewModel.goBackFromSubjects()
                                else -> {}
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
        ) {
            AnimatedContent(
                targetState = currentPage,
                transitionSpec = {
                    if (enableLightweightMode) {
                        (EnterTransition.None togetherWith ExitTransition.None).apply {
                            targetContentZIndex = if (targetState.ordinal > initialState.ordinal) 1f else -1f
                        }
                    } else if (targetState.ordinal > initialState.ordinal) {
                        // Forward navigation (Target on top)
                        val enter = if (enableHyperAnimation) {
                            slideInHorizontally(initialOffsetX = { it }, animationSpec = androidx.compose.animation.core.spring(dampingRatio = springDampingEnter, stiffness = springStiffnessEnter)) + fadeIn(tween(300))
                        } else {
                            slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(350, easing = FastOutSlowInEasing)) + fadeIn(tween(350))
                        }
                        val exit = if (enableHyperAnimation) {
                            slideOutHorizontally(targetOffsetX = { -it / 4 }, animationSpec = androidx.compose.animation.core.spring(dampingRatio = springDampingExit, stiffness = springStiffnessExit)) + fadeOut(tween(200))
                        } else {
                            slideOutHorizontally(targetOffsetX = { -it / 4 }, animationSpec = tween(350, easing = FastOutSlowInEasing)) + fadeOut(tween(150))
                        }
                        (enter togetherWith exit).apply {
                            targetContentZIndex = 1f
                        }
                    } else {
                        // Backward navigation (Exiting/Initial on top)
                        val enter = if (enableHyperAnimation) {
                            slideInHorizontally(initialOffsetX = { -it / 4 }, animationSpec = androidx.compose.animation.core.spring(dampingRatio = springDampingExit, stiffness = springStiffnessExit)) + fadeIn(tween(300))
                        } else {
                            slideInHorizontally(initialOffsetX = { -it / 4 }, animationSpec = tween(350, easing = FastOutSlowInEasing)) + fadeIn(tween(350))
                        }
                        val exit = if (enableHyperAnimation) {
                            slideOutHorizontally(targetOffsetX = { it }, animationSpec = androidx.compose.animation.core.spring(dampingRatio = springDampingEnter, stiffness = springStiffnessEnter)) + fadeOut(tween(200))
                        } else {
                            slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(350, easing = FastOutSlowInEasing)) + fadeOut(tween(150))
                        }
                        (enter togetherWith exit).apply {
                            targetContentZIndex = -1f
                        }
                    }
                },
                label = "page_transition"
            ) { page ->
                if (hasError) {
                    ErrorState(
                        onRetry = {
                            viewModel.setHasError(false)
                            viewModel.setLoading(true)
                            viewModel.triggerReload()
                        }
                    )
                } else if (isLoading) {
                    LoadingState(page)
                } else {
                    when (page) {
                        SyllabusPage.DEPARTMENTS -> DepartmentList(
                            departments = departments,
                            onDepartmentClick = { dept ->
                                viewModel.setSelectedDepartment(dept.name)
                                viewModel.setCurrentPage(SyllabusPage.SUBJECTS)
                                viewModel.setLoading(true)
                                viewModel.triggerLoadUrl(dept.publicSubjectsUrl)
                            }
                        )
                        SyllabusPage.SUBJECTS -> SubjectList(
                            subjects = subjects,
                            departmentName = selectedDepartment,
                            viewModel = viewModel,
                            onSubjectClick = { subject ->
                                if (subject.syllabusUrl.isNotEmpty()) {
                                    viewModel.setSelectedSubjectName(subject.name)
                                    viewModel.setCurrentPage(SyllabusPage.DETAIL)
                                    viewModel.setLoading(true)
                                    viewModel.triggerLoadUrl(subject.syllabusUrl)
                                }
                            }
                        )
                        SyllabusPage.DETAIL -> {
                            syllabusDetail?.let { detail ->
                                SyllabusDetailView(
                                    detail = detail,
                                    listState = detailListState,
                                    onLaunchSimulator = { viewModel.setCurrentPage(SyllabusPage.SIMULATOR) }
                                )
                            }
                        }
                        SyllabusPage.SIMULATOR -> {
                            syllabusDetail?.let { detail ->
                                SyllabusSimulatorView(
                                    evaluationList = detail.evaluation,
                                    onBack = { viewModel.goBackFromSimulator() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Loading State ────────────────────────────────────────────────
@Composable
private fun LoadingState(currentPage: SyllabusPage) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth > 600.dp
        
        when (currentPage) {
            SyllabusPage.DEPARTMENTS -> {
                val columns = if (isWideScreen)
                    androidx.compose.foundation.lazy.grid.GridCells.Adaptive(320.dp)
                else
                    androidx.compose.foundation.lazy.grid.GridCells.Fixed(1)

                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = columns,
                    contentPadding = PaddingValues(top = 0.dp, start = 16.dp, end = 16.dp, bottom = 24.dp),
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Section header skeleton
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(modifier = Modifier.width(4.dp).height(20.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                            Box(modifier = Modifier.size(18.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                            Box(modifier = Modifier.width(120.dp).height(18.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                        }
                    }
                    // Department cards
                    items(5) {
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Surface(modifier = Modifier.width(4.dp).fillMaxHeight(), color = MaterialTheme.colorScheme.surfaceVariant) {}
                                Row(
                                    modifier = Modifier.weight(1f).padding(start = 12.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Box(modifier = Modifier.size(40.dp).clip(MaterialTheme.shapes.small).shimmerEffect())
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Box(modifier = Modifier.fillMaxWidth(0.6f).height(16.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                                    }
                                    Box(modifier = Modifier.size(18.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                                }
                            }
                        }
                    }
                    // Second section header
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(modifier = Modifier.width(4.dp).height(20.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                            Box(modifier = Modifier.size(18.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                            Box(modifier = Modifier.width(100.dp).height(18.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                        }
                    }
                    items(2) {
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Surface(modifier = Modifier.width(4.dp).fillMaxHeight(), color = MaterialTheme.colorScheme.surfaceVariant) {}
                                Row(
                                    modifier = Modifier.weight(1f).padding(start = 12.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Box(modifier = Modifier.size(40.dp).clip(MaterialTheme.shapes.small).shimmerEffect())
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Box(modifier = Modifier.fillMaxWidth(0.5f).height(16.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                                        Box(modifier = Modifier.fillMaxWidth(0.3f).height(12.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                                    }
                                    Box(modifier = Modifier.size(18.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                                }
                            }
                        }
                    }
                }
            }
            SyllabusPage.SUBJECTS -> {
                val columns = if (isWideScreen)
                    androidx.compose.foundation.lazy.grid.GridCells.Adaptive(300.dp)
                else
                    androidx.compose.foundation.lazy.grid.GridCells.Fixed(1)

                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = columns,
                    contentPadding = PaddingValues(bottom = 24.dp),
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Search bar + filter chips skeleton
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Search bar
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .clip(MaterialTheme.shapes.medium)
                                    .shimmerEffect()
                            )
                            // Filter chips row
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(modifier = Modifier.width(72.dp).height(32.dp).clip(MaterialTheme.shapes.small).shimmerEffect())
                                Box(modifier = Modifier.width(64.dp).height(32.dp).clip(MaterialTheme.shapes.small).shimmerEffect())
                                Box(modifier = Modifier.width(56.dp).height(32.dp).clip(MaterialTheme.shapes.small).shimmerEffect())
                            }
                        }
                    }
                    // First category header
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(modifier = Modifier.width(4.dp).height(20.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                            Box(modifier = Modifier.size(18.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                            Box(modifier = Modifier.width(100.dp).height(18.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                            Spacer(modifier = Modifier.weight(1f))
                            Box(modifier = Modifier.width(30.dp).height(14.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                        }
                    }
                    // Subject cards
                    items(4) {
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Surface(modifier = Modifier.width(4.dp).fillMaxHeight(), color = MaterialTheme.colorScheme.surfaceVariant) {}
                                Column(
                                    modifier = Modifier.weight(1f).padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Box(modifier = Modifier.width(36.dp).height(18.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                                        Box(modifier = Modifier.width(56.dp).height(18.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                                        Box(modifier = Modifier.width(40.dp).height(18.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Box(modifier = Modifier.size(20.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                                        Box(modifier = Modifier.fillMaxWidth(0.7f).height(16.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Box(modifier = Modifier.size(14.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                                        Box(modifier = Modifier.width(80.dp).height(12.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                                    }
                                }
                            }
                        }
                    }
                    // Second category header
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(modifier = Modifier.width(4.dp).height(20.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                            Box(modifier = Modifier.size(18.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                            Box(modifier = Modifier.width(80.dp).height(18.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                            Spacer(modifier = Modifier.weight(1f))
                            Box(modifier = Modifier.width(30.dp).height(14.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                        }
                    }
                    items(3) {
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Surface(modifier = Modifier.width(4.dp).fillMaxHeight(), color = MaterialTheme.colorScheme.surfaceVariant) {}
                                Column(
                                    modifier = Modifier.weight(1f).padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Box(modifier = Modifier.width(36.dp).height(18.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                                        Box(modifier = Modifier.width(48.dp).height(18.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                                        Box(modifier = Modifier.width(40.dp).height(18.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Box(modifier = Modifier.size(20.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                                        Box(modifier = Modifier.fillMaxWidth(0.6f).height(16.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Box(modifier = Modifier.size(14.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                                        Box(modifier = Modifier.width(60.dp).height(12.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                                    }
                                }
                            }
                        }
                    }
                }
            }
            SyllabusPage.DETAIL, SyllabusPage.SIMULATOR -> {
               Column(
                   modifier = Modifier.fillMaxSize(),
                   horizontalAlignment = Alignment.CenterHorizontally
               ) {
                   // Sticky Navigation Chips Skeleton at the top
                   Row(
                       modifier = Modifier
                           .fillMaxWidth()
                           .background(MaterialTheme.colorScheme.surface)
                           .padding(horizontal = 16.dp, vertical = 8.dp),
                       horizontalArrangement = Arrangement.spacedBy(8.dp),
                       verticalAlignment = Alignment.CenterVertically
                   ) {
                       Box(modifier = Modifier.width(60.dp).height(32.dp).clip(RoundedCornerShape(16.dp)).shimmerEffect())
                       Box(modifier = Modifier.width(80.dp).height(32.dp).clip(RoundedCornerShape(16.dp)).shimmerEffect())
                       Box(modifier = Modifier.width(80.dp).height(32.dp).clip(RoundedCornerShape(16.dp)).shimmerEffect())
                       Box(modifier = Modifier.width(90.dp).height(32.dp).clip(RoundedCornerShape(16.dp)).shimmerEffect())
                       Box(modifier = Modifier.width(80.dp).height(32.dp).clip(RoundedCornerShape(16.dp)).shimmerEffect())
                       Box(modifier = Modifier.width(80.dp).height(32.dp).clip(RoundedCornerShape(16.dp)).shimmerEffect())
                   }
                   HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                   
                   Column(
                       modifier = Modifier
                           .fillMaxSize()
                           .padding(16.dp),
                       verticalArrangement = Arrangement.spacedBy(16.dp)
                   ) {
                       // Title
                       Box(modifier = Modifier.fillMaxWidth(0.7f).height(24.dp).clip(MaterialTheme.shapes.small).shimmerEffect())
                       // Chips row
                       Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                           Box(modifier = Modifier.width(70.dp).height(28.dp).clip(MaterialTheme.shapes.small).shimmerEffect())
                           Box(modifier = Modifier.width(90.dp).height(28.dp).clip(MaterialTheme.shapes.small).shimmerEffect())
                           Box(modifier = Modifier.width(60.dp).height(28.dp).clip(MaterialTheme.shapes.small).shimmerEffect())
                       }
                       // Section heading
                       Box(modifier = Modifier.fillMaxWidth(0.3f).height(18.dp).clip(MaterialTheme.shapes.small).shimmerEffect())
                       // Info card
                       OutlinedCard(
                           modifier = Modifier.fillMaxWidth(),
                           colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                       ) {
                           Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                               repeat(4) {
                                   Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                       Box(modifier = Modifier.width(80.dp).height(14.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                                       Box(modifier = Modifier.width(140.dp).height(14.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                                   }
                               }
                           }
                       }
                       // Another section heading
                       Box(modifier = Modifier.fillMaxWidth(0.35f).height(18.dp).clip(MaterialTheme.shapes.small).shimmerEffect())
                       // Content card
                       OutlinedCard(
                           modifier = Modifier.fillMaxWidth(),
                           colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                       ) {
                           Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                               Box(modifier = Modifier.fillMaxWidth(0.9f).height(14.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                               Box(modifier = Modifier.fillMaxWidth(0.7f).height(14.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                               Box(modifier = Modifier.fillMaxWidth(0.5f).height(14.dp).clip(MaterialTheme.shapes.extraSmall).shimmerEffect())
                           }
                       }
                   }
               }
            }
        }
    }
}

// ─── Error State ──────────────────────────────────────────────────
@Composable
private fun ErrorState(onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "データの読み込みに失敗しました",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FilledTonalButton(onClick = onRetry) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("再試行")
            }
        }
    }
}

// ─── Department List ──────────────────────────────────────────────
@Composable
private fun DepartmentList(
    departments: List<SyllabusDepartment>,
    onDepartmentClick: (SyllabusDepartment) -> Unit
) {
    // Categorize departments
    val generalDepts = departments.filter { it.name.contains("一般") || it.name.contains("総合") }
    val advancedDepts = departments.filter { it.name.contains("専攻") }
    val regularDepts = departments.filter { !it.name.contains("一般") && !it.name.contains("総合") && !it.name.contains("専攻") }

    androidx.compose.foundation.layout.BoxWithConstraints {
        val isWideScreen = maxWidth > 600.dp
        val columns = if (isWideScreen)
            androidx.compose.foundation.lazy.grid.GridCells.Adaptive(320.dp)
        else
            androidx.compose.foundation.lazy.grid.GridCells.Fixed(1)

        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = columns,
            contentPadding = PaddingValues(top = 0.dp, start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header removed as per user request

            // Regular Departments
            if (regularDepts.isNotEmpty()) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    SectionHeader("本科 (準学士課程)", Icons.Outlined.School)
                }
                items(regularDepts.size) { index ->
                    DepartmentCard(regularDepts[index], onDepartmentClick)
                }
            }

            // Advanced Departments
            if (advancedDepts.isNotEmpty()) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    SectionHeader("専攻科 (学士課程)", Icons.Outlined.School) // Could use a different icon like Engineering
                }
                items(advancedDepts.size) { index ->
                    DepartmentCard(advancedDepts[index], onDepartmentClick)
                }
            }

            // General/Other Departments
            if (generalDepts.isNotEmpty()) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    SectionHeader("一般科目・その他", Icons.Outlined.Info) // Or Book/MenuBook
                }
                items(generalDepts.size) { index ->
                    DepartmentCard(generalDepts[index], onDepartmentClick)
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, icon: ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(4.dp).height(20.dp)
        ) {}
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun DepartmentCard(
    department: SyllabusDepartment,
    onClick: (SyllabusDepartment) -> Unit
) {
    val icon = getDepartmentIcon(department.name)
    val color = getDepartmentColor(department.name)

    OutlinedCard(
        onClick = { onClick(department) },
        modifier = Modifier.fillMaxWidth(),
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
                color = color
            ) {}

            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Tinted icon badge
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = MaterialTheme.shapes.small,
                    color = color.copy(alpha = 0.12f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = color,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Text
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = department.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }

                // Chevron
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }
        }
    }
}

private fun getDepartmentIcon(name: String): ImageVector {
    val n = name.lowercase()
    return when {
        n.contains("機械") || n.contains("ロボット") -> Icons.Outlined.PrecisionManufacturing
        n.contains("電気") || n.contains("電子") -> Icons.Outlined.Bolt
        n.contains("情報") || n.contains("知能") -> Icons.Outlined.Computer
        n.contains("化学") || n.contains("物質") -> Icons.Outlined.Science
        n.contains("建築") || n.contains("環境") || n.contains("都市") -> Icons.Outlined.Apartment
        n.contains("商船") -> Icons.Outlined.DirectionsBoat
        n.contains("一般") || n.contains("総合") -> Icons.Outlined.MenuBook
        n.contains("専攻") -> Icons.Outlined.School
        else -> Icons.Outlined.School
    }
}

@Composable
private fun getDepartmentColor(name: String): Color {
    val n = name.lowercase()
    return when {
        n.contains("機械") || n.contains("ロボット") -> MaterialTheme.colorScheme.error
        n.contains("電気") || n.contains("電子") -> MaterialTheme.colorScheme.tertiary
        n.contains("情報") || n.contains("知能") -> MaterialTheme.colorScheme.primary
        n.contains("化学") || n.contains("物質") -> MaterialTheme.colorScheme.secondary
        n.contains("建築") || n.contains("環境") || n.contains("都市") -> MaterialTheme.colorScheme.onSurfaceVariant
        n.contains("商船") -> MaterialTheme.colorScheme.tertiary
        n.contains("一般") || n.contains("総合") -> MaterialTheme.colorScheme.outline
        n.contains("専攻") -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.secondary
    }
}

// ─── Subject List ─────────────────────────────────────────────────

// Sealed class for flattened list items to enable proper lazy rendering
private sealed class SubjectListItem {
    data class Header(val category: String) : SubjectListItem()
    data class SubjectItem(val subject: SyllabusSubject) : SubjectListItem()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubjectList(
    subjects: List<SyllabusSubject>,
    departmentName: String,
    viewModel: com.webclass.plus.ui.viewmodels.SyllabusViewModel,
    onSubjectClick: (SyllabusSubject) -> Unit
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val selectedRequirement by viewModel.selectedRequirement.collectAsState()
    val selectedTargetYear by viewModel.selectedTargetYear.collectAsState()

    // Unique filter options
    val categories = remember(subjects) { subjects.map { it.category.ifEmpty { "その他" } }.distinct().sorted() }
    val requirements = remember(subjects) { subjects.map { it.requirement }.filter { it.isNotEmpty() }.distinct().sorted() }
    val targetYears = remember(subjects) { subjects.map { it.targetYear }.filter { it.isNotEmpty() }.distinct().sorted() }

    // Filter logic
    val filteredSubjects = remember(subjects, searchQuery, selectedCategory, selectedRequirement, selectedTargetYear) {
        subjects.filter { subject ->
            val matchesSearch = searchQuery.isBlank() ||
                    subject.name.contains(searchQuery, ignoreCase = true) ||
                    subject.code.contains(searchQuery, ignoreCase = true) ||
                    subject.teachers.contains(searchQuery, ignoreCase = true)

            val matchesCategory = selectedCategory == null || (subject.category.ifEmpty { "その他" }) == selectedCategory
            val matchesRequirement = selectedRequirement == null || subject.requirement == selectedRequirement
            val matchesTargetYear = selectedTargetYear == null || subject.targetYear == selectedTargetYear

            matchesSearch && matchesCategory && matchesRequirement && matchesTargetYear
        }
    }

    // Pre-flatten grouped subjects into a single list for truly lazy rendering
    val groupedSubjects = remember(filteredSubjects) {
        filteredSubjects.groupBy { it.category.ifEmpty { "その他" } }
    }
    val flattenedItems = remember(filteredSubjects) {
        buildList {
            groupedSubjects.forEach { (category, categorySubjects) ->
                add(SubjectListItem.Header(category))
                categorySubjects.forEach { add(SubjectListItem.SubjectItem(it)) }
            }
        }
    }

    val filteredCount = filteredSubjects.size

    androidx.compose.foundation.layout.BoxWithConstraints {
    val isWideScreen = maxWidth > 600.dp
    val columns = if (isWideScreen)
        androidx.compose.foundation.lazy.grid.GridCells.Adaptive(300.dp)
    else
        androidx.compose.foundation.lazy.grid.GridCells.Fixed(1)

    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
        columns = columns,
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Sticky Header with Search and Filters
        item(
            key = "search_header",
            span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("科目名、コード、教員名で検索") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    } else null,
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    )
                )

                // Filters Row
                Row(
                    modifier = Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Category Filter
                    FilterChip(
                        selected = selectedCategory != null,
                        onClick = { /* Handle via Dropdown or Dialog if many options, or cycle/toggle if few */ },
                        label = { Text(selectedCategory ?: "科目区分") },
                        trailingIcon = {
                            Box {
                                var expanded by remember { mutableStateOf(false) }
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    null,
                                    Modifier.clickable { expanded = true }
                                )
                                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                    DropdownMenuItem(
                                        text = { Text("すべて") },
                                        onClick = { viewModel.setSelectedCategory(null); expanded = false }
                                    )
                                    categories.forEach { cat ->
                                        DropdownMenuItem(
                                            text = { Text(cat) },
                                            onClick = { viewModel.setSelectedCategory(cat); expanded = false }
                                        )
                                    }
                                }
                            }
                        },
                        leadingIcon = if (selectedCategory != null) {
                            { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                        } else null
                    )

                    // Requirement Filter
                    if (requirements.isNotEmpty()) {
                        FilterChip(
                            selected = selectedRequirement != null,
                            onClick = { /* */ },
                            label = { Text(selectedRequirement ?: "必修区分") },
                            trailingIcon = {
                                Box {
                                    var expanded by remember { mutableStateOf(false) }
                                    Icon(Icons.Default.ArrowDropDown, null, Modifier.clickable { expanded = true })
                                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                        DropdownMenuItem(
                                            text = { Text("すべて") },
                                            onClick = { viewModel.setSelectedRequirement(null); expanded = false }
                                        )
                                        requirements.forEach { req ->
                                            DropdownMenuItem(
                                                text = { Text(req) },
                                                onClick = { viewModel.setSelectedRequirement(req); expanded = false }
                                            )
                                        }
                                    }
                                }
                            },
                             leadingIcon = if (selectedRequirement != null) {
                                { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                            } else null
                        )
                    }

                    // Target Year Filter
                    if (targetYears.isNotEmpty()) {
                        FilterChip(
                            selected = selectedTargetYear != null,
                            onClick = { /* */ },
                            label = { Text(selectedTargetYear ?: "対象学年") },
                            trailingIcon = {
                                Box {
                                    var expanded by remember { mutableStateOf(false) }
                                    Icon(Icons.Default.ArrowDropDown, null, Modifier.clickable { expanded = true })
                                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                        DropdownMenuItem(
                                            text = { Text("すべて") },
                                            onClick = { viewModel.setSelectedTargetYear(null); expanded = false }
                                        )
                                        targetYears.forEach { year ->
                                            DropdownMenuItem(
                                                text = { Text(year) },
                                                onClick = { viewModel.setSelectedTargetYear(year); expanded = false }
                                            )
                                        }
                                    }
                                }
                            },
                             leadingIcon = if (selectedTargetYear != null) {
                                { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                }
                
                // Result Count
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        "$filteredCount 件",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }

        // Render flattened items with stable keys
        items(
            count = flattenedItems.size,
            key = { index ->
                when (val item = flattenedItems[index]) {
                    is SubjectListItem.Header -> "header_${item.category}"
                    is SubjectListItem.SubjectItem -> "subject_${item.subject.name}_${item.subject.code}_$index"
                }
            },
            span = { index ->
                when (flattenedItems[index]) {
                    is SubjectListItem.Header -> androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan)
                    is SubjectListItem.SubjectItem -> androidx.compose.foundation.lazy.grid.GridItemSpan(1)
                }
            }
        ) { index ->
            when (val item = flattenedItems[index]) {
                is SubjectListItem.Header -> {
                    Row(
                        modifier = Modifier
                            .padding(top = 16.dp, bottom = 4.dp, start = 16.dp, end = 16.dp)
                            .animateItem(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(4.dp).height(18.dp)
                        ) {}
                        Text(
                            item.category,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        // Item count badge
                        val count = groupedSubjects[item.category]?.size ?: 0
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                "$count",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                is SubjectListItem.SubjectItem -> {
                    Box(modifier = Modifier.padding(horizontal = 16.dp).animateItem()) {
                        SubjectCard(
                            subject = item.subject,
                            onClick = { onSubjectClick(item.subject) }
                        )
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun SubjectCard(
    subject: SyllabusSubject,
    onClick: () -> Unit
) {
    val icon = remember(subject.name) { getCourseIcon(subject.name) }

    val accentColor = when {
        subject.requirement.contains("必") -> MaterialTheme.colorScheme.error
        subject.requirement.contains("選") -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
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
                    .padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)
            ) {
                // Top row: badges
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (subject.requirement.isNotEmpty()) {
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = if (subject.requirement.contains("必"))
                                MaterialTheme.colorScheme.errorContainer
                            else
                                MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Text(
                                text = subject.requirement,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (subject.requirement.contains("必"))
                                    MaterialTheme.colorScheme.onErrorContainer
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (subject.credits.isNotEmpty()) {
                        val creditLabel = remember(subject.creditType, subject.credits) {
                            if (subject.creditType.isNotEmpty()) "${subject.creditType} ${subject.credits}" else subject.credits
                        }
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = creditLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (subject.targetYear.isNotEmpty()) {
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = subject.targetYear,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
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
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        subject.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Teacher info
                if (subject.teachers.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Person,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            subject.teachers,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallInfoChip(
    text: String,
    isHighlight: Boolean = false
) {
    val bgColor = if (isHighlight)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceContainerHigh
    val textColor = if (isHighlight)
        MaterialTheme.colorScheme.onPrimaryContainer
    else
        MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bgColor
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = textColor
        )
    }
}

// ─── Syllabus Detail View ─────────────────────────────────────────
@Composable
private fun SyllabusDetailView(
    detail: SyllabusDetail,
    listState: LazyListState,
    onLaunchSimulator: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val themePreferences = remember { com.webclass.plus.util.ThemePreferences(context) }
    val enableLightweightMode by themePreferences.enableLightweightModeFlow.collectAsState(initial = false)


    val coroutineScope = rememberCoroutineScope()

    val sections = remember(detail) {
        val list = mutableListOf<Pair<String, Int>>()
        list.add("トップ" to 0)
        
        var currentIndex = 1 // index of Basic Info (0 is Header Card)
        list.add("基礎情報" to currentIndex++)
        
        if (detail.goals.isNotEmpty()) {
            list.add("到達目標" to currentIndex++)
        }
        if (detail.rubric.isNotEmpty()) {
            list.add("ルーブリック" to currentIndex++)
        }
        if (detail.overview.isNotEmpty() || detail.methodology.isNotEmpty() || detail.notes.isNotEmpty()) {
            list.add("教育方法等" to currentIndex++)
        }
        if (detail.lessonPlan.isNotEmpty()) {
            list.add("授業計画" to currentIndex++)
        }
        if (detail.evaluation.isNotEmpty()) {
            list.add("評価割合" to currentIndex++)
        }
        list
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Sticky Horizontal Scrollable Navigation Chips at the top of the detail screen
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            sections.forEach { (name, targetIndex) ->
                AssistChip(
                    onClick = {
                        coroutineScope.launch {
                            if (enableLightweightMode) {
                                listState.scrollToItem(targetIndex)
                            } else {
                                listState.animateScrollToItem(targetIndex)
                            }
                        }
                    },
                    label = { Text(name, style = MaterialTheme.typography.labelMedium) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                        labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
            }
        }
        
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.widthIn(max = 800.dp),
                contentPadding = PaddingValues(top = 12.dp, start = 16.dp, end = 16.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ── Header Card ──
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
                                color = MaterialTheme.colorScheme.primary
                            ) {}
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(20.dp)
                            ) {
                                Text(
                                    detail.basicInfo.subjectName.ifEmpty { "科目名不明" },
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                if (detail.basicInfo.teachers.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            Icons.Outlined.Person, null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            detail.basicInfo.teachers,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Quick info chips
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (detail.basicInfo.targetYear.isNotEmpty()) {
                                        DetailChip(text = detail.basicInfo.targetYear)
                                    }
                                    if (detail.basicInfo.credits.isNotEmpty()) {
                                        val creditStr = if (detail.basicInfo.creditType.isNotEmpty()) {
                                            "${detail.basicInfo.creditType} ${detail.basicInfo.credits}"
                                        } else detail.basicInfo.credits
                                        DetailChip(text = creditStr)
                                    }
                                    if (detail.basicInfo.semester.isNotEmpty()) {
                                        DetailChip(text = detail.basicInfo.semester)
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Basic Info ──
                item {
                    DetailSection(title = "科目基礎情報") {
                        val info = detail.basicInfo
                        val items = listOf(
                            "学校" to info.school,
                            "開講年度" to info.year,
                            "科目番号" to info.subjectCode,
                            "科目区分" to info.category,
                            "授業形態" to info.teachingFormat,
                            "開設学科" to info.department,
                            "週時間数" to info.weeklyHours,
                        ).filter { it.second.isNotEmpty() }

                        items.forEachIndexed { idx, (label, value) ->
                            DetailInfoRow(label = label, value = value)
                            if (idx < items.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            }
                        }

                        if (info.textbook.isNotEmpty()) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                            DetailInfoRow(label = "教科書/教材", value = info.textbook)
                        }
                    }
                }

                // ── Goals ──
                if (detail.goals.isNotEmpty()) {
                    item {
                        DetailSection(title = "到達目標") {
                            Text(
                                detail.goals,
                                style = MaterialTheme.typography.bodyMedium,
                                lineHeight = 22.sp
                            )
                        }
                    }
                }

                // ── Rubric ──
                if (detail.rubric.isNotEmpty()) {
                    item {
                        DetailSection(title = "ルーブリック") {
                            detail.rubric.forEachIndexed { index, rubric ->
                                if (index > 0) Spacer(modifier = Modifier.height(10.dp))
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
                                        Column(modifier = Modifier.weight(1f).padding(14.dp)) {
                                            Text(
                                                rubric.item,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            RubricRow("理想的", rubric.ideal, MaterialTheme.colorScheme.primary)
                                            RubricRow("標準的", rubric.standard, MaterialTheme.colorScheme.tertiary)
                                            RubricRow("未到達", rubric.notReached, MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Education Methods ──
                if (detail.overview.isNotEmpty() || detail.methodology.isNotEmpty() || detail.notes.isNotEmpty()) {
                    item {
                        DetailSection(title = "教育方法等") {
                            if (detail.overview.isNotEmpty()) {
                                SubSectionLabel("概要")
                                Text(detail.overview, style = MaterialTheme.typography.bodyMedium, lineHeight = 22.sp)
                                if (detail.methodology.isNotEmpty() || detail.notes.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(14.dp))
                                }
                            }
                            if (detail.methodology.isNotEmpty()) {
                                SubSectionLabel("授業の進め方・方法")
                                Text(detail.methodology, style = MaterialTheme.typography.bodyMedium, lineHeight = 22.sp)
                                if (detail.notes.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(14.dp))
                                }
                            }
                            if (detail.notes.isNotEmpty()) {
                                SubSectionLabel("注意点")
                                Text(detail.notes, style = MaterialTheme.typography.bodyMedium, lineHeight = 22.sp)
                            }
                        }
                    }
                }

                // ── Lesson Plan ──
                if (detail.lessonPlan.isNotEmpty()) {
                    item {
                        DetailSection(title = "授業計画") {
                            detail.lessonPlan.forEachIndexed { index, lesson ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Week number badge
                                    Surface(
                                        modifier = Modifier.size(32.dp),
                                        shape = MaterialTheme.shapes.small,
                                        color = MaterialTheme.colorScheme.secondaryContainer
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                lesson.week,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            lesson.content,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        if (lesson.target.isNotEmpty()) {
                                            Text(
                                                lesson.target,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(top = 2.dp),
                                                lineHeight = 18.sp
                                            )
                                        }
                                    }
                                }
                                if (index < detail.lessonPlan.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 44.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Evaluation ──
                if (detail.evaluation.isNotEmpty()) {
                    item {
                        DetailSection(title = "評価割合") {
                            val overall = detail.evaluation.first()
                            val specificItems = detail.evaluation.drop(1)
                            
                            // Display Overall as Pie Chart
                            Text(
                                text = overall.name,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            EvaluationPieChart(evaluationItem = overall)

                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = onLaunchSimulator,
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.small,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TrendingUp,
                                    contentDescription = null
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("評価シミュレーターを起動", fontWeight = FontWeight.Bold)
                            }
                            
                            // Display specific items if any
                            if (specificItems.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(24.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                Text(
                                    text = "評価詳細",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                
                                specificItems.forEachIndexed { index, eval ->
                                    OutlinedCard(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = MaterialTheme.shapes.small,
                                        colors = CardDefaults.outlinedCardColors(
                                            containerColor = MaterialTheme.colorScheme.surface
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                eval.name,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Surface(
                                                shape = MaterialTheme.shapes.extraSmall,
                                                color = MaterialTheme.colorScheme.primaryContainer
                                            ) {
                                                Text(
                                                    "${eval.total}%",
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                        }
                                    }
                                    if (index < specificItems.lastIndex) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom spacer

            }
        }
    }
}

// ─── Reusable Components ──────────────────────────────────────────

@Composable
private fun DetailSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(4.dp).height(18.dp)
                ) {}
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun DetailInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(100.dp),
            fontWeight = FontWeight.Medium
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SubSectionLabel(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(bottom = 6.dp)
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraSmall,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.width(3.dp).height(14.dp)
        ) {}
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun DetailChip(text: String) {
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
private fun RubricRow(
    label: String,
    text: String,
    color: androidx.compose.ui.graphics.Color
) {
    if (text.isNotEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraSmall,
                color = color.copy(alpha = 0.12f)
            ) {
                Text(
                    label,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun EvaluationPieChart(
    evaluationItem: EvaluationItem
) {
    // Material 3 color palette from theme
    val colorScheme = MaterialTheme.colorScheme
    val palette = listOf(
        colorScheme.primary,
        colorScheme.secondary,
        colorScheme.tertiary,
        colorScheme.primaryContainer,
        colorScheme.secondaryContainer,
        colorScheme.tertiaryContainer,
        colorScheme.error,
        colorScheme.inversePrimary,
        colorScheme.outline,
        colorScheme.outlineVariant
    )
    
    // Group components: merge entries containing "試験" into "試験"
    val grouped = mutableMapOf<String, Int>()
    evaluationItem.components.forEach { (key, value) ->
        if (key.contains("試験")) {
            grouped["試験"] = (grouped["試験"] ?: 0) + value
        } else {
            grouped[key] = (grouped[key] ?: 0) + value
        }
    }
    
    // Build chart items, filtering out zero values
    val chartEntries = grouped.entries
        .filter { it.value > 0 }
        .mapIndexed { index, entry ->
            Triple(entry.key, entry.value, palette[index % palette.size])
        }
    
    val denominator = if (evaluationItem.total > 0) evaluationItem.total else chartEntries.sumOf { it.second }
    
    if (denominator == 0) return

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Donut chart
        Box(
            modifier = Modifier.size(180.dp),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                var startAngle = -90f
                val strokeWidth = 40.dp.toPx()
                
                chartEntries.forEach { item ->
                    val sweepAngle = (item.second.toFloat() / denominator.toFloat()) * 360f
                    
                    drawArc(
                        color = item.third,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = androidx.compose.ui.geometry.Offset(
                            strokeWidth / 2, strokeWidth / 2
                        ),
                        size = androidx.compose.ui.geometry.Size(
                            size.width - strokeWidth,
                            size.height - strokeWidth
                        ),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = strokeWidth,
                            cap = androidx.compose.ui.graphics.StrokeCap.Butt
                        )
                    )
                    startAngle += sweepAngle
                }
            }
            
            // Center label
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "合計",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${evaluationItem.total}%",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface
                )
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // Legend using M3 chips
        val rows = chartEntries.chunked(2)
        rows.forEach { rowItems ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                rowItems.forEach { item ->
                    Surface(
                        modifier = Modifier.padding(horizontal = 4.dp),
                        shape = MaterialTheme.shapes.extraSmall,
                        color = colorScheme.surfaceContainerHigh,
                        tonalElevation = 1.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(item.third)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = item.first,
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${item.second}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SyllabusSimulatorView(
    evaluationList: List<EvaluationItem>,
    onBack: () -> Unit
) {
    if (evaluationList.isEmpty()) return
    val overall = evaluationList.first()
    val specificItems = evaluationList.drop(1)

    // Detect screen width and developer settings
    val context = androidx.compose.ui.platform.LocalContext.current
    val developerPreferences = remember { com.webclass.plus.util.DeveloperPreferences(context) }
    val forceTabletMode by developerPreferences.forceTabletModeFlow.collectAsState(initial = false)
    val forceSmartphoneMode by developerPreferences.forceSmartphoneModeFlow.collectAsState(initial = false)
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isWideScreen = when {
        forceTabletMode -> true
        forceSmartphoneMode -> false
        else -> configuration.screenWidthDp >= 600
    }

    // Build the initial list of items to calculate
    val simEntries = remember(evaluationList) {
        val entries = mutableListOf<SimEntry>()
        overall.components.forEach { (categoryName, overallWeight) ->
            if (overallWeight > 0) {
                val specItem = specificItems.find { it.name == categoryName }
                val subComponents = specItem?.components?.filterKeys { it != "合計" && it != "合計点" }
                if (subComponents != null && subComponents.isNotEmpty()) {
                    val sumOfSub = subComponents.values.sum().coerceAtLeast(1)
                    subComponents.forEach { (subName, subValue) ->
                        val calculatedWeight = (subValue.toFloat() / sumOfSub.toFloat()) * overallWeight
                        entries.add(
                            SimEntry(
                                name = subName,
                                parentCategory = categoryName,
                                weight = calculatedWeight
                            )
                        )
                    }
                } else {
                    entries.add(
                        SimEntry(
                            name = categoryName,
                            parentCategory = categoryName,
                            weight = overallWeight.toFloat()
                        )
                    )
                }
            }
        }
        entries
    }

    // Keep state of entered scores (Float, default to 100f)
    val scores = remember(simEntries) {
        mutableStateMapOf<Int, Float>().apply {
            simEntries.forEachIndexed { index, _ -> put(index, 100f) }
        }
    }
    val scoreInputs = remember(simEntries) {
        mutableStateMapOf<Int, String>().apply {
            simEntries.forEachIndexed { index, _ -> put(index, "100") }
        }
    }

    // Calculate total score dynamically
    val totalScore = simEntries.mapIndexed { index, entry ->
        val scoreVal = scores[index] ?: 0f
        (scoreVal / 100f) * entry.weight
    }.sum()

    // Grade and color configuration based on Material 3 design values
    val (gradeLetter, gradeColor, gradeBgColor) = when {
        totalScore >= 90f -> Triple(
            "秀",
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primaryContainer
        )
        totalScore >= 80f -> Triple(
            "優",
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.secondaryContainer
        )
        totalScore >= 70f -> Triple(
            "良",
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.tertiaryContainer
        )
        totalScore >= 60f -> Triple(
            "可",
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.surfaceVariant
        )
        else -> Triple(
            "不可",
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.errorContainer
        )
    }

    val summaryCard = @Composable {
        // Futuristic Android 17 / Material 3 Dashboard-Style Summary Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "総合評価シミュレーション",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // High-End Circular Donut Score Gauge
                Box(
                    modifier = Modifier.size(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val activeColor = if (totalScore >= 60f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    
                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 14.dp.toPx()
                        // 360-degree background ring
                        drawCircle(
                            color = trackColor,
                            radius = (size.minDimension - strokeWidth) / 2,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
                        )
                        // Progress arc
                        drawArc(
                            color = activeColor,
                            startAngle = -90f,
                            sweepAngle = (totalScore / 100f) * 360f,
                            useCenter = false,
                            topLeft = androidx.compose.ui.geometry.Offset(strokeWidth / 2, strokeWidth / 2),
                            size = androidx.compose.ui.geometry.Size(size.width - strokeWidth, size.height - strokeWidth),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = strokeWidth,
                                cap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                        )
                    }

                    // Display inner grade letter & score details
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = gradeLetter,
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.sp
                            ),
                            color = gradeColor
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${String.format("%.1f", totalScore)} 点",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Dynamic Status Pill Banner
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (totalScore >= 60f) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                    contentColor = if (totalScore >= 60f) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (totalScore >= 60f) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (totalScore >= 60f) "単位取得基準点（60点）クリア 🎉" else "単位取得基準点（60点）未満 ⚠️",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }
    }

    val slidersSection = @Composable {
        // Section: Detailed Component Adjustments
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "評価項目別の入力",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${simEntries.size}項目",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            simEntries.forEachIndexed { index, entry ->
                val currentScore = scores[index] ?: 100f
                
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Title and Description Label
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ) {
                                        Text(
                                            text = entry.parentCategory,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        text = "配点比率: ${String.format("%.1f", entry.weight)}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Slider + Quick Preset Controls Row
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Row 1: Slider and Value Pill
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Slider(
                                    value = currentScore,
                                    onValueChange = {
                                        scores[index] = it
                                        scoreInputs[index] = it.roundToInt().toString()
                                    },
                                    valueRange = 0f..100f,
                                    modifier = Modifier.weight(1f),
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.primary,
                                        activeTrackColor = MaterialTheme.colorScheme.primary,
                                        inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    )
                                )
                                
                                OutlinedTextField(
                                    value = scoreInputs[index] ?: currentScore.roundToInt().toString(),
                                    onValueChange = { input ->
                                        val digits = input.filter(Char::isDigit).take(3)
                                        val parsed = digits.toIntOrNull()
                                        scoreInputs[index] = when {
                                            parsed == null -> ""
                                            parsed > 100 -> "100"
                                            else -> digits
                                        }
                                        if (parsed != null) {
                                            scores[index] = parsed.coerceIn(0, 100).toFloat()
                                        }
                                    },
                                    modifier = Modifier.width(92.dp),
                                    suffix = { Text("点") },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }

                            // Row 2: Quick Presets
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "クイック設定:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                listOf(60, 80, 100).forEach { preset ->
                                    val isActive = currentScore.roundToInt() == preset
                                    Surface(
                                        onClick = {
                                            scores[index] = preset.toFloat()
                                            scoreInputs[index] = preset.toString()
                                        },
                                        shape = CircleShape,
                                        color = if (isActive) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        },
                                        border = androidx.compose.foundation.BorderStroke(
                                            width = 1.dp,
                                            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                        )
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "$preset",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isActive) {
                                                    MaterialTheme.colorScheme.onPrimary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                }
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

    if (isWideScreen) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .width(320.dp)
                    .align(Alignment.Top),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                summaryCard()
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                slidersSection()
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            summaryCard()
            slidersSection()
        }
    }
}


private data class SimEntry(
    val name: String,
    val parentCategory: String,
    val weight: Float
)




