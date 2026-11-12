package com.webclass.plus.ui.screens

import com.webclass.plus.ui.viewmodels.PomodoroMode
import com.webclass.plus.ui.viewmodels.PomodoroState

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.webclass.plus.util.ThemePreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

enum class ToolsPage {
    LIST, ATTENDANCE, LIBRARY_SEARCH, POMODORO, STOPWATCH, TODO, UNIT_CONVERTER, KARNAUGH_MAP, SILENT_CAMERA,
    OTHELLO, GOMOKU, NUMBER_MERGE, AIR_HOCKEY, MINE_SWEEPER, FLAPPY_SKY, REACTION_TEST
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    navController: NavController,
    tabClickTrigger: Int = 0,
    isOthelloPlaying: Boolean,
    onOthelloPlayingChanged: (Boolean) -> Unit,
    isGomokuPlaying: Boolean,
    onGomokuPlayingChanged: (Boolean) -> Unit,
    isAirHockeyPlaying: Boolean,
    onAirHockeyPlayingChanged: (Boolean) -> Unit,
    isMineSweeperPlaying: Boolean,
    onMineSweeperPlayingChanged: (Boolean) -> Unit,
    isFlappySkyPlaying: Boolean,
    onFlappySkyPlayingChanged: (Boolean) -> Unit,
    onNavigationOverlayEligibilityChanged: (Boolean) -> Unit,
    viewModel: com.webclass.plus.ui.viewmodels.ToolsViewModel
) {
    val context = LocalContext.current
    val themePreferences = remember { ThemePreferences(context) }
    val useWallpaper by themePreferences.useWallpaperFlow.collectAsState(initial = false)
    val useBrowser by themePreferences.useBrowserFlow.collectAsState(initial = false)
    val enableHyperAnimation by themePreferences.enableHyperAnimationFlow.collectAsState(initial = false)
    val enableLightweightMode by themePreferences.enableLightweightModeFlow.collectAsState(initial = false)
    val hyperAnimationStrength by themePreferences.hyperAnimationStrengthFlow.collectAsState(initial = 1.0f)

    val developerPreferences = remember { com.webclass.plus.util.DeveloperPreferences(context) }
    val forceTabletMode by developerPreferences.forceTabletModeFlow.collectAsState(initial = false)
    val forceSmartphoneMode by developerPreferences.forceSmartphoneModeFlow.collectAsState(initial = false)

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isWideScreen = when {
        forceTabletMode -> true
        forceSmartphoneMode -> false
        else -> configuration.screenWidthDp >= 600
    }

    val springDampingEnter = (0.8f - hyperAnimationStrength * 0.4f).coerceIn(0.15f, 0.8f)
    val springStiffnessEnter = (200f / hyperAnimationStrength).coerceIn(50f, 800f)
    val springDampingExit = (0.9f - hyperAnimationStrength * 0.4f).coerceIn(0.2f, 0.9f)
    val springStiffnessExit = (400f / hyperAnimationStrength).coerceIn(100f, 1500f)

    var currentPage by rememberSaveable { mutableStateOf(ToolsPage.LIST) }

    LaunchedEffect(currentPage) {
        onNavigationOverlayEligibilityChanged(currentPage == ToolsPage.LIST)
    }

    var showExitWarningDialog by remember { mutableStateOf(false) }

    fun handleExitRequest() {
        val isWarningActive = (currentPage == ToolsPage.OTHELLO && isOthelloPlaying) ||
                (currentPage == ToolsPage.GOMOKU && isGomokuPlaying) ||
                (currentPage == ToolsPage.AIR_HOCKEY && isAirHockeyPlaying) ||
                (currentPage == ToolsPage.MINE_SWEEPER && isMineSweeperPlaying) ||
                (currentPage == ToolsPage.FLAPPY_SKY && isFlappySkyPlaying)

        if (isWarningActive) {
            showExitWarningDialog = true
        } else {
            currentPage = ToolsPage.LIST
        }
    }

    LaunchedEffect(tabClickTrigger) {
        if (tabClickTrigger > 0 && currentPage != ToolsPage.LIST) {
            handleExitRequest()
        }
    }

    BackHandler(enabled = currentPage != ToolsPage.LIST) {
        handleExitRequest()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = when (currentPage) {
                                ToolsPage.LIST -> "ツール"
                                ToolsPage.ATTENDANCE -> "電子出席簿"
                                ToolsPage.LIBRARY_SEARCH -> "図書館蔵書検索"
                                ToolsPage.POMODORO -> "ポモドーロタイマー"
                                ToolsPage.STOPWATCH -> "ストップウォッチ"
                                ToolsPage.TODO -> "ToDoリスト"
                                ToolsPage.UNIT_CONVERTER -> "単位換算"
                                ToolsPage.KARNAUGH_MAP -> "カルノー図"
                                ToolsPage.SILENT_CAMERA -> "無音カメラ"
                                ToolsPage.OTHELLO -> "オセロ"
                                ToolsPage.GOMOKU -> "五目並べ"
                                ToolsPage.NUMBER_MERGE -> "Number Merge"
                                ToolsPage.AIR_HOCKEY -> "エアホッケー"
                                ToolsPage.MINE_SWEEPER -> "マインスイーパ"
                                ToolsPage.FLAPPY_SKY -> "Flappy Sky"
                                ToolsPage.REACTION_TEST -> "反射神経テスト"
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        if (currentPage == ToolsPage.AIR_HOCKEY) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.error,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.padding(horizontal = 2.dp)
                            ) {
                                Text(
                                    text = "BETA",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    if (currentPage != ToolsPage.LIST) {
                        IconButton(onClick = { handleExitRequest() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                        }
                    }
                },
                colors = appTopBarColors()
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
        ) {
            AnimatedContent(
                targetState = currentPage,
                transitionSpec = {
                    if (enableLightweightMode) {
                        (EnterTransition.None togetherWith ExitTransition.None).apply {
                            targetContentZIndex = if (targetState.ordinal > initialState.ordinal) 1f else -1f
                        }
                    } else if (targetState.ordinal > initialState.ordinal) {
                        // Forward navigation
                        val enter = if (enableHyperAnimation) {
                            slideInHorizontally(initialOffsetX = { it }, animationSpec = spring(dampingRatio = springDampingEnter, stiffness = springStiffnessEnter)) + fadeIn(tween(300))
                        } else {
                            slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(350, easing = FastOutSlowInEasing)) + fadeIn(tween(350))
                        }
                        val exit = if (enableHyperAnimation) {
                            slideOutHorizontally(targetOffsetX = { -it / 4 }, animationSpec = spring(dampingRatio = springDampingExit, stiffness = springStiffnessExit)) + fadeOut(tween(200))
                        } else {
                            slideOutHorizontally(targetOffsetX = { -it / 4 }, animationSpec = tween(350, easing = FastOutSlowInEasing)) + fadeOut(tween(150))
                        }
                        (enter togetherWith exit).apply {
                            targetContentZIndex = 1f
                        }
                    } else {
                        // Backward navigation
                        val enter = if (enableHyperAnimation) {
                            slideInHorizontally(initialOffsetX = { -it / 4 }, animationSpec = spring(dampingRatio = springDampingExit, stiffness = springStiffnessExit)) + fadeIn(tween(300))
                        } else {
                            slideInHorizontally(initialOffsetX = { -it / 4 }, animationSpec = tween(350, easing = FastOutSlowInEasing)) + fadeIn(tween(350))
                        }
                        val exit = if (enableHyperAnimation) {
                            slideOutHorizontally(targetOffsetX = { it }, animationSpec = spring(dampingRatio = springDampingEnter, stiffness = springStiffnessEnter)) + fadeOut(tween(200))
                        } else {
                            slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(350, easing = FastOutSlowInEasing)) + fadeOut(tween(150))
                        }
                        (enter togetherWith exit).apply {
                            targetContentZIndex = -1f
                        }
                    }
                },
                label = "tools_page_transition"
            ) { page ->
                when (page) {
                    ToolsPage.LIST -> ToolsListDashboard(
                        useWallpaper = useWallpaper,
                        isWideScreen = isWideScreen,
                        onSelectPage = { page ->
                            if (page == ToolsPage.ATTENDANCE || page == ToolsPage.LIBRARY_SEARCH) {
                                val url = if (page == ToolsPage.ATTENDANCE) {
                                    "https://std.kisarazu.ac.jp/"
                                } else {
                                    "https://libopac-c.kosen-k.go.jp/webopac15/cattab.do"
                                }
                                val title = if (page == ToolsPage.ATTENDANCE) "電子出席簿" else "図書館蔵書検索"
                                if (useBrowser) {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                    context.startActivity(intent)
                                } else {
                                    val encodedUrl = java.net.URLEncoder.encode(url, "UTF-8")
                                    val encodedTitle = java.net.URLEncoder.encode(title, "UTF-8")
                                    navController.navigate("viewer?url=$encodedUrl&title=$encodedTitle&origin=tools")
                                }
                            } else {
                                currentPage = page
                            }
                        }
                    )
                    ToolsPage.ATTENDANCE -> {
                        Box(modifier = Modifier.fillMaxSize())
                    }
                    ToolsPage.LIBRARY_SEARCH -> {
                        Box(modifier = Modifier.fillMaxSize())
                    }
                    ToolsPage.POMODORO -> PomodoroTab(
                        useWallpaper = useWallpaper,
                        isWideScreen = isWideScreen,
                        viewModel = viewModel
                    )
                    ToolsPage.STOPWATCH -> StopwatchTab(
                        useWallpaper = useWallpaper,
                        isWideScreen = isWideScreen,
                        viewModel = viewModel
                    )
                    ToolsPage.TODO -> TodoTab(useWallpaper = useWallpaper, isWideScreen = isWideScreen)
                    ToolsPage.UNIT_CONVERTER -> UnitConverterScreen(isWideScreen = isWideScreen)
                    ToolsPage.KARNAUGH_MAP -> KarnaughMapScreen(isWideScreen = isWideScreen)
                    ToolsPage.SILENT_CAMERA -> SilentCameraScreen(isWideScreen = isWideScreen)
                    ToolsPage.OTHELLO -> OthelloScreen(
                        useWallpaper = useWallpaper,
                        isPlaying = isOthelloPlaying,
                        onPlayingStateChanged = onOthelloPlayingChanged,
                        isWideScreen = isWideScreen
                    )
                    ToolsPage.GOMOKU -> GomokuScreen(
                        useWallpaper = useWallpaper,
                        isPlaying = isGomokuPlaying,
                        onPlayingStateChanged = onGomokuPlayingChanged,
                        isWideScreen = isWideScreen
                    )
                    ToolsPage.NUMBER_MERGE -> NumberMergeScreen(
                        useWallpaper = useWallpaper,
                        isWideScreen = isWideScreen
                    )
                    ToolsPage.AIR_HOCKEY -> AirHockeyScreen(
                        useWallpaper = useWallpaper,
                        isPlaying = isAirHockeyPlaying,
                        onPlayingStateChanged = onAirHockeyPlayingChanged,
                        isWideScreen = isWideScreen
                    )
                    ToolsPage.MINE_SWEEPER -> MineSweeperScreen(
                        useWallpaper = useWallpaper,
                        isPlaying = isMineSweeperPlaying,
                        onPlayingStateChanged = onMineSweeperPlayingChanged,
                        isWideScreen = isWideScreen
                    )
                    ToolsPage.FLAPPY_SKY -> FlappySkyScreen(
                        useWallpaper = useWallpaper,
                        isPlaying = isFlappySkyPlaying,
                        onPlayingStateChanged = onFlappySkyPlayingChanged,
                        isWideScreen = isWideScreen
                    )
                    ToolsPage.REACTION_TEST -> ReactionTestScreen(isWideScreen = isWideScreen)
                }
            }

            if (showExitWarningDialog) {
                AlertDialog(
                    onDismissRequest = { showExitWarningDialog = false },
                    title = { Text("確認") },
                    text = { Text("現在ゲーム中ですが、終了してもよろしいですか？") },
                    confirmButton = {
                        Button(
                            onClick = {
                                onOthelloPlayingChanged(false)
                                onGomokuPlayingChanged(false)
                                onAirHockeyPlayingChanged(false)
                                onMineSweeperPlayingChanged(false)
                                onFlappySkyPlayingChanged(false)
                                currentPage = ToolsPage.LIST
                                showExitWarningDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("終了する")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showExitWarningDialog = false }) {
                            Text("キャンセル")
                        }
                    }
                )
            }
        }
    }
}

