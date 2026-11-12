package com.webclass.plus.ui.screens

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

enum class AirHockeyMode { VS_CPU, VS_HUMAN }
enum class AirHockeyDifficulty { EASY, MEDIUM, HARD }

// Particle data structure for premium visual hits
data class AirHockeyParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val color: Color,
    var size: Float,
    var alpha: Float,
    val lifeDecay: Float
)

@Composable
private fun AirHockeyConfigCard(
    cardColor: Color,
    borderStroke: BorderStroke,
    gameMode: AirHockeyMode,
    onGameModeChanged: (AirHockeyMode) -> Unit,
    difficulty: AirHockeyDifficulty,
    onDifficultyChanged: (AirHockeyDifficulty) -> Unit
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
                    onClick = { onGameModeChanged(AirHockeyMode.VS_CPU) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (gameMode == AirHockeyMode.VS_CPU) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Android,
                        contentDescription = null,
                        tint = if (gameMode == AirHockeyMode.VS_CPU) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "CPU対戦",
                        color = if (gameMode == AirHockeyMode.VS_CPU) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = { onGameModeChanged(AirHockeyMode.VS_HUMAN) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (gameMode == AirHockeyMode.VS_HUMAN) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.People,
                        contentDescription = null,
                        tint = if (gameMode == AirHockeyMode.VS_HUMAN) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "2人プレイ",
                        color = if (gameMode == AirHockeyMode.VS_HUMAN) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (gameMode == AirHockeyMode.VS_CPU) {
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
                        Triple(AirHockeyDifficulty.EASY, "初級", MaterialTheme.colorScheme.primary),
                        Triple(AirHockeyDifficulty.MEDIUM, "中級", MaterialTheme.colorScheme.secondary),
                        Triple(AirHockeyDifficulty.HARD, "上級 (強力)", MaterialTheme.colorScheme.error)
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
            }
        }
    }
}

