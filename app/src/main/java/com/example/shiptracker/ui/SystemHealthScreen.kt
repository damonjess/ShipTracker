package com.example.shiptracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SystemHealthScreen(
    viewModel: ShipViewModel,
    onLaunchSentinel: () -> Unit = {}
) {
    val webSocketState by viewModel.webSocketState.collectAsState()
    val shipsInMemory by viewModel.totalShipsInMemory.collectAsState()
    val dbSize by viewModel.databasePointCount.collectAsState()
    val isSatellite by viewModel.isSatelliteMode.collectAsState()
    val mps by viewModel.messagesPerSecond.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("System Diagnostics", style = MaterialTheme.typography.headlineMedium)
        HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))

        // 🚀 LAUNCH SENTINEL BUTTON
        Button(
            onClick = onLaunchSentinel,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF0F172A),
                contentColor = Color.Green
            )
        ) {
            Icon(
                imageVector = Icons.Default.Videocam,
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(
                "🚀 LAUNCH PROJECT SENTINEL (NPU AI)",
                fontWeight = FontWeight.Bold
            )
        }

        // Network Layer
        DiagnosticCard(
            title = "Network Layer (AISStream)",
            icon = Icons.Default.Wifi,
            value = webSocketState,
            subtitle = if (webSocketState.contains("Live")) "$mps msgs/sec (Live Velocity)" else "0 msgs/sec",
            valueColor = if (webSocketState.contains("Live")) Color(0xFF4CAF50) else Color(0xFFF44336)
        )

        // Memory Layer
        DiagnosticCard(
            title = "Memory Layer (Live State)",
            icon = Icons.Default.Memory,
            value = "$shipsInMemory Vessels in RAM",
            subtitle = if (isSatellite) "Global S-AIS Enabled" else "Coastal Mode Active"
        )

        // Storage Layer
        DiagnosticCard(
            title = "Storage Layer (SQLite)",
            icon = Icons.Default.Storage,
            value = "$dbSize Track Points",
            subtitle = "48-Hour Purge Cycle Active"
        )

        // Power Layer
        DiagnosticCard(
            title = "Power Management",
            icon = Icons.Default.BatteryChargingFull,
            value = "Foreground Service Active",
            subtitle = "CPU Partial WakeLock Acquired"
        )
    }
}

@Composable
fun DiagnosticCard(
    title: String,
    icon: ImageVector,
    value: String,
    subtitle: String? = null,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp).padding(end = 12.dp)
            )
            Column {
                Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(value, style = MaterialTheme.typography.titleMedium, color = valueColor)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
