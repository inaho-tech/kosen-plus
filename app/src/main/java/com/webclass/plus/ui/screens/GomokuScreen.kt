package com.webclass.plus.ui.screens

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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class GomokuMode { VS_CPU, VS_HUMAN }
enum class GomokuDifficulty { EASY, MEDIUM, HARD }

@Composable
fun GomokuStone(stoneColor: Int, isWinning: Boolean, isLastPlaced: Boolean) {
    val scale = remember { Animatable(0f) }

    LaunchedEffect(stoneColor) {
        if (stoneColor != 0) {
            scale.snapTo(0f)
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "win")
    val winScale by if (isWinning) {
        infiniteTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.15f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "winScale"
        )
    } else {
        remember { mutableStateOf(1.0f) }
    }

    if (stoneColor != 0 || scale.value > 0f) {
        val stoneGradientColors = if (stoneColor == 1) {
            listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary)
        } else {
            listOf(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.tertiary)
        }
        val stoneBorderColor = if (isWinning) {
            MaterialTheme.colorScheme.error
        } else {
            if (stoneColor == 1) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.onTertiary.copy(alpha = 0.3f)
        }

        val finalScale = scale.value * winScale

        Box(
            modifier = Modifier
                .fillMaxSize(0.92f)
                .graphicsLayer {
                    this.scaleX = finalScale
                    this.scaleY = finalScale
                }
                .shadow(elevation = if (isWinning) 8.dp else 2.dp, shape = CircleShape)
                .background(
                    Brush.radialGradient(colors = stoneGradientColors),
                    shape = CircleShape
                )
                .border(
                    width = if (isWinning) 3.dp else 0.5.dp,
                    color = stoneBorderColor,
                    shape = CircleShape
                )
        ) {
            if (isLastPlaced && !isWinning) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .background(MaterialTheme.colorScheme.error, CircleShape)
                        .align(Alignment.Center)
                )
            }
        }
    }
}

