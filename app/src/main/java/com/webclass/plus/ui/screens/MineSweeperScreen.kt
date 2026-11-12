package com.webclass.plus.ui.screens

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

enum class MineSweeperDifficulty(val rows: Int, val cols: Int, val mines: Int, val label: String) {
    EASY(9, 9, 10, "初級 (9x9)"),
    MEDIUM(12, 10, 20, "中級 (12x10)"),
    HARD(15, 12, 35, "上級 (15x12)")
}

enum class MineSweeperStatus { WAITING, PLAYING, WON, LOST }

data class Cell(
    val r: Int,
    val c: Int,
    val isMine: Boolean = false,
    val isRevealed: Boolean = false,
    val isFlagged: Boolean = false,
    val adjacentMines: Int = 0
)

// Helper to save Cell state across recompositions / config changes
val CellSaver = Saver<Cell, Map<String, Any>>(
    save = { cell ->
        mapOf(
            "r" to cell.r,
            "c" to cell.c,
            "isMine" to cell.isMine,
            "isRevealed" to cell.isRevealed,
            "isFlagged" to cell.isFlagged,
            "adjacentMines" to cell.adjacentMines
        )
    },
    restore = { map ->
        Cell(
            r = map["r"] as Int,
            c = map["c"] as Int,
            isMine = map["isMine"] as Boolean,
            isRevealed = map["isRevealed"] as Boolean,
            isFlagged = map["isFlagged"] as Boolean,
            adjacentMines = map["adjacentMines"] as Int
        )
    }
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MineSweeperScreen(
    useWallpaper: Boolean,
    isPlaying: Boolean,
    onPlayingStateChanged: (Boolean) -> Unit,
    isWideScreen: Boolean
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val sharedPrefs = remember { context.getSharedPreferences("minesweeper_prefs", Context.MODE_PRIVATE) }

    // Game Configurations
    var difficulty by rememberSaveable { mutableStateOf(MineSweeperDifficulty.EASY) }
    var isFlagMode by rememberSaveable { mutableStateOf(false) } // Toggle for mobile tap-to-flag mode

    // Game Dynamic States
    var board by rememberSaveable(difficulty) {
        val defaultBoard = List(difficulty.rows * difficulty.cols) { index ->
            Cell(r = index / difficulty.cols, c = index % difficulty.cols)
        }
        mutableStateOf(defaultBoard)
    }
    var gameStatus by rememberSaveable(difficulty) { mutableStateOf(MineSweeperStatus.WAITING) }
    var timerSeconds by rememberSaveable(difficulty) { mutableIntStateOf(0) }
    var isFirstTap by rememberSaveable(difficulty) { mutableStateOf(true) }

    // High Score / Best Time States
    var bestTimeEasy by remember { mutableIntStateOf(sharedPrefs.getInt("best_time_EASY", 9999)) }
    var bestTimeMedium by remember { mutableIntStateOf(sharedPrefs.getInt("best_time_MEDIUM", 9999)) }
    var bestTimeHard by remember { mutableIntStateOf(sharedPrefs.getInt("best_time_HARD", 9999)) }

    val currentBestTime = when (difficulty) {
        MineSweeperDifficulty.EASY -> bestTimeEasy
        MineSweeperDifficulty.MEDIUM -> bestTimeMedium
        MineSweeperDifficulty.HARD -> bestTimeHard
    }

    // Dialog States
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var showExitConfirmDialog by remember { mutableStateOf(false) }

    // Timer Loop
    LaunchedEffect(gameStatus) {
        if (gameStatus == MineSweeperStatus.PLAYING) {
            while (true) {
                delay(1000)
                timerSeconds = (timerSeconds + 1).coerceAtMost(9999)
            }
        }
    }

    // Reset Game Function
    fun resetGame() {
        board = List(difficulty.rows * difficulty.cols) { index ->
            Cell(r = index / difficulty.cols, c = index % difficulty.cols)
        }
        gameStatus = MineSweeperStatus.WAITING
        timerSeconds = 0
        isFirstTap = true
    }

    // Populate Mines after first tap to ensure safety
    fun generateBoard(firstR: Int, firstC: Int) {
        val rows = difficulty.rows
        val cols = difficulty.cols
        val mineCount = difficulty.mines
        val cellsCount = rows * cols

        val mineIndices = mutableSetOf<Int>()
        val forbiddenIndices = mutableSetOf<Int>()

        // Forbid mine placement in the clicked cell and all its immediate 8 neighbors
        for (dr in -1..1) {
            for (dc in -1..1) {
                val nr = firstR + dr
                val nc = firstC + dc
                if (nr in 0 until rows && nc in 0 until cols) {
                    forbiddenIndices.add(nr * cols + nc)
                }
            }
        }

        val random = Random(System.currentTimeMillis())
        while (mineIndices.size < mineCount && mineIndices.size + forbiddenIndices.size < cellsCount) {
            val idx = random.nextInt(cellsCount)
            if (idx !in forbiddenIndices) {
                mineIndices.add(idx)
            }
        }

        // Create updated board
        val newBoard = ArrayList<Cell>(cellsCount)
        for (i in 0 until cellsCount) {
            val r = i / cols
            val c = i % cols
            val isMine = i in mineIndices
            newBoard.add(Cell(r = r, c = c, isMine = isMine))
        }

        // Calculate adjacent mine counts
        for (i in 0 until cellsCount) {
            val cell = newBoard[i]
            if (cell.isMine) continue
            var count = 0
            for (dr in -1..1) {
                for (dc in -1..1) {
                    val nr = cell.r + dr
                    val nc = cell.c + dc
                    if (nr in 0 until rows && nc in 0 until cols) {
                        if (newBoard[nr * cols + nc].isMine) {
                            count++
                        }
                    }
                }
            }
            newBoard[i] = cell.copy(adjacentMines = count)
        }

        board = newBoard
        isFirstTap = false
        gameStatus = MineSweeperStatus.PLAYING
    }

    // Recursive empty reveal function
    fun revealCellRecursive(startR: Int, startC: Int, currentBoard: MutableList<Cell>) {
        val rows = difficulty.rows
        val cols = difficulty.cols
        val queue = ArrayDeque<Pair<Int, Int>>()
        queue.add(Pair(startR, startC))

        val visited = HashSet<Int>()
        visited.add(startR * cols + startC)

        while (queue.isNotEmpty()) {
            val (r, c) = queue.removeFirst()
            val index = r * cols + c
            val cell = currentBoard[index]

            if (cell.isFlagged) continue

            currentBoard[index] = cell.copy(isRevealed = true)

            // If it has 0 adjacent mines, reveal neighbors
            if (cell.adjacentMines == 0 && !cell.isMine) {
                for (dr in -1..1) {
                    for (dc in -1..1) {
                        val nr = r + dr
                        val nc = c + dc
                        if (nr in 0 until rows && nc in 0 until cols) {
                            val neighborIndex = nr * cols + nc
                            if (neighborIndex !in visited && !currentBoard[neighborIndex].isRevealed && !currentBoard[neighborIndex].isFlagged) {
                                visited.add(neighborIndex)
                                queue.add(Pair(nr, nc))
                            }
                        }
                    }
                }
            }
        }
    }

    // Check Win Condition
    fun checkWinCondition(currentBoard: List<Cell>): Boolean {
        // Win when all non-mine cells are revealed
        return currentBoard.all { it.isMine || it.isRevealed }
    }

    // Trigger Game Over
    fun handleGameOver(explodedR: Int, explodedC: Int) {
        gameStatus = MineSweeperStatus.LOST
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        // Reveal all mines
        board = board.map { cell ->
            if (cell.isMine) {
                cell.copy(isRevealed = true)
            } else {
                cell
            }
        }
    }

    // Handle Victory
    fun handleWin() {
        gameStatus = MineSweeperStatus.WON
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)

        // Flag all remaining mines automatically
        board = board.map { cell ->
            if (cell.isMine) cell.copy(isFlagged = true) else cell
        }

        // Save Best Time
        val scoreKey = "best_time_${difficulty.name}"
        val previousBest = sharedPrefs.getInt(scoreKey, 9999)
        if (timerSeconds < previousBest) {
            sharedPrefs.edit().putInt(scoreKey, timerSeconds).apply()
            // Update local state
            when (difficulty) {
                MineSweeperDifficulty.EASY -> bestTimeEasy = timerSeconds
                MineSweeperDifficulty.MEDIUM -> bestTimeMedium = timerSeconds
                MineSweeperDifficulty.HARD -> bestTimeHard = timerSeconds
            }
        }
    }

    // Reveal Action
    fun revealCell(r: Int, c: Int) {
        if (gameStatus == MineSweeperStatus.LOST || gameStatus == MineSweeperStatus.WON) return

        val cols = difficulty.cols
        val index = r * cols + c
        val cell = board[index]

        if (cell.isRevealed || cell.isFlagged) return

        if (isFirstTap) {
            generateBoard(r, c)
            val updatedBoard = board.toMutableList()
            revealCellRecursive(r, c, updatedBoard)
            board = updatedBoard
            if (checkWinCondition(board)) {
                handleWin()
            }
            return
        }

        if (cell.isMine) {
            handleGameOver(r, c)
        } else {
            val updatedBoard = board.toMutableList()
            revealCellRecursive(r, c, updatedBoard)
            board = updatedBoard
            if (checkWinCondition(board)) {
                handleWin()
            }
        }
    }

    // Flag Action
    fun toggleFlag(r: Int, c: Int) {
        if (gameStatus == MineSweeperStatus.LOST || gameStatus == MineSweeperStatus.WON) return
        if (isFirstTap) return // Cannot flag on first move (before generation)

        val cols = difficulty.cols
        val index = r * cols + c
        val cell = board[index]

        if (cell.isRevealed) return

        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        board = board.toMutableList().apply {
            this[index] = cell.copy(isFlagged = !cell.isFlagged)
        }
    }

    // Total marked flags count
    val flagCount = board.count { it.isFlagged }
    val remainingMines = (difficulty.mines - flagCount).coerceAtLeast(0)

    // Layout configuration
    val containerColor = if (useWallpaper) MaterialTheme.colorScheme.surface.copy(alpha = 0.85f) else MaterialTheme.colorScheme.surface
    val borderStroke = if (useWallpaper) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)

    val difficultyAndBestCard = @Composable {
        // --- Game Setup / Difficulty & High Score Row ---
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.outlinedCardColors(containerColor = containerColor),
            border = borderStroke
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "マインスイーパ 💣",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "難易度選択:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        MineSweeperDifficulty.values().forEach { diff ->
                            FilterChip(
                                selected = difficulty == diff,
                                onClick = {
                                    if (gameStatus == MineSweeperStatus.PLAYING) {
                                        showResetConfirmDialog = true
                                    } else {
                                        difficulty = diff
                                    }
                                },
                                label = { Text(diff.name) }
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "自己ベスト:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (currentBestTime == 9999) "記録なし" else "${currentBestTime}秒",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }

    val statusPanel = @Composable {
        // --- Status Panel ---
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.outlinedCardColors(containerColor = containerColor),
            border = borderStroke
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mines Count
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "地雷", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = String.format("%03d", remainingMines),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                // Retro Smiley Restart Button
                val faceIcon = when (gameStatus) {
                    MineSweeperStatus.WAITING, MineSweeperStatus.PLAYING -> "🙂"
                    MineSweeperStatus.WON -> "😎"
                    MineSweeperStatus.LOST -> "😵"
                }

                IconButton(
                    onClick = { resetGame() },
                    modifier = Modifier
                        .size(54.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(12.dp))
                ) {
                    Text(
                        text = faceIcon,
                        fontSize = 24.sp,
                        textAlign = TextAlign.Center
                    )
                }

                // Timer
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "タイム", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = String.format("%03d", timerSeconds),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }

    val boardGrid = @Composable {
        // --- Minesweeper Board Grid ---
        Box(
            modifier = Modifier
                .then(
                    if (isWideScreen) Modifier.widthIn(max = 480.dp)
                    else Modifier.fillMaxWidth()
                )
                .aspectRatio(difficulty.cols.toFloat() / difficulty.rows.toFloat())
                .shadow(elevation = 2.dp, shape = RoundedCornerShape(16.dp))
                .background(
                    if (useWallpaper) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                for (r in 0 until difficulty.rows) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        for (c in 0 until difficulty.cols) {
                            val cell = board[r * difficulty.cols + c]
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        getCellBackgroundColor(cell, gameStatus, MaterialTheme.colorScheme)
                                    )
                                    .border(
                                        width = 0.5.dp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .combinedClickable(
                                        onClick = {
                                            if (isFlagMode) {
                                                toggleFlag(cell.r, cell.c)
                                            } else {
                                                revealCell(cell.r, cell.c)
                                            }
                                        },
                                        onLongClick = {
                                            toggleFlag(cell.r, cell.c)
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                CellContent(cell = cell, gameStatus = gameStatus)
                            }
                        }
                    }
                }
            }
        }
    }

    val actionModeToggle = @Composable {
        // --- Action Mode Toggle (Tap-to-Reveal / Tap-to-Flag) ---
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.outlinedCardColors(containerColor = containerColor),
            border = borderStroke
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { isFlagMode = false }
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = "オープン",
                        tint = if (!isFlagMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "掘る",
                        fontWeight = if (!isFlagMode) FontWeight.Bold else FontWeight.Normal,
                        color = if (!isFlagMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(24.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { isFlagMode = true }
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Flag,
                        contentDescription = "フラグ",
                        tint = if (isFlagMode) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "旗を立てる",
                        fontWeight = if (isFlagMode) FontWeight.Bold else FontWeight.Normal,
                        color = if (isFlagMode) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    val gameInstructions = @Composable {
        // --- Game Instructions ---
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.outlinedCardColors(containerColor = containerColor),
            border = borderStroke
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "遊び方 🎮",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "1. マスをタップすると、そのマスを開けます。\n" +
                            "2. マスに書かれた数字は、周囲8マスにある地雷の数を示します。\n" +
                            "3. 地雷をすべて避け、地雷以外のマスをすべて開ければ勝利です。\n" +
                            "4. 長押しするか、下の「旗を立てる」モードに切り替えることで、地雷があると思われるマスに旗（フラグ）を立てることができます。\n" +
                            "※ 最初のタップで地雷を踏むことはありません。",
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    val resultBanner = @Composable {
        AnimatedVisibility(
            visible = gameStatus == MineSweeperStatus.LOST || gameStatus == MineSweeperStatus.WON,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            val isWon = gameStatus == MineSweeperStatus.WON
            val bannerColor = if (isWon) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
            val contentColor = if (isWon) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
            val message = if (isWon) "GAME CLEAR! 🎉 素晴らしい！" else "GAME OVER 💣 地雷を踏んでしまいました"

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(16.dp),
                color = bannerColor,
                contentColor = contentColor,
                border = BorderStroke(1.dp, contentColor.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = message,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (isWon) {
                            Text(
                                text = "クリアタイム: ${timerSeconds}秒",
                                style = MaterialTheme.typography.bodySmall,
                                color = contentColor.copy(alpha = 0.8f)
                            )
                        }
                    }
                    Button(
                        onClick = { resetGame() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isWon) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "リトライ",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }

    val setupScreen = @Composable {
        val content = @Composable {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "マインスイーパ",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "盤面に隠された地雷をすべて避け、地雷以外のマスをすべて開ける古典的な頭脳パズルゲームです！",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        resetGame()
                        onPlayingStateChanged(true)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("ゲームを開始する", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        if (isWideScreen) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    content()
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    difficultyAndBestCard()
                    gameInstructions()
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                content()
                difficultyAndBestCard()
                gameInstructions()
            }
        }
    }

    val activeGameScreen = @Composable {
        val controls = @Composable {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        if (gameStatus == MineSweeperStatus.PLAYING) {
                            showExitConfirmDialog = true
                        } else {
                            onPlayingStateChanged(false)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("終了する")
                }

                Button(
                    onClick = { resetGame() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("リセット")
                }
            }
        }

        if (isWideScreen) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    statusPanel()
                    resultBanner()
                    actionModeToggle()
                    Spacer(modifier = Modifier.height(8.dp))
                    controls()
                }
                Box(
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    boardGrid()
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                statusPanel()
                boardGrid()
                resultBanner()
                actionModeToggle()
                Spacer(modifier = Modifier.height(4.dp))
                controls()
            }
        }
    }

    if (!isPlaying) {
        setupScreen()
    } else {
        activeGameScreen()
    }

    // Exit Confirmation Dialog
    if (showExitConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showExitConfirmDialog = false },
            title = { Text("確認") },
            text = { Text("現在進行中のゲームを破棄して設定画面に戻りますか？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitConfirmDialog = false
                        onPlayingStateChanged(false)
                    }
                ) {
                    Text("終了する", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirmDialog = false }) {
                    Text("キャンセル")
                }
            }
        )
    }

    // Reset Confirmation Dialog (if resetting when playing)
    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = { Text("確認") },
            text = { Text("現在進行中のゲームをリセットして、難易度を変更してもよろしいですか？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetConfirmDialog = false
                        resetGame()
                    }
                ) {
                    Text("リセット", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text("キャンセル")
                }
            }
        )
    }
}

