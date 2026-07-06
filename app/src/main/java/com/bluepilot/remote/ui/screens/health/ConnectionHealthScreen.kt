package com.bluepilot.remote.ui.screens.health

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bluepilot.remote.domain.ConnectionHealthTracker
import com.bluepilot.remote.model.HidConnectionState
import com.bluepilot.remote.ui.components.GlassCard
import com.bluepilot.remote.viewmodel.ConnectionHealthViewModel

/**
 * ADV SECTION 5 — Connection Health dashboard.
 *
 * Every number on this screen is measured (see ConnectionHealthViewModel
 * doc header). Metrics the platform does not expose are shown as
 * "n/a (platform)" instead of fake values.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionHealthScreen(
    onBack: () -> Unit,
    viewModel: ConnectionHealthViewModel = hiltViewModel()
) {
    val snapshot by viewModel.snapshot.collectAsState()
    val connection by viewModel.connection.collectAsState()
    val reconnect by viewModel.reconnectStatus.collectAsState()
    val battery by viewModel.battery.collectAsState()
    val batterySaver by viewModel.batterySaver.collectAsState()
    val diagnostic by viewModel.diagnostic.collectAsState()
    val diagRunning by viewModel.diagRunning.collectAsState()
    val exportPayload by viewModel.exportPayload.collectAsState()
    val appSettings by viewModel.appSettings.collectAsState()
    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        val payload = exportPayload
        if (uri != null && payload != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(payload.second.toByteArray()) }
            }
        }
        viewModel.consumeExport()
    }
    LaunchedEffect(exportPayload) { exportPayload?.let { exportLauncher.launch(it.first) } }

    val health = ConnectionHealthTracker.classify(snapshot)
    val healthColor = when (health) {
        ConnectionHealthTracker.Health.GOOD -> Color(0xFF2ECC71)
        ConnectionHealthTracker.Health.FAIR -> Color(0xFFF1C40F)
        ConnectionHealthTracker.Health.POOR -> Color(0xFFE74C3C)
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Connection Health") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---------- Status card ----------
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(
                                if (connection.isConnected) healthColor
                                else MaterialTheme.colorScheme.outline,
                                CircleShape
                            )
                    )
                    Spacer(Modifier.size(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            when (connection) {
                                is HidConnectionState.Connected ->
                                    "Connected — " + (connection as HidConnectionState.Connected).device.name
                                is HidConnectionState.Connecting -> "Connecting…"
                                else -> "Not connected"
                            },
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            if (connection.isConnected) when (health) {
                                ConnectionHealthTracker.Health.GOOD -> "Excellent — link performing normally"
                                ConnectionHealthTracker.Health.FAIR -> "Fair — may lag, see suggestions"
                                ConnectionHealthTracker.Health.POOR -> "Poor — likely to disconnect"
                            } else "Connect to a host to see live metrics",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (connection.isConnected) healthColor
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        // ADV S5 — auto-reconnect live status (real backoff state).
                        reconnect?.let { (attempt, max) ->
                            Text(
                                "Auto-reconnect: attempt $attempt of $max…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFF1C40F)
                            )
                        }
                    }
                }
            }

            // ---------- Live metrics ----------
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Live metrics", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(6.dp))
                    MetricRow("HID reports sent", "${snapshot.totalSent}")
                    MetricRow("Failed sends", "${snapshot.totalFailed}")
                    MetricRow("Report rate", "${snapshot.reportsPerSecond}/s")
                    MetricRow(
                        "sendReport() time (measured)",
                        if (snapshot.totalSent > 0)
                            "mean ${snapshot.recentMeanSendUs}µs • max ${snapshot.recentMaxSendUs}µs"
                        else "—"
                    )
                    MetricRow(
                        "Signal (RSSI)",
                        snapshot.rssiDbm?.let { "$it dBm" }
                            ?: "n/a — Android doesn't expose RSSI for the HID-device role"
                    )
                    MetricRow(
                        "Host round-trip",
                        "n/a — Bluetooth HID has no host ACK (protocol limit)"
                    )
                    MetricRow("Session", "${snapshot.sessionMs / 1000}s • ${snapshot.disconnects} disconnects")
                    MetricRow(
                        "Battery",
                        "${battery.first}%" +
                            (battery.second?.let { " • ${kotlin.math.abs(it) / 1000}mA ${if (it < 0) "draw" else "charge"}" } ?: "") +
                            (if (batterySaver) " • SAVER ON" else "")
                    )
                }
            }

            // ---------- 60s stability graph ----------
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Report activity — last 60s", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    val barColor = MaterialTheme.colorScheme.primary
                    val failColor = MaterialTheme.colorScheme.error
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                    ) {
                        val buckets = snapshot.history.takeLast(60)
                        if (buckets.isEmpty()) return@Canvas
                        val maxSent = buckets.maxOf { it.sent }.coerceAtLeast(1)
                        val bw = size.width / 60f
                        buckets.forEachIndexed { i, b ->
                            val x = size.width - (buckets.size - i) * bw
                            val hgt = (b.sent.toFloat() / maxSent) * size.height
                            drawRect(
                                color = if (b.failed > 0) failColor else barColor.copy(alpha = 0.75f),
                                topLeft = androidx.compose.ui.geometry.Offset(x, size.height - hgt),
                                size = androidx.compose.ui.geometry.Size((bw - 1f).coerceAtLeast(1f), hgt)
                            )
                        }
                    }
                    Text(
                        "Bars = reports/second (red = second with failed sends). Gaps = idle.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ---------- Suggestions ----------
            val tips = ConnectionHealthTracker.suggestions(
                snapshot,
                effects3dOn = appSettings.quality3D != "FLAT" && !appSettings.reduceMotion,
                batterySaver = batterySaver
            )
            if (tips.isNotEmpty()) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Suggestions", style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary)
                        tips.forEach { tip ->
                            Text("• $tip", style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }

            // ---------- Diagnostics ----------
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Diagnostics", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(6.dp))
                    Button(
                        onClick = { viewModel.runDiagnostic() },
                        enabled = connection.isConnected && !diagRunning,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (diagRunning) "Running…" else "Run diagnostic test (25 neutral reports)")
                    }
                    diagnostic?.let { d ->
                        Text(
                            "Burst result: ${d.sent} sent, ${d.failed} failed • mean ${d.meanUs}µs, max ${d.maxUs}µs • ${d.totalMs}ms total",
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = { viewModel.requestExport() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Export diagnostic log") }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.weight(1.4f)
        )
    }
}