@Composable
private fun GomokuConfigCard(
    cardColor: Color,
    borderStroke: BorderStroke,
    gameMode: GomokuMode,
    onGameModeChanged: (GomokuMode) -> Unit,
    difficulty: GomokuDifficulty,
    onDifficultyChanged: (GomokuDifficulty) -> Unit,
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
                    onClick = { onGameModeChanged(GomokuMode.VS_CPU) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (gameMode == GomokuMode.VS_CPU) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Android, contentDescription = null, tint = if (gameMode == GomokuMode.VS_CPU) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("CPU対戦", color = if (gameMode == GomokuMode.VS_CPU) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Button(
                    onClick = { onGameModeChanged(GomokuMode.VS_HUMAN) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (gameMode == GomokuMode.VS_HUMAN) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.People, contentDescription = null, tint = if (gameMode == GomokuMode.VS_HUMAN) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("2人プレイ", color = if (gameMode == GomokuMode.VS_HUMAN) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (gameMode == GomokuMode.VS_CPU) {
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
                        Triple(GomokuDifficulty.EASY, "初級", MaterialTheme.colorScheme.primary),
                        Triple(GomokuDifficulty.MEDIUM, "中級", MaterialTheme.colorScheme.secondary),
                        Triple(GomokuDifficulty.HARD, "上級 (極強)", MaterialTheme.colorScheme.error)
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
                    text = "プレイヤーの石色",
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

                    val p2Selected = playerColor == 2
                    Button(
                        onClick = { onPlayerColorChanged(2) },
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
private fun GomokuScoreCard(
    cardColor: Color,
    borderStroke: BorderStroke,
    currentTurn: Int,
    isGameOver: Boolean,
    gameMode: GomokuMode,
    playerColor: Int,
    difficulty: GomokuDifficulty
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
                        .size(20.dp)
                        .background(turnColor, CircleShape)
                        .border(1.dp, turnBorder, CircleShape)
                )
                Text(
                    text = if (isGameOver) "対局終了" else {
                        if (gameMode == GomokuMode.VS_CPU) {
                            if (currentTurn == playerColor) "あなたの番 (石を置いてください)" else "CPU思考中..."
                        } else {
                            if (currentTurn == 1) "先手の番" else "後手の番"
                        }
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Mode Info
            Text(
                text = if (gameMode == GomokuMode.VS_CPU) {
                    "CPU: ${if (difficulty == GomokuDifficulty.HARD) "上級" else if (difficulty == GomokuDifficulty.MEDIUM) "中級" else "初級"}"
                } else "2P対戦",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GomokuBoard(
    board: IntArray,
    isCpuThinking: Boolean,
    isGameOver: Boolean,
    currentTurn: Int,
    winningIndices: List<Int>,
    lastPlacedIndex: Int,
    onCellClick: (Int) -> Unit
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        val gridLineColor = MaterialTheme.colorScheme.outline
        val starPointColor = MaterialTheme.colorScheme.onSurfaceVariant
        LazyVerticalGrid(
            columns = GridCells.Fixed(11),
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(4.dp),
            userScrollEnabled = false
        ) {
            items(121) { index ->
                val stoneColor = board[index]
                val r = index / 11
                val c = index % 11
                
                // Check if it's one of the star points (hoshi)
                val isStarPoint = (r == 3 && c == 3) || (r == 3 && c == 7) ||
                        (r == 5 && c == 5) ||
                        (r == 7 && c == 3) || (r == 7 && c == 7)

                val isWinning = winningIndices.contains(index)
                val isLastPlaced = lastPlacedIndex == index

                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clickable(enabled = stoneColor == 0 && !isCpuThinking && !isGameOver) {
                            onCellClick(index)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Draw Goban lines inside the cell representing intersections
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        
                        // Draw horizontal grid line
                        drawLine(
                            color = gridLineColor,
                            start = androidx.compose.ui.geometry.Offset(0f, h / 2),
                            end = androidx.compose.ui.geometry.Offset(w, h / 2),
                            strokeWidth = 1.dp.toPx()
                        )
                        // Draw vertical grid line
                        drawLine(
                            color = gridLineColor,
                            start = androidx.compose.ui.geometry.Offset(w / 2, 0f),
                            end = androidx.compose.ui.geometry.Offset(w / 2, h),
                            strokeWidth = 1.dp.toPx()
                        )

                        // Star point dots
                        if (isStarPoint) {
                            drawCircle(
                                color = starPointColor,
                                radius = 3.5.dp.toPx(),
                                center = androidx.compose.ui.geometry.Offset(w / 2, h / 2)
                            )
                        }
                    }

                    // Draw Stones
                    if (stoneColor != 0) {
                        GomokuStone(stoneColor = stoneColor, isWinning = isWinning, isLastPlaced = isLastPlaced)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GomokuScreen(
    useWallpaper: Boolean,
    isPlaying: Boolean,
    onPlayingStateChanged: (Boolean) -> Unit,
    isWideScreen: Boolean
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Game Setup States
    var gameMode by rememberSaveable { mutableStateOf(GomokuMode.VS_CPU) }
    var difficulty by rememberSaveable { mutableStateOf(GomokuDifficulty.MEDIUM) }
    var playerColor by rememberSaveable { mutableStateOf(1) } // 1 = Black, 2 = White

    // Game Board States
    var board by rememberSaveable { mutableStateOf(IntArray(121)) } // 11x11 board
    var currentTurn by rememberSaveable { mutableStateOf(1) } // 1 = Black, 2 = White
    var isGameOver by rememberSaveable { mutableStateOf(false) }
    var winnerColor by rememberSaveable { mutableStateOf(0) }
    var winningIndices by rememberSaveable { mutableStateOf<List<Int>>(emptyList()) }
    var lastPlacedIndex by rememberSaveable { mutableStateOf(-1) }
    var isCpuThinking by remember { mutableStateOf(false) }
    var showResignConfirmDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }

    // Helpers to reset the game
    fun startNewGame() {
        board = IntArray(121)
        currentTurn = 1
        isGameOver = false
        winnerColor = 0
        winningIndices = emptyList()
        lastPlacedIndex = -1
        isCpuThinking = false
        onPlayingStateChanged(true)
    }

    // Check if there is a 5-in-a-row winner
    fun checkWinner(board: IntArray): Pair<Int, List<Int>>? {
        val size = 11
        for (r in 0 until size) {
            for (c in 0 until size) {
                val color = board[r * size + c]
                if (color == 0) continue

                // Horizontal
                if (c + 4 < size) {
                    val indices = (0..4).map { r * size + (c + it) }
                    if (indices.all { board[it] == color }) return Pair(color, indices)
                }
                // Vertical
                if (r + 4 < size) {
                    val indices = (0..4).map { (r + it) * size + c }
                    if (indices.all { board[it] == color }) return Pair(color, indices)
                }
                // Diagonal Down-Right
                if (r + 4 < size && c + 4 < size) {
                    val indices = (0..4).map { (r + it) * size + (c + it) }
                    if (indices.all { board[it] == color }) return Pair(color, indices)
                }
                // Diagonal Up-Right
                if (r - 4 >= 0 && c + 4 < size) {
                    val indices = (0..4).map { (r - it) * size + (c + it) }
                    if (indices.all { board[it] == color }) return Pair(color, indices)
                }
            }
        }
        return null
    }

    // Get candidate moves (cells adjacent to existing stones within distance 2)
    fun getCandidateMoves(board: IntArray): List<Int> {
        val size = 11
        val candidates = mutableSetOf<Int>()
        for (i in 0 until 121) {
            if (board[i] != 0) {
                val r = i / size
                val c = i % size
                for (dr in -2..2) {
                    for (dc in -2..2) {
                        val nr = r + dr
                        val nc = c + dc
                        if (nr in 0 until size && nc in 0 until size) {
                            val nIdx = nr * size + nc
                            if (board[nIdx] == 0) {
                                candidates.add(nIdx)
                            }
                        }
                    }
                }
            }
        }
        if (candidates.isEmpty()) {
            return listOf(60) // Center cell (5,5)
        }
        return candidates.toList()
    }

    // Evaluation function for Gomoku
    fun evaluateBoard(board: IntArray, color: Int): Int {
        val size = 11
        var score = 0
        val oppColor = if (color == 1) 2 else 1

        fun scoreSegment(c1: Int, c2: Int, c3: Int, c4: Int, c5: Int): Int {
            var mine = 0
            var opp = 0
            val cells = intArrayOf(c1, c2, c3, c4, c5)
            for (c in cells) {
                if (c == color) mine++
                else if (c == oppColor) opp++
            }
            if (mine > 0 && opp > 0) return 0 // Blocked segment, worth nothing
            if (mine == 5) return 100000
            if (opp == 5) return -100000

            if (mine == 4) return 10000
            if (opp == 4) return -45000 // Opponent 4 is extremely dangerous, block immediately!

            if (mine == 3) return 1000
            if (opp == 3) return -6000 // Block opponent 3s before they become open 4s

            if (mine == 2) return 100
            if (opp == 2) return -300

            if (mine == 1) return 10
            if (opp == 1) return -20

            return 0
        }

        // Horizontal Scan
        for (r in 0 until size) {
            for (c in 0..size-5) {
                val b = r * size + c
                score += scoreSegment(board[b], board[b+1], board[b+2], board[b+3], board[b+4])
            }
        }
        // Vertical Scan
        for (c in 0 until size) {
            for (r in 0..size-5) {
                val b = r * size + c
                score += scoreSegment(board[b], board[b+size], board[b+2*size], board[b+3*size], board[b+4*size])
            }
        }
        // Diagonal Down-Right
        for (r in 0..size-5) {
            for (c in 0..size-5) {
                val b = r * size + c
                score += scoreSegment(board[b], board[b+size+1], board[b+2*size+2], board[b+3*size+3], board[b+4*size+4])
            }
        }
        // Diagonal Up-Right
        for (r in 4 until size) {
            for (c in 0..size-5) {
                val b = r * size + c
                score += scoreSegment(board[b], board[b-size+1], board[b-2*size+2], board[b-3*size+3], board[b-4*size+4])
            }
        }

        // Center Bias (encourage occupying central area)
        for (i in 0 until 121) {
            if (board[i] == color) {
                val r = i / size
                val c = i % size
                val dist = Math.abs(r - 5) + Math.abs(c - 5)
                score += (10 - dist)
            }
        }

        return score
    }

    // Minimax with Alpha-Beta pruning for Gomoku
    fun minimax(board: IntArray, depth: Int, alpha: Int, beta: Int, isMaximizing: Boolean, color: Int): Int {
        val winnerCheck = checkWinner(board)
        if (winnerCheck != null) {
            return if (winnerCheck.first == color) 100000 + depth else -100000 - depth
        }

        if (depth == 0) {
            return evaluateBoard(board, color)
        }

        val candidates = getCandidateMoves(board)
        if (candidates.isEmpty()) return 0

        var currentAlpha = alpha
        var currentBeta = beta
        val activeColor = if (isMaximizing) color else (if (color == 1) 2 else 1)

        // Move ordering: sort candidates by immediate score to trigger fast alpha-beta cuts
        val scoredCandidates = candidates.map { move ->
            val tempBoard = board.clone()
            tempBoard[move] = activeColor
            val immediateScore = evaluateBoard(tempBoard, color)
            Pair(move, immediateScore)
        }

        val orderedMoves = if (isMaximizing) {
            scoredCandidates.sortedByDescending { it.second }.map { it.first }
        } else {
            scoredCandidates.sortedBy { it.second }.map { it.first }
        }

        // Pruning: limit branching factor on deep search
        val maxBranch = if (depth >= 3) 8 else 12
        val prunedMoves = orderedMoves.take(maxBranch)

        if (isMaximizing) {
            var maxEval = Int.MIN_VALUE
            for (move in prunedMoves) {
                val nextBoard = board.clone()
                nextBoard[move] = color
                val eval = minimax(nextBoard, depth - 1, currentAlpha, currentBeta, false, color)
                maxEval = maxOf(maxEval, eval)
                currentAlpha = maxOf(currentAlpha, eval)
                if (currentBeta <= currentAlpha) break
            }
            return maxEval
        } else {
            val oppColor = if (color == 1) 2 else 1
            var minEval = Int.MAX_VALUE
            for (move in prunedMoves) {
                val nextBoard = board.clone()
                nextBoard[move] = oppColor
                val eval = minimax(nextBoard, depth - 1, currentAlpha, currentBeta, true, color)
                minEval = minOf(minEval, eval)
                currentBeta = minOf(currentBeta, eval)
                if (currentBeta <= currentAlpha) break
            }
            return minEval
        }
    }

    // Get best move for CPU
    fun getBestMove(board: IntArray, color: Int, difficulty: GomokuDifficulty): Int {
        val candidates = getCandidateMoves(board)
        if (candidates.isEmpty()) return -1
        if (candidates.size == 1) return candidates[0]

        val depth = when (difficulty) {
            GomokuDifficulty.EASY -> 1
            GomokuDifficulty.MEDIUM -> 2
            GomokuDifficulty.HARD -> 4 // Incredibly strong defensive block + aggressive trap strategy
        }

        var bestMove = -1
        var bestVal = Int.MIN_VALUE

        val scoredCandidates = candidates.map { move ->
            val tempBoard = board.clone()
            tempBoard[move] = color
            val immediateScore = evaluateBoard(tempBoard, color)
            Pair(move, immediateScore)
        }.sortedByDescending { it.second }

        val searchLimit = if (difficulty == GomokuDifficulty.HARD) 8 else 12
        val candidateList = scoredCandidates.take(searchLimit).map { it.first }

        for (move in candidateList) {
            val nextBoard = board.clone()
            nextBoard[move] = color
            val valEval = minimax(nextBoard, depth - 1, Int.MIN_VALUE, Int.MAX_VALUE, false, color)
            if (valEval > bestVal) {
                bestVal = valEval
                bestMove = move
            }
        }

        if (bestMove == -1 && candidates.isNotEmpty()) {
            bestMove = scoredCandidates[0].first
        }
        return bestMove
    }

    // CPU Turn Handler
    LaunchedEffect(currentTurn, isPlaying) {
        if (isPlaying && gameMode == GomokuMode.VS_CPU && currentTurn == (if (playerColor == 1) 2 else 1) && !isGameOver) {
            isCpuThinking = true
            delay(600) // Natural thinking delay
            val cpuMove = getBestMove(board, if (playerColor == 1) 2 else 1, difficulty)
            if (cpuMove != -1) {
                val nextBoard = board.clone()
                nextBoard[cpuMove] = if (playerColor == 1) 2 else 1
                board = nextBoard
                lastPlacedIndex = cpuMove

                // Check winner
                val winCheck = checkWinner(nextBoard)
                if (winCheck != null) {
                    winnerColor = winCheck.first
                    winningIndices = winCheck.second
                    isGameOver = true
                } else if (nextBoard.all { it != 0 }) {
                    // Draw
                    winnerColor = 0
                    isGameOver = true
                } else {
                    currentTurn = playerColor
                }
            }
            isCpuThinking = false
        }
    }

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
        // --- Game Setup Screen ---
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
                        text = "五目並べ",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "交互に石を置き、縦・横・斜めのいずれかに5つ先に並べた方が勝ちとなるシンプルなゲームです！",
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
                    GomokuConfigCard(
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
                    text = "五目並べ",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp)
                )

                Text(
                    text = "交互に石を置き、縦・横・斜めのいずれかに5つ先に並べた方が勝ちとなるシンプルなゲームです！",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                GomokuConfigCard(
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
                    GomokuScoreCard(
                        cardColor = cardColor,
                        borderStroke = borderStroke,
                        currentTurn = currentTurn,
                        isGameOver = isGameOver,
                        gameMode = gameMode,
                        playerColor = playerColor,
                        difficulty = difficulty
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

                    // CPU Thinking notification
                    AnimatedVisibility(
                        visible = isCpuThinking,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "CPUが思考しています...",
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
                    GomokuBoard(
                        board = board,
                        isCpuThinking = isCpuThinking,
                        isGameOver = isGameOver,
                        currentTurn = currentTurn,
                        winningIndices = winningIndices,
                        lastPlacedIndex = lastPlacedIndex,
                        onCellClick = { index ->
                            val nextBoard = board.clone()
                            nextBoard[index] = currentTurn
                            board = nextBoard
                            lastPlacedIndex = index

                            // Check winner
                            val winCheck = checkWinner(nextBoard)
                            if (winCheck != null) {
                                winnerColor = winCheck.first
                                winningIndices = winCheck.second
                                isGameOver = true
                            } else if (nextBoard.all { it != 0 }) {
                                winnerColor = 0
                                isGameOver = true
                            } else {
                                // Switch turn
                                currentTurn = if (currentTurn == 1) 2 else 1
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
                GomokuScoreCard(
                    cardColor = cardColor,
                    borderStroke = borderStroke,
                    currentTurn = currentTurn,
                    isGameOver = isGameOver,
                    gameMode = gameMode,
                    playerColor = playerColor,
                    difficulty = difficulty
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                ) {
                    GomokuBoard(
                        board = board,
                        isCpuThinking = isCpuThinking,
                        isGameOver = isGameOver,
                        currentTurn = currentTurn,
                        winningIndices = winningIndices,
                        lastPlacedIndex = lastPlacedIndex,
                        onCellClick = { index ->
                            val nextBoard = board.clone()
                            nextBoard[index] = currentTurn
                            board = nextBoard
                            lastPlacedIndex = index

                            // Check winner
                            val winCheck = checkWinner(nextBoard)
                            if (winCheck != null) {
                                winnerColor = winCheck.first
                                winningIndices = winCheck.second
                                isGameOver = true
                            } else if (nextBoard.all { it != 0 }) {
                                winnerColor = 0
                                isGameOver = true
                            } else {
                                // Switch turn
                                currentTurn = if (currentTurn == 1) 2 else 1
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

                // CPU Thinking notification
                AnimatedVisibility(
                    visible = isCpuThinking,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "CPUが思考しています...",
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
                val winText = when (winnerColor) {
                    1 -> "先手の勝ち！ 🏆"
                    2 -> "後手の勝ち！ 🏆"
                    else -> "引き分け！ 🤝"
                }

                val matchResult = if (gameMode == GomokuMode.VS_CPU) {
                    val playerWin = (playerColor == winnerColor)
                    val draw = winnerColor == 0
                    when {
                        draw -> "大熱戦でした！引き分けです。"
                        playerWin -> "おめでとうございます！CPU（${if (difficulty == GomokuDifficulty.HARD) "上級" else if (difficulty == GomokuDifficulty.MEDIUM) "中級" else "初級"}）に勝利しました！ 🎉"
                        else -> "CPU（${if (difficulty == GomokuDifficulty.HARD) "上級" else if (difficulty == GomokuDifficulty.MEDIUM) "中級" else "初級"}）の勝利です。次は勝てるようがんばりましょう！ 👍"
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
                        text = matchResult,
                        fontSize = 15.sp,
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

