package com.webclass.plus.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Keeps content visually open, adding separation only after the page scrolls. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun appTopBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = Color.Transparent,
    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
    titleContentColor = MaterialTheme.colorScheme.onSurface,
    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
)