@Composable
private fun AirHockeyScoreCard(
    cardColor: Color,
    borderStroke: BorderStroke,
    score1: Int,
    score2: Int,
    gameMode: AirHockeyMode,
    isPaused: Boolean,
    onPauseToggle: () -> Unit
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
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (gameMode == AirHockeyMode.VS_CPU) "VS CPU (7点先取)" else "2人プレイ (7点先取)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "上 (後手) VS 下 (先手)",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Score HUD
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$score2",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Text(
                    text = "-",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$score1",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            IconButton(
                onClick = onPauseToggle,
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            ) {
                Icon(
                    imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = if (isPaused) "再開" else "一時停止",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// Glowing drawing extensions
private fun DrawScope.drawGlowingLine(color: Color, start: Offset, end: Offset, width: Float, alpha: Float = 1.0f) {
    drawLine(color = color, start = start, end = end, strokeWidth = width * 4.5f, alpha = alpha * 0.12f)
    drawLine(color = color, start = start, end = end, strokeWidth = width * 2.0f, alpha = alpha * 0.35f)
    drawLine(color = color, start = start, end = end, strokeWidth = width, alpha = alpha)
}

private fun DrawScope.drawGlowingCircle(color: Color, center: Offset, radius: Float, strokeWidth: Float = 0f, alpha: Float = 1.0f) {
    if (strokeWidth == 0f) {
        drawCircle(color = color, center = center, radius = radius + 8f, alpha = alpha * 0.12f)
        drawCircle(color = color, center = center, radius = radius + 4f, alpha = alpha * 0.35f)
        drawCircle(color = color, center = center, radius = radius, alpha = alpha)
    } else {
        drawCircle(color = color, center = center, radius = radius, style = Stroke(strokeWidth * 4.0f), alpha = alpha * 0.12f)
        drawCircle(color = color, center = center, radius = radius, style = Stroke(strokeWidth * 2.0f), alpha = alpha * 0.35f)
        drawCircle(color = color, center = center, radius = radius, style = Stroke(strokeWidth), alpha = alpha)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AirHockeyScreen(
    useWallpaper: Boolean,
    isPlaying: Boolean,
    onPlayingStateChanged: (Boolean) -> Unit,
    isWideScreen: Boolean
) {
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()

    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary


    // Game configurations
    var gameMode by rememberSaveable { mutableStateOf(AirHockeyMode.VS_CPU) }
    var difficulty by rememberSaveable { mutableStateOf(AirHockeyDifficulty.MEDIUM) }

    // Game status
    var isPaused by rememberSaveable { mutableStateOf(false) }
    var isGameOver by rememberSaveable { mutableStateOf(false) }
    var winner by rememberSaveable { mutableStateOf(0) } // 1 for Bottom (Player), 2 for Top (CPU/P2)
    var score1 by rememberSaveable { mutableStateOf(0) } // Bottom
    var score2 by rememberSaveable { mutableStateOf(0) } // Top
    var showResignConfirmDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }

    // Screen sizing in pixels
    var canvasWidth by remember { mutableStateOf(1f) }
    var canvasHeight by remember { mutableStateOf(1f) }

    // Table coordinates mappings: Virtual space (600 width, 1000 height)
    val virtualWidth = 600f
    val virtualHeight = 1000f

    // Radii in virtual space
    val rStriker = 42f
    val rPuck = 28f

    // Physics States (Coordinates in virtual space)
    var x1 by remember { mutableStateOf(300f) } // Striker 1 (Bottom)
    var y1 by remember { mutableStateOf(850f) }
    var x2 by remember { mutableStateOf(300f) } // Striker 2 (Top/CPU)
    var y2 by remember { mutableStateOf(150f) }

    var px by remember { mutableStateOf(300f) } // Puck position
    var py by remember { mutableStateOf(500f) }
    var vx by remember { mutableStateOf(0f) }   // Puck velocity
    var vy by remember { mutableStateOf(0f) }

    // Target positions (controlled by touches)
    var targetX1 by remember { mutableStateOf(300f) }
    var targetY1 by remember { mutableStateOf(850f) }
    var targetX2 by remember { mutableStateOf(300f) }
    var targetY2 by remember { mutableStateOf(150f) }

    // Active Pointer IDs to track multi-touch correctly
    var activePointerId1 by remember { mutableStateOf<PointerId?>(null) }
    var activePointerId2 by remember { mutableStateOf<PointerId?>(null) }

    // Particles list
    val particles = remember { mutableStateListOf<AirHockeyParticle>() }

    // Temporary goal text notification animations
    var goalText by remember { mutableStateOf<String?>(null) }
    var goalTextOffset by remember { mutableStateOf(0f) }

    // CPU decision state variables (to simulate reaction lag)
    var cpuLastDecisionTime by remember { mutableStateOf(0L) }
    var cpuTargetX by remember { mutableStateOf(300f) }
    var cpuTargetY by remember { mutableStateOf(150f) }
    var lastResetTime by remember { mutableStateOf(0L) }
    var particleUpdateTrigger by remember { mutableStateOf(0) }

    // Reset Table after Goal
    fun resetTable(scoredOnPlayer: Int) {
        x1 = 300f
        y1 = 850f
        targetX1 = 300f
        targetY1 = 850f

        x2 = 300f
        y2 = 150f
        targetX2 = 300f
        targetY2 = 150f

        px = 300f
        // Start puck on the side of the player who conceded the goal
        py = if (scoredOnPlayer == 1) 650f else 350f
        vx = 0f
        vy = 0f
        activePointerId1 = null
        activePointerId2 = null
        lastResetTime = System.currentTimeMillis()
    }

    // Reset whole game
    fun startNewGame() {
        score1 = 0
        score2 = 0
        isGameOver = false
        winner = 0
        isPaused = false
        goalText = null
        particles.clear()
        resetTable(1)
        onPlayingStateChanged(true)
    }

    // Trigger Goal Effect (Particles, Sound/Haptic, and text display)
    fun triggerGoal(scoredOnPlayer: Int) {
        if (goalText != null) return // Ignore concurrent goal triggers

        vx = 0f
        vy = 0f

        val yGoal = if (scoredOnPlayer == 1) 1000f else 0f
        // Spawn fireworks particles at the goal area
        for (i in 0 until 10) {
            val color = if (scoredOnPlayer == 1) tertiaryColor else primaryColor
            val p = AirHockeyParticle(
                x = 200f + (Math.random() * 200).toFloat(),
                y = yGoal,
                vx = ((Math.random() - 0.5) * 800).toFloat(),
                vy = (if (scoredOnPlayer == 1) -Math.random() * 800 else Math.random() * 800).toFloat(),
                color = color,
                size = (5 + Math.random() * 8).toFloat(),
                alpha = 1f,
                lifeDecay = (1f + Math.random() * 1.5).toFloat()
            )
            particles.add(p)
        }

        // Haptic feedback
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

        // Set scores
        if (scoredOnPlayer == 1) {
            score2++
            goalText = if (gameMode == AirHockeyMode.VS_CPU) "CPU GOAL!" else "P2 GOAL!"
            if (score2 >= 7) {
                isGameOver = true
                winner = 2
            }
        } else {
            score1++
            goalText = if (gameMode == AirHockeyMode.VS_CPU) "PLAYER GOAL!" else "P1 GOAL!"
            if (score1 >= 7) {
                isGameOver = true
                winner = 1
            }
        }

        coroutineScope.launch {
            delay(1800)
            if (!isGameOver) {
                resetTable(scoredOnPlayer)
            }
            goalText = null
        }
    }

    // Physics engine run inside a continuous game loop
    LaunchedEffect(isPlaying, isPaused, isGameOver) {
        if (isPlaying && !isPaused && !isGameOver) {
            var lastTimeNanos = System.nanoTime()
            while (true) {
                withFrameNanos { frameNanos ->
                    if (goalText != null) {
                        val elapsedSec = ((frameNanos - lastTimeNanos) / 1_000_000_000f).coerceIn(0f, 0.03f)
                        lastTimeNanos = frameNanos
                        
                        val iterator = particles.iterator()
                        while (iterator.hasNext()) {
                            val p = iterator.next()
                            p.x += p.vx * elapsedSec
                            p.y += p.vy * elapsedSec
                            p.alpha -= p.lifeDecay * elapsedSec
                            if (p.alpha <= 0f) {
                                iterator.remove()
                            }
                        }
                        if (particles.isNotEmpty()) {
                            particleUpdateTrigger++
                        }
                        return@withFrameNanos
                    }
                    val elapsedSec = ((frameNanos - lastTimeNanos) / 1_000_000_000f).coerceIn(0f, 0.03f)
                    lastTimeNanos = frameNanos

                    // Run sub-steps to prevent puck tunneling through boundaries or mallets
                    val substeps = 4
                    val dt = elapsedSec / substeps

                    for (step in 0 until substeps) {
                        // 1. Move Strikers towards target touches & calculate velocities
                        val prevX1 = x1
                        val prevY1 = y1
                        val prevX2 = x2
                        val prevY2 = y2

                        // Striker 1 (Player - bottom) - Clamp to bottom half boundaries
                        x1 = targetX1.coerceIn(rStriker, virtualWidth - rStriker)
                        y1 = targetY1.coerceIn(500f + rStriker, virtualHeight - rStriker)

                        val s1vx = (x1 - prevX1) / dt
                        val s1vy = (y1 - prevY1) / dt

                        // Striker 2 (CPU / Opponent Player - top) - Clamp to top half boundaries
                        if (gameMode == AirHockeyMode.VS_CPU) {
                            // Run Bot AI to decide CPU target position
                            val now = System.currentTimeMillis()
                            val reactionInterval = when (difficulty) {
                                AirHockeyDifficulty.EASY -> 250L
                                AirHockeyDifficulty.MEDIUM -> 120L
                                AirHockeyDifficulty.HARD -> 0L
                            }

                            val cpuMaxSpeed = when (difficulty) {
                                AirHockeyDifficulty.EASY -> 420f
                                AirHockeyDifficulty.MEDIUM -> 750f
                                AirHockeyDifficulty.HARD -> 1200f
                            }

                            if (now - cpuLastDecisionTime >= reactionInterval) {
                                cpuLastDecisionTime = now
                                val isBotServeDelay = py < 500f && (vx == 0f && vy == 0f) && (now - lastResetTime < 1500L)
                                if (py > 500f || isBotServeDelay) {
                                    // Puck is in player's half or bot serve delay is active: CPU goes back to home base
                                    cpuTargetX = 300f
                                    cpuTargetY = 150f
                                } else {
                                    // Puck is in CPU's half: CPU tracks puck
                                    if (py < 160f) {
                                        // Puck is near the top wall/corners!
                                        // Target below the puck so the CPU stays on the defensive side
                                        cpuTargetY = py + 90f
                                        // If in the corners, target slightly to the side (inward) to swipe it out and avoid jamming
                                        if (px < 180f) {
                                            cpuTargetX = px + 80f
                                        } else if (px > 420f) {
                                            cpuTargetX = px - 80f
                                        } else {
                                            cpuTargetX = px
                                        }
                                    } else if (py < y2) {
                                        // Puck got behind the CPU striker! Go around it to avoid own goals.
                                        cpuTargetY = py - 60f
                                        if (x2 < px) {
                                            cpuTargetX = px - 80f
                                        } else {
                                            cpuTargetX = px + 80f
                                        }
                                    } else {
                                        cpuTargetX = px
                                        cpuTargetY = py
                                        // Hard AI tries to hit puck with smart angles
                                        if (difficulty == AirHockeyDifficulty.HARD && py < 400f) {
                                            // Slight offsets to target the player goal corner
                                            val offsetTarget = if (px < 300f) px - 20f else px + 20f
                                            cpuTargetX = offsetTarget
                                        }
                                    }
                                }
                            }

                            // Interpolate striker position based on max speed
                            val dx = cpuTargetX - x2
                            val dy = cpuTargetY - y2
                            val distance = sqrt(dx * dx + dy * dy)
                            if (distance > 0f) {
                                val moveDist = min(cpuMaxSpeed * dt, distance)
                                x2 += (dx / distance) * moveDist
                                y2 += (dy / distance) * moveDist
                            }
                            x2 = x2.coerceIn(rStriker, virtualWidth - rStriker)
                            y2 = y2.coerceIn(rStriker, 500f - rStriker)
                        } else {
                            // Human 2 is drag controlled
                            x2 = targetX2.coerceIn(rStriker, virtualWidth - rStriker)
                            y2 = targetY2.coerceIn(rStriker, 500f - rStriker)
                        }

                        val s2vx = (x2 - prevX2) / dt
                        val s2vy = (y2 - prevY2) / dt

                        // 2. Move Puck
                        px += vx * dt
                        py += vy * dt

                        // Apply standard friction
                        val frictionDecay = 0.985f
                        vx *= (1f - (1f - frictionDecay) * dt * 60f)
                        vy *= (1f - (1f - frictionDecay) * dt * 60f)

                        // 3. Goal detection
                        val goalRange = 200f..400f
                        if (px in goalRange) {
                            if (py - rPuck < 0f && py < -10f) {
                                triggerGoal(2) // Scored on top goal (P1 point!)
                                break
                            } else if (py + rPuck > 1000f && py > 1010f) {
                                triggerGoal(1) // Scored on bottom goal (P2/CPU point!)
                                break
                            }
                        }

                        // 4. Boundary Wall Collisions (excluding goal area)
                        val bounceRest = 0.95f
                        
                        // Left & Right
                        if (px - rPuck < 0f) {
                            px = rPuck
                            if (vx < 0f) {
                                vx = -vx * bounceRest
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                        } else if (px + rPuck > virtualWidth) {
                            px = virtualWidth - rPuck
                            if (vx > 0f) {
                                vx = -vx * bounceRest
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                        }

                        // Top & Bottom Walls (outside the goal opening)
                        if (px < goalRange.start || px > goalRange.endInclusive) {
                            if (py - rPuck < 0f) {
                                py = rPuck
                                if (vy < 0f) {
                                    vy = -vy * bounceRest
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                }
                            } else if (py + rPuck > virtualHeight) {
                                py = virtualHeight - rPuck
                                if (vy > 0f) {
                                    vy = -vy * bounceRest
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                }
                            }
                        } else {
                            // Check collisions with inner edges of the goal post corners
                            val posts = listOf(
                                Offset(200f, 0f), Offset(400f, 0f),
                                Offset(200f, 1000f), Offset(400f, 1000f)
                            )
                            for (post in posts) {
                                val dxPost = px - post.x
                                val dyPost = py - post.y
                                val distPost = sqrt(dxPost * dxPost + dyPost * dyPost)
                                if (distPost < rPuck + 4f) { // Post has virtual thickness of 4f
                                    val nx = dxPost / distPost
                                    val ny = dyPost / distPost
                                    // Move outside post
                                    px = post.x + nx * (rPuck + 4f)
                                    py = post.y + ny * (rPuck + 4f)
                                    // Reflect velocity
                                    val dot = vx * nx + vy * ny
                                    if (dot < 0f) {
                                        vx = (vx - 2 * dot * nx) * bounceRest
                                        vy = (vy - 2 * dot * ny) * bounceRest
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    }
                                }
                            }
                        }

                        // 5. Puck-Striker Collisions
                        // Striker 1 (Bottom)
                        val dx1 = px - x1
                        val dy1 = py - y1
                        val dist1 = sqrt(dx1 * dx1 + dy1 * dy1)
                        if (dist1 < rStriker + rPuck) {
                            val nx = dx1 / dist1
                            val ny = dy1 / dist1
                            // Separate the puck
                            var newPx = x1 + nx * (rStriker + rPuck)
                            var newPy = y1 + ny * (rStriker + rPuck)

                            // Clamp puck to board boundaries, and if it exceeds, push the striker back!
                            // Left/Right walls
                            if (newPx - rPuck < 0f) {
                                newPx = rPuck
                                x1 = newPx - nx * (rStriker + rPuck)
                            } else if (newPx + rPuck > virtualWidth) {
                                newPx = virtualWidth - rPuck
                                x1 = newPx - nx * (rStriker + rPuck)
                            }
                            
                            // Top/Bottom walls (excluding goal area)
                            val goalRange = 200f..400f
                            if (newPx < goalRange.start || newPx > goalRange.endInclusive) {
                                if (newPy - rPuck < 0f) {
                                    newPy = rPuck
                                    y1 = newPy - ny * (rStriker + rPuck)
                                } else if (newPy + rPuck > virtualHeight) {
                                    newPy = virtualHeight - rPuck
                                    y1 = newPy - ny * (rStriker + rPuck)
                                }
                            }
                            px = newPx
                            py = newPy

                            // Physics of hit
                            val rvx = vx - s1vx
                            val rvy = vy - s1vy
                            val dot = rvx * nx + rvy * ny
                            if (dot < -120f) {
                                val hitRest = 0.9f
                                vx = vx - (1f + hitRest) * dot * nx
                                vy = vy - (1f + hitRest) * dot * ny
                                
                                // Add velocity boost from striker motion
                                vx += s1vx * 0.4f
                                vy += s1vy * 0.4f
                                
                                // Clamp extreme velocity to prevent escaping boundaries
                                val maxV = 2200f
                                val currentV = sqrt(vx * vx + vy * vy)
                                if (currentV > maxV) {
                                    vx = (vx / currentV) * maxV
                                    vy = (vy / currentV) * maxV
                                }

                                // Visual Spark hits
                                val contactX = x1 + nx * rStriker
                                val contactY = y1 + ny * rStriker
                                for (i in 0 until 4) {
                                    particles.add(
                                        AirHockeyParticle(
                                            x = contactX,
                                            y = contactY,
                                            vx = (nx * 300f + (Math.random() - 0.5f) * 250f).toFloat(),
                                            vy = (ny * 300f + (Math.random() - 0.5f) * 250f).toFloat(),
                                            color = primaryColor,
                                            size = (3 + Math.random() * 5).toFloat(),
                                            alpha = 1f,
                                            lifeDecay = (1.5f + Math.random() * 2f).toFloat()
                                        )
                                    )
                                }
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                        }

                        // Striker 2 (Top)
                        val dx2 = px - x2
                        val dy2 = py - y2
                        val dist2 = sqrt(dx2 * dx2 + dy2 * dy2)
                        if (dist2 < rStriker + rPuck) {
                            val nx = dx2 / dist2
                            val ny = dy2 / dist2
                            // Separate the puck
                            var newPx = x2 + nx * (rStriker + rPuck)
                            var newPy = y2 + ny * (rStriker + rPuck)

                            // Clamp puck to board boundaries, and if it exceeds, push the striker back!
                            // Left/Right walls
                            if (newPx - rPuck < 0f) {
                                newPx = rPuck
                                x2 = newPx - nx * (rStriker + rPuck)
                            } else if (newPx + rPuck > virtualWidth) {
                                newPx = virtualWidth - rPuck
                                x2 = newPx - nx * (rStriker + rPuck)
                            }
                            
                            // Top/Bottom walls (excluding goal area)
                            val goalRange = 200f..400f
                            if (newPx < goalRange.start || newPx > goalRange.endInclusive) {
                                if (newPy - rPuck < 0f) {
                                    newPy = rPuck
                                    y2 = newPy - ny * (rStriker + rPuck)
                                } else if (newPy + rPuck > virtualHeight) {
                                    newPy = virtualHeight - rPuck
                                    y2 = newPy - ny * (rStriker + rPuck)
                                }
                            }
                            px = newPx
                            py = newPy

                            // Physics of hit
                            val rvx = vx - s2vx
                            val rvy = vy - s2vy
                            val dot = rvx * nx + rvy * ny
                            if (dot < -120f) {
                                val hitRest = 0.9f
                                vx = vx - (1f + hitRest) * dot * nx
                                vy = vy - (1f + hitRest) * dot * ny
                                
                                vx += s2vx * 0.4f
                                vy += s2vy * 0.4f

                                val maxV = 2200f
                                val currentV = sqrt(vx * vx + vy * vy)
                                if (currentV > maxV) {
                                    vx = (vx / currentV) * maxV
                                    vy = (vy / currentV) * maxV
                                }

                                // Visual Spark hits
                                val contactX = x2 + nx * rStriker
                                val contactY = y2 + ny * rStriker
                                for (i in 0 until 4) {
                                    particles.add(
                                        AirHockeyParticle(
                                            x = contactX,
                                            y = contactY,
                                            vx = (nx * 300f + (Math.random() - 0.5f) * 250f).toFloat(),
                                            vy = (ny * 300f + (Math.random() - 0.5f) * 250f).toFloat(),
                                            color = tertiaryColor,
                                            size = (3 + Math.random() * 5).toFloat(),
                                            alpha = 1f,
                                            lifeDecay = (1.5f + Math.random() * 2f).toFloat()
                                        )
                                    )
                                }
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                        }
                    }

                    // 6. Update visual particles status
                    val iterator = particles.iterator()
                    while (iterator.hasNext()) {
                        val p = iterator.next()
                        p.x += p.vx * elapsedSec
                        p.y += p.vy * elapsedSec
                        p.alpha -= p.lifeDecay * elapsedSec
                        if (p.alpha <= 0f) {
                            iterator.remove()
                        }
                    }
                    if (particles.isNotEmpty()) {
                        particleUpdateTrigger++
                    }
                }
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

    if (!isPlaying) {
        // --- Setup Dashboard Screen ---
        if (isWideScreen) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "エアホッケー",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.error,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "BETA",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = "マレットを操作してパックを相手ゴールに入れましょう。超高速な対戦がスマホで楽しめます！",
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
                        Text("対戦を開始する", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1.2f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    AirHockeyConfigCard(
                        cardColor = cardColor,
                        borderStroke = borderStroke,
                        gameMode = gameMode,
                        onGameModeChanged = { gameMode = it },
                        difficulty = difficulty,
                        onDifficultyChanged = { difficulty = it }
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text(
                        text = "エアホッケー",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.error,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "BETA",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text = "マレットを操作してパックを相手ゴールに入れましょう。超高速な対戦がスマホで楽しめます！",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                AirHockeyConfigCard(
                    cardColor = cardColor,
                    borderStroke = borderStroke,
                    gameMode = gameMode,
                    onGameModeChanged = { gameMode = it },
                    difficulty = difficulty,
                    onDifficultyChanged = { difficulty = it }
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
                    Text("対戦を開始する", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    } else {
        // --- Active Game Screen ---
        if (isWideScreen) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Pane: Info, Score, and general controls
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AirHockeyScoreCard(
                        cardColor = cardColor,
                        borderStroke = borderStroke,
                        score1 = score1,
                        score2 = score2,
                        gameMode = gameMode,
                        isPaused = isPaused,
                        onPauseToggle = { isPaused = !isPaused }
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
                }

                // Right Pane: Active Canvas
                Box(
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    AirHockeyCanvasBoard(
                        px = px, py = py,
                        x1 = x1, y1 = y1,
                        x2 = x2, y2 = y2,
                        rPuck = rPuck, rStriker = rStriker,
                        particles = particles,
                        particleUpdateTrigger = particleUpdateTrigger,
                        goalText = goalText,
                        score1 = score1, score2 = score2,
                        gameMode = gameMode,
                        isPaused = isPaused,
                        isGameOver = isGameOver,
                        winner = winner,
                        onRestart = { startNewGame() },
                        onGloballyPositioned = { w, h ->
                            canvasWidth = w
                            canvasHeight = h
                        },
                        onPointerInput = { virtualTouchX, virtualTouchY, pointerId, isDown, previousPressed ->
                            if (!isDown) {
                                if (pointerId == activePointerId1) {
                                    activePointerId1 = null
                                } else if (pointerId == activePointerId2) {
                                    activePointerId2 = null
                                }
                            } else {
                                if (previousPressed) {
                                    if (pointerId == activePointerId1) {
                                        targetX1 = virtualTouchX
                                        targetY1 = virtualTouchY
                                    } else if (pointerId == activePointerId2) {
                                        targetX2 = virtualTouchX
                                        targetY2 = virtualTouchY
                                    }
                                } else {
                                    if (virtualTouchY > 500f) {
                                        if (activePointerId1 == null) {
                                            activePointerId1 = pointerId
                                            targetX1 = virtualTouchX
                                            targetY1 = virtualTouchY
                                        }
                                    } else if (gameMode == AirHockeyMode.VS_HUMAN) {
                                        if (activePointerId2 == null) {
                                            activePointerId2 = pointerId
                                            targetX2 = virtualTouchX
                                            targetY2 = virtualTouchY
                                        }
                                    }
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
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AirHockeyScoreCard(
                    cardColor = cardColor,
                    borderStroke = borderStroke,
                    score1 = score1,
                    score2 = score2,
                    gameMode = gameMode,
                    isPaused = isPaused,
                    onPauseToggle = { isPaused = !isPaused }
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    AirHockeyCanvasBoard(
                        px = px, py = py,
                        x1 = x1, y1 = y1,
                        x2 = x2, y2 = y2,
                        rPuck = rPuck, rStriker = rStriker,
                        particles = particles,
                        particleUpdateTrigger = particleUpdateTrigger,
                        goalText = goalText,
                        score1 = score1, score2 = score2,
                        gameMode = gameMode,
                        isPaused = isPaused,
                        isGameOver = isGameOver,
                        winner = winner,
                        onRestart = { startNewGame() },
                        onGloballyPositioned = { w, h ->
                            canvasWidth = w
                            canvasHeight = h
                        },
                        onPointerInput = { virtualTouchX, virtualTouchY, pointerId, isDown, previousPressed ->
                            if (!isDown) {
                                if (pointerId == activePointerId1) {
                                    activePointerId1 = null
                                } else if (pointerId == activePointerId2) {
                                    activePointerId2 = null
                                }
                            } else {
                                if (previousPressed) {
                                    if (pointerId == activePointerId1) {
                                        targetX1 = virtualTouchX
                                        targetY1 = virtualTouchY
                                    } else if (pointerId == activePointerId2) {
                                        targetX2 = virtualTouchX
                                        targetY2 = virtualTouchY
                                    }
                                } else {
                                    if (virtualTouchY > 500f) {
                                        if (activePointerId1 == null) {
                                            activePointerId1 = pointerId
                                            targetX1 = virtualTouchX
                                            targetY1 = virtualTouchY
                                        }
                                    } else if (gameMode == AirHockeyMode.VS_HUMAN) {
                                        if (activePointerId2 == null) {
                                            activePointerId2 = pointerId
                                            targetX2 = virtualTouchX
                                            targetY2 = virtualTouchY
                                        }
                                    }
                                }
                            }
                        }
                    )
                }

                // Small quick action bar on smartphone
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = { showResignConfirmDialog = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("終了")
                    }

                    Button(
                        onClick = { showResetConfirmDialog = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("リセット")
                    }
                }
            }
        }
    }

    // Dialog confirmations
    if (showResignConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResignConfirmDialog = false },
            title = { Text("対戦終了") },
            text = { Text("対戦を終了して、設定画面に戻りますか？") },
            confirmButton = {
                Button(
                    onClick = {
                        showResignConfirmDialog = false
                        onPlayingStateChanged(false)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
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
            title = { Text("ゲームリセットの確認") },
            text = { Text("現在のゲームスコアと配置をリセットし、最初からやり直しますか？") },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirmDialog = false
                        startNewGame()
                    }
                ) {
                    Text("リセット")
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

@Composable
private fun AirHockeyCanvasBoard(
    px: Float, py: Float,
    x1: Float, y1: Float,
    x2: Float, y2: Float,
    rPuck: Float, rStriker: Float,
    particles: List<AirHockeyParticle>,
    particleUpdateTrigger: Int,
    goalText: String?,
    score1: Int, score2: Int,
    gameMode: AirHockeyMode,
    isPaused: Boolean,
    isGameOver: Boolean,
    winner: Int,
    onRestart: () -> Unit,
    onGloballyPositioned: (Float, Float) -> Unit,
    onPointerInput: (Float, Float, PointerId, Boolean, Boolean) -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val outlineColor = MaterialTheme.colorScheme.outline
    val errorColor = MaterialTheme.colorScheme.error
    val primaryContainerColor = MaterialTheme.colorScheme.primaryContainer
    val tertiaryContainerColor = MaterialTheme.colorScheme.tertiaryContainer
    val onPrimaryContainerColor = MaterialTheme.colorScheme.onPrimaryContainer
    val onTertiaryContainerColor = MaterialTheme.colorScheme.onTertiaryContainer
    val onErrorColor = MaterialTheme.colorScheme.onError


    Box(
        modifier = Modifier
            .fillMaxSize()
            .aspectRatio(0.6f, matchHeightConstraintsFirst = true)
            .shadow(12.dp, shape = RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(2.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .onGloballyPositioned { coordinates ->
                onGloballyPositioned(
                    coordinates.size.width.toFloat(),
                    coordinates.size.height.toFloat()
                )
            }
            .pointerInput(gameMode, isGameOver, isPaused) {
                if (isGameOver || isPaused) return@pointerInput
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { change ->
                            val touchX = (change.position.x / size.width.toFloat()) * 600f
                            val touchY = (change.position.y / size.height.toFloat()) * 1000f
                            onPointerInput(
                                touchX,
                                touchY,
                                change.id,
                                change.pressed,
                                change.previousPressed
                            )
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val _trigger = particleUpdateTrigger
            val scaleX = size.width / 600f
            val scaleY = size.height / 1000f

            // 1. Draw table guidelines & lines (Cyberpunk neon theme)
            // Draw center circle
            drawGlowingCircle(
                color = outlineColor,
                center = Offset(300f * scaleX, 500f * scaleY),
                radius = 80f * scaleX,
                strokeWidth = 2.dp.toPx()
            )
            // Draw center line
            drawGlowingLine(
                color = outlineColor,
                start = Offset(0f, 500f * scaleY),
                end = Offset(size.width, 500f * scaleY),
                width = 2.dp.toPx()
            )

            // Draw goal crease semi-circles
            drawGlowingCircle(
                color = tertiaryColor,
                center = Offset(300f * scaleX, 0f),
                radius = 120f * scaleX,
                strokeWidth = 2.dp.toPx()
            )
            drawGlowingCircle(
                color = primaryColor,
                center = Offset(300f * scaleX, 1000f * scaleY),
                radius = 120f * scaleX,
                strokeWidth = 2.dp.toPx()
            )

            // Draw side borders (Neon tubes)
            // Top half borders (Red/Magenta)
            drawGlowingLine(
                color = tertiaryColor,
                start = Offset(0f, 0f),
                end = Offset(0f, 500f * scaleY),
                width = 3.dp.toPx()
            )
            drawGlowingLine(
                color = tertiaryColor,
                start = Offset(size.width, 0f),
                end = Offset(size.width, 500f * scaleY),
                width = 3.dp.toPx()
            )
            drawGlowingLine(
                color = tertiaryColor,
                start = Offset(0f, 0f),
                end = Offset(200f * scaleX, 0f),
                width = 4.dp.toPx()
            )
            drawGlowingLine(
                color = tertiaryColor,
                start = Offset(400f * scaleX, 0f),
                end = Offset(size.width, 0f),
                width = 4.dp.toPx()
            )

            // Bottom half borders (Cyan)
            drawGlowingLine(
                color = primaryColor,
                start = Offset(0f, 500f * scaleY),
                end = Offset(0f, size.height),
                width = 3.dp.toPx()
            )
            drawGlowingLine(
                color = primaryColor,
                start = Offset(size.width, 500f * scaleY),
                end = Offset(size.width, size.height),
                width = 3.dp.toPx()
            )
            drawGlowingLine(
                color = primaryColor,
                start = Offset(0f, size.height),
                end = Offset(200f * scaleX, size.height),
                width = 4.dp.toPx()
            )
            drawGlowingLine(
                color = primaryColor,
                start = Offset(400f * scaleX, size.height),
                end = Offset(size.width, size.height),
                width = 4.dp.toPx()
            )

            // Draw Goal posts representation
            drawGlowingCircle(outlineColor, Offset(200f * scaleX, 0f), 5f * scaleX, alpha = 0.5f)
            drawGlowingCircle(outlineColor, Offset(400f * scaleX, 0f), 5f * scaleX, alpha = 0.5f)
            drawGlowingCircle(outlineColor, Offset(200f * scaleX, 1000f * scaleY), 5f * scaleX, alpha = 0.5f)
            drawGlowingCircle(outlineColor, Offset(400f * scaleX, 1000f * scaleY), 5f * scaleX, alpha = 0.5f)

            // 2. Draw active particles
            for (p in particles) {
                drawCircle(
                    color = p.color,
                    center = Offset(p.x * scaleX, p.y * scaleY),
                    radius = p.size * scaleX,
                    alpha = p.alpha.coerceIn(0f, 1f)
                )
            }

            // 3. Draw Strikers
            // Striker 2 (Top - Red)
            drawGlowingCircle(
                color = tertiaryColor,
                center = Offset(x2 * scaleX, y2 * scaleY),
                radius = rStriker * scaleX
            )
            // Metallic center core of striker 2
            drawCircle(
                color = tertiaryContainerColor,
                center = Offset(x2 * scaleX, y2 * scaleY),
                radius = (rStriker - 8f) * scaleX
            )
            drawCircle(
                color = onTertiaryContainerColor,
                center = Offset(x2 * scaleX, y2 * scaleY),
                radius = 5f * scaleX,
                alpha = 0.5f
            )

            // Striker 1 (Bottom - Blue)
            drawGlowingCircle(
                color = primaryColor,
                center = Offset(x1 * scaleX, y1 * scaleY),
                radius = rStriker * scaleX
            )
            // Metallic center core of striker 1
            drawCircle(
                color = primaryContainerColor,
                center = Offset(x1 * scaleX, y1 * scaleY),
                radius = (rStriker - 8f) * scaleX
            )
            drawCircle(
                color = onPrimaryContainerColor,
                center = Offset(x1 * scaleX, y1 * scaleY),
                radius = 5f * scaleX,
                alpha = 0.5f
            )

            // 4. Draw Puck (Glowing Orange neon disc)
            drawGlowingCircle(
                color = errorColor,
                center = Offset(px * scaleX, py * scaleY),
                radius = rPuck * scaleX
            )
            drawCircle(
                color = onErrorColor,
                center = Offset(px * scaleX, py * scaleY),
                radius = (rPuck - 6f) * scaleX,
                style = Stroke(2.dp.toPx())
            )
        }

        // Overlay: Goal notifications
        goalText?.let { text ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .border(1.dp, primaryColor, RoundedCornerShape(8.dp))
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(
                    text = text,
                    color = primaryColor,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Overlay: Paused
        if (isPaused) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "PAUSED",
                    color = Color.White,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 4.sp
                )
            }
        }

        // Overlay: Game Over Screen
        if (isGameOver) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.82f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Text(
                        text = "GAME OVER",
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )

                    val winMsg = if (winner == 1) "下 (プレイヤー/P1) の勝利！" else "上 (CPU/P2) の勝利！"
                    val winColor = if (winner == 1) primaryColor else tertiaryColor
                    Text(
                        text = winMsg,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = winColor
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = onRestart,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("もう一度プレイ", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}
