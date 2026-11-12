package com.webclass.plus.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoControls(
    modifier: Modifier = Modifier,
    isVisible: Boolean,
    isPlaying: Boolean,
    playbackState: Int, // ExoPlayer.STATE_...
    currentPosition: Long,
    duration: Long,
    bufferedPercentage: Int,
    title: String,
    isFullScreen: Boolean,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onForward: () -> Unit,
    onRewind: () -> Unit,
    onFullScreenToggle: () -> Unit,
    onClose: () -> Unit,
    playbackSpeed: Float = 1.0f,
    onPlaybackSpeedChange: (Float) -> Unit = {},
    onDownload: () -> Unit = {}
) {
    val durationText = remember(duration) { formatTime(duration) }
    val positionText = remember(currentPosition) { formatTime(currentPosition) }
    
    // Animate visibility of the controls
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.7f),
                            Color.Transparent,
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.7f)
                        )
                    )
                )
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "閉じる",
                        tint = Color.White
                    )
                }
            }

            // Center Controls
            Row(
                modifier = Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(48.dp)
            ) {
                // Rewind
                IconButton(
                    onClick = onRewind,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FastRewind,
                        contentDescription = "Rewind 10s",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Play/Pause/Replay
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(72.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f), MaterialTheme.shapes.extraLarge)
                        .clickable { onPlayPause() }
                ) {
                    val icon = when {
                        playbackState == 4 -> Icons.Rounded.Replay // STATE_ENDED
                        isPlaying -> Icons.Rounded.Pause
                        else -> Icons.Rounded.PlayArrow
                    }
                    
                    Icon(
                        imageVector = icon,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(40.dp)
                    )
                }

                // Forward
                IconButton(
                    onClick = onForward,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FastForward,
                        contentDescription = "Forward 10s",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            // Bottom Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                // Slider Logic
                var sliderPosition by remember(currentPosition) { mutableFloatStateOf(currentPosition.toFloat()) }
                var isDragging by remember { mutableStateOf(false) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isDragging) formatTime(sliderPosition.toLong()) else positionText,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                    
                    Text(
                        text = durationText,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Slider(
                        value = if (isDragging) sliderPosition else currentPosition.toFloat(),
                        onValueChange = { 
                            isDragging = true
                            sliderPosition = it
                        },
                        onValueChangeFinished = {
                            isDragging = false
                            onSeek(sliderPosition.toLong())
                        },
                        valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    
                    // Playback Speed Button
                    var showSpeedMenu by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.wrapContentSize(Alignment.TopStart)) {
                        TextButton(
                            onClick = { showSpeedMenu = true }
                        ) {
                            Text(
                                text = "${if (playbackSpeed == 1.0f) "1.0" else playbackSpeed.toString()}x",
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        DropdownMenu(
                            expanded = showSpeedMenu,
                            onDismissRequest = { showSpeedMenu = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer)
                        ) {
                            val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
                            speeds.forEach { speed ->
                                DropdownMenuItem(
                                    text = { 
                                        Text(
                                            text = if (speed == 1.0f) "1.0x (標準)" else "${speed}x",
                                            style = MaterialTheme.typography.bodyMedium
                                        ) 
                                    },
                                    onClick = {
                                        onPlaybackSpeedChange(speed)
                                        showSpeedMenu = false
                                    }
                                )
                            }
                        }
                    }
                    
                    // Download Button
                    IconButton(onClick = onDownload) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "ダウンロード",
                            tint = Color.White
                        )
                    }
                    
                    IconButton(onClick = onFullScreenToggle) {
                        Icon(
                            imageVector = if (isFullScreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                            contentDescription = "Toggle Fullscreen",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    if (millis < 0) return "00:00"
    
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60

    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
