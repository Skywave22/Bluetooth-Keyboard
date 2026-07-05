package com.bluepilot.remote.ui.components

import androidx.compose.ui.graphics.Color

/** OPTIMIZATION: single ARGB-Long -> Compose Color conversion (was 9 copies). */
fun Long.toComposeColor(): Color = Color(this.toULong().toLong() and 0xFFFFFFFF)
