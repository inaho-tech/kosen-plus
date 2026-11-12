package com.webclass.plus.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.webclass.plus.data.models.NativeQuestionInput
import com.webclass.plus.data.models.NativeQuestionPage

@Composable
fun NativeQuestionScreen(
    page: NativeQuestionPage,
    isLoading: Boolean,
    onAction: (action: String, targetPage: String?, answers: Map<String, List<String>>) -> Unit
) {
    val stateKey = remember(page) {
        listOf(page.mode, page.page, page.sectionTitle, page.warning, page.inputs.joinToString { it.name + it.value }).joinToString("|")
    }
    val answers = remember(stateKey) {
        page.inputs.map { input ->
            input.name to when {
                input.options.isNotEmpty() -> input.options.filter { it.selected }.map { it.value }
                else -> listOf(input.value)
            }
        }.toMutableStateMap()
    }
    val listState = rememberLazyListState()
    LaunchedEffect(stateKey) {
        listState.scrollToItem(0)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (page.deadline.isNotBlank()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.AccessTime, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("期限 ${page.deadline}", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            if (page.mode == "confirm") {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Outlined.TaskAlt, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                            Text(
                                page.warning.ifBlank { "この内容で採点しますか？" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { onAction("restart", null, answers) },
                            modifier = Modifier.weight(1f),
                            enabled = !isLoading
                        ) { Text("戻る") }
                        Button(
                            onClick = { onAction("finish", null, answers) },
                            modifier = Modifier.weight(1f),
                            enabled = !isLoading
                        ) {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("採点する")
                        }
                    }
                }
                if (page.indexItems.isNotEmpty()) {
                    item {
                        Text("回答状況", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    items(page.indexItems, key = { "confirm_${it.page}" }) { item ->
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth().clickable(enabled = !isLoading) {
                                onAction("move", item.page, answers)
                            }
                        ) {
                            Text(item.label, modifier = Modifier.padding(16.dp))
                        }
                    }
                }
            } else if (page.mode == "index") {
                item {
                    Text("目次", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                items(page.indexItems, key = { it.page }) { item ->
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth().clickable(enabled = !isLoading) {
                            onAction("move", item.page, answers)
                        }
                    ) {
                        Text(item.label, modifier = Modifier.padding(18.dp), style = MaterialTheme.typography.titleMedium)
                    }
                }
            } else {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            page.sectionTitle.ifBlank { if (page.page.isNotBlank()) "設問 ${page.page}" else "設問" },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (page.prompt.isNotBlank()) {
                            Text(page.prompt, style = MaterialTheme.typography.bodyLarge)
                        }
                        if (page.point.isNotBlank()) {
                            Text(page.point, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                items(page.inputs, key = { it.name }) { input ->
                    QuestionInput(input = input, values = answers[input.name].orEmpty()) { values ->
                        answers[input.name] = values
                    }
                }

                item {
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { onAction("previous", null, answers) },
                            enabled = page.canPrevious && !isLoading,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                            Text("前へ")
                        }
                        FilledTonalButton(
                            onClick = { onAction("index", null, answers) },
                            enabled = page.canShowIndex && !isLoading,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.GridView, contentDescription = null)
                            Text("目次")
                        }
                        Button(
                            onClick = { onAction("next", null, answers) },
                            enabled = page.canNext && !isLoading,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("次へ")
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                        }
                    }
                }
                if (page.canFinish) {
                    item {
                        OutlinedButton(
                            onClick = { onAction("finish", null, answers) },
                            enabled = !isLoading,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("回答を確認して採点") }
                    }
                }
            }
        }

        if (isLoading) {
            androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }
}

@Composable
private fun QuestionInput(
    input: NativeQuestionInput,
    values: List<String>,
    onValuesChanged: (List<String>) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        if (input.label.isNotBlank()) {
            Text(input.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
        when (input.type) {
            "radio", "select" -> input.options.forEach { option ->
                val selected = option.value in values
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth().clickable { onValuesChanged(listOf(option.value)) },
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selected, onClick = { onValuesChanged(listOf(option.value)) })
                        Text(option.label, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            "checkbox" -> input.options.forEach { option ->
                val checked = option.value in values
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth().clickable {
                        onValuesChanged(if (checked) values - option.value else values + option.value)
                    },
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = if (checked) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { onValuesChanged(if (checked) values - option.value else values + option.value) }
                        )
                        Text(option.label, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            else -> OutlinedTextField(
                value = values.firstOrNull().orEmpty(),
                onValueChange = { onValuesChanged(listOf(it)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = if (input.type == "textarea") 4 else 1,
                label = input.label.takeIf { it.isNotBlank() }?.let { label -> { Text(label) } }
            )
        }
    }
}
