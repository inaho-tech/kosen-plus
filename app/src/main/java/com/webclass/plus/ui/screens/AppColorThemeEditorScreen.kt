package com.webclass.plus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.webclass.plus.util.ThemePreferences
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppColorThemeEditorScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val themePreferences = remember { ThemePreferences(context) }
    val scope = rememberCoroutineScope()
    
    val customColors by themePreferences.customColorsFlow.collectAsState(initial = emptyMap())
    val customDarkMode by themePreferences.customDarkModeFlow.collectAsState(initial = false)
    
    // Local state for sliders to ensure smooth UI updates
    var primaryColor by remember { mutableStateOf(Color(0xFF4FC3F7)) }
    var secondaryColor by remember { mutableStateOf(Color(0xFF81D4FA)) }
    var backgroundColor by remember { mutableStateOf(Color(0xFFFFFFFF)) }
    var surfaceColor by remember { mutableStateOf(Color(0xFFFAFBFD)) }
    var isDarkMode by remember { mutableStateOf(false) }
    
    // Sync with flow initially
    LaunchedEffect(customColors, customDarkMode) {
        if (customColors.isNotEmpty()) {
            primaryColor = Color(customColors["primary"] ?: 0xFF4FC3F7.toInt())
            secondaryColor = Color(customColors["secondary"] ?: 0xFF81D4FA.toInt())
            backgroundColor = Color(customColors["background"] ?: 0xFFFFFFFF.toInt())
            surfaceColor = Color(customColors["surface"] ?: 0xFFFAFBFD.toInt())
        }
        isDarkMode = customDarkMode
    }
    
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            CenterAlignedTopAppBar(
                windowInsets = WindowInsets.displayCutout.union(WindowInsets.statusBars),
                title = { Text("アプリテーマカラー") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        // Reset to default Light theme values
                        scope.launch {
                            themePreferences.setCustomColor("primary", 0xFF4FC3F7.toInt())
                            themePreferences.setCustomColor("secondary", 0xFF81D4FA.toInt())
                            themePreferences.setCustomColor("background", 0xFFFFFFFF.toInt())
                            themePreferences.setCustomColor("surface", 0xFFFAFBFD.toInt())
                            themePreferences.setCustomDarkMode(false)
                        }
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Preview Section
            ThemePreviewCard(primaryColor, secondaryColor, backgroundColor, surfaceColor)
            
            HorizontalDivider()
            
            // Dark Mode Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("ダークモード", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "テキストを白に、コンテナ色を暗めに",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isDarkMode,
                    onCheckedChange = { checked ->
                        isDarkMode = checked
                        scope.launch { themePreferences.setCustomDarkMode(checked) }
                    }
                )
            }
            
            HorizontalDivider()
            
            // Editors
            ColorEditorItem("プライマリ (メインカラー)", primaryColor) { newColor ->
                primaryColor = newColor
                scope.launch { themePreferences.setCustomColor("primary", newColor.toArgb()) }
            }
            
            ColorEditorItem("セカンダリ (アクセント)", secondaryColor) { newColor ->
                secondaryColor = newColor
                scope.launch { themePreferences.setCustomColor("secondary", newColor.toArgb()) }
            }
            
            ColorEditorItem("背景色", backgroundColor) { newColor ->
                backgroundColor = newColor
                scope.launch { themePreferences.setCustomColor("background", newColor.toArgb()) }
            }
            
            ColorEditorItem("サーフェス (カードなど)", surfaceColor) { newColor ->
                surfaceColor = newColor
                scope.launch { themePreferences.setCustomColor("surface", newColor.toArgb()) }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun ThemePreviewCard(
    primary: Color,
    secondary: Color,
    background: Color,
    surface: Color
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = background
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("プレビュー", fontWeight = FontWeight.Bold, color = Color.Black) // Assuming black text for simplicity or calculate contrast
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {},
                        colors = ButtonDefaults.buttonColors(containerColor = primary)
                    ) {
                        Text("Primary")
                    }
                    
                    OutlinedButton(
                        onClick = {},
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = primary)
                    ) {
                        Text("Outlined")
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Card(
                    colors = CardDefaults.cardColors(containerColor = surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(secondary)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Box(modifier = Modifier.width(100.dp).height(12.dp).background(Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(4.dp)))
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(modifier = Modifier.width(60.dp).height(10.dp).background(Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(4.dp)))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ColorEditorItem(
    label: String,
    color: Color,
    onColorChange: (Color) -> Unit
) {
    var red by remember(color) { mutableFloatStateOf(color.red) }
    var green by remember(color) { mutableFloatStateOf(color.green) }
    var blue by remember(color) { mutableFloatStateOf(color.blue) }

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Red
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("R", modifier = Modifier.width(20.dp), style = MaterialTheme.typography.bodySmall, color = Color.Red)
            Slider(
                value = red,
                onValueChange = { 
                    red = it
                    onColorChange(Color(red, green, blue))
                },
                modifier = Modifier.weight(1f)
            )
        }
        
        // Green
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("G", modifier = Modifier.width(20.dp), style = MaterialTheme.typography.bodySmall, color = Color.Green)
            Slider(
                value = green,
                onValueChange = { 
                    green = it
                    onColorChange(Color(red, green, blue))
                },
                modifier = Modifier.weight(1f)
            )
        }
        
        // Blue
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("B", modifier = Modifier.width(20.dp), style = MaterialTheme.typography.bodySmall, color = Color.Blue)
            Slider(
                value = blue,
                onValueChange = { 
                    blue = it
                    onColorChange(Color(red, green, blue))
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}