private enum class ToolGenre {
    KOSEN, STUDY, RELAXATION
}

private data class ToolItem(
    val page: ToolsPage,
    val title: String,
    val description: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val iconContainerColor: Color,
    val iconColor: Color,
    val genre: ToolGenre,
    val isBeta: Boolean = false
)

@Composable
private fun getToolItems(): List<ToolItem> {
    return listOf(
        ToolItem(
            page = ToolsPage.SILENT_CAMERA,
            title = "無音カメラ",
            description = "シャッター音をアプリから再生せず、板書や資料をすばやく撮影します。",
            icon = Icons.Default.PhotoCamera,
            iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            iconColor = MaterialTheme.colorScheme.secondary,
            genre = ToolGenre.KOSEN
        ),
        ToolItem(
            page = ToolsPage.ATTENDANCE,
            title = "電子出席簿",
            description = "木更津高専の電子出席簿システムに素早くアクセスし、出席状況を登録・確認します。",
            icon = Icons.Default.HowToReg,
            iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            iconColor = MaterialTheme.colorScheme.secondary,
            genre = ToolGenre.KOSEN
        ),
        ToolItem(
            page = ToolsPage.LIBRARY_SEARCH,
            title = "図書館蔵書検索",
            description = "木更津高専の図書館蔵書検索システム（OPAC）にアクセスし、本を検索・予約します。",
            icon = Icons.AutoMirrored.Filled.MenuBook,
            iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
            iconColor = MaterialTheme.colorScheme.primary,
            genre = ToolGenre.KOSEN
        ),
        ToolItem(
            page = ToolsPage.POMODORO,
            title = "ポモドーロタイマー",
            description = "集中時間と休憩時間を交互に繰り返し、学習効率を高めます。",
            icon = Icons.Default.Timer,
            iconContainerColor = MaterialTheme.colorScheme.errorContainer,
            iconColor = MaterialTheme.colorScheme.error,
            genre = ToolGenre.STUDY
        ),
        ToolItem(
            page = ToolsPage.STOPWATCH,
            title = "ストップウォッチ",
            description = "ラップ計測が可能なストップウォッチで、勉強や演習の時間を計ります。",
            icon = Icons.Default.HourglassEmpty,
            iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            iconColor = MaterialTheme.colorScheme.secondary,
            genre = ToolGenre.STUDY
        ),
        ToolItem(
            page = ToolsPage.TODO,
            title = "ToDoリスト",
            description = "今日の学習目標やタスクをシンプルに登録し、進捗を管理します。",
            icon = Icons.AutoMirrored.Filled.PlaylistAddCheck,
            iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
            iconColor = MaterialTheme.colorScheme.primary,
            genre = ToolGenre.STUDY
        ),
        ToolItem(
            page = ToolsPage.UNIT_CONVERTER,
            title = "単位換算",
            description = "長さ・重さ・温度・速度をすばやく相互変換します。",
            icon = Icons.Default.SwapHoriz,
            iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
            iconColor = MaterialTheme.colorScheme.tertiary,
            genre = ToolGenre.STUDY
        ),
        ToolItem(
            page = ToolsPage.KARNAUGH_MAP,
            title = "カルノー図",
            description = "2〜4変数のカルノー図を編集し、最小SOP・POS式とグループを確認します。",
            icon = Icons.Default.GridView,
            iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
            iconColor = MaterialTheme.colorScheme.primary,
            genre = ToolGenre.STUDY
        ),
        ToolItem(
            page = ToolsPage.AIR_HOCKEY,
            title = "エアホッケー",
            description = "CPU対戦または2人プレイができる対戦アクションゲームです。",
            icon = Icons.Default.Gamepad,
            iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
            iconColor = MaterialTheme.colorScheme.tertiary,
            genre = ToolGenre.RELAXATION,
            isBeta = true
        ),
        ToolItem(
            page = ToolsPage.OTHELLO,
            title = "オセロ",
            description = "CPU対戦または人間同士の対局ができる定番ボードゲームです（難易度3段階）。",
            icon = Icons.Default.BlurOn,
            iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
            iconColor = MaterialTheme.colorScheme.primary,
            genre = ToolGenre.RELAXATION
        ),
        ToolItem(
            page = ToolsPage.GOMOKU,
            title = "五目並べ",
            description = "11×11 of 盤面で5つの石を先に並べるゲームです（難易度3段階）。",
            icon = Icons.Default.GridOn,
            iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
            iconColor = MaterialTheme.colorScheme.tertiary,
            genre = ToolGenre.RELAXATION
        ),
        ToolItem(
            page = ToolsPage.REACTION_TEST,
            title = "反射神経テスト",
            description = "色が変わった瞬間にタップして反応速度を測定します。",
            icon = Icons.Default.Bolt,
            iconContainerColor = MaterialTheme.colorScheme.errorContainer,
            iconColor = MaterialTheme.colorScheme.error,
            genre = ToolGenre.RELAXATION
        ),
        ToolItem(
            page = ToolsPage.NUMBER_MERGE,
            title = "Number Merge",
            description = "同じ数字のタイルをスライドして合体させ、大きな数字を目指すパズルゲームです。",
            icon = Icons.Default.Casino,
            iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            iconColor = MaterialTheme.colorScheme.secondary,
            genre = ToolGenre.RELAXATION
        ),
        ToolItem(
            page = ToolsPage.MINE_SWEEPER,
            title = "マインスイーパ",
            description = "地雷を避けながら、すべての安全なマスを開ける定番パズルゲームです。",
            icon = Icons.Default.Extension,
            iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
            iconColor = MaterialTheme.colorScheme.primary,
            genre = ToolGenre.RELAXATION
        ),
        ToolItem(
            page = ToolsPage.FLAPPY_SKY,
            title = "Flappy Sky",
            description = "障害物をよけながら、大空を飛び続けるタイミングアクションゲームです。",
            icon = Icons.Default.Cloud,
            iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            iconColor = MaterialTheme.colorScheme.secondary,
            genre = ToolGenre.RELAXATION
        )
    )
}

