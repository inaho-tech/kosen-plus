package com.webclass.plus.ui.screens

import android.content.Context
import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.random.Random

private enum class ReactionPhase { IDLE, WAITING, READY, RESULT, TOO_EARLY }

@Composable
fun ReactionTestScreen(isWideScreen: Boolean) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("reaction_test_prefs", Context.MODE_PRIVATE) }
    var phase by rememberSaveable { mutableStateOf(ReactionPhase.IDLE) }
    var round by rememberSaveable { mutableIntStateOf(0) }
    var readyAt by remember { mutableLongStateOf(0L) }
    var resultMs by rememberSaveable { mutableLongStateOf(preferences.getLong("last_ms", 0L)) }
    var bestMs by rememberSaveable { mutableLongStateOf(preferences.getLong("best_ms", Long.MAX_VALUE)) }
    var attempts by rememberSaveable { mutableIntStateOf(preferences.getInt("attempts", 0)) }

    LaunchedEffect(round, phase) {
        if (phase == ReactionPhase.WAITING) {
            delay(Random.nextLong(1_500L, 4_000L))
            if (phase == ReactionPhase.WAITING) {
                readyAt = SystemClock.elapsedRealtime()
                phase = ReactionPhase.READY
            }
        }
    }

    fun start() {
        round += 1
        phase = ReactionPhase.WAITING
    }

    fun recordResult(milliseconds: Long) {
        resultMs = milliseconds
        attempts += 1
        if (milliseconds < bestMs) bestMs = milliseconds
        preferences.edit()
            .putLong("last_ms", resultMs)
            .putLong("best_ms", bestMs)
            .putInt("attempts", attempts)
            .apply()
        phase = ReactionPhase.RESULT
    }

    val testColor = when (phase) {
        ReactionPhase.READY -> Color(0xFF1B8F4D)
        ReactionPhase.TOO_EARLY -> MaterialTheme.colorScheme.errorContainer
        ReactionPhase.WAITING -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val testContentColor = when (phase) {
        ReactionPhase.READY -> Color.White
        ReactionPhase.TOO_EARLY -> MaterialTheme.colorScheme.onErrorContainer
        ReactionPhase.WAITING -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val title = when (phase) {
        ReactionPhase.IDLE -> "準備はできましたか？"
        ReactionPhase.WAITING -> "緑色になるまで待つ"
        ReactionPhase.READY -> "タップ！"
        ReactionPhase.RESULT -> "${resultMs} ms"
        ReactionPhase.TOO_EARLY -> "フライングです"
    }
    val description = when (phase) {
        ReactionPhase.IDLE -> "下のボタンから計測を始めてください"
        ReactionPhase.WAITING -> "今はまだタップしないでください"
        ReactionPhase.READY -> "できるだけ早く画面をタップ"
        ReactionPhase.RESULT -> when {
            resultMs < 200 -> "驚異的な反応速度です"
            resultMs < 260 -> "とても速いです"
            resultMs < 350 -> "良い反応速度です"
            else -> "繰り返すと記録を伸ばせます"
        }
        ReactionPhase.TOO_EARLY -> "緑色になる前にタップしました"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (isWideScreen) 48.dp else 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.primary,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Info, contentDescription = null) }
                }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("遊び方", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "画面が緑色に変わった瞬間にタップして、反応速度を測定します。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReactionStatCard("自己ベスト", if (bestMs == Long.MAX_VALUE) "—" else "$bestMs ms", Modifier.weight(1f))
            ReactionStatCard("前回", if (resultMs == 0L) "—" else "$resultMs ms", Modifier.weight(1f))
            ReactionStatCard("挑戦回数", "$attempts 回", Modifier.weight(1f))
        }

        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 280.dp)
                .clickable(enabled = phase == ReactionPhase.WAITING || phase == ReactionPhase.READY) {
                    when (phase) {
                        ReactionPhase.WAITING -> phase = ReactionPhase.TOO_EARLY
                        ReactionPhase.READY -> recordResult(SystemClock.elapsedRealtime() - readyAt)
                        else -> Unit
                    }
                },
            colors = CardDefaults.elevatedCardColors(containerColor = testColor, contentColor = testContentColor),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(min = 280.dp).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(58.dp))
                Spacer(Modifier.height(14.dp))
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(description, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                if (phase == ReactionPhase.WAITING) {
                    Spacer(Modifier.height(22.dp))
                    LinearProgressIndicator(modifier = Modifier.width(160.dp))
                }
            }
        }

        if (phase != ReactionPhase.WAITING && phase != ReactionPhase.READY) {
            Button(onClick = { start() }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(if (phase == ReactionPhase.IDLE) Icons.Default.Bolt else Icons.Outlined.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (phase == ReactionPhase.IDLE) "計測を開始" else "もう一度測る", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ReactionStatCard(label: String, value: String, modifier: Modifier = Modifier) {
    OutlinedCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1)
        }
    }
}
