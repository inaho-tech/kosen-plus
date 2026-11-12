package com.webclass.plus.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

fun Modifier.shimmerEffect(): Modifier = composed {
    val context = androidx.compose.ui.platform.LocalContext.current
    val themePreferences = remember { com.webclass.plus.util.ThemePreferences(context) }
    val enableLightweightMode by themePreferences.enableLightweightModeFlow.collectAsState(initial = false)

    val baseColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)

    if (enableLightweightMode) {
        this.background(baseColor)
    } else {
        val shimmerColors = listOf(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        )

        val transition = rememberInfiniteTransition(label = "shimmer_transition")
        val translateAnim = transition.animateFloat(
            initialValue = 0f,
            targetValue = 1000f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 1200,
                    easing = FastOutSlowInEasing
                ),
                repeatMode = RepeatMode.Restart
            ),
            label = "shimmer_animation"
        )

        this.background(
            brush = Brush.linearGradient(
                colors = shimmerColors,
                start = Offset.Zero,
                end = Offset(x = translateAnim.value, y = translateAnim.value)
            )
        )
    }
}