@Composable
private fun ToolsSectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = tint
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ToolsListDashboard(
    useWallpaper: Boolean,
    isWideScreen: Boolean,
    onSelectPage: (ToolsPage) -> Unit
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedGenre by rememberSaveable { mutableStateOf<ToolGenre?>(null) }

    val allTools = getToolItems()
    val filteredTools = remember(searchQuery, selectedGenre, allTools) {
        allTools.filter { item ->
            val matchesSearch = searchQuery.isBlank() ||
                    item.title.contains(searchQuery, ignoreCase = true) ||
                    item.description.contains(searchQuery, ignoreCase = true)
            val matchesGenre = selectedGenre == null || item.genre == selectedGenre
            matchesSearch && matchesGenre
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = 16.dp,
                top = 16.dp,
                end = 16.dp,
                bottom = 16.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Search Bar (syllabus style)
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("ツール名や説明で検索") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = if (searchQuery.isNotEmpty()) {
                {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "クリア")
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
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedGenre == null,
                onClick = { selectedGenre = null },
                label = { Text("すべて") },
                leadingIcon = if (selectedGenre == null) {
                    { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                } else null
            )

            FilterChip(
                selected = selectedGenre == ToolGenre.KOSEN,
                onClick = { selectedGenre = ToolGenre.KOSEN },
                label = { Text("高専") },
                leadingIcon = if (selectedGenre == ToolGenre.KOSEN) {
                    { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                } else null
            )

            FilterChip(
                selected = selectedGenre == ToolGenre.STUDY,
                onClick = { selectedGenre = ToolGenre.STUDY },
                label = { Text("勉強効率化") },
                leadingIcon = if (selectedGenre == ToolGenre.STUDY) {
                    { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                } else null
            )

            FilterChip(
                selected = selectedGenre == ToolGenre.RELAXATION,
                onClick = { selectedGenre = ToolGenre.RELAXATION },
                label = { Text("息抜き・脳トレ") },
                leadingIcon = if (selectedGenre == ToolGenre.RELAXATION) {
                    { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                } else null
            )
        }

        if (searchQuery.isBlank() && selectedGenre == null) {
            val quickPages = listOf(
                ToolsPage.SILENT_CAMERA,
                ToolsPage.TODO,
                ToolsPage.STOPWATCH,
                ToolsPage.UNIT_CONVERTER
            )
            val quickTools = quickPages.mapNotNull { page -> allTools.firstOrNull { it.page == page } }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "クイックアクセス",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "よく使う機能",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    quickTools.forEach { tool ->
                        ToolQuickAccessCard(
                            tool = tool,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelectPage(tool.page) }
                        )
                    }
                }
            }
        }

        // List Content
        if (filteredTools.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "該当するツールが見つかりませんでした",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            if (isWideScreen) {
                // Wide screen layout: 2 columns
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    if (selectedGenre == null) {
                        // "すべて" layout: attendance/study on left, relaxation on right
                        val kosenTools = filteredTools.filter { it.genre == ToolGenre.KOSEN }
                        val studyTools = filteredTools.filter { it.genre == ToolGenre.STUDY }
                        val relaxationTools = filteredTools.filter { it.genre == ToolGenre.RELAXATION }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (kosenTools.isNotEmpty()) {
                                ToolsSectionHeader(
                                    title = "高専",
                                    icon = Icons.Default.HowToReg,
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                kosenTools.forEach { tool ->
                                    ToolDashboardCard(
                                        title = tool.title,
                                        description = tool.description,
                                        icon = tool.icon,
                                        iconContainerColor = tool.iconContainerColor,
                                        iconColor = tool.iconColor,
                                        useWallpaper = useWallpaper,
                                        isBeta = tool.isBeta,
                                        onClick = { onSelectPage(tool.page) }
                                    )
                                }
                            }

                            if (studyTools.isNotEmpty()) {
                                ToolsSectionHeader(
                                    title = "勉強効率化",
                                    icon = Icons.Default.School,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                studyTools.forEach { tool ->
                                    ToolDashboardCard(
                                        title = tool.title,
                                        description = tool.description,
                                        icon = tool.icon,
                                        iconContainerColor = tool.iconContainerColor,
                                        iconColor = tool.iconColor,
                                        useWallpaper = useWallpaper,
                                        isBeta = tool.isBeta,
                                        onClick = { onSelectPage(tool.page) }
                                    )
                                }
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (relaxationTools.isNotEmpty()) {
                                ToolsSectionHeader(
                                    title = "息抜き・脳トレ",
                                    icon = Icons.Default.Extension,
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                                relaxationTools.forEach { tool ->
                                    ToolDashboardCard(
                                        title = tool.title,
                                        description = tool.description,
                                        icon = tool.icon,
                                        iconContainerColor = tool.iconContainerColor,
                                        iconColor = tool.iconColor,
                                        useWallpaper = useWallpaper,
                                        isBeta = tool.isBeta,
                                        onClick = { onSelectPage(tool.page) }
                                    )
                                }
                            }
                        }
                    } else {
                        // Single category filtered wide layout: balanced 2 columns
                        val leftTools = filteredTools.filterIndexed { index, _ -> index % 2 == 0 }
                        val rightTools = filteredTools.filterIndexed { index, _ -> index % 2 != 0 }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            leftTools.forEach { tool ->
                                ToolDashboardCard(
                                    title = tool.title,
                                    description = tool.description,
                                    icon = tool.icon,
                                    iconContainerColor = tool.iconContainerColor,
                                    iconColor = tool.iconColor,
                                    useWallpaper = useWallpaper,
                                    isBeta = tool.isBeta,
                                    onClick = { onSelectPage(tool.page) }
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rightTools.forEach { tool ->
                                ToolDashboardCard(
                                    title = tool.title,
                                    description = tool.description,
                                    icon = tool.icon,
                                    iconContainerColor = tool.iconContainerColor,
                                    iconColor = tool.iconColor,
                                    useWallpaper = useWallpaper,
                                    isBeta = tool.isBeta,
                                    onClick = { onSelectPage(tool.page) }
                                )
                            }
                        }
                    }
                }
            } else {
                // Smartphone layout: 1 column
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    if (selectedGenre == null) {
                        // "すべて" layout: kosen category, then study category, then divider, then relaxation category
                        val kosenTools = filteredTools.filter { it.genre == ToolGenre.KOSEN }
                        val studyTools = filteredTools.filter { it.genre == ToolGenre.STUDY }
                        val relaxationTools = filteredTools.filter { it.genre == ToolGenre.RELAXATION }

                        if (kosenTools.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                ToolsSectionHeader(
                                    title = "高専",
                                    icon = Icons.Default.HowToReg,
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                kosenTools.forEach { tool ->
                                    ToolDashboardCard(
                                        title = tool.title,
                                        description = tool.description,
                                        icon = tool.icon,
                                        iconContainerColor = tool.iconContainerColor,
                                        iconColor = tool.iconColor,
                                        useWallpaper = useWallpaper,
                                        isBeta = tool.isBeta,
                                        onClick = { onSelectPage(tool.page) }
                                    )
                                }
                            }
                        }

                        if (kosenTools.isNotEmpty() && (studyTools.isNotEmpty() || relaxationTools.isNotEmpty())) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }

                        if (studyTools.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                ToolsSectionHeader(
                                    title = "勉強効率化",
                                    icon = Icons.Default.School,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                studyTools.forEach { tool ->
                                    ToolDashboardCard(
                                        title = tool.title,
                                        description = tool.description,
                                        icon = tool.icon,
                                        iconContainerColor = tool.iconContainerColor,
                                        iconColor = tool.iconColor,
                                        useWallpaper = useWallpaper,
                                        isBeta = tool.isBeta,
                                        onClick = { onSelectPage(tool.page) }
                                    )
                                }
                            }
                        }

                        if (studyTools.isNotEmpty() && relaxationTools.isNotEmpty()) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }

                        if (relaxationTools.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                ToolsSectionHeader(
                                    title = "息抜き・脳トレ",
                                    icon = Icons.Default.Extension,
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                                relaxationTools.forEach { tool ->
                                    ToolDashboardCard(
                                        title = tool.title,
                                        description = tool.description,
                                        icon = tool.icon,
                                        iconContainerColor = tool.iconContainerColor,
                                        iconColor = tool.iconColor,
                                        useWallpaper = useWallpaper,
                                        isBeta = tool.isBeta,
                                        onClick = { onSelectPage(tool.page) }
                                    )
                                }
                            }
                        }
                    } else {
                        // Single category filtered layout
                        val isStudy = selectedGenre == ToolGenre.STUDY
                        val isKosen = selectedGenre == ToolGenre.KOSEN
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            ToolsSectionHeader(
                                title = when {
                                    isKosen -> "高専"
                                    isStudy -> "勉強効率化"
                                    else -> "息抜き・脳トレ"
                                },
                                icon = when {
                                    isKosen -> Icons.Default.HowToReg
                                    isStudy -> Icons.Default.School
                                    else -> Icons.Default.Extension
                                },
                                tint = when {
                                    isKosen -> MaterialTheme.colorScheme.secondary
                                    isStudy -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.tertiary
                                }
                            )
                            filteredTools.forEach { tool ->
                                ToolDashboardCard(
                                    title = tool.title,
                                    description = tool.description,
                                    icon = tool.icon,
                                    iconContainerColor = tool.iconContainerColor,
                                    iconColor = tool.iconColor,
                                    useWallpaper = useWallpaper,
                                    isBeta = tool.isBeta,
                                    onClick = { onSelectPage(tool.page) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolQuickAccessCard(
    tool: ToolItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(104.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = tool.iconContainerColor.copy(alpha = 0.72f)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start
        ) {
            Icon(
                imageVector = tool.icon,
                contentDescription = null,
                tint = tool.iconColor,
                modifier = Modifier.size(25.dp)
            )
            Text(
                text = tool.title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ToolDashboardCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconContainerColor: Color,
    iconColor: Color,
    useWallpaper: Boolean,
    isBeta: Boolean = false,
    onClick: () -> Unit
) {
    val cardColor = if (useWallpaper) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    val borderStroke = if (useWallpaper) {
        BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }

    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = cardColor
        ),
        border = borderStroke,
        elevation = CardDefaults.outlinedCardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Icon Container
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(iconContainerColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Text content
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (isBeta) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.error,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.padding(horizontal = 2.dp)
                        ) {
                            Text(
                                text = "BETA",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Trailing Chevron
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// ──────────────────────────────────────────
// 1. POMODORO TIMER TAB
// ──────────────────────────────────────────

@Composable
fun PomodoroTab(
    useWallpaper: Boolean,
    isWideScreen: Boolean,
    viewModel: com.webclass.plus.ui.viewmodels.ToolsViewModel
) {
    val context = LocalContext.current
    var hasNotificationPermission by remember { mutableStateOf(true) }
    var showPermissionWarningDialog by remember { mutableStateOf(false) }

    fun checkPermission() {
        hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
        if (isGranted) {
            viewModel.startPomodoro(context)
        } else {
            showPermissionWarningDialog = true
        }
    }

    // Synchronize timer state on launch/resume
    LaunchedEffect(Unit) {
        viewModel.syncTimerState(context)
        checkPermission()
    }

    // Synchronize keep screen on
    val view = LocalView.current
    DisposableEffect(viewModel.keepScreenOn, viewModel.timerState) {
        val shouldKeep = viewModel.keepScreenOn && viewModel.timerState == PomodoroState.RUNNING
        view.keepScreenOn = shouldKeep
        onDispose {
            view.keepScreenOn = false
        }
    }

    val progress = if (viewModel.totalDurationSeconds > 0) {
        viewModel.timeLeftSeconds.toFloat() / viewModel.totalDurationSeconds.toFloat()
    } else 1f

    val minutes = viewModel.timeLeftSeconds / 60
    val seconds = viewModel.timeLeftSeconds % 60
    val timeString = String.format("%02d:%02d", minutes, seconds)

    val cardColor = if (useWallpaper) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    val surfaceBorder = if (useWallpaper) {
        BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }

    // Mode-specific color gradients for visual wow
    val pomodoroColors = if (viewModel.currentMode == PomodoroMode.WORK) {
        listOf(Color(0xFFE57373), Color(0xFFFF8A80)) // Warm red-pink
    } else {
        listOf(Color(0xFF4DB6AC), Color(0xFF80CBC4)) // Cool teal-mint
    }

    val modeTitle = if (viewModel.currentMode == PomodoroMode.WORK) "集中時間 ✍️" else "休憩時間 ☕️"

    if (isWideScreen) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1.2f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (!hasNotificationPermission) {
                    PermissionWarningCard(context)
                }
                TimerCard(
                    useWallpaper = useWallpaper,
                    cardColor = cardColor,
                    surfaceBorder = surfaceBorder,
                    pomodoroColors = pomodoroColors,
                    modeTitle = modeTitle,
                    progress = progress,
                    timeString = timeString,
                    viewModel = viewModel,
                    context = context,
                    permissionLauncher = permissionLauncher
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SettingsCard(
                    useWallpaper = useWallpaper,
                    cardColor = cardColor,
                    surfaceBorder = surfaceBorder,
                    viewModel = viewModel
                )
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!hasNotificationPermission) {
                PermissionWarningCard(context)
            }
            // Mode badge & Timer Card
            TimerCard(
                useWallpaper = useWallpaper,
                cardColor = cardColor,
                surfaceBorder = surfaceBorder,
                pomodoroColors = pomodoroColors,
                modeTitle = modeTitle,
                progress = progress,
                timeString = timeString,
                viewModel = viewModel,
                context = context,
                permissionLauncher = permissionLauncher
            )

            // Settings Adjustments Card
            SettingsCard(
                useWallpaper = useWallpaper,
                cardColor = cardColor,
                surfaceBorder = surfaceBorder,
                viewModel = viewModel
            )
        }
    }

    if (showPermissionWarningDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionWarningDialog = false },
            title = { Text("通知権限が必要です") },
            text = { Text("タイマーの通知表示には通知権限が必要です。端末の設定アプリから通知を許可してください。") },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionWarningDialog = false
                        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = android.net.Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }
                ) {
                    Text("設定を開く")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionWarningDialog = false }) {
                    Text("閉じる")
                }
            }
        )
    }
}

// ──────────────────────────────────────────
// 2. STOPWATCH TAB
// ──────────────────────────────────────────

@Composable
fun StopwatchTab(
    useWallpaper: Boolean,
    isWideScreen: Boolean,
    viewModel: com.webclass.plus.ui.viewmodels.ToolsViewModel
) {
    val context = LocalContext.current
    var hasNotificationPermission by remember { mutableStateOf(true) }
    var showPermissionWarningDialog by remember { mutableStateOf(false) }

    fun checkPermission() {
        hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
        if (isGranted) {
            viewModel.startStopwatch(context)
        } else {
            showPermissionWarningDialog = true
        }
    }

    // Synchronize stopwatch state on launch/resume
    LaunchedEffect(Unit) {
        viewModel.syncStopwatchState(context)
        checkPermission()
    }

    // Format Milliseconds to MM:SS.CC
    fun formatTime(timeMs: Long): String {
        val min = (timeMs / 60000)
        val sec = (timeMs % 60000) / 1000
        val hundredths = (timeMs % 1000) / 10
        return String.format("%02d:%02d.%02d", min, sec, hundredths)
    }

    val cardColor = if (useWallpaper) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    val surfaceBorder = if (useWallpaper) {
        BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }

    if (isWideScreen) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (!hasNotificationPermission) {
                    PermissionWarningCardStopwatch(context)
                }
                StopwatchClockCard(
                    viewModel = viewModel,
                    useWallpaper = useWallpaper,
                    cardColor = cardColor,
                    surfaceBorder = surfaceBorder,
                    formatTime = { formatTime(it) },
                    permissionLauncher = permissionLauncher,
                    context = context
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "ラップ履歴",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                )
                StopwatchLapsCard(
                    viewModel = viewModel,
                    cardColor = cardColor,
                    surfaceBorder = surfaceBorder,
                    formatTime = { formatTime(it) }
                )
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!hasNotificationPermission) {
                PermissionWarningCardStopwatch(context)
            }
            // Digital Clock View
            StopwatchClockCard(
                viewModel = viewModel,
                useWallpaper = useWallpaper,
                cardColor = cardColor,
                surfaceBorder = surfaceBorder,
                formatTime = { formatTime(it) },
                permissionLauncher = permissionLauncher,
                context = context
            )

            // Laps list
            Text(
                text = "ラップ履歴",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
            )

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                StopwatchLapsCard(
                    viewModel = viewModel,
                    cardColor = cardColor,
                    surfaceBorder = surfaceBorder,
                    formatTime = { formatTime(it) }
                )
            }
        }
    }

    if (showPermissionWarningDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionWarningDialog = false },
            title = { Text("通知権限が必要です") },
            text = { Text("ストップウォッチの通知表示には通知権限が必要です。端末の設定アプリから通知を許可してください。") },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionWarningDialog = false
                        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = android.net.Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }
                ) {
                    Text("設定を開く")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionWarningDialog = false }) {
                    Text("閉じる")
                }
            }
        )
    }
}

