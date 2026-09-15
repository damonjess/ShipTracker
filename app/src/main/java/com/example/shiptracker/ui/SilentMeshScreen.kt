package com.example.shiptracker.ui

import android.net.wifi.p2p.WifiP2pDevice
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.shiptracker.mesh.MeshViewModel

@Composable
fun SilentMeshScreen(
    viewModel: MeshViewModel,
    onBack: () -> Unit = {}
) {
    // 1. Observe the live list of terminals from the ViewModel
    val discoveredPeers by viewModel.discoveredPeers.collectAsState()

    // 2. Safely manage the hardware lifecycle (CRITICAL to prevent memory leaks)
    DisposableEffect(Unit) {
        viewModel.meshNegotiator.register()
        viewModel.receiver.startListening() // Open the TCP port 8988
        
        onDispose {
            viewModel.meshNegotiator.unregister()
            viewModel.receiver.stopListening() // Close the TCP port
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        IconButton(onClick = onBack, modifier = Modifier.padding(bottom = 16.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Text(
            text = "SILENT MESH DATALINK",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Button(
            onClick = { viewModel.startScan() }, 
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text("📡 INITIATE MESH SCAN (OFF-GRID)")
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "NEARBY TERMINALS (${discoveredPeers.size})",
            style = MaterialTheme.typography.titleMedium
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // 3. The Radar List
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(discoveredPeers) { device ->
                TerminalCard(
                    device = device,
                    onConnectClick = { 
                        // Call your negotiator's connectToDevice() here via the ViewModel
                        viewModel.connectToTarget(device) 
                    }
                )
            }
        }
    }
}

@Composable
fun TerminalCard(device: WifiP2pDevice, onConnectClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(text = device.deviceName ?: "Unknown Device", style = MaterialTheme.typography.titleMedium)
                Text(text = "MAC: ${device.deviceAddress}", style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = onConnectClick) {
                Text("CONNECT")
            }
        }
    }
}
