package com.webclass.plus.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Person
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.webclass.plus.R

import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Description

data class NavItem(
    val route: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val labelResId: Int
)

@Composable
fun getNavItems(showBookmarks: Boolean): List<NavItem> {
    val allItems = mutableListOf(
        NavItem("home", Icons.Outlined.School, R.string.nav_home),
        NavItem("syllabus", Icons.Filled.Description, R.string.nav_syllabus),
        NavItem("bus", Icons.Outlined.Schedule, R.string.nav_bus),
        NavItem("tools", Icons.Default.Build, R.string.nav_tools),
        NavItem("bookmarks", Icons.Default.Bookmark, R.string.nav_bookmarks),
        NavItem("user", Icons.Outlined.Person, R.string.nav_user)
    )

    if (!showBookmarks) {
        allItems.removeIf { it.route == "bookmarks" }
    }
    
    return allItems
}

@Composable
fun BottomNavBar(
    selectedRoute: String,
    showBookmarks: Boolean = false,
    useWallpaper: Boolean = false,
    wallpaperBackgroundAlpha: Float = 0.55f,
    onNavigate: (String) -> Unit
) {
    val items = getNavItems(showBookmarks)
    
    val containerColor = if (useWallpaper) {
        androidx.compose.material3.MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
    } else {
        androidx.compose.material3.NavigationBarDefaults.containerColor
    }
    
    val tonalElevation = if (useWallpaper) {
        0.dp
    } else {
        androidx.compose.material3.NavigationBarDefaults.Elevation
    }
    
    NavigationBar(
        containerColor = containerColor,
        tonalElevation = tonalElevation
    ) {
        items.forEach { item ->
            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = stringResource(item.labelResId)
                    )
                },
                label = { Text(text = stringResource(item.labelResId)) },
                selected = selectedRoute.startsWith(item.route),
                onClick = { onNavigate(item.route) }
            )
        }
    }
}



@Composable
fun SideNavRail(
    selectedRoute: String,
    showBookmarks: Boolean = false,
    isCollapsed: Boolean = false,
    onToggleCollapse: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val items = getNavItems(showBookmarks)
    val width by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (isCollapsed) 76.dp else 280.dp,
        label = "SidebarWidth"
    )

    val surfaceColor = androidx.compose.material3.MaterialTheme.colorScheme.surface
    val railBackgroundColor = if (surfaceColor.alpha < 1f) {
        surfaceColor.copy(alpha = 0.92f)
    } else {
        surfaceColor
    }

    androidx.compose.material3.Surface(
        color = railBackgroundColor,
        modifier = Modifier.width(width).fillMaxHeight(),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .windowInsetsPadding(
                    WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)
                        .union(WindowInsets.systemBars.only(WindowInsetsSides.Vertical))
                )
                .fillMaxHeight()
        ) {
            // ── Header ──
            if (isCollapsed) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Tinted icon badge
                    androidx.compose.material3.Surface(
                        modifier = Modifier.size(44.dp),
                        shape = androidx.compose.material3.MaterialTheme.shapes.medium,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Outlined.School, null,
                                modifier = Modifier.size(24.dp),
                                tint = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    // Toggle button
                    androidx.compose.material3.IconButton(onClick = onToggleCollapse) {
                        Icon(
                            Icons.Default.Menu, contentDescription = "Expand",
                            tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Tinted icon badge
                    androidx.compose.material3.Surface(
                        modifier = Modifier.size(44.dp),
                        shape = androidx.compose.material3.MaterialTheme.shapes.medium,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Outlined.School, null,
                                modifier = Modifier.size(24.dp),
                                tint = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Kosen+",
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        maxLines = 1
                    )
                    androidx.compose.material3.IconButton(onClick = onToggleCollapse) {
                        Icon(
                            Icons.AutoMirrored.Filled.MenuOpen,
                            contentDescription = "Collapse",
                            tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Divider after header
            androidx.compose.material3.HorizontalDivider(
                modifier = Modifier.padding(horizontal = if (isCollapsed) 12.dp else 20.dp),
                color = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── Nav Items ──
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = if (isCollapsed) 8.dp else 12.dp),
                horizontalAlignment = if (isCollapsed) Alignment.CenterHorizontally else Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items.forEach { item ->
                    val selected = selectedRoute.startsWith(item.route)

                    if (isCollapsed) {
                        // Collapsed: use NavigationRailItem for stable layout
                        androidx.compose.material3.NavigationRailItem(
                            selected = selected,
                            onClick = { onNavigate(item.route) },
                            icon = {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = stringResource(item.labelResId),
                                    modifier = Modifier.size(22.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = stringResource(item.labelResId),
                                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1
                                )
                            },
                            colors = androidx.compose.material3.NavigationRailItemDefaults.colors(
                                selectedIconColor = androidx.compose.material3.MaterialTheme.colorScheme.onSecondaryContainer,
                                selectedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.onSecondaryContainer,
                                indicatorColor = androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer,
                                unselectedIconColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    } else {
                        // Expanded: accent strip + icon badge + label
                        androidx.compose.material3.Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            shape = androidx.compose.material3.MaterialTheme.shapes.medium,
                            color = if (selected)
                                androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                            else
                                androidx.compose.ui.graphics.Color.Transparent,
                            onClick = { onNavigate(item.route) }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Accent strip
                                if (selected) {
                                    androidx.compose.material3.Surface(
                                        modifier = Modifier
                                            .width(4.dp)
                                            .height(36.dp),
                                        shape = androidx.compose.material3.MaterialTheme.shapes.extraSmall,
                                        color = androidx.compose.material3.MaterialTheme.colorScheme.primary
                                    ) {}
                                } else {
                                    Spacer(modifier = Modifier.width(4.dp))
                                }

                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Tinted icon badge
                                    androidx.compose.material3.Surface(
                                        modifier = Modifier.size(36.dp),
                                        shape = androidx.compose.material3.MaterialTheme.shapes.small,
                                        color = if (selected)
                                            androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
                                        else
                                            androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                item.icon, null,
                                                modifier = Modifier.size(20.dp),
                                                tint = if (selected)
                                                    androidx.compose.material3.MaterialTheme.colorScheme.primary
                                                else
                                                    androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Text(
                                        stringResource(item.labelResId),
                                        style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selected)
                                            androidx.compose.material3.MaterialTheme.colorScheme.onSecondaryContainer
                                        else
                                            androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