// ──────────────────────────────────────────
// 3. TODO LIST TAB
// ──────────────────────────────────────────

data class TodoItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isCompleted: Boolean = false
)

@Composable
fun TodoTab(useWallpaper: Boolean, isWideScreen: Boolean) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("quick_tools_prefs", Context.MODE_PRIVATE) }
    val gson = remember { Gson() }
    
    val todoItems = remember { mutableStateListOf<TodoItem>() }
    var newTodoText by remember { mutableStateOf("") }

    // Load items on startup
    LaunchedEffect(Unit) {
        val savedJson = sharedPrefs.getString("quick_todo_items", null)
        if (!savedJson.isNullOrBlank()) {
            try {
                val listType = object : TypeToken<List<TodoItem>>() {}.type
                val loadedList: List<TodoItem> = gson.fromJson(savedJson, listType)
                todoItems.addAll(loadedList)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Save items helper
    fun saveTodos() {
        val json = gson.toJson(todoItems.toList())
        sharedPrefs.edit().putString("quick_todo_items", json).apply()
    }

    val cardColor = if (useWallpaper) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    val surfaceBorder = if (useWallpaper) {
        BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }

    val completedCount = todoItems.count { it.isCompleted }
    val totalCount = todoItems.size
    val progress = if (totalCount > 0) completedCount.toFloat() / totalCount else 0f
    val progressPercentage = (progress * 100).toInt()

    if (isWideScreen) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                TodoProgressCard(completedCount, totalCount, progress, progressPercentage, cardColor, surfaceBorder)
                TodoInputCard(
                    newTodoText = newTodoText,
                    onValueChange = { newTodoText = it },
                    useWallpaper = useWallpaper,
                    cardColor = cardColor,
                    surfaceBorder = surfaceBorder,
                    onAdd = {
                        val trimmedText = newTodoText.trim()
                        if (trimmedText.isNotEmpty()) {
                            todoItems.add(TodoItem(text = trimmedText))
                            saveTodos()
                            newTodoText = ""
                        }
                    }
                )
            }

            Column(
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxHeight()
            ) {
                TodoListCard(
                    todoItems = todoItems,
                    onToggle = { index, isChecked ->
                        todoItems[index] = todoItems[index].copy(isCompleted = isChecked)
                        saveTodos()
                    },
                    onDelete = { index ->
                        todoItems.removeAt(index)
                        saveTodos()
                    },
                    cardColor = cardColor,
                    surfaceBorder = surfaceBorder
                )
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TodoInputCard(
                newTodoText = newTodoText,
                onValueChange = { newTodoText = it },
                useWallpaper = useWallpaper,
                cardColor = cardColor,
                surfaceBorder = surfaceBorder,
                onAdd = {
                    val trimmedText = newTodoText.trim()
                    if (trimmedText.isNotEmpty()) {
                        todoItems.add(TodoItem(text = trimmedText))
                        saveTodos()
                        newTodoText = ""
                    }
                }
            )

            TodoListCard(
                todoItems = todoItems,
                onToggle = { index, isChecked ->
                    todoItems[index] = todoItems[index].copy(isCompleted = isChecked)
                    saveTodos()
                },
                onDelete = { index ->
                    todoItems.removeAt(index)
                    saveTodos()
                },
                cardColor = cardColor,
                surfaceBorder = surfaceBorder
            )
        }
    }
}

