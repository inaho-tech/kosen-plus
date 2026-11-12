package com.webclass.plus.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.webclass.plus.util.ThemePreferences
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeEditorScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val themePreferences = remember { ThemePreferences(context) }
    val scope = rememberCoroutineScope()
    
    // Load initial CSS
    val initialCss by themePreferences.customThemeCssFlow.collectAsState(initial = "")
    var cssContent by remember(initialCss) { mutableStateOf(initialCss) }
    
    // Track if there are unsaved changes (simple heuristic)
    var isModified by remember { mutableStateOf(false) }

    // Update isModified when cssContent changes
    LaunchedEffect(cssContent, initialCss) {
        isModified = cssContent != initialCss
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.safeDrawing,
                title = { Text("カラーテーマエディタ") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    if (isModified) {
                        IconButton(onClick = {
                            scope.launch {
                                themePreferences.setCustomThemeCss(cssContent)
                                // Show snackbar or toast? For now just save.
                            }
                        }) {
                            Icon(Icons.Default.Save, contentDescription = "保存")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                text = "カスタムCSSを入力してください。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = cssContent,
                onValueChange = { 
                    cssContent = it 
                },
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                placeholder = { Text("/* ここにCSSを入力 */") }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    scope.launch {
                        themePreferences.setCustomThemeCss(cssContent)
                        onBackClick()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存して戻る")
            }
        }
    }
}
