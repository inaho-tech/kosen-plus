package com.webclass.plus.ui.screens

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

enum class Direction {
    UP, DOWN, LEFT, RIGHT
}

data class GameTile(
    val id: Int,
    val value: Int,
    val r: Int,
    val c: Int,
    val isMerging: Boolean = false
)

@Composable
fun TileView(value: Int, useWallpaper: Boolean) {
    val scale = remember { Animatable(0f) }
    
    LaunchedEffect(value) {
        if (value > 0) {
            scale.snapTo(0.8f)
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        } else {
            scale.snapTo(0f)
        }
    }

    if (value > 0) {
        val (bgColor, textColor) = getTileColors(value, MaterialTheme.colorScheme)
        val fontSize = when {
            value < 100 -> 26.sp
            value < 1000 -> 22.sp
            value < 10000 -> 18.sp
            else -> 14.sp
        }

        Box(
            modifier = Modifier
                .fillMaxSize(0.9f)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                }
                .shadow(elevation = 3.dp, shape = RoundedCornerShape(12.dp))
                .background(bgColor, shape = RoundedCornerShape(12.dp))
                .border(
                    width = 1.dp,
                    color = if (useWallpaper) Color.White.copy(alpha = 0.2f) else Color.Transparent,
                    shape = RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = value.toString(),
                fontSize = fontSize,
                fontWeight = FontWeight.Black,
                color = textColor,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun NumberMergeScores(
    score: Int,
    highScore: Int,
    cardColor: Color,
    borderStroke: BorderStroke
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Current Score Card
        OutlinedCard(
            modifier = Modifier
                .weight(1f)
                .height(68.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.outlinedCardColors(containerColor = cardColor),
            border = borderStroke
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "SCORE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = score.toString(),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // High Score Card
        OutlinedCard(
            modifier = Modifier
                .weight(1f)
                .height(68.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.outlinedCardColors(containerColor = cardColor),
            border = borderStroke
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "BEST",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = highScore.toString(),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun InstructionsCard(
    cardColor: Color,
    borderStroke: BorderStroke
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = cardColor),
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
                text = "画面をスワイプしてタイルを動かします。同じ数字のタイルがぶつかると合体して、数字が足し合わされます。タイルを次々に合体させて、より大きな数字のタイルを作り出しましょう！",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun NumberMergeGrid(
    tiles: List<GameTile>,
    isGameOver: Boolean,
    score: Int,
    useWallpaper: Boolean,
    onReset: () -> Unit,
    onMove: (Direction) -> Unit,
    modifier: Modifier = Modifier
) {
    var dragStartX by remember { mutableStateOf(0f) }
    var dragStartY by remember { mutableStateOf(0f) }
    var dragDone by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(2.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
            .pointerInput(isGameOver) {
                if (isGameOver) return@pointerInput
                detectDragGestures(
                    onDragStart = { offset ->
                        dragStartX = offset.x
                        dragStartY = offset.y
                        dragDone = false
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (dragDone) return@detectDragGestures
                        
                        val diffX = change.position.x - dragStartX
                        val diffY = change.position.y - dragStartY
                        val threshold = 90f // Drag threshold in px
                        
                        if (abs(diffX) > threshold || abs(diffY) > threshold) {
                            if (abs(diffX) > abs(diffY)) {
                                if (diffX > 0) {
                                    onMove(Direction.RIGHT)
                                } else {
                                    onMove(Direction.LEFT)
                                }
                            } else {
                                if (diffY > 0) {
                                    onMove(Direction.DOWN)
                                } else {
                                    onMove(Direction.UP)
                                }
                            }
                            dragDone = true
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Render 4x4 Grid (Background cells only)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            for (r in 0 until 4) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    for (c in 0 until 4) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (useWallpaper) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f)
                                )
                        )
                    }
                }
            }
        }

        // Render Sliding Active Tiles
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            val cellWidth = maxWidth / 4
            val cellHeight = maxHeight / 4

            for (tile in tiles) {
                key(tile.id) {
                    val xOffset by animateDpAsState(
                        targetValue = cellWidth * tile.c,
                        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
                        label = "xOffset_${tile.id}"
                    )
                    val yOffset by animateDpAsState(
                        targetValue = cellHeight * tile.r,
                        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
                        label = "yOffset_${tile.id}"
                    )

                    Box(
                        modifier = Modifier
                            .size(cellWidth, cellHeight)
                            .offset(x = xOffset, y = yOffset)
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        TileView(value = tile.value, useWallpaper = useWallpaper)
                    }
                }
            }
        }

        // Game Over overlay
        if (isGameOver) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.72f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Game Over",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    Text(
                        text = "スコア: $score",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Button(
                        onClick = onReset,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("もう一度挑戦", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NumberMergeScreen(
    useWallpaper: Boolean,
    isWideScreen: Boolean
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    val sharedPrefs = remember { context.getSharedPreferences("number_merge_prefs", Context.MODE_PRIVATE) }
    
    var nextTileId by remember { mutableStateOf(17) }
    
    // Load state
    var tiles by remember {
        mutableStateOf<List<GameTile>>(
            sharedPrefs.getString("number_merge_board", null)?.let { boardStr ->
                try {
                    val parts = boardStr.split(",").map { it.toInt() }
                    if (parts.size == 16) {
                        val initialList = mutableListOf<GameTile>()
                        for (i in 0 until 16) {
                            val v = parts[i]
                            if (v > 0) {
                                initialList.add(GameTile(id = i + 1, value = v, r = i / 4, c = i % 4))
                            }
                        }
                        initialList
                    } else null
                } catch (e: Exception) {
                    null
                }
            } ?: run {
                val initialList = mutableListOf<GameTile>()
                val first = (0..15).random()
                var second = (0..15).random()
                while (second == first) {
                    second = (0..15).random()
                }
                val val1 = if (Math.random() < 0.9) 2 else 4
                val val2 = if (Math.random() < 0.9) 2 else 4
                initialList.add(GameTile(id = 1, value = val1, r = first / 4, c = first % 4))
                initialList.add(GameTile(id = 2, value = val2, r = second / 4, c = second % 4))
                initialList
            }
        )
    }
    
    var score by remember { mutableStateOf(sharedPrefs.getInt("number_merge_score", 0)) }
    var highScore by remember { mutableStateOf(sharedPrefs.getInt("number_merge_high_score", 0)) }
    var isGameOver by remember { mutableStateOf(sharedPrefs.getBoolean("number_merge_game_over", false)) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    
    // Migrate old game data to number merge game data if needed
    LaunchedEffect(Unit) {
        val oldPrefs = context.getSharedPreferences("game_2048_prefs", Context.MODE_PRIVATE)
        if (oldPrefs.all.isNotEmpty()) {
            val oldBoard = oldPrefs.getString("game_2048_board", null)
            val oldScore = oldPrefs.getInt("game_2048_score", -1)
            val oldHighScore = oldPrefs.getInt("game_2048_high_score", -1)
            val oldGameOver = oldPrefs.getBoolean("game_2048_game_over", false)

            val editor = sharedPrefs.edit()
            if (oldBoard != null) editor.putString("number_merge_board", oldBoard)
            if (oldScore != -1) editor.putInt("number_merge_score", oldScore)
            if (oldHighScore != -1) editor.putInt("number_merge_high_score", oldHighScore)
            editor.putBoolean("number_merge_game_over", oldGameOver)
            editor.apply()

            // Clear old preferences to avoid running migration again
            oldPrefs.edit().clear().apply()

            // Update state variables with migrated data
            if (oldHighScore != -1) highScore = oldHighScore
            if (oldScore != -1) score = oldScore
            if (oldBoard != null) {
                try {
                    val parts = oldBoard.split(",").map { it.toInt() }
                    if (parts.size == 16) {
                        val initialList = mutableListOf<GameTile>()
                        for (i in 0 until 16) {
                            val v = parts[i]
                            if (v > 0) {
                                initialList.add(GameTile(id = i + 1, value = v, r = i / 4, c = i % 4))
                            }
                        }
                        tiles = initialList
                        nextTileId = 17
                    }
                } catch (e: Exception) {}
            }
            isGameOver = oldGameOver
        }
    }

    fun getGridCoords(direction: Direction, lineIndexInGrid: Int, posInLine: Int): Pair<Int, Int> {
        return when (direction) {
            Direction.LEFT -> Pair(lineIndexInGrid, posInLine)
            Direction.RIGHT -> Pair(lineIndexInGrid, 3 - posInLine)
            Direction.UP -> Pair(posInLine, lineIndexInGrid)
            Direction.DOWN -> Pair(3 - posInLine, lineIndexInGrid)
        }
    }

    fun getPosInLine(direction: Direction, tile: GameTile): Int {
        return when (direction) {
            Direction.LEFT -> tile.c
            Direction.RIGHT -> 3 - tile.c
            Direction.UP -> tile.r
            Direction.DOWN -> 3 - tile.r
        }
    }

    fun getLineIndexInGrid(direction: Direction, tile: GameTile): Int {
        return when (direction) {
            Direction.LEFT, Direction.RIGHT -> tile.r
            Direction.UP, Direction.DOWN -> tile.c
        }
    }

    fun getBoardArray(tileList: List<GameTile>): IntArray {
        val arr = IntArray(16)
        for (t in tileList) {
            if (!t.isMerging) {
                arr[t.r * 4 + t.c] = t.value
            }
        }
        return arr
    }

    fun saveGameState(currentBoard: IntArray, currentScore: Int, currentHighScore: Int, gameOver: Boolean) {
        val boardStr = currentBoard.joinToString(",")
        sharedPrefs.edit()
            .putString("number_merge_board", boardStr)
            .putInt("number_merge_score", currentScore)
            .putInt("number_merge_high_score", currentHighScore)
            .putBoolean("number_merge_game_over", gameOver)
            .apply()
    }

    fun spawnRandomTile(targetTiles: MutableList<GameTile>) {
        val occupied = targetTiles.filter { !it.isMerging }.map { it.r * 4 + it.c }.toSet()
        val emptyIndices = (0..15).filter { it !in occupied }
        if (emptyIndices.isNotEmpty()) {
            val randomIndex = emptyIndices.random()
            val r = randomIndex / 4
            val c = randomIndex % 4
            val value = if (Math.random() < 0.9) 2 else 4
            targetTiles.add(GameTile(id = nextTileId++, value = value, r = r, c = c))
        }
    }

    fun startNewGame() {
        val newTiles = mutableListOf<GameTile>()
        nextTileId = 17
        spawnRandomTile(newTiles)
        spawnRandomTile(newTiles)
        tiles = newTiles
        score = 0
        isGameOver = false
        saveGameState(getBoardArray(newTiles), 0, highScore, false)
    }

    fun hasMovesLeft(checkBoard: IntArray): Boolean {
        if (checkBoard.contains(0)) return true
        
        // Check horizontal adjacents
        for (r in 0 until 4) {
            for (c in 0 until 3) {
                if (checkBoard[r * 4 + c] == checkBoard[r * 4 + c + 1]) return true
            }
        }
        
        // Check vertical adjacents
        for (c in 0 until 4) {
            for (r in 0 until 3) {
                if (checkBoard[r * 4 + c] == checkBoard[(r + 1) * 4 + c]) return true
            }
        }
        
        return false
    }

    var isAnimating by remember { mutableStateOf(false) }

    fun handleMove(direction: Direction) {
        if (isGameOver || isAnimating) return
        
        val currentTiles = tiles
        var anyMoved = false
        var totalScoreAdded = 0
        
        val normalTilesAfterMove = mutableListOf<GameTile>()
        val mergingTiles = mutableListOf<GameTile>()
        val spawnedTiles = mutableListOf<GameTile>()
        
        for (lineIndexInGrid in 0 until 4) {
            val lineTiles = currentTiles.filter { !it.isMerging && getLineIndexInGrid(direction, it) == lineIndexInGrid }
            val sortedTiles = lineTiles.sortedBy { getPosInLine(direction, it) }
            
            var targetPos = 0
            var i = 0
            while (i < sortedTiles.size) {
                val current = sortedTiles[i]
                if (i + 1 < sortedTiles.size && current.value == sortedTiles[i + 1].value) {
                    val next = sortedTiles[i + 1]
                    val (destR, destC) = getGridCoords(direction, lineIndexInGrid, targetPos)
                    
                    mergingTiles.add(current.copy(r = destR, c = destC, isMerging = true))
                    mergingTiles.add(next.copy(r = destR, c = destC, isMerging = true))
                    
                    val newValue = current.value * 2
                    totalScoreAdded += newValue
                    
                    spawnedTiles.add(GameTile(id = nextTileId++, value = newValue, r = destR, c = destC))
                    
                    anyMoved = true
                    targetPos++
                    i += 2
                } else {
                    val (destR, destC) = getGridCoords(direction, lineIndexInGrid, targetPos)
                    if (current.r != destR || current.c != destC) {
                        anyMoved = true
                        normalTilesAfterMove.add(current.copy(r = destR, c = destC))
                    } else {
                        normalTilesAfterMove.add(current)
                    }
                    targetPos++
                    i++
                }
            }
        }
        
        if (anyMoved) {
            isAnimating = true
            tiles = normalTilesAfterMove + mergingTiles
            
            coroutineScope.launch {
                delay(150)
                
                val nextTiles = normalTilesAfterMove.toMutableList()
                nextTiles.addAll(spawnedTiles)
                
                spawnRandomTile(nextTiles)
                
                tiles = nextTiles
                score += totalScoreAdded
                if (score > highScore) {
                    highScore = score
                }
                
                val currentBoard = getBoardArray(nextTiles)
                isGameOver = !hasMovesLeft(currentBoard)
                saveGameState(currentBoard, score, highScore, isGameOver)
                
                isAnimating = false
            }
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

    if (isWideScreen) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Column: Scores, Instructions, Reset Button
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                NumberMergeScores(
                    score = score,
                    highScore = highScore,
                    cardColor = cardColor,
                    borderStroke = borderStroke
                )

                InstructionsCard(
                    cardColor = cardColor,
                    borderStroke = borderStroke
                )

                OutlinedButton(
                    onClick = { showResetConfirmDialog = true },
                    shape = RoundedCornerShape(14.dp),
                    border = borderStroke,
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = cardColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("リセット")
                }
            }

            // Right Column: 4x4 Grid
            Box(
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                NumberMergeGrid(
                    tiles = tiles,
                    isGameOver = isGameOver,
                    score = score,
                    useWallpaper = useWallpaper,
                    onReset = { startNewGame() },
                    onMove = { handleMove(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
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
            NumberMergeScores(
                score = score,
                highScore = highScore,
                cardColor = cardColor,
                borderStroke = borderStroke
            )

            NumberMergeGrid(
                tiles = tiles,
                isGameOver = isGameOver,
                score = score,
                useWallpaper = useWallpaper,
                onReset = { startNewGame() },
                onMove = { handleMove(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            )

            // Reset button
            OutlinedButton(
                onClick = { showResetConfirmDialog = true },
                shape = RoundedCornerShape(14.dp),
                border = borderStroke,
                colors = ButtonDefaults.outlinedButtonColors(containerColor = cardColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("リセット")
            }
        }
    }

    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = { Text("ゲームリセットの確認") },
            text = { Text("ゲームを最初からやり直しますか？現在のスコアと盤面は消去されます。") },
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

// Map numbers dynamically to active Material 3 ColorScheme color tokens
private fun getTileColors(value: Int, cs: ColorScheme): Pair<Color, Color> {
    return when (value) {
        2 -> cs.primaryContainer to cs.onPrimaryContainer
        4 -> cs.secondaryContainer to cs.onSecondaryContainer
        8 -> cs.tertiaryContainer to cs.onTertiaryContainer
        16 -> cs.errorContainer to cs.onErrorContainer
        32 -> cs.primary to cs.onPrimary
        64 -> cs.secondary to cs.onSecondary
        128 -> cs.tertiary to cs.onTertiary
        256 -> cs.error to cs.onError
        512 -> cs.outline to cs.surfaceVariant
        1024 -> cs.inverseSurface to cs.inverseOnSurface
        2048 -> cs.primaryContainer to cs.primary
        else -> cs.surfaceVariant to cs.onSurfaceVariant
    }
}
