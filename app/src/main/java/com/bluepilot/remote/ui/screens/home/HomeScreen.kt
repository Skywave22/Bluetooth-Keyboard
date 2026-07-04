package com.bluepilot.remote.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bluepilot.remote.ui.components.ConnectionStatusCard
import com.bluepilot.remote.ui.navigation.Routes
import com.bluepilot.remote.viewmodel.ConnectionViewModel

/**
 * Home: live connection status + navigation grid.
 *
 * The Connect tile routes through the permission screen when permissions
 * are missing; control tiles activate as their modules are built.
 */
private data class HomeTile(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val route: String?,          // null = not built yet (disabled)
    val needsPermissions: Boolean = false,
    /** Gel icon color (designs/glass-01: every tile has its own vivid hue). */
    val gel: Long = 0xFF2F6BFF
)

private val tiles = listOf(
    HomeTile("Connect", "Pair with a PC or device", Icons.Rounded.Bluetooth, Routes.CONNECTION, needsPermissions = true, gel = 0xFF2F6BFF),
    HomeTile("Mouse", "Trackpad and buttons", Icons.Rounded.Mouse, Routes.MOUSE, gel = 0xFF3D8BFF),
    HomeTile("Keyboard", "Full PC keyboard", Icons.Rounded.Keyboard, Routes.KEYBOARD, gel = 0xFF9B59F6),
    HomeTile("Numpad", "Numeric keypad", Icons.Rounded.Pin, Routes.NUMPAD, gel = 0xFFFF8C42),
    HomeTile("Multimedia", "Media and volume", Icons.Rounded.MusicNote, Routes.MULTIMEDIA, gel = 0xFF17C3CE),
    HomeTile("Presenter", "Slide control", Icons.Rounded.Slideshow, Routes.PRESENTER, gel = 0xFFF5C542),
    HomeTile("Gamepad", "Game controller", Icons.Rounded.Gamepad, Routes.GAMEPAD, gel = 0xFFFF5C8A),
    HomeTile("Layouts", "Custom control screens", Icons.Rounded.Dashboard, Routes.LAYOUTS, gel = 0xFF2ED5A5),
    HomeTile("Macros", "Record & play sequences", Icons.Rounded.Bolt, Routes.MACROS, gel = 0xFF57D163),
    HomeTile("Themes", "Change the whole look", Icons.Rounded.Palette, Routes.THEMES, gel = 0xFFB86BFF),
    HomeTile("Pad Builder", "Design your own gamepad", Icons.Rounded.SportsEsports, Routes.GAMEPAD_BUILDER, gel = 0xFF6E8BFF),
    HomeTile("Settings", "Tune everything", Icons.Rounded.Settings, Routes.SETTINGS, gel = 0xFF8B9BB5),
    HomeTile("Help", "Pairing & troubleshooting", Icons.AutoMirrored.Rounded.HelpOutline, Routes.HELP, gel = 0xFF64B6F0)
)

@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    viewModel: ConnectionViewModel = hiltViewModel()
) {
    val state by viewModel.connectionState.collectAsState()
    val permissionsGranted by viewModel.permissionsGranted.collectAsState()

    // If permissions are already granted, keep the engine warm so the
    // status card is truthful even before visiting the Connection screen.
    LaunchedEffect(Unit) { viewModel.initialize() }

    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "BluePilot Remote",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Bluetooth HID controller",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            ConnectionStatusCard(state)

            Spacer(Modifier.height(16.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(tiles) { tile ->
                    HomeTileCard(
                        tile = tile,
                        enabled = tile.route != null,
                        onClick = {
                            val route = tile.route ?: return@HomeTileCard
                            if (tile.needsPermissions && !permissionsGranted) {
                                onNavigate(Routes.PERMISSIONS)
                            } else {
                                onNavigate(route)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeTileCard(tile: HomeTile, enabled: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (enabled) 2.dp else 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Gel icon badge (designs/glass-01): vivid per-tile gradient with
            // a soft top highlight, white glyph — the glassy gummy look.
            val gel = androidx.compose.ui.graphics.Color(tile.gel.toULong().toLong() and 0xFFFFFFFF)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.linearGradient(
                            colors = if (enabled) listOf(
                                gel.copy(alpha = 1f),
                                gel.copy(red = (gel.red * 0.72f), green = (gel.green * 0.72f), blue = (gel.blue * 0.72f))
                            ) else listOf(
                                MaterialTheme.colorScheme.outline,
                                MaterialTheme.colorScheme.outline
                            )
                        ),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.35f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
                    ),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Icon(
                    imageVector = tile.icon,
                    contentDescription = tile.title,
                    tint = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = tile.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (enabled) tile.subtitle else "Coming in a later module",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
