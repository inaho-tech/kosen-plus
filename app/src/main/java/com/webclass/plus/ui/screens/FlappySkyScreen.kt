package com.webclass.plus.ui.screens

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.sin
import kotlin.random.Random

enum class FlappyDifficulty(val speedMultiplier: Float, val gapHeightDp: Float, val label: String) {
    EASY(0.8f, 210f, "イージー"),
    NORMAL(1.0f, 175f, "ノーマル"),
    HARD(1.2f, 145f, "ハード")
}

enum class FlappyStatus { WAITING, PLAYING, GAMEOVER }

data class FlappyPipe(
    var x: Float,
    val gapY: Float, // Center Y of the gap
    val gapHeight: Float,
    var passed: Boolean = false
)

data class FlappyCloud(
    var x: Float,
    val y: Float,
    val speed: Float,
    val scale: Float
)

data class FlappyParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var alpha: Float,
    val size: Float,
    val color: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlappySkyScreen(
    useWallpaper: Boolean,
    isPlaying: Boolean,
    onPlayingStateChanged: (Boolean) -> Unit,
    isWideScreen: Boolean
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val sharedPrefs = remember { context.getSharedPreferences("flappy_sky_prefs", Context.MODE_PRIVATE) }

    // Game Difficulty
    var difficulty by rememberSaveable { mutableStateOf(FlappyDifficulty.NORMAL) }

    // Best Score States
    var bestEasy by remember { mutableIntStateOf(sharedPrefs.getInt("best_EASY", 0)) }
    var bestNormal by remember { mutableIntStateOf(sharedPrefs.getInt("best_NORMAL", 0)) }
    var bestHard by remember { mutableIntStateOf(sharedPrefs.getInt("best_HARD", 0)) }

    val currentBest = when (difficulty) {
        FlappyDifficulty.EASY -> bestEasy
        FlappyDifficulty.NORMAL -> bestNormal
        FlappyDifficulty.HARD -> bestHard
    }

    // Dynamic Game States
    var gameStatus by rememberSaveable { mutableStateOf(FlappyStatus.WAITING) }
    var score by rememberSaveable { mutableIntStateOf(0) }

    // Physics parameters & state (will be initialized when canvas constraints are loaded)
    var birdY by remember { mutableStateOf(0f) }
    var birdVelocity by remember { mutableStateOf(0f) }
    val pipes = remember { mutableStateListOf<FlappyPipe>() }
    val clouds = remember { mutableStateListOf<FlappyCloud>() }
    val particles = remember { mutableStateListOf<FlappyParticle>() }

    // Local configuration metrics (loaded on first layout)
    var canvasWidth by remember { mutableStateOf(0f) }
    var canvasHeight by remember { mutableStateOf(0f) }
    val density = LocalDensity.current.density

    // State updates are handled explicitly via UI buttons now.

    // Trigger Game Over
    fun handleGameOver() {
        gameStatus = FlappyStatus.GAMEOVER
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)

        // Save high score
        val scoreKey = "best_${difficulty.name}"
        val previousBest = sharedPrefs.getInt(scoreKey, 0)
        if (score > previousBest) {
            sharedPrefs.edit().putInt(scoreKey, score).apply()
            when (difficulty) {
                FlappyDifficulty.EASY -> bestEasy = score
                FlappyDifficulty.NORMAL -> bestNormal = score
                FlappyDifficulty.HARD -> bestHard = score
            }
        }
    }

    // Trigger Flap
    fun flap() {
        if (gameStatus == FlappyStatus.GAMEOVER) return
        if (gameStatus == FlappyStatus.WAITING) {
            gameStatus = FlappyStatus.PLAYING
        }

        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        // Flap strength scaled by screen density
        birdVelocity = -7.5f * density

        // Spawn flap particles
        repeat(5) {
            particles.add(
                FlappyParticle(
                    x = canvasWidth * 0.25f,
                    y = birdY,
                    vx = -Random.nextFloat() * 2f - 1f,
                    vy = (Random.nextFloat() - 0.5f) * 2f,
                    alpha = 1.0f,
                    size = Random.nextFloat() * 8f + 4f,
                    color = Color.White.copy(alpha = 0.6f)
                )
            )
        }
    }

    // Reset Game Function
    fun resetGame() {
        score = 0
        birdY = canvasHeight * 0.4f
        birdVelocity = 0f
        pipes.clear()
        particles.clear()
        gameStatus = FlappyStatus.WAITING

        // Pre-populate a few clouds
        clouds.clear()
        if (canvasWidth > 0f) {
            repeat(4) { idx ->
                clouds.add(
                    FlappyCloud(
                        x = Random.nextFloat() * canvasWidth,
                        y = Random.nextFloat() * (canvasHeight * 0.5f),
                        speed = (Random.nextFloat() * 0.3f + 0.1f) * density,
                        scale = Random.nextFloat() * 0.8f + 0.6f
                    )
                )
            }
        }
    }

    // Physics and Game Loop
    LaunchedEffect(gameStatus, canvasHeight, difficulty) {
        if (gameStatus == FlappyStatus.PLAYING && canvasHeight > 0f) {
            var frameCount = 0
            while (gameStatus == FlappyStatus.PLAYING) {
                frameCount++
                delay(16) // ~60fps

                // --- 1. Bird Physics ---
                val gravity = 0.36f * density
                val terminalVelocity = 12f * density
                birdVelocity = (birdVelocity + gravity).coerceAtMost(terminalVelocity)
                birdY += birdVelocity

                // Collision with boundaries
                val birdRadius = 18f * density
                val groundHeight = 60f * density
                val groundY = canvasHeight - groundHeight

                if (birdY - birdRadius < 0f) {
                    birdY = birdRadius
                    birdVelocity = 0f
                }
                if (birdY + birdRadius >= groundY) {
                    birdY = groundY - birdRadius
                    handleGameOver()
                    break
                }

                // --- 2. Background Clouds Loop ---
                // Randomly spawn a cloud
                if (frameCount % 240 == 0 && clouds.size < 6) {
                    clouds.add(
                        FlappyCloud(
                            x = canvasWidth + 100f,
                            y = Random.nextFloat() * (canvasHeight * 0.4f) + 30f,
                            speed = (Random.nextFloat() * 0.4f + 0.1f) * density,
                            scale = Random.nextFloat() * 0.8f + 0.5f
                        )
                    )
                }
                clouds.forEach { cloud ->
                    cloud.x -= cloud.speed
                }
                clouds.removeAll { it.x < -200f }

                // --- 3. Pipe Physics & Spawning ---
                val speed = 2.8f * density * difficulty.speedMultiplier
                val gapHeight = difficulty.gapHeightDp * density
                val pipeWidth = 64f * density
                val minPipeHeight = 80f * density
                val pipeSpawnIntervalX = 260f * density // horizontal distance between pipes

                // Spawn first pipe, or spawn next when the last pipe has traveled far enough
                if (pipes.isEmpty() || (canvasWidth - pipes.last().x) >= pipeSpawnIntervalX) {
                    val gapCenterY = Random.nextFloat() * (groundY - 2 * minPipeHeight - gapHeight) + minPipeHeight + gapHeight / 2
                    pipes.add(
                        FlappyPipe(
                            x = canvasWidth,
                            gapY = gapCenterY,
                            gapHeight = gapHeight
                        )
                    )
                }

                // Move and filter pipes
                pipes.forEach { pipe ->
                    pipe.x -= speed

                    // Score calculation
                    val birdX = canvasWidth * 0.25f
                    if (!pipe.passed && pipe.x + pipeWidth < birdX) {
                        pipe.passed = true
                        score++
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                        // Score Pop Particles
                        repeat(10) {
                            particles.add(
                                FlappyParticle(
                                    x = birdX,
                                    y = birdY,
                                    vx = (Random.nextFloat() - 0.5f) * 4f,
                                    vy = (Random.nextFloat() - 0.5f) * 4f,
                                    alpha = 1.0f,
                                    size = Random.nextFloat() * 6f + 3f,
                                    color = Color(0xFFFFF176) // Sparkly Yellow
                                )
                            )
                        }
                    }

                    // Collision checking
                    val bx = birdX
                    val by = birdY
                    val px = pipe.x
                    val pw = pipeWidth
                    val gy = pipe.gapY
                    val gh = pipe.gapHeight

                    // Check if bird enters horizontal bounds of the pipe
                    if (bx + birdRadius > px && bx - birdRadius < px + pw) {
                        // Check if bird hits upper pipe or lower pipe
                        if (by - birdRadius < gy - gh / 2 || by + birdRadius > gy + gh / 2) {
                            // Explosion particles!
                            repeat(15) {
                                particles.add(
                                    FlappyParticle(
                                        x = bx,
                                        y = by,
                                        vx = (Random.nextFloat() - 0.5f) * 8f,
                                        vy = (Random.nextFloat() - 0.5f) * 8f,
                                        alpha = 1.0f,
                                        size = Random.nextFloat() * 12f + 4f,
                                        color = Color(0xFFFF5722) // Fiery Orange
                                    )
                                )
                            }
                            handleGameOver()
                        }
                    }
                }

                // Remove offscreen pipes
                pipes.removeAll { it.x < -pipeWidth }

                // --- 4. Particles update ---
                particles.forEach { particle ->
                    particle.x += particle.vx
                    particle.y += particle.vy
                    particle.alpha -= 0.04f
                }
                particles.removeAll { it.alpha <= 0f }
            }
        }
    }

    // Initialize layout dimensions on render
    LaunchedEffect(canvasWidth, canvasHeight) {
        if (canvasHeight > 0f) {
            resetGame()
        }
    }

    // Outer UI Design
    val containerColor = if (useWallpaper) MaterialTheme.colorScheme.surface.copy(alpha = 0.85f) else MaterialTheme.colorScheme.surface
    val borderStroke = if (useWallpaper) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)

    val headerCard = @Composable {
        // --- Header Card ---
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
                        text = "Flappy Sky ☁️",
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
                        text = "難易度:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FlappyDifficulty.values().forEach { diff ->
                            FilterChip(
                                selected = difficulty == diff,
                                onClick = {
                                    if (gameStatus == FlappyStatus.PLAYING) {
                                        gameStatus = FlappyStatus.GAMEOVER
                                    }
                                    difficulty = diff
                                    resetGame()
                                },
                                label = { Text(diff.label) }
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
                        text = "${currentBest}点",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }

    val gameCanvas = @Composable {
        // --- Main Game Canvas ---
        Box(
            modifier = Modifier
                .then(
                    if (isWideScreen) Modifier.width(380.dp).height(450.dp)
                    else Modifier.fillMaxWidth().height(450.dp)
                )
                .shadow(elevation = 3.dp, shape = RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF80DEEA), // Light cyan/blue
                            Color(0xFFE0F7FA)  // Soft pale sky blue
                        )
                    )
                )
                .clickable(enabled = gameStatus != FlappyStatus.GAMEOVER) {
                    flap()
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                canvasWidth = size.width
                canvasHeight = size.height

                // Draw clouds
                clouds.forEach { cloud ->
                    drawCloud(cloud, density)
                }

                // Draw pipes
                pipes.forEach { pipe ->
                    drawPipe(pipe, canvasHeight, density)
                }

                // Draw particles
                particles.forEach { particle ->
                    drawCircle(
                        color = particle.color.copy(alpha = particle.alpha),
                        radius = particle.size * density * 0.5f,
                        center = Offset(particle.x, particle.y)
                    )
                }

                // Draw Ground
                val groundHeight = 60f * density
                val groundY = canvasHeight - groundHeight
                drawRect(
                    color = Color(0xFF81C784), // Light Green Ground
                    topLeft = Offset(0f, groundY),
                    size = Size(canvasWidth, groundHeight)
                )
                drawRect(
                    color = Color(0xFF388E3C), // Solid Green Grass Border
                    topLeft = Offset(0f, groundY),
                    size = Size(canvasWidth, 6f * density)
                )

                // Draw Bird (rotate based on velocity to give nose-up/nose-down effect)
                if (canvasHeight > 0f) {
                    val birdX = canvasWidth * 0.25f
                    val angle = (birdVelocity * 3f).coerceIn(-30f, 75f)
                    val birdRadius = 18f * density

                    rotate(degrees = angle, pivot = Offset(birdX, birdY)) {
                        // Draw Bird Body
                        drawCircle(
                            color = Color(0xFFFBC02D), // Beautiful Gold/Yellow
                            radius = birdRadius,
                            center = Offset(birdX, birdY)
                        )
                        // Bird Beak
                        drawBeak(birdX, birdY, birdRadius)
                        // Bird Eye
                        drawCircle(
                            color = Color.Black,
                            radius = birdRadius * 0.15f,
                            center = Offset(birdX + birdRadius * 0.4f, birdY - birdRadius * 0.3f)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = birdRadius * 0.05f,
                            center = Offset(birdX + birdRadius * 0.45f, birdY - birdRadius * 0.35f)
                        )
                        // Bird Wing (alternate flap animation using system clock)
                        val isFlapping = (System.currentTimeMillis() / 150) % 2 == 0L
                        val wingOffset = if (isFlapping) -4f * density else 2f * density
                        drawOval(
                            color = Color(0xFFFFF9C4), // Pale yellow wing
                            topLeft = Offset(birdX - birdRadius * 0.6f, birdY - birdRadius * 0.2f + wingOffset),
                            size = Size(birdRadius * 0.8f, birdRadius * 0.5f)
                        )
                    }
                }
            }

            // --- Game Screen Overlays ---
            when (gameStatus) {
                FlappyStatus.WAITING -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.15f))
                            .fillMaxSize()
                            .wrapContentHeight()
                            .padding(24.dp)
                    ) {
                        Text(
                            text = "画面タップでスタート",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "タップするたびに上昇します。\n雲や障害物をくぐり抜けましょう！",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.9f),
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                }
                FlappyStatus.PLAYING -> {
                    // Display current score dynamically
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 20.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Text(
                            text = score.toString(),
                            fontSize = 64.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White.copy(alpha = 0.85f),
                            style = LocalTextStyle.current.copy(
                                shadow = androidx.compose.ui.graphics.Shadow(
                                    color = Color.Black.copy(alpha = 0.5f),
                                    offset = Offset(2f, 4f),
                                    blurRadius = 8f
                                )
                            )
                        )
                    }
                }
                FlappyStatus.GAMEOVER -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.45f))
                            .fillMaxSize()
                            .wrapContentHeight()
                            .padding(24.dp)
                    ) {
                        Text(
                            text = "GAME OVER",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFFE53935),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "スコア: ${score}点",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { resetGame() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("もう一度プレイ", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    val instructionsCard = @Composable {
        // --- Action controls / Instructions ---
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
                    text = "操作方法 🎮",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "・ゲーム画面をタップすると鳥が少し浮き上がります。\n" +
                            "・タップしないと重力で下に落ちてしまいます。\n" +
                            "・緑色の土管のすき間を通るとスコアが加算されます。\n" +
                            "・土管や地面、天井にぶつかるとゲームオーバーになります。",
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                    text = "Flappy Sky",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "画面をタップして鳥を少しずつ浮かび上がらせ、障害物のすき間を通り抜けるアクションゲームです！",
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
                    Text("プレイを開始する", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
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
                    headerCard()
                    instructionsCard()
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
                headerCard()
                instructionsCard()
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
                    onClick = { onPlayingStateChanged(false) },
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
                    headerCard()
                    Spacer(modifier = Modifier.height(8.dp))
                    controls()
                }
                Box(
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    gameCanvas()
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
                gameCanvas()
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
}

// Draw a pipe
private fun DrawScope.drawPipe(pipe: FlappyPipe, canvasHeight: Float, density: Float) {
    val pipeWidth = 64f * density
    val gy = pipe.gapY
    val gh = pipe.gapHeight

    // Top pipe bottom Y and bottom pipe top Y
    val topPipeBottom = gy - gh / 2
    val bottomPipeTop = gy + gh / 2

    val pipeGreen = Color(0xFF4CAF50)
    val pipeGreenDark = Color(0xFF2E7D32)
    val pipeBorder = Color(0xFF1B5E20)
    val lipHeight = 24f * density
    val lipExtraWidth = 6f * density

    // Draw Upper Pipe
    drawRoundRect(
        color = pipeGreen,
        topLeft = Offset(pipe.x, 0f),
        size = Size(pipeWidth, topPipeBottom),
        cornerRadius = CornerRadius(2f * density, 2f * density)
    )
    // Draw shading on upper pipe
    drawRect(
        color = pipeGreenDark.copy(alpha = 0.3f),
        topLeft = Offset(pipe.x, 0f),
        size = Size(pipeWidth * 0.3f, topPipeBottom)
    )
    // Upper pipe lip
    drawRoundRect(
        color = pipeGreen,
        topLeft = Offset(pipe.x - lipExtraWidth, topPipeBottom - lipHeight),
        size = Size(pipeWidth + 2 * lipExtraWidth, lipHeight),
        cornerRadius = CornerRadius(4f * density, 4f * density)
    )
    // Border for upper pipe lip
    drawRoundRect(
        color = pipeBorder,
        topLeft = Offset(pipe.x - lipExtraWidth, topPipeBottom - lipHeight),
        size = Size(pipeWidth + 2 * lipExtraWidth, lipHeight),
        cornerRadius = CornerRadius(4f * density, 4f * density),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f * density)
    )

    // Draw Lower Pipe
    val groundHeight = 60f * density
    val bottomPipeHeight = canvasHeight - groundHeight - bottomPipeTop
    drawRoundRect(
        color = pipeGreen,
        topLeft = Offset(pipe.x, bottomPipeTop),
        size = Size(pipeWidth, bottomPipeHeight),
        cornerRadius = CornerRadius(2f * density, 2f * density)
    )
    // Draw shading on lower pipe
    drawRect(
        color = pipeGreenDark.copy(alpha = 0.3f),
        topLeft = Offset(pipe.x, bottomPipeTop),
        size = Size(pipeWidth * 0.3f, bottomPipeHeight)
    )
    // Lower pipe lip
    drawRoundRect(
        color = pipeGreen,
        topLeft = Offset(pipe.x - lipExtraWidth, bottomPipeTop),
        size = Size(pipeWidth + 2 * lipExtraWidth, lipHeight),
        cornerRadius = CornerRadius(4f * density, 4f * density)
    )
    // Border for lower pipe lip
    drawRoundRect(
        color = pipeBorder,
        topLeft = Offset(pipe.x - lipExtraWidth, bottomPipeTop),
        size = Size(pipeWidth + 2 * lipExtraWidth, lipHeight),
        cornerRadius = CornerRadius(4f * density, 4f * density),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f * density)
    )
}

// Draw a cloud
private fun DrawScope.drawCloud(cloud: FlappyCloud, density: Float) {
    val cloudColor = Color.White.copy(alpha = 0.65f)
    val baseRadius = 15f * density * cloud.scale
    val cx = cloud.x
    val cy = cloud.y

    drawCircle(color = cloudColor, radius = baseRadius, center = Offset(cx, cy))
    drawCircle(color = cloudColor, radius = baseRadius * 1.3f, center = Offset(cx + baseRadius * 0.9f, cy - baseRadius * 0.3f))
    drawCircle(color = cloudColor, radius = baseRadius * 1.1f, center = Offset(cx + baseRadius * 1.8f, cy))
    drawCircle(color = cloudColor, radius = baseRadius * 0.8f, center = Offset(cx - baseRadius * 0.8f, cy))
}

// Draw Bird's beak
private fun DrawScope.drawBeak(birdX: Float, birdY: Float, birdRadius: Float) {
    val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(birdX + birdRadius * 0.8f, birdY - birdRadius * 0.2f)
        lineTo(birdX + birdRadius * 1.3f, birdY)
        lineTo(birdX + birdRadius * 0.8f, birdY + birdRadius * 0.2f)
        close()
    }
    drawPath(path = path, color = Color(0xFFFF5722)) // Bright Orange
}
