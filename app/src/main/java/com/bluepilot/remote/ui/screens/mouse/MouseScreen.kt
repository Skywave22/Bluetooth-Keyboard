package com.bluepilot.remote.ui.screens.mouse

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bluepilot.remote.model.MouseButton
import com.bluepilot.remote.ui.components.KeyCard
import com.bluepilot.remote.ui.components.NotConnectedBanner
import com.bluepilot.remote.ui.components.rememberHaptic
import com.bluepilot.remote.viewmodel.RemoteControlViewModel

/**
 * Mouse screen: trackpad (drag = move, tap = left click, double-tap =
 * double click, long-press = right click), scroll strip, and L/M/R buttons.
 * All motion runs through PointerMath with the user's settings applied.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MouseScreen(
    onBack: () -> Unit,
    viewModel: RemoteControlViewModel = hiltViewModel()
) {
    val isConnected by viewModel.isConnected.collectAsState()
    val vibration by viewModel.vibrationsEnabled.collectAsState()
    val haptic = rememberHaptic(vibration)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mouse") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            NotConnectedBanner(!isConnected)

            Row(modifier = Modifier.weight(1f)) {
                // ---------- Trackpad ----------
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(16.dp)
                        )
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { haptic(); viewModel.onTrackpadTap() },
                                onDoubleTap = { haptic(); viewModel.onTrackpadDoubleTap() },
                                onLongPress = { haptic(); viewModel.onTrackpadLongPress() }
                            )
                        }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { viewModel.onTrackpadGestureStart() }
                            ) { change, dragAmount ->
                                change.consume()
                                viewModel.onTrackpadDelta(dragAmount.x, dragAmount.y)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Trackpad\ntap = click • hold = right click",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }

                Spacer(Modifier.width(12.dp))

                // ---------- Scroll strip ----------
                Box(
                    modifier = Modifier
                        .width(56.dp)
                        .fillMaxHeight()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(16.dp)
                        )
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                viewModel.onScrollDelta(dragAmount.y)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "S\nC\nR\nO\nL\nL",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ---------- Mouse buttons ----------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                KeyCard("Left", modifier = Modifier.weight(2f), height = 56.dp) {
                    haptic(); viewModel.clickButton(MouseButton.LEFT)
                }
                KeyCard("Middle", modifier = Modifier.weight(1f), height = 56.dp) {
                    haptic(); viewModel.clickButton(MouseButton.MIDDLE)
                }
                KeyCard("Right", modifier = Modifier.weight(2f), height = 56.dp) {
                    haptic(); viewModel.clickButton(MouseButton.RIGHT)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
