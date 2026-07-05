package com.bluepilot.remote.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * UI/UX REDESIGN — Floating glass bottom dock (the pill dock from the
 * v2-01 mockups). Shown on top-level destinations only; navigation between
 * the app's four hubs: Home, Layouts, Devices, Settings.
 *
 * Bottom navigation was chosen over a side drawer: this is a one-handed
 * controller app used in portrait AND landscape — thumb-reachable tabs
 * beat a hidden drawer for its core loop (pick a control surface fast).
 */
data class DockItem(val route: String, val label: String, val icon: ImageVector)

val DockItems = listOf(
    DockItem("home", "Home", Icons.Rounded.Home),
    DockItem("layouts", "Layouts", Icons.Rounded.Dashboard),
    DockItem("devices", "Devices", Icons.Rounded.Computer),
    DockItem("settings", "Settings", Icons.Rounded.Settings)
)

@Composable
fun GlassDock(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp, vertical = 10.dp),
        shape = CircleShape
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            DockItems.forEach { item ->
                val selected = currentRoute?.startsWith(item.route) == true
                val tint by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "dockTint"
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { if (!selected) onNavigate(item.route) }
                        .padding(horizontal = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                else androidx.compose.ui.graphics.Color.Transparent,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(item.icon, contentDescription = item.label, tint = tint, modifier = Modifier.size(22.dp))
                    }
                    Text(item.label, style = MaterialTheme.typography.bodyMedium, color = tint)
                }
            }
        }
    }
}
