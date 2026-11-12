package com.webclass.plus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.webclass.plus.util.KarnaughForm
import com.webclass.plus.util.KarnaughImplicant
import com.webclass.plus.util.KarnaughMinimizer

private val karnaughGroupColors = listOf(
    Color(0xFF1565C0), Color(0xFF2E7D32), Color(0xFFC62828), Color(0xFF6A1B9A),
    Color(0xFFEF6C00), Color(0xFF00838F), Color(0xFFAD1457), Color(0xFF455A64)
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun KarnaughMapScreen(isWideScreen: Boolean) {
    var variableCount by rememberSaveable { mutableIntStateOf(4) }
    var form by rememberSaveable { mutableStateOf(KarnaughForm.SOP) }
    var values by remember(variableCount) { mutableStateOf(List(1 shl variableCount) { 0 }) }
    val solution = remember(values, variableCount, form) { KarnaughMinimizer.solve(values, variableCount, form) }

    val rowBits = if (variableCount == 4) 2 else 1
    val columnBits = variableCount - rowBits
    val rowLabels = grayLabels(rowBits)
    val columnLabels = grayLabels(columnBits)
    val variableNames = listOf("A", "B", "C", "D")
    val rowAxis = variableNames.take(rowBits).joinToString("")
    val columnAxis = variableNames.drop(rowBits).take(columnBits).joinToString("")
    val targetValue = if (form == KarnaughForm.SOP) 1 else 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (isWideScreen) 40.dp else 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.primary
                ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.GridView, null) } }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("カルノー図", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "セルをタップして 0 → 1 → X（ドントケア）の順に切り替えます。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Text("変数の数", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            (2..4).forEachIndexed { index, count ->
                SegmentedButton(
                    selected = variableCount == count,
                    onClick = { variableCount = count },
                    shape = SegmentedButtonDefaults.itemShape(index, 3),
                    label = { Text("$count 変数") }
                )
            }
        }

        Text("簡単化形式", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = form == KarnaughForm.SOP,
                onClick = { form = KarnaughForm.SOP },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
                label = { Text("SOP（1をまとめる）") }
            )
            SegmentedButton(
                selected = form == KarnaughForm.POS,
                onClick = { form = KarnaughForm.POS },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
                label = { Text("POS（0をまとめる）") }
            )
        }

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("カルノー図", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("行・列はグレイコード順です", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { values = List(1 shl variableCount) { 0 } }) {
                        Icon(Icons.Outlined.DeleteSweep, null)
                        Spacer(Modifier.width(4.dp))
                        Text("クリア")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("$rowAxis＼$columnAxis", modifier = Modifier.width(54.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
                            columnLabels.forEach { label ->
                                Text(label, modifier = Modifier.width(68.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                            }
                        }
                        rowLabels.forEach { rowLabel ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(rowLabel, modifier = Modifier.width(54.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                                columnLabels.forEach { columnLabel ->
                                    val minterm = (rowLabel.toInt(2) shl columnBits) or columnLabel.toInt(2)
                                    val value = values[minterm]
                                    val groupIndexes = solution.groups.indices.filter { solution.groups[it].covers(minterm) }
                                    KarnaughCell(
                                        minterm = minterm,
                                        value = value,
                                        groupIndexes = groupIndexes,
                                        onClick = {
                                            values = values.toMutableList().also { list ->
                                                list[minterm] = when (value) { 0 -> 1; 1 -> -1; else -> 0 }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("最小${if (form == KarnaughForm.SOP) "SOP" else "POS"}式", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(solution.expression, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                val targets = values.indices.filter { values[it] == targetValue }
                val dontCares = values.indices.filter { values[it] == -1 }
                Text(
                    buildString {
                        append(if (form == KarnaughForm.SOP) "Σm" else "ΠM")
                        append(targets.joinToString(prefix = "(", postfix = ")"))
                        if (dontCares.isNotEmpty()) append("  d${dontCares.joinToString(prefix = "(", postfix = ")")}")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (solution.groups.isNotEmpty()) {
            Text("選択されたグループ", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                solution.groups.forEachIndexed { index, group ->
                    val cells = values.indices.filter(group::covers).filter { values[it] == targetValue || values[it] == -1 }
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = karnaughGroupColors[index % karnaughGroupColors.size].copy(alpha = 0.14f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, karnaughGroupColors[index % karnaughGroupColors.size])
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                            Text(groupTerm(group, variableCount, form), fontWeight = FontWeight.Bold)
                            Text(cells.joinToString { "m$it" }, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KarnaughCell(minterm: Int, value: Int, groupIndexes: List<Int>, onClick: () -> Unit) {
    val container = when (value) {
        1 -> MaterialTheme.colorScheme.primaryContainer
        -1 -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    Card(
        modifier = Modifier.size(width = 68.dp, height = 70.dp).padding(2.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = container),
        shape = MaterialTheme.shapes.small
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(5.dp)) {
            Text("m$minterm", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                if (value == -1) "X" else value.toString(),
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Row(modifier = Modifier.align(Alignment.BottomCenter), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                groupIndexes.forEach { index ->
                    Box(
                        Modifier.size(7.dp).clip(CircleShape)
                            .background(karnaughGroupColors[index % karnaughGroupColors.size])
                    )
                }
            }
        }
    }
}

private fun grayLabels(bits: Int): List<String> = if (bits == 1) listOf("0", "1") else listOf("00", "01", "11", "10")

private fun groupTerm(group: KarnaughImplicant, variableCount: Int, form: KarnaughForm): String {
    val names = listOf("A", "B", "C", "D")
    val literals = (0 until variableCount).mapNotNull { index ->
        val bit = 1 shl (variableCount - index - 1)
        if (group.mask and bit != 0) return@mapNotNull null
        val isOne = group.value and bit != 0
        when (form) {
            KarnaughForm.SOP -> if (isOne) names[index] else "${names[index]}̅"
            KarnaughForm.POS -> if (isOne) "${names[index]}̅" else names[index]
        }
    }
    return when (form) {
        KarnaughForm.SOP -> literals.joinToString("·").ifEmpty { "1" }
        KarnaughForm.POS -> "(${literals.joinToString(" + ").ifEmpty { "0" }})"
    }
}
