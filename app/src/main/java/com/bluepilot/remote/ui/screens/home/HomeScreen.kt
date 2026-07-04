package com.bluepilot.remote.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
    val needsPermissions: Boolean = false
)

private val tiles = listOf(
    HomeTile("Connect", "Pair with a PC or device", Icons.Rounded.Bluetooth, Routes.CONNECTION, needsPermissions = true),
    HomeTile("Mouse", "Trackpad and buttons", Icons.Rounded.Mouse, Routes.MOUSE),
    HomeTile("Keyboard", "Full PC keyboard", Icons.Rounded.Keyboard, Routes.KEYBOARD),
    HomeTile("Numpad", "Numeric keypad", Icons.Rounded.Pin, Routes.NUMPAD),
    HomeTile("Multimedia", "Media and volume", Icons.Rounded.MusicNote, Routes.MULTIMEDIA),
    HomeTile("Presenter", "Slide control", Icons.Rounded.Slideshow, Routes.PRESENTER),
    HomeTile("Gamepad", "Game controller", Icons.Rounded.Gamepad, Routes.GAMEPAD),
    HomeTile("Layouts", "Custom control screens", Icons.Rounded.Dashboard, Routes.LAYOUTS),
    HomeTile("Macros", "Record & play sequences", Icons.Rounded.Bolt, Routes.MACROS),
    HomeTile("Themes", "Change the whole look", Icons.Rounded.Palette, Routes.THEMES),
    HomeTile("Pad Builder", "Design your own gamepad", Icons.Rounded.SportsEsports, Routes.GAMEPAD_BUILDER),
    HomeTile("Settings", "Tune everything", Icons.Rounded.Settings, Routes.SETTINGS),
    HomeTile("Help", "Pairing & troubleshooting", Icons.AutoMirrored.Rounded.HelpOutline, Routes.HELP)
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
            Icon(
                imageVector = tile.icon,
                contentDescription = tile.title,
                tint = if (enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(28.dp)
            )
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