// Helpers for cell UI colors
@Composable
private fun getCellBackgroundColor(cell: Cell, status: MineSweeperStatus, colors: ColorScheme): Color {
    return if (cell.isRevealed) {
        if (cell.isMine) {
            colors.errorContainer
        } else {
            colors.surface
        }
    } else {
        if (cell.isFlagged && status == MineSweeperStatus.LOST && !cell.isMine) {
            // Flag was wrong (game lost)
            colors.error.copy(alpha = 0.3f)
        } else {
            colors.primaryContainer.copy(alpha = 0.6f)
        }
    }
}

@Composable
private fun CellContent(cell: Cell, gameStatus: MineSweeperStatus) {
    if (cell.isRevealed) {
        if (cell.isMine) {
            Icon(
                imageVector = Icons.Default.BrightnessLow, // bomb icon
                contentDescription = "地雷",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp)
            )
        } else if (cell.adjacentMines > 0) {
            val textColor = when (cell.adjacentMines) {
                1 -> Color(0xFF1976D2) // Blue
                2 -> Color(0xFF388E3C) // Green
                3 -> Color(0xFFD32F2F) // Red
                4 -> Color(0xFF7B1FA2) // Purple
                5 -> Color(0xFFFF5722) // Orange
                6 -> Color(0xFF0097A7) // Teal
                7 -> Color(0xFF3E2723) // Brown
                else -> Color.Black
            }
            Text(
                text = cell.adjacentMines.toString(),
                fontWeight = FontWeight.Black,
                fontSize = 15.sp,
                color = textColor,
                textAlign = TextAlign.Center
            )
        }
    } else if (cell.isFlagged) {
        Icon(
            imageVector = Icons.Default.Flag,
            contentDescription = "フラグ",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp)
        )
    }
}
