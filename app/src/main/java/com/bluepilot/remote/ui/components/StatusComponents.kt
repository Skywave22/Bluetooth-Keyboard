package com.bluepilot.remote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bluepilot.remote.model.HidConnectionState
import com.bluepilot.remote.ui.theme.StatusConnected
import com.bluepilot.remote.ui.theme.StatusConnecting
import com.bluepilot.remote.ui.theme.StatusError

/**
 * Shared status widgets used by Home + Connection screens.
 * One mapping from HidConnectionState → (color, title, subtitle) lives here
 * so every screen tells the same story.
 */

data class StatusVisual(val color: Color, val title: String, val subtitle: String)

@Composable
fun statusVisual(state: HidConnectionState): StatusVisual = when (state) {
    is HidConnectionState.Connected ->
        StatusVisual(StatusConnected, "Connected", state.device.name)
    is HidConnectionState.Connecting ->
        StatusVisual(StatusConnecting, "Connecting…", state.device.name)
    is HidConnectionState.Initializing ->
        StatusVisual(StatusConnecting, "Starting HID engine…", "Registering with Android Bluetooth")
    is HidConnectionState.Idle ->
        StatusVisual(MaterialTheme.colorScheme.outline, "Ready", "Waiting for a host connection")
    is HidConnectionState.BluetoothDisabled ->
        StatusVisual(StatusError, "Bluetooth is off", "Turn on Bluetooth to continue")
    is HidConnectionState.PermissionMissing ->
        StatusVisual(StatusError, "Permissions needed", "Grant Bluetooth permissions to continue")
    is HidConnectionState.HidUnsupported ->
        StatusVisual(StatusError, "HID not supported", state.reason)
    is HidConnectionState.Error ->
        StatusVisual(StatusError, "Error", state.message)
}

/** Status card: colored dot + title + subtitle, driven by the state machine. */
@Composable
fun ConnectionStatusCard(state: HidConnectionState, modifier: Modifier = Modifier) {
    val visual = statusVisual(state)
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(visual.color, CircleShape)
            )
            Spacer(Modifier.size(12.dp))
            Column {
                Text(
                    text = visual.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = visual.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
