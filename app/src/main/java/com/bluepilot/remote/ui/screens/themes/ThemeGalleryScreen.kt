package com.bluepilot.remote.ui.screens.themes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.lazy.grid.GridItemSpan
import com.bluepilot.remote.ui.theme.AppThemeSpec
import com.bluepilot.remote.ui.theme.BuiltInThemes
import com.bluepilot.remote.viewmodel.SettingsViewModel
import kotlin.math.max

/**
 * SECTION 1 — Theme Gallery.
 *
 * Grid of theme cards. Each card contains a LIVE RENDERED MINI-PREVIEW of
 * the theme (miniature home screen: background orbs, status pill, tile
 * grid, nav dock — all painted from the actual AppThemeSpec tokens), the
 * theme name and an Apply button. The active theme is highlighted.
 * Applying persists via DataStore → whole app re-skins instantly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeGalleryScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val app by viewModel.app.collectAsState()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Themes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        // Gallery grouped by design family (one section per mockup set).
        val families: List<Pair<String, List<AppThemeSpec>>> = listOf(
            "Classic" to listOf(BuiltInThemes.PILOT_DARK, BuiltInThemes.PILOT_GLOW),
            "Liquid Glass" to listOf(BuiltInThemes.LIQUID_GLASS, BuiltInThemes.LIQUID_GLASS_LIGHT),
            "Glass × Material You" to listOf(BuiltInThemes.GLASS_YOU_DARK, BuiltInThemes.GLASS_YOU_LIGHT),
            "Hawaii Harmony" to listOf(BuiltInThemes.HAWAII_NIGHT, BuiltInThemes.HAWAII_DAY),
            "Cockpit HUD" to listOf(BuiltInThemes.COCKPIT_HUD, BuiltInThemes.DAY_FLIGHT),
            "More" to listOf(BuiltInThemes.DARK_NEON, BuiltInThemes.OLED_BLACK, BuiltInThemes.MINIMAL_LIGHT)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            families.forEach { (family, specs) ->
                item(key = "header-" + family, span = { GridItemSpan(2) }) {
                    Text(
                        text = family,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                items(specs, key = { it.id }) { spec ->
                    ThemeCard(
                        spec = spec,
                        isActive = spec.id == app.themeId,
                        onApply = { viewModel.setThemeId(spec.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeCard(
    spec: AppThemeSpec,
    isActive: Boolean,
    onApply: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isActive) Modifier.border(
                    2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(18.dp)
                ) else Modifier
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            ThemeMiniPreview(
                spec = spec,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            )
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(spec.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (spec.isDark) "Dark" else "Light",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isActive) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = "Active theme",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            Box(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                if (isActive) {
                    OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                        Text("Applied")
                    }
                } else {
                    Button(onClick = onApply, modifier = Modifier.fillMaxWidth()) {
                        Text("Apply")
                    }
                }
            }
        }
    }
}

/**
 * Live mini-preview: paints a miniature home screen from the spec's own
 * tokens — orbs, glass status pill, 2x2 tile grid with colored icon dots,
 * and a nav dock. What you see is exactly what the theme produces.
 */
@Composable
private fun ThemeMiniPreview(spec: AppThemeSpec, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(spec.background, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
            .clickable(enabled = false) {}
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val maxEdge = max(size.width, size.height)
            // Background orbs
            spec.backgroundOrbs.forEach { orb ->
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(orb.color.copy(alpha = orb.alpha), orb.color.copy(alpha = 0f)),
                        center = Offset(size.width * orb.x, size.height * orb.y),
                        radius = maxEdge * orb.radius
                    ),
                    radius = maxEdge * orb.radius,
                    center = Offset(size.width * orb.x, size.height * orb.y)
                )
            }

            val pad = size.width * 0.07f
            val r = spec.cornerRadius * size.width / 360f * 2f
            val surface = spec.surface.copy(alpha = spec.surfaceAlpha)
            val corner = androidx.compose.ui.geometry.CornerRadius(r, r)

            fun panel(x: Float, y: Float, w: Float, h: Float) {
                drawRoundRect(
                    color = surface,
                    topLeft = Offset(x, y),
                    size = androidx.compose.ui.geometry.Size(w, h),
                    cornerRadius = corner
                )
                if (spec.edgeGlow) {
                    drawRoundRect(
                        color = (spec.glowColor ?: spec.onSurface).copy(alpha = 0.28f),
                        topLeft = Offset(x, y),
                        size = androidx.compose.ui.geometry.Size(w, h),
                        cornerRadius = corner,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.4f)
                    )
                }
            }

            // Status pill with connected dot
            val pillH = size.height * 0.14f
            panel(pad, pad, size.width - 2 * pad, pillH)
            drawCircle(
                color = spec.connected,
                radius = pillH * 0.20f,
                center = Offset(pad + pillH * 0.5f, pad + pillH * 0.5f)
            )

            // 2x2 tile grid with theme-colored icon dots
            val gridTop = pad + pillH + size.height * 0.06f
            val gap = size.width * 0.045f
            val tileW = (size.width - 2 * pad - gap) / 2f
            val tileH = size.height * 0.22f
            val iconColors = listOf(spec.primary, spec.secondary, spec.connected, spec.error)
            var i = 0
            for (row in 0..1) {
                for (col in 0..1) {
                    val x = pad + col * (tileW + gap)
                    val y = gridTop + row * (tileH + gap)
                    panel(x, y, tileW, tileH)
                    drawCircle(
                        color = iconColors[i % iconColors.size].copy(alpha = 0.9f),
                        radius = tileH * 0.18f,
                        center = Offset(x + tileW * 0.5f, y + tileH * 0.42f)
                    )
                    i++
                }
            }

            // Bottom nav dock
            val dockH = size.height * 0.10f
            panel(size.width * 0.22f, size.height - dockH - pad * 0.7f, size.width * 0.56f, dockH)
        }
    }
}