// ──────────────────────────────────────────
// HELPER COMPONENTS FOR TABLET LAYOUTS
// ──────────────────────────────────────────

@Composable
private fun PermissionWarningCard(context: Context) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "警告",
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "通知権限が無効です",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontSize = 14.sp
                )
                Text(
                    text = "タイマーを通知バーに表示するには、設定から通知を許可してください。",
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                    fontSize = 12.sp
                )
            }
            TextButton(
                onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }
            ) {
                Text("許可する", color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TimerCard(
    useWallpaper: Boolean,
    cardColor: Color,
    surfaceBorder: BorderStroke,
    pomodoroColors: List<Color>,
    modeTitle: String,
    progress: Float,
    timeString: String,
    viewModel: com.webclass.plus.ui.viewmodels.ToolsViewModel,
    context: Context,
    permissionLauncher: androidx.activity.result.ActivityResultLauncher<String>
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = cardColor
        ),
        border = surfaceBorder,
        elevation = CardDefaults.outlinedCardElevation(defaultElevation = if (useWallpaper) 0.dp else 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Brush.horizontalGradient(pomodoroColors))
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                Text(
                    text = modeTitle,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(240.dp)
            ) {
                val animatedProgress by animateFloatAsState(
                    targetValue = progress,
                    animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
                    label = "TimerProgress"
                )

                Canvas(modifier = Modifier.size(220.dp)) {
                    drawCircle(
                        color = pomodoroColors[0].copy(alpha = 0.15f),
                        style = Stroke(width = 12.dp.toPx())
                    )
                    drawArc(
                        brush = Brush.sweepGradient(pomodoroColors),
                        startAngle = -90f,
                        sweepAngle = 360f * animatedProgress,
                        useCenter = false,
                        style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = timeString,
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (viewModel.timerState == PomodoroState.RUNNING) "計測中" else "待機中",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 2.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledIconButton(
                    onClick = {
                        viewModel.resetPomodoro(context)
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "リセット",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val permission = android.Manifest.permission.POST_NOTIFICATIONS
                            val isGranted = androidx.core.content.ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            if (!isGranted) {
                                permissionLauncher.launch(permission)
                            } else {
                                if (viewModel.timerState == PomodoroState.RUNNING) {
                                    viewModel.pausePomodoro(context)
                                } else {
                                    viewModel.startPomodoro(context)
                                }
                            }
                        } else {
                            if (viewModel.timerState == PomodoroState.RUNNING) {
                                viewModel.pausePomodoro(context)
                            } else {
                                viewModel.startPomodoro(context)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = pomodoroColors[0]
                    ),
                    modifier = Modifier
                        .height(56.dp)
                        .widthIn(min = 140.dp),
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = if (viewModel.timerState == PomodoroState.RUNNING) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "スタート/一時停止",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (viewModel.timerState == PomodoroState.RUNNING) "一時停止" else "スタート",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                FilledIconButton(
                    onClick = {
                        viewModel.skipPomodoro(context)
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "スキップ",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    useWallpaper: Boolean,
    cardColor: Color,
    surfaceBorder: BorderStroke,
    viewModel: com.webclass.plus.ui.viewmodels.ToolsViewModel
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = cardColor
        ),
        border = surfaceBorder,
        elevation = CardDefaults.outlinedCardElevation(defaultElevation = if (useWallpaper) 0.dp else 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "タイマー設定",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.primary
            )

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("学習時間", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${viewModel.workDurationMinutes}分",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = viewModel.workDurationMinutes.toFloat(),
                    onValueChange = { viewModel.updateWorkDuration(it.toInt()) },
                    valueRange = 15f..60f,
                    steps = 8,
                    enabled = viewModel.timerState == PomodoroState.IDLE
                )
            }

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("休憩時間", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${viewModel.breakDurationMinutes}分",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = viewModel.breakDurationMinutes.toFloat(),
                    onValueChange = { viewModel.updateBreakDuration(it.toInt()) },
                    valueRange = 3f..20f,
                    steps = 16,
                    enabled = viewModel.timerState == PomodoroState.IDLE
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { viewModel.keepScreenOn = !viewModel.keepScreenOn }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LightMode,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Column {
                        Text("画面を常時点灯", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("計測中に画面がスリープするのを防ぎます", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Switch(
                    checked = viewModel.keepScreenOn,
                    onCheckedChange = { viewModel.keepScreenOn = it }
                )
            }
        }
    }
}

@Composable
private fun PermissionWarningCardStopwatch(context: Context) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "警告",
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "通知権限が無効です",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontSize = 14.sp
                )
                Text(
                    text = "ストップウォッチを通知バーに表示するには、設定から通知を許可してください。",
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                    fontSize = 12.sp
                )
            }
            TextButton(
                onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }
            ) {
                Text("許可する", color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun StopwatchClockCard(
    viewModel: com.webclass.plus.ui.viewmodels.ToolsViewModel,
    useWallpaper: Boolean,
    cardColor: Color,
    surfaceBorder: BorderStroke,
    formatTime: (Long) -> String,
    permissionLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    context: Context
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = cardColor
        ),
        border = surfaceBorder,
        elevation = CardDefaults.outlinedCardElevation(defaultElevation = if (useWallpaper) 0.dp else 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = formatTime(viewModel.elapsedTime),
                fontSize = 54.sp,
                fontWeight = FontWeight.Light,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        if (viewModel.isStopwatchRunning) {
                            viewModel.addLap(context)
                        } else {
                            viewModel.resetStopwatch(context)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = CircleShape,
                    enabled = viewModel.elapsedTime > 0L
                ) {
                    Text(if (viewModel.isStopwatchRunning) "ラップ" else "リセット")
                }

                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val permission = android.Manifest.permission.POST_NOTIFICATIONS
                            val isGranted = androidx.core.content.ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            if (!isGranted) {
                                permissionLauncher.launch(permission)
                            } else {
                                if (viewModel.isStopwatchRunning) {
                                    viewModel.pauseStopwatch(context)
                                } else {
                                    viewModel.startStopwatch(context)
                                }
                            }
                        } else {
                            if (viewModel.isStopwatchRunning) {
                                viewModel.pauseStopwatch(context)
                            } else {
                                viewModel.startStopwatch(context)
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (viewModel.isStopwatchRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(if (viewModel.isStopwatchRunning) "ストップ" else "スタート", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun StopwatchLapsCard(
    viewModel: com.webclass.plus.ui.viewmodels.ToolsViewModel,
    cardColor: Color,
    surfaceBorder: BorderStroke,
    formatTime: (Long) -> String
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = cardColor
        ),
        border = surfaceBorder
    ) {
        if (viewModel.laps.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.alpha(0.5f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "記録はありません",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(viewModel.laps) { index, lapTime ->
                    val lapNum = viewModel.laps.size - index
                    val diff = if (index == viewModel.laps.size - 1) {
                        lapTime
                    } else {
                        lapTime - viewModel.laps[index + 1]
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ラップ $lapNum",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "+${formatTime(diff)}",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                            )
                            Text(
                                text = formatTime(lapTime),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    if (index < viewModel.laps.size - 1) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    }
                }
            }
        }
    }
}

@Composable
private fun TodoProgressCard(
    completedCount: Int,
    totalCount: Int,
    progress: Float,
    progressPercentage: Int,
    cardColor: Color,
    surfaceBorder: BorderStroke
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = cardColor),
        border = surfaceBorder
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "本日の進捗",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.Start)
            )
            
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(140.dp)
            ) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(130.dp),
                    strokeWidth = 10.dp,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    strokeCap = StrokeCap.Round
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$progressPercentage%",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "$completedCount / $totalCount 完了",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun TodoInputCard(
    newTodoText: String,
    onValueChange: (String) -> Unit,
    useWallpaper: Boolean,
    cardColor: Color,
    surfaceBorder: BorderStroke,
    onAdd: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = cardColor
        ),
        border = surfaceBorder,
        elevation = CardDefaults.outlinedCardElevation(defaultElevation = if (useWallpaper) 0.dp else 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextField(
                value = newTodoText,
                onValueChange = onValueChange,
                placeholder = { Text("今日のタスクを入力...") },
                modifier = Modifier.weight(1f),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                singleLine = true
            )

            FilledIconButton(
                onClick = onAdd,
                modifier = Modifier.size(48.dp),
                enabled = newTodoText.trim().isNotEmpty()
            ) {
                Icon(Icons.Default.Add, contentDescription = "タスク追加")
            }
        }
    }
}

@Composable
private fun TodoListCard(
    todoItems: List<TodoItem>,
    onToggle: (Int, Boolean) -> Unit,
    onDelete: (Int) -> Unit,
    cardColor: Color,
    surfaceBorder: BorderStroke
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = cardColor
        ),
        border = surfaceBorder
    ) {
        if (todoItems.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.alpha(0.5f)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.PlaylistAddCheck,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "今日の目標を追加しましょう！",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(todoItems) { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (item.isCompleted) {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                } else {
                                    Color.Transparent
                                }
                            )
                            .padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = item.isCompleted,
                            onCheckedChange = { isChecked ->
                                onToggle(index, isChecked)
                            }
                        )
                        
                        Text(
                            text = item.text,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                textDecoration = if (item.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                            ),
                            color = if (item.isCompleted) {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        IconButton(
                            onClick = {
                                onDelete(index)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = "削除",
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    if (index < todoItems.size - 1) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    }
                }
            }
        }
    }
}
