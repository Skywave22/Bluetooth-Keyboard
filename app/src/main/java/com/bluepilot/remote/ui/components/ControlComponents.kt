package com.bluepilot.remote.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable

/**
 * Shared building blocks for control screens.
 * Haptics honor the user's touch-vibrations setting via [hapticEnabled].
 */

/** Performs a key-press haptic if enabled. */
@Composable
fun rememberHaptic(hapticEnabled: Boolean): () -> Unit {
    val view = LocalView.current
    return {
        if (hapticEnabled) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }
}

/** A tappable key/button card used across keyboard/numpad/media screens. */
@Composable
fun KeyCard(
    label: String,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 48.dp,
    emphasized: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(height)
            .clickable(enabled = enabled, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (emphasized) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (emphasized) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

/** Thin banner shown on control screens when not connected. */
@Composable
fun NotConnectedBanner(visible: Boolean) {
    if (!visible) return
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Text(
            text = "Not connected — controls are inactive. Open Connect to pair.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(12.dp)
        )
    }
}
