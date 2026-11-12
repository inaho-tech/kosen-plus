package com.webclass.plus.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    enableHyperBootAnimation: Boolean = false,
    enableLightweightMode: Boolean = false,
    onAnimationFinished: () -> Unit
) {
    if (enableLightweightMode) {
        LightweightSplashScreen(onAnimationFinished = onAnimationFinished)
    } else if (enableHyperBootAnimation) {
        HyperSplashScreen(onAnimationFinished = onAnimationFinished)
    } else {
        StandardSplashScreen(onAnimationFinished = onAnimationFinished)
    }
}

private data class ParticleConfig(
    val angle: Float,
    val speedFactor: Float,
    val baseRadius: Float,
    val size: Float,
    val delay: Float,
    val isSparkle: Boolean,
    val color: Color
)

@Composable
fun HyperSplashScreen(
    onAnimationFinished: () -> Unit
) {
    var startAnimation by remember { mutableStateOf(false) }
    var startPhaseExit by remember { mutableStateOf(false) }

    // Theme color mappings to make the boot animation feel unified
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val errorColor = MaterialTheme.colorScheme.error
    val surfaceColor = MaterialTheme.colorScheme.surface
    val surfaceLow = MaterialTheme.colorScheme.surfaceContainerLow
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    // Overall transition progress (0f to 1f)
    val transitionProgress by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 2000, easing = LinearEasing),
        label = "transitionProgress"
    )

    // 1. Neon aurora glow background oscillation
    val infiniteTransition = rememberInfiniteTransition(label = "neonGlow")
    val glowPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "glowPhase"
    )

    // 2. Logo 'K' 3D spin & cubic overshoot scale
    val kScale by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 1000, easing = EaseOutBack),
        label = "kScale"
    )
    val kAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 700, easing = EaseOutQuad),
        label = "kAlpha"
    )
    val kRotationY by animateFloatAsState(
        targetValue = if (startAnimation) 0f else -180f,
        animationSpec = tween(durationMillis = 1200, easing = EaseOutCubic),
        label = "kRotationY"
    )
    val kRotationZ by animateFloatAsState(
        targetValue = if (startAnimation) 0f else -90f,
        animationSpec = tween(durationMillis = 1200, easing = EaseOutBack),
        label = "kRotationZ"
    )

    // 3. Logo '+' Comet-like slide-in & dock
    val plusScale by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 850, delayMillis = 350, easing = EaseOutBack),
        label = "plusScale"
    )
    val plusAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 650, delayMillis = 350, easing = EaseOutQuad),
        label = "plusAlpha"
    )
    val plusTranslateX by animateFloatAsState(
        targetValue = if (startAnimation) 0f else 60f,
        animationSpec = tween(durationMillis = 950, delayMillis = 350, easing = EaseOutQuart),
        label = "plusTranslateX"
    )
    val plusTranslateY by animateFloatAsState(
        targetValue = if (startAnimation) 0f else -60f,
        animationSpec = tween(durationMillis = 950, delayMillis = 350, easing = EaseOutQuart),
        label = "plusTranslateY"
    )
    val plusRotation by animateFloatAsState(
        targetValue = if (startAnimation) 0f else 270f,
        animationSpec = tween(durationMillis = 950, delayMillis = 350, easing = EaseOutBack),
        label = "plusRotation"
    )

    // 4. Logo impact recoil bounce on docking
    var logoImpactTarget by remember { mutableStateOf(1.0f) }
    val logoImpactScale by animateFloatAsState(
        targetValue = logoImpactTarget,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 250f),
        label = "logoImpactScale"
    )

    LaunchedEffect(startAnimation) {
        if (startAnimation) {
            delay(600) // approx transitionProgress >= 0.3f
            logoImpactTarget = 1.25f
            delay(200) // approx transitionProgress >= 0.4f
            logoImpactTarget = 1.0f
        }
    }

    // 5. Title text reveal & neon flicker
    val textAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 800, delayMillis = 800, easing = EaseOutCubic),
        label = "textAlpha"
    )
    val textScale by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.85f,
        animationSpec = tween(durationMillis = 850, delayMillis = 800, easing = EaseOutBack),
        label = "textScale"
    )

    // Random neon flicker alpha
    val textGlitchAlpha by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(120, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "textGlitchAlpha"
    )

    // 6. Cybernetic exit warp zoom
    val exitAlpha by animateFloatAsState(
        targetValue = if (startPhaseExit) 0f else 1f,
        animationSpec = tween(durationMillis = 450, easing = EaseInBack),
        label = "exitAlpha"
    )
    val exitScale by animateFloatAsState(
        targetValue = if (startPhaseExit) 2.2f else 1f,
        animationSpec = tween(durationMillis = 450, easing = EaseInCubic),
        label = "exitScale"
    )

    LaunchedEffect(Unit) {
        delay(100)
        startAnimation = true
        
        // Exquisite animation showcase duration
        delay(2200)
        
        startPhaseExit = true
        delay(400)
        onAnimationFinished()
    }

    // High fidelity particles setup (Optimized particle count to 26, matching theme primary/tertiary colors)
    val particles = remember(primaryColor, tertiaryColor) {
        List(26) { i ->
            val angle = (i * (360f / 26f)) * (Math.PI / 180f)
            val speedFactor = 0.6f + (i % 4) * 0.25f
            val baseRadius = 180f + (i % 3) * 60f
            val size = 3f + (i % 3) * 2f
            val delayVal = (i % 5) * 0.05f
            
            val isSparkle = i % 3 == 0
            val color = if (i % 2 == 0) primaryColor else tertiaryColor
            
            ParticleConfig(
                angle = angle.toFloat(),
                speedFactor = speedFactor,
                baseRadius = baseRadius,
                size = size,
                delay = delayVal,
                isSparkle = isSparkle,
                color = color
            )
        }
    }

    val kPath = remember {
        Path().apply {
            moveTo(3f, 3f)
            lineTo(3f, 21f)
            lineTo(7.5f, 21f)
            lineTo(7.5f, 13.5f)
            lineTo(14.5f, 21f)
            lineTo(20.5f, 21f)
            lineTo(11.5f, 11.5f)
            lineTo(20.5f, 3f)
            lineTo(14.5f, 3f)
            lineTo(7.5f, 10f)
            lineTo(7.5f, 3f)
            close()
        }
    }

    val plusPath = remember {
        Path().apply {
            moveTo(19f, 3f)
            lineTo(19f, 7f)
            lineTo(15f, 7f)
            lineTo(15f, 10f)
            lineTo(19f, 10f)
            lineTo(19f, 14f)
            lineTo(22f, 14f)
            lineTo(22f, 10f)
            lineTo(26f, 10f)
            lineTo(26f, 7f)
            lineTo(22f, 7f)
            lineTo(22f, 3f)
            close()
        }
    }

    // Cache gradient brushes aligned to selected theme (Avoid allocations inside onDraw frames)
    val backgroundBrush = remember(surfaceColor, surfaceLow) {
        Brush.radialGradient(
            colors = listOf(
                surfaceColor,
                surfaceLow
            )
        )
    }
    val neonCyanGlow = remember(primaryColor) {
        Brush.radialGradient(
            colors = listOf(primaryColor.copy(alpha = 0.08f), Color.Transparent)
        )
    }
    val neonPinkGlow = remember(tertiaryColor) {
        Brush.radialGradient(
            colors = listOf(tertiaryColor.copy(alpha = 0.08f), Color.Transparent)
        )
    }
    val logoBloomGlow = remember(primaryColor) {
        Brush.radialGradient(
            colors = listOf(primaryColor.copy(alpha = 0.12f), Color.Transparent)
        )
    }
    val shockwaveBrush = remember(primaryColor, tertiaryColor) {
        Brush.sweepGradient(
            colors = listOf(primaryColor, tertiaryColor, primaryColor)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .graphicsLayer {
                alpha = exitAlpha
                scaleX = exitScale
                scaleY = exitScale
            }
            .drawBehind {
                // Read glowPhase in draw scope to avoid recomposition
                val currentGlowPhase = glowPhase
                val cosVal = kotlin.math.cos(currentGlowPhase * Math.PI / 180f).toFloat()
                val sinVal = kotlin.math.sin(currentGlowPhase * Math.PI / 180f).toFloat()
                
                // Drawing organic floating glows with matrix translate (Zero allocation)
                withTransform({
                    translate(
                        left = 120f * cosVal,
                        top = 120f * sinVal
                    )
                }) {
                    drawRect(brush = neonCyanGlow, size = size)
                }
                withTransform({
                    translate(
                        left = -120f * cosVal,
                        top = -120f * sinVal
                    )
                }) {
                    drawRect(brush = neonPinkGlow, size = size)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Particles canvas rendering spiral implosion and explosion
        androidx.compose.foundation.Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val centerX = size.width / 2f
            val centerY = size.height / 2f
            
            // Pre-calculated densities for maximum performance
            val shockwaveMaxRadius = 320.dp.toPx()
            val strokeMaxWidth = 6.dp.toPx()
            val flareStrokeWidth = 1.5.dp.toPx()

            particles.forEach { p ->
                val adjustedProgress = transitionProgress - p.delay
                if (adjustedProgress > 0f) {
                    var x = centerX
                    var y = centerY
                    var alpha = 0f

                    // Phase 1: Spiral implosion (0.0f - 0.4f)
                    if (adjustedProgress < 0.4f) {
                        val ratio = adjustedProgress / 0.4f
                        val currentRadius = p.baseRadius * (1f - ratio)
                        val currentAngle = p.angle + (3.5f * Math.PI * (1f - ratio)).toFloat()

                        x = centerX + kotlin.math.cos(currentAngle) * currentRadius
                        y = centerY + kotlin.math.sin(currentAngle) * currentRadius
                        alpha = ratio.coerceIn(0f, 1f)
                    }
                    // Phase 2: Supernova explosion (0.4f - 1.0f)
                    else {
                        val ratio = (adjustedProgress - 0.4f) / 0.6f
                        val currentRadius = p.baseRadius * 0.15f + ratio * 450f * p.speedFactor
                        val currentAngle = p.angle

                        x = centerX + kotlin.math.cos(currentAngle) * currentRadius
                        y = centerY + kotlin.math.sin(currentAngle) * currentRadius
                        alpha = (1f - ratio).coerceIn(0f, 1f)
                    }

                    if (alpha > 0f) {
                        drawCircle(
                            color = p.color.copy(alpha = alpha),
                            radius = p.size * (if (adjustedProgress >= 0.4f) 1.6f else 1.0f),
                            center = Offset(x, y)
                        )

                        // Light sparkles crosshair flares
                        if (p.isSparkle && adjustedProgress >= 0.4f) {
                            val glowSize = p.size * 2.8f * (1f - (adjustedProgress - 0.4f) / 0.6f)
                            drawLine(
                                color = p.color.copy(alpha = alpha * 0.6f),
                                start = Offset(x - glowSize, y),
                                end = Offset(x + glowSize, y),
                                strokeWidth = flareStrokeWidth
                            )
                            drawLine(
                                color = p.color.copy(alpha = alpha * 0.6f),
                                start = Offset(x, y - glowSize),
                                end = Offset(x, y + glowSize),
                                strokeWidth = flareStrokeWidth
                            )
                        }
                    }
                }
            }

            // Shockwave blast ripple ring (Allocation-free using pre-calculated translate)
            if (transitionProgress >= 0.4f) {
                val shockwaveRatio = (transitionProgress - 0.4f) / 0.6f
                val radius = shockwaveRatio * shockwaveMaxRadius
                val shockwaveAlpha = (1f - shockwaveRatio * 1.3f).coerceIn(0f, 1f)

                if (shockwaveAlpha > 0f) {
                    withTransform({
                        translate(centerX, centerY)
                    }) {
                        drawCircle(
                            brush = shockwaveBrush,
                            radius = radius,
                            center = Offset.Zero,
                            style = Stroke(
                                width = (strokeMaxWidth * (1f - shockwaveRatio)).coerceIn(1f, 100f),
                                cap = StrokeCap.Round
                            ),
                            alpha = shockwaveAlpha
                        )
                    }
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .graphicsLayer {
                        scaleX = logoImpactScale
                        scaleY = logoImpactScale
                        rotationY = kRotationY
                        rotationZ = kRotationZ
                        cameraDistance = 12f * density
                    }
                    .drawBehind {
                        // Drawing static bloom glow directly (Saves layouts)
                        withTransform({
                            scale(1.3f, 1.3f, pivot = center)
                        }) {
                            drawCircle(
                                brush = logoBloomGlow,
                                radius = size.width / 2f,
                                alpha = kAlpha
                            )
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                // Vector Canvas drawing logo with highly optimized 3-layer neon glow stroke (Saves GPU commands)
                androidx.compose.foundation.Canvas(
                    modifier = Modifier.size(110.dp)
                ) {
                    val scaleFactor = size.width / 24f

                    withTransform({
                        scale(scaleFactor, scaleFactor, pivot = Offset.Zero)
                    }) {
                        // 1. Draw 'K' Shape (Synchronized to Theme primary)
                        withTransform({
                            scale(kScale, kScale, pivot = Offset(12f, 12f))
                        }) {
                            val neonColor = primaryColor
                            // Outer neon bloom stroke
                            drawPath(
                                path = kPath,
                                color = neonColor.copy(alpha = kAlpha * 0.25f),
                                style = Stroke(width = 3.2f, cap = StrokeCap.Round)
                            )
                            // White-hot core fill
                            drawPath(
                                path = kPath,
                                color = Color(0xEFFFFFFF).copy(alpha = kAlpha)
                            )
                            // Precision overlay outline
                            drawPath(
                                path = kPath,
                                color = neonColor.copy(alpha = kAlpha * 0.9f),
                                style = Stroke(width = 0.8f, cap = StrokeCap.Round)
                            )
                        }

                        // 2. Draw '+' Accent (Synchronized to Theme errorColor)
                        withTransform({
                            scale(plusScale, plusScale, pivot = Offset(20.5f, 8.5f))
                            translate(plusTranslateX, plusTranslateY)
                            rotate(plusRotation, pivot = Offset(20.5f, 8.5f))
                        }) {
                            val accentNeon = errorColor
                            // Outer neon bloom stroke
                            drawPath(
                                path = plusPath,
                                color = accentNeon.copy(alpha = plusAlpha * 0.25f),
                                style = Stroke(width = 3.2f, cap = StrokeCap.Round)
                            )
                            // White-hot core fill
                            drawPath(
                                path = plusPath,
                                color = Color(0xEFFFFFFF).copy(alpha = plusAlpha)
                            )
                            // Precision overlay outline
                            drawPath(
                                path = plusPath,
                                color = accentNeon.copy(alpha = plusAlpha * 0.9f),
                                style = Stroke(width = 0.8f, cap = StrokeCap.Round)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Anaglyph neon glitch text (Highly optimized 3-layer overlay layout synchronized to Theme)
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        alpha = textAlpha
                        scaleX = textScale
                        scaleY = textScale
                    },
                contentAlignment = Alignment.Center
            ) {
                // Tertiary shadow (Offset bottom-right)
                Text(
                    text = "Kosen+",
                    style = MaterialTheme.typography.displayMedium,
                    color = tertiaryColor,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .offset(x = 1.8.dp, y = 0.6.dp)
                        .graphicsLayer {
                            alpha = textGlitchAlpha * textAlpha * 0.38f
                        }
                )

                // Primary shadow (Offset top-left)
                Text(
                    text = "Kosen+",
                    style = MaterialTheme.typography.displayMedium,
                    color = primaryColor,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .offset(x = (-1.8).dp, y = (-0.6).dp)
                        .graphicsLayer {
                            alpha = textGlitchAlpha * textAlpha * 0.38f
                        }
                )

                // High intensity solid text core (Uses theme onSurface for perfect readability)
                Text(
                    text = "Kosen+",
                    style = MaterialTheme.typography.displayMedium,
                    color = onSurface,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.graphicsLayer {
                        alpha = textGlitchAlpha * textAlpha
                    }
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Futuristic slow reveal subtitle
            Text(
                text = "高専生活をもっと便利に",
                style = MaterialTheme.typography.bodyMedium,
                color = onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .graphicsLayer {
                        alpha = textAlpha * 0.8f
                        translationY = (1f - textAlpha) * 12f
                    }
            )
        }
    }
}

@Composable
fun StandardSplashScreen(
    onAnimationFinished: () -> Unit
) {
    var startAnimation by remember { mutableStateOf(false) }
    var startPhaseExit by remember { mutableStateOf(false) }

    // Staggered premium transitions using a single animation trigger for absolute reliability
    
    // 1. 'K' Shape: Elegant cubic scale & fade-in
    val kScale by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.85f,
        animationSpec = tween(durationMillis = 1200, easing = EaseOutQuart),
        label = "kScale"
    )
    val kAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 1000, easing = EaseOutCubic),
        label = "kAlpha"
    )

    // 2. '+' Shape: Soft delayed scale, fade, and a very subtle 5dp float/glide in
    val plusScale by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.8f,
        animationSpec = tween(durationMillis = 1000, delayMillis = 200, easing = EaseOutBack),
        label = "plusScale"
    )
    val plusAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 800, delayMillis = 200, easing = EaseOutCubic),
        label = "plusAlpha"
    )
    val plusOffsetX by animateFloatAsState(
        targetValue = if (startAnimation) -2f else 3f,
        animationSpec = tween(durationMillis = 1100, delayMillis = 200, easing = EaseOutQuart),
        label = "plusOffsetX"
    )
    val plusOffsetY by animateFloatAsState(
        targetValue = if (startAnimation) 2f else -3f,
        animationSpec = tween(durationMillis = 1100, delayMillis = 200, easing = EaseOutQuart),
        label = "plusOffsetY"
    )

    // 3. Background soft bloom glow
    val glowScale by animateFloatAsState(
        targetValue = if (startAnimation) 1.4f else 0.6f,
        animationSpec = tween(durationMillis = 1500, delayMillis = 100, easing = EaseOutQuart),
        label = "glowScale"
    )
    val glowAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 0.12f else 0f,
        animationSpec = tween(durationMillis = 1200, delayMillis = 100, easing = EaseOutCubic),
        label = "glowAlpha"
    )

    // 4. Text reveal: Clean delayed float & fade-in
    val textAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 1000, delayMillis = 400, easing = EaseOutCubic),
        label = "textAlpha"
    )
    val textOffsetY by animateFloatAsState(
        targetValue = if (startAnimation) 0f else 12f,
        animationSpec = tween(durationMillis = 1000, delayMillis = 400, easing = EaseOutCubic),
        label = "textOffsetY"
    )

    // 5. Exit Transition: Gentle fade and slow scale out
    val exitAlpha by animateFloatAsState(
        targetValue = if (startPhaseExit) 0f else 1f,
        animationSpec = tween(durationMillis = 450, easing = EaseInOutCubic),
        label = "exitAlpha"
    )
    val exitScale by animateFloatAsState(
        targetValue = if (startPhaseExit) 1.08f else 1f,
        animationSpec = tween(durationMillis = 450, easing = EaseInOutCubic),
        label = "exitScale"
    )

    LaunchedEffect(Unit) {
        delay(150)
        startAnimation = true
        
        // Wait in elegant static state
        delay(1950)
        
        // Seamless exit transition
        startPhaseExit = true
        delay(400)
        onAnimationFinished()
    }

    // Custom Path vectors
    val kPath = remember {
        Path().apply {
            moveTo(3f, 3f)
            lineTo(3f, 21f)
            lineTo(7.5f, 21f)
            lineTo(7.5f, 13.5f)
            lineTo(14.5f, 21f)
            lineTo(20.5f, 21f)
            lineTo(11.5f, 11.5f)
            lineTo(20.5f, 3f)
            lineTo(14.5f, 3f)
            lineTo(7.5f, 10f)
            lineTo(7.5f, 3f)
            close()
        }
    }

    val plusPath = remember {
        Path().apply {
            moveTo(19f, 3f)
            lineTo(19f, 7f)
            lineTo(15f, 7f)
            lineTo(15f, 10f)
            lineTo(19f, 10f)
            lineTo(19f, 14f)
            lineTo(22f, 14f)
            lineTo(22f, 10f)
            lineTo(26f, 10f)
            lineTo(26f, 7f)
            lineTo(22f, 7f)
            lineTo(22f, 3f)
            close()
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val accentColor = MaterialTheme.colorScheme.error

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceContainerLow
                    )
                )
            )
            .graphicsLayer {
                alpha = exitAlpha
                scaleX = exitScale
                scaleY = exitScale
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.size(150.dp),
                contentAlignment = Alignment.Center
            ) {
                // Soft background glow
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(glowScale)
                        .alpha(glowAlpha)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    primaryColor,
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                )

                // Vector Canvas drawing logo with graceful staggering
                androidx.compose.foundation.Canvas(
                    modifier = Modifier.size(110.dp)
                ) {
                    val scaleFactor = size.width / 24f

                    withTransform({
                        scale(scaleFactor, scaleFactor, pivot = Offset.Zero)
                    }) {
                        // 1. Draw 'K' Shape (Soft Fade-in + Scale)
                        withTransform({
                            scale(kScale, kScale, pivot = Offset(12f, 12f))
                        }) {
                            drawPath(
                                path = kPath,
                                color = primaryColor.copy(alpha = kAlpha)
                            )
                        }

                        // 2. Draw '+' Accent (Soft float-in + Scale)
                        withTransform({
                            scale(plusScale, plusScale, pivot = Offset(20.5f, 8.5f))
                            translate(plusOffsetX, plusOffsetY)
                        }) {
                            drawPath(
                                path = plusPath,
                                color = accentColor.copy(alpha = plusAlpha)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // App Name Text
            Text(
                text = "Kosen+",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .alpha(textAlpha)
                    .graphicsLayer {
                        translationY = textOffsetY
                    }
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Subtitle / Tagline
            Text(
                text = "高専生活をもっと便利に",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .alpha(textAlpha * 0.8f)
                    .graphicsLayer {
                        translationY = textOffsetY * 1.5f
                    }
            )
        }
    }
}

@Composable
fun LightweightSplashScreen(
    onAnimationFinished: () -> Unit
) {
    LaunchedEffect(Unit) {
        delay(800)
        onAnimationFinished()
    }

    val kPath = remember {
        Path().apply {
            moveTo(3f, 3f)
            lineTo(3f, 21f)
            lineTo(7.5f, 21f)
            lineTo(7.5f, 13.5f)
            lineTo(14.5f, 21f)
            lineTo(20.5f, 21f)
            lineTo(11.5f, 11.5f)
            lineTo(20.5f, 3f)
            lineTo(14.5f, 3f)
            lineTo(7.5f, 10f)
            lineTo(7.5f, 3f)
            close()
        }
    }

    val plusPath = remember {
        Path().apply {
            moveTo(19f, 3f)
            lineTo(19f, 7f)
            lineTo(15f, 7f)
            lineTo(15f, 10f)
            lineTo(19f, 10f)
            lineTo(19f, 14f)
            lineTo(22f, 14f)
            lineTo(22f, 10f)
            lineTo(26f, 10f)
            lineTo(26f, 7f)
            lineTo(22f, 7f)
            lineTo(22f, 3f)
            close()
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val accentColor = MaterialTheme.colorScheme.error

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceContainerLow
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.size(150.dp),
                contentAlignment = Alignment.Center
            ) {
                // Vector Canvas drawing logo with full opacity and scale, static position
                androidx.compose.foundation.Canvas(
                    modifier = Modifier.size(110.dp)
                ) {
                    val scaleFactor = size.width / 24f

                    withTransform({
                        scale(scaleFactor, scaleFactor, pivot = Offset.Zero)
                    }) {
                        // 1. Draw 'K' Shape
                        drawPath(
                            path = kPath,
                            color = primaryColor
                        )

                        // 2. Draw '+' Accent (at final static offset)
                        withTransform({
                            translate(-2f, 2f)
                        }) {
                            drawPath(
                                path = plusPath,
                                color = accentColor
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // App Name Text
            Text(
                text = "Kosen+",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Subtitle / Tagline
            Text(
                text = "高専生活をもっと便利に",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alpha(0.8f)
            )
        }
    }
}

