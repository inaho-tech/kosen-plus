package com.webclass.plus.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AirportShuttle
import androidx.compose.material.icons.outlined.DirectionsRailway
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.webclass.plus.util.ThemePreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableHubScreen(isWideScreen: Boolean) {
    val context = LocalContext.current
    val themePreferences = remember { ThemePreferences(context) }
    val useWallpaper by themePreferences.useWallpaperFlow.collectAsState(initial = false)
    val lightweight by themePreferences.enableLightweightModeFlow.collectAsState(initial = false)
    var selectedMode by rememberSaveable { mutableIntStateOf(0) }
    var isDokoDetailOpen by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val pageStateHolder = rememberSaveableStateHolder()
    val surfaceColor = if (useWallpaper) MaterialTheme.colorScheme.surface.copy(alpha = 0.88f) else MaterialTheme.colorScheme.surface

    Column(modifier = Modifier.fillMaxSize()) {
        if (!(selectedMode == 1 && isDokoDetailOpen)) {
            Column(
                modifier = Modifier
                    .background(surfaceColor)
                    .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))
            ) {
                PrimaryTabRow(
                    selectedTabIndex = selectedMode,
                    containerColor = surfaceColor
                ) {
                    Tab(
                        selected = selectedMode == 0,
                        onClick = { selectedMode = 0 },
                        text = { Text("小湊バス") },
                        icon = { Icon(Icons.Outlined.AirportShuttle, contentDescription = null) }
                    )
                    Tab(
                        selected = selectedMode == 1,
                        onClick = { selectedMode = 1 },
                        text = { Text("電車") },
                        icon = { Icon(Icons.Outlined.DirectionsRailway, contentDescription = null) }
                    )
                }
            }
        } else {
            Spacer(
                modifier = Modifier
                    .background(surfaceColor)
                    .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))
            )
        }

        AnimatedContent(
            targetState = selectedMode,
            transitionSpec = {
                if (lightweight) EnterTransition.None togetherWith ExitTransition.None
                else fadeIn() togetherWith fadeOut()
            },
            label = "timetable_mode",
            modifier = Modifier.fillMaxSize()
        ) { mode ->
            pageStateHolder.SaveableStateProvider(key = "timetable_mode_$mode") {
                if (mode == 0) {
                    BusTimetableScreen(isWideScreen = isWideScreen, embedded = true)
                } else {
                    DokoTrainScreen(
                        useWallpaper = useWallpaper,
                        forceTwoPane = isWideScreen,
                        onDetailVisibilityChanged = { isDokoDetailOpen = it }
                    )
                }
            }
        }
    }
}
