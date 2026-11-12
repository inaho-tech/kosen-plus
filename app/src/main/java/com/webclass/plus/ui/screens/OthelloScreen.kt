package com.webclass.plus.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class OthelloMode { VS_CPU, VS_HUMAN }
enum class OthelloDifficulty { EASY, MEDIUM, HARD }

@Composable
fun OthelloDisc(cellValue: Int) {
    var displayedVal by remember { mutableStateOf(cellValue) }
    val rotation = remember { Animatable(0f) }
    val scale = remember { Animatable(if (cellValue != 0) 1f else 0f) }

    LaunchedEffect(cellValue) {
        if (displayedVal == 0 && cellValue != 0) {
            // New placement scale animation
            displayedVal = cellValue
            scale.snapTo(0f)
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        } else if (displayedVal != cellValue && cellValue != 0 && displayedVal != 0) {
            // Flip animation
            val currentRotation = rotation.value % 360f
            rotation.snapTo(currentRotation)
            val targetRotation = currentRotation + 180f
            
            coroutineScope {
                launch {
                    rotation.animateTo(
                        targetValue = targetRotation,
                        animationSpec = tween(durationMillis = 400, easing = LinearOutSlowInEasing)
                    )
                }
                delay(200)
                displayedVal = cellValue
            }
        } else {
            displayedVal = cellValue
        }
    }

    if (displayedVal != 0 || scale.value > 0f) {
        val discGradientColors = if (displayedVal == 1) {
            listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary)
        } else {
            listOf(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.tertiary)
        }
        val discBorderColor = if (displayedVal == 1) {
            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f)
        } else {
            MaterialTheme.colorScheme.onTertiary.copy(alpha = 0.3f)
        }

        Box(
            modifier = Modifier
                .fillMaxSize(0.85f)
                .graphicsLayer {
                    this.rotationY = rotation.value
                    this.scaleX = scale.value
                    this.scaleY = scale.value
                    cameraDistance = 12f * density
                }
                .shadow(elevation = 3.dp, shape = CircleShape)
                .background(
                    Brush.radialGradient(colors = discGradientColors),
                    shape = CircleShape
                )
                .border(1.dp, discBorderColor, CircleShape)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OthelloScreen(
    useWallpaper: Boolean,
    isPlaying: Boolean,
    onPlayingStateChanged: (Boolean) -> Unit,
    isWideScreen: Boolean
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Game Setup States
    var gameMode by rememberSaveable { mutableStateOf(OthelloMode.VS_CPU) }
    var difficulty by rememberSaveable { mutableStateOf(OthelloDifficulty.MEDIUM) }
    var playerColor by rememberSaveable { mutableStateOf(1) } // 1 = Black, -1 = White

    // Game Board States
    var board by rememberSaveable {
        mutableStateOf(
            IntArray(64).apply {
                this[27] = -1 // White
                this[28] = 1  // Black
                this[35] = 1  // Black
                this[36] = -1 // White
            }
        )
    }
    var currentTurn by rememberSaveable { mutableStateOf(1) } // 1 = Black, -1 = White
    var isGameOver by rememberSaveable { mutableStateOf(false) }
    var isCpuThinking by remember { mutableStateOf(false) }
    var passMessage by remember { mutableStateOf<String?>(null) }
    var showResignConfirmDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var gameId by rememberSaveable { mutableStateOf(0) }

    // Helpers to reset the game
    fun startNewGame() {
        board = IntArray(64).apply {
            this[27] = -1
            this[28] = 1
            this[35] = 1
            this[36] = -1
        }
        currentTurn = 1
        isGameOver = false
        isCpuThinking = false
        passMessage = null
        gameId++
        onPlayingStateChanged(true)
    }

    // Directions and offsets for Reversi move checking
    val dirR = remember { intArrayOf(-1, -1, -1, 0, 0, 1, 1, 1) }
    val dirC = remember { intArrayOf(-1, 0, 1, -1, 1, -1, 0, 1) }
    val dirStep = remember { intArrayOf(-9, -8, -7, -1, 1, 7, 8, 9) }
    val corners = remember { intArrayOf(0, 7, 56, 63) }
    val adjToCorner = remember {
        arrayOf(
            intArrayOf(1, 8, 9),      // corner 0
            intArrayOf(6, 14, 15),    // corner 7
            intArrayOf(48, 49, 57),   // corner 56
            intArrayOf(62, 54, 55)    // corner 63
        )
    }

    // Reversi Evaluation matrix
    val weights = remember {
        intArrayOf(
            120, -20,  20,   5,   5,  20, -20, 120,
            -20, -40,  -5,  -5,  -5,  -5, -40, -20,
             20,  -5,  15,   3,   3,  15,  -5,  20,
              5,  -5,   3,   3,   3,   3,  -5,   5,
              5,  -5,   3,   3,   3,   3,  -5,   5,
             20,  -5,  15,   3,   3,  15,  -5,  20,
            -20, -40,  -5,  -5,  -5,  -5, -40, -20,
            120, -20,  20,   5,   5,  20, -20, 120
        )
    }

    // Check if a cell is a valid move (highly optimized, quick exit on non-opponent neighbor)
    fun isValidMove(board: IntArray, index: Int, color: Int): Boolean {
        if (board[index] != 0) return false
        val r = index shr 3
        val c = index and 7
        for (i in 0 until 8) {
            val dr = dirR[i]
            val dc = dirC[i]
            val step = dirStep[i]
            
            val nr = r + dr
            val nc = c + dc
            if (nr !in 0..7 || nc !in 0..7) continue
            
            val neighborIndex = index + step
            if (board[neighborIndex] != -color) continue
            
            var nnr = nr + dr
            var nnc = nc + dc
            var nIndex = neighborIndex + step
            while (nnr in 0..7 && nnc in 0..7) {
                val valAt = board[nIndex]
                if (valAt == color) {
                    return true
                } else if (valAt == 0) {
                    break
                }
                nnr += dr
                nnc += dc
                nIndex += step
            }
        }
        return false
    }

    // Get all valid moves for a color as an ArrayList to avoid custom list wrappers
    fun getValidMoves(board: IntArray, color: Int): ArrayList<Int> {
        val list = ArrayList<Int>(10)
        for (i in 0 until 64) {
            if (isValidMove(board, i, color)) {
                list.add(i)
            }
        }
        return list
    }

    // Count the number of valid moves without allocating a list
    fun countValidMoves(board: IntArray, color: Int): Int {
        var count = 0
        for (i in 0 until 64) {
            if (isValidMove(board, i, color)) {
                count++
            }
        }
        return count
    }

    // Make a move and return a new board with flipped discs (highly optimized, quick exit on non-opponent neighbor)
    fun makeMove(board: IntArray, index: Int, color: Int): IntArray {
        val newBoard = board.clone()
        newBoard[index] = color
        val r = index shr 3
        val c = index and 7
        
        for (i in 0 until 8) {
            val dr = dirR[i]
            val dc = dirC[i]
            val step = dirStep[i]
            
            val nr = r + dr
            val nc = c + dc
            if (nr !in 0..7 || nc !in 0..7) continue
            
            val neighborIndex = index + step
            if (newBoard[neighborIndex] != -color) continue
            
            var nnr = nr + dr
            var nnc = nc + dc
            var nIndex = neighborIndex + step
            var steps = 1
            var isValidDirection = false
            while (nnr in 0..7 && nnc in 0..7) {
                val valAt = newBoard[nIndex]
                if (valAt == color) {
                    isValidDirection = true
                    break
                } else if (valAt == 0) {
                    break
                }
                nnr += dr
                nnc += dc
                nIndex += step
                steps++
            }
            
            if (isValidDirection) {
                var flipIndex = index + step
                for (s in 0 until steps) {
                    newBoard[flipIndex] = color
                    flipIndex += step
                }
            }
        }
        return newBoard
    }

    // Heuristic Evaluation function (optimized, zero allocations, dynamic weight adjustments)
    fun evaluateBoard(board: IntArray, color: Int): Int {
        var score = 0
        var myCount = 0
        var oppCount = 0
        
        // Sum weights
        for (i in 0 until 64) {
            val cellVal = board[i]
            if (cellVal == color) {
                score += weights[i]
                myCount++
            } else if (cellVal == -color) {
                score -= weights[i]
                oppCount++
            }
        }
        
        // Adjust for corner-adjacent cells dynamically if corners are occupied
        for (cIdx in 0 until 4) {
            val corner = corners[cIdx]
            if (board[corner] != 0) {
                val adjCells = adjToCorner[cIdx]
                for (adj in adjCells) {
                    val occupant = board[adj]
                    if (occupant != 0) {
                        val staticW = weights[adj]
                        val adjustedW = 25
                        val diff = adjustedW - staticW
                        if (occupant == color) {
                            score += diff
                        } else {
                            score -= diff
                        }
                    }
                }
            }
        }

        // Mobility: keep opponent moves low, ours high (zero allocation count)
        val myMobility = countValidMoves(board, color)
        val oppMobility = countValidMoves(board, -color)
        score += (myMobility - oppMobility) * 15

        // Parity
        val totalDiscs = myCount + oppCount
        val parityWeight = if (totalDiscs > 52) 15 else 2
        score += (myCount - oppCount) * parityWeight

        return score
    }

    // Sort moves in-place without Comparator object allocations (insertion sort)
    fun sortMoves(moves: ArrayList<Int>) {
        val size = moves.size
        for (i in 1 until size) {
            val key = moves[i]
            val keyWeight = weights[key]
            var j = i - 1
            while (j >= 0 && weights[moves[j]] < keyWeight) {
                moves[j + 1] = moves[j]
                j--
            }
            moves[j + 1] = key
        }
    }

    // Minimax with Alpha-Beta Pruning (optimized, zero allocation for passes/game overs, sorted moves)
    fun alphaBeta(board: IntArray, depth: Int, alpha: Int, beta: Int, isMaximizing: Boolean, color: Int): Int {
        if (depth <= 0) {
            return evaluateBoard(board, color)
        }

        val activeColor = if (isMaximizing) color else -color
        val validMoves = getValidMoves(board, activeColor)
        
        if (validMoves.isEmpty()) {
            // Check if opponent has moves (without allocating lists)
            val hasOpponentMoves = (0 until 64).any { isValidMove(board, it, -activeColor) }
            if (!hasOpponentMoves) {
                // Game over: return exact disc count difference with high weighting
                val myDiscs = board.count { it == color }
                val oppDiscs = board.count { it == -color }
                val diff = myDiscs - oppDiscs
                return if (diff > 0) 1000000 + diff else if (diff < 0) -1000000 - diff else 0
            }
            // Pass turn
            return alphaBeta(board, depth - 1, alpha, beta, !isMaximizing, color)
        }

        // Sort in place to maximize alpha-beta pruning efficiency
        sortMoves(validMoves)

        var currentAlpha = alpha
        var currentBeta = beta

        if (isMaximizing) {
            var maxEval = Int.MIN_VALUE
            for (move in validMoves) {
                val nextBoard = makeMove(board, move, color)
                val eval = alphaBeta(nextBoard, depth - 1, currentAlpha, currentBeta, false, color)
                maxEval = maxOf(maxEval, eval)
                currentAlpha = maxOf(currentAlpha, eval)
                if (currentBeta <= currentAlpha) break
            }
            return maxEval
        } else {
            var minEval = Int.MAX_VALUE
            for (move in validMoves) {
                val nextBoard = makeMove(board, move, -color)
                val eval = alphaBeta(nextBoard, depth - 1, currentAlpha, currentBeta, true, color)
                minEval = minOf(minEval, eval)
                currentBeta = minOf(currentBeta, eval)
                if (currentBeta <= currentAlpha) break
            }
            return minEval
        }
    }

    // Get best move for CPU (using dynamic depth for HARD difficulty to keep UI responsive)
    fun getBestMove(board: IntArray, color: Int, difficulty: OthelloDifficulty): Int {
        val validMoves = getValidMoves(board, color)
        if (validMoves.isEmpty()) return -1

        val depth = when (difficulty) {
            OthelloDifficulty.EASY -> 1
            OthelloDifficulty.MEDIUM -> 3
            OthelloDifficulty.HARD -> {
                val emptyCount = board.count { it == 0 }
                when {
                    emptyCount <= 12 -> emptyCount // Last 12 moves: perfect solver (<100ms)
                    emptyCount <= 20 -> 8          // Late game: depth 8 (very strong, <100ms)
                    emptyCount <= 36 -> 7          // Mid game: depth 7 (strong, <80ms)
                    else -> 6                      // Early game: depth 6 (good structure, <50ms)
                }
            }
        }

        var bestMove = -1
        var bestVal = Int.MIN_VALUE

        // Prioritize moves by static weights initially
        sortMoves(validMoves)

        for (move in validMoves) {
            val nextBoard = makeMove(board, move, color)
            val eval = alphaBeta(nextBoard, depth - 1, Int.MIN_VALUE, Int.MAX_VALUE, false, color)
            if (eval > bestVal) {
                bestVal = eval
                bestMove = move
            }
        }
        return bestMove
    }

    // CPU Turn Handler (runs on Dispatchers.Default to prevent main thread blocking)
    LaunchedEffect(currentTurn, isPlaying, gameId) {
        if (isPlaying && gameMode == OthelloMode.VS_CPU && currentTurn == -playerColor && !isGameOver) {
            var cpuShouldMove = true
            while (cpuShouldMove && !isGameOver) {
                isCpuThinking = true
                delay(500) // Natural thinking delay
                val cpuMove = withContext(Dispatchers.Default) {
                    getBestMove(board, -playerColor, difficulty)
                }
                if (cpuMove != -1) {
                    val nextBoard = makeMove(board, cpuMove, -playerColor)
                    board = nextBoard
                    
                    // Determine next turn
                    val oppMoves = getValidMoves(nextBoard, playerColor)
                    if (oppMoves.isNotEmpty()) {
                        currentTurn = playerColor
                        cpuShouldMove = false
                    } else {
                        val cpuNextMoves = getValidMoves(nextBoard, -playerColor)
                        if (cpuNextMoves.isNotEmpty()) {
                            passMessage = "プレイヤーに有効な手がありません。パスします。"
                            // Remains CPU's turn, so cpuShouldMove remains true to loop again
                        } else {
                            isGameOver = true
                            cpuShouldMove = false
                        }
                    }
                } else {
                    // CPU has no valid moves, check player
                    val oppMoves = getValidMoves(board, playerColor)
                    if (oppMoves.isNotEmpty()) {
                        passMessage = "CPUに有効な手がありません。パスします。"
                        currentTurn = playerColor
                        cpuShouldMove = false
                    } else {
                        isGameOver = true
                        cpuShouldMove = false
                    }
                }
                isCpuThinking = false
            }
        }
    }

    // Clears pass message after a short delay
    LaunchedEffect(passMessage) {
        if (passMessage != null) {
            delay(2000)
            passMessage = null
        }
    }

    // Board counts
    val blackCount = board.count { it == 1 }
    val whiteCount = board.count { it == -1 }
    val playerMoves = getValidMoves(board, currentTurn)



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

    if (!isPlaying) {
        // --- Game Config Setup Screen ---
        if (isWideScreen) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left pane: Title, desc, and action
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "オセロ (リバーシ)",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "学習の合間の脳トレとして、CPUまたは友達と対戦しましょう！",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { startNewGame() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("対局を開始する", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                // Right pane: Configuration options
                Column(
                    modifier = Modifier
                        .weight(1.2f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    OthelloConfigCard(
                        cardColor = cardColor,
                        borderStroke = borderStroke,
                        gameMode = gameMode,
                        onGameModeChanged = { gameMode = it },
                        difficulty = difficulty,
                        onDifficultyChanged = { difficulty = it },
                        playerColor = playerColor,
                        onPlayerColorChanged = { playerColor = it }
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "オセロ (リバーシ)",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp)
                )

                Text(
                    text = "学習の合間の脳トレとして、CPUまたは友達と対戦しましょう！",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                OthelloConfigCard(
                    cardColor = cardColor,
                    borderStroke = borderStroke,
                    gameMode = gameMode,
                    onGameModeChanged = { gameMode = it },
                    difficulty = difficulty,
                    onDifficultyChanged = { difficulty = it },
                    playerColor = playerColor,
                    onPlayerColorChanged = { playerColor = it }
                )

                Button(
                    onClick = { startNewGame() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("対局を開始する", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    } else {
        // --- Game Active Screen ---
        if (isWideScreen) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left pane: Status, scoreboard, messages, buttons
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    OthelloScoreCard(
                        cardColor = cardColor,
                        borderStroke = borderStroke,
                        currentTurn = currentTurn,
                        isGameOver = isGameOver,
                        gameMode = gameMode,
                        playerColor = playerColor,
                        blackCount = blackCount,
                        whiteCount = whiteCount
                    )

                    // Control Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showResignConfirmDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("投了・終了")
                        }

                        Button(
                            onClick = { showResetConfirmDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("リセット")
                        }
                    }

                    // Notification banner
                    AnimatedVisibility(
                        visible = passMessage != null || isCpuThinking,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = passMessage ?: "CPUが最良の戦略を検討しています...",
                                modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Right pane: Board
                Box(
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    OthelloBoard(
                        board = board,
                        playerMoves = playerMoves,
                        isCpuThinking = isCpuThinking,
                        isGameOver = isGameOver,
                        gameMode = gameMode,
                        currentTurn = currentTurn,
                        playerColor = playerColor,
                        onCellClick = { index ->
                            val nextBoard = makeMove(board, index, currentTurn)
                            board = nextBoard

                            val nextColor = -currentTurn
                            val nextOppMoves = getValidMoves(nextBoard, nextColor)
                            if (nextOppMoves.isNotEmpty()) {
                                currentTurn = nextColor
                            } else {
                                val myNextMoves = getValidMoves(nextBoard, currentTurn)
                                if (myNextMoves.isNotEmpty()) {
                                    passMessage = if (nextColor == 1) "先手に有効な手がありません。パスします。" else "後手に有効な手がありません。パスします。"
                                } else {
                                    isGameOver = true
                                }
                            }
                        }
                    )
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
                OthelloScoreCard(
                    cardColor = cardColor,
                    borderStroke = borderStroke,
                    currentTurn = currentTurn,
                    isGameOver = isGameOver,
                    gameMode = gameMode,
                    playerColor = playerColor,
                    blackCount = blackCount,
                    whiteCount = whiteCount
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                ) {
                    OthelloBoard(
                        board = board,
                        playerMoves = playerMoves,
                        isCpuThinking = isCpuThinking,
                        isGameOver = isGameOver,
                        gameMode = gameMode,
                        currentTurn = currentTurn,
                        playerColor = playerColor,
                        onCellClick = { index ->
                            val nextBoard = makeMove(board, index, currentTurn)
                            board = nextBoard

                            val nextColor = -currentTurn
                            val nextOppMoves = getValidMoves(nextBoard, nextColor)
                            if (nextOppMoves.isNotEmpty()) {
                                currentTurn = nextColor
                            } else {
                                val myNextMoves = getValidMoves(nextBoard, currentTurn)
                                if (myNextMoves.isNotEmpty()) {
                                    passMessage = if (nextColor == 1) "先手に有効な手がありません。パスします。" else "後手に有効な手がありません。パスします。"
                                } else {
                                    isGameOver = true
                                }
                            }
                        }
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = { showResignConfirmDialog = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("投了・終了")
                    }

                    Button(
                        onClick = { showResetConfirmDialog = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("リセット")
                    }
                }

                AnimatedVisibility(
                    visible = passMessage != null || isCpuThinking,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = passMessage ?: "CPUが最良の戦略を検討しています...",
                            modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    // Game Over Results Dialog
    if (isGameOver) {
        AlertDialog(
            onDismissRequest = { isGameOver = false },
            title = {
                Text(
                    text = "対局終了！",
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                val winText = when {
                    blackCount > whiteCount -> "先手の勝ち！ 🏆"
                    whiteCount > blackCount -> "後手の勝ち！ 🏆"
                    else -> "引き分け！ 🤝"
                }

                val scoreDetails = "先手 $blackCount 石  vs  後手 $whiteCount 石"

                val matchResult = if (gameMode == OthelloMode.VS_CPU) {
                    val playerWin = (playerColor == 1 && blackCount > whiteCount) || (playerColor == -1 && whiteCount > blackCount)
                    val draw = blackCount == whiteCount
                    when {
                        draw -> "大接戦でした！引き分けです。"
                        playerWin -> "おめでとうございます！CPU（${if (difficulty == OthelloDifficulty.HARD) "上級" else if (difficulty == OthelloDifficulty.MEDIUM) "中級" else "初級"}）に勝利しました！ 🎉"
                        else -> "CPU（${if (difficulty == OthelloDifficulty.HARD) "上級" else if (difficulty == OthelloDifficulty.MEDIUM) "中級" else "初級"}）の勝利です。次は勝てるようがんばりましょう！ 👍"
                    }
                } else {
                    "お互い素晴らしい対局でした！"
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = winText,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = scoreDetails,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = matchResult,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        startNewGame()
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("もう一度遊ぶ")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        isGameOver = false
                        onPlayingStateChanged(false)
                    }
                ) {
                    Text("ゲーム設定に戻る")
                }
            }
        )
    }

    if (showResignConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResignConfirmDialog = false },
            title = { Text("投了・終了の確認") },
            text = { Text("現在対局中ですが、投了して終了しますか？") },
            confirmButton = {
                Button(
                    onClick = {
                        showResignConfirmDialog = false
                        onPlayingStateChanged(false)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("終了する")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResignConfirmDialog = false }) {
                    Text("キャンセル")
                }
            }
        )
    }

    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = { Text("対局リセットの確認") },
            text = { Text("対局を最初からやり直しますか？") },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirmDialog = false
                        startNewGame()
                    }
                ) {
                    Text("リセットする")
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

// ──────────────────────────────────────────
// HELPER COMPONENTS FOR TABLET OTHELLO
// ──────────────────────────────────────────

@Composable
private fun OthelloConfigCard(
    cardColor: Color,
    borderStroke: BorderStroke,
    gameMode: OthelloMode,
    onGameModeChanged: (OthelloMode) -> Unit,
    difficulty: OthelloDifficulty,
    onDifficultyChanged: (OthelloDifficulty) -> Unit,
    playerColor: Int,
    onPlayerColorChanged: (Int) -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = cardColor),
        border = borderStroke
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "対戦モード",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { onGameModeChanged(OthelloMode.VS_CPU) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (gameMode == OthelloMode.VS_CPU) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Android, contentDescription = null, tint = if (gameMode == OthelloMode.VS_CPU) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("CPU対戦", color = if (gameMode == OthelloMode.VS_CPU) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Button(
                    onClick = { onGameModeChanged(OthelloMode.VS_HUMAN) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (gameMode == OthelloMode.VS_HUMAN) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.People, contentDescription = null, tint = if (gameMode == OthelloMode.VS_HUMAN) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("2人プレイ", color = if (gameMode == OthelloMode.VS_HUMAN) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (gameMode == OthelloMode.VS_CPU) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Text(
                    text = "CPU 難易度",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val diffs = listOf(
                        Triple(OthelloDifficulty.EASY, "初級", MaterialTheme.colorScheme.primary),
                        Triple(OthelloDifficulty.MEDIUM, "中級", MaterialTheme.colorScheme.secondary),
                        Triple(OthelloDifficulty.HARD, "上級 (極強)", MaterialTheme.colorScheme.error)
                    )
                    diffs.forEach { (d, label, color) ->
                        val selected = difficulty == d
                        Button(
                            onClick = { onDifficultyChanged(d) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selected) color else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = label,
                                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Text(
                    text = "プレイヤーの手番",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val p1Selected = playerColor == 1
                    Button(
                        onClick = { onPlayerColorChanged(1) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (p1Selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (p1Selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = if (p1Selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                                .border(1.dp, MaterialTheme.colorScheme.onPrimary, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("先手 (P1)")
                    }

                    val p2Selected = playerColor == -1
                    Button(
                        onClick = { onPlayerColorChanged(-1) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (p2Selected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (p2Selected) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = if (p2Selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(MaterialTheme.colorScheme.tertiary, CircleShape)
                                .border(1.dp, MaterialTheme.colorScheme.onTertiary, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("後手 (P2)")
                    }
                }
            }
        }
    }
}

@Composable
private fun OthelloScoreCard(
    cardColor: Color,
    borderStroke: BorderStroke,
    currentTurn: Int,
    isGameOver: Boolean,
    gameMode: OthelloMode,
    playerColor: Int,
    blackCount: Int,
    whiteCount: Int
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = cardColor),
        border = borderStroke
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val turnColor = if (currentTurn == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                val turnBorder = if (currentTurn == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onTertiary
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(turnColor, CircleShape)
                        .border(1.dp, turnBorder, CircleShape)
                )
                Text(
                    text = if (isGameOver) "終局" else {
                        if (gameMode == OthelloMode.VS_CPU) {
                            if (currentTurn == playerColor) "あなたの番" else "CPU思考中..."
                        } else {
                            if (currentTurn == 1) "先手の番" else "後手の番"
                        }
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("先手", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text("$blackCount", fontSize = 20.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                }
                Text("-", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("後手", fontSize = 11.sp, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                    Text("$whiteCount", fontSize = 20.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.tertiary)
                }
            }
        }
    }
}

@Composable
private fun OthelloBoard(
    board: IntArray,
    playerMoves: List<Int>,
    isCpuThinking: Boolean,
    isGameOver: Boolean,
    gameMode: OthelloMode,
    currentTurn: Int,
    playerColor: Int,
    onCellClick: (Int) -> Unit
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(8),
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(2.dp),
            userScrollEnabled = false
        ) {
            items(64) { index ->
                val cellValue = board[index]
                val isValid = playerMoves.contains(index) && !isCpuThinking && !isGameOver &&
                        (gameMode == OthelloMode.VS_HUMAN || currentTurn == playerColor)

                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                        .clickable(enabled = isValid) {
                            onCellClick(index)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (cellValue != 0) {
                        OthelloDisc(cellValue = cellValue)
                    } else if (isValid) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), CircleShape)
                        )
                    }
                }
            }
        }
    }
}

