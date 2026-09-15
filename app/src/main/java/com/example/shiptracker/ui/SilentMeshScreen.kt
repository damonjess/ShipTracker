package com.example.shiptracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.shiptracker.mesh.MeshNegotiator
import com.example.shiptracker.mesh.MeshViewModel

@Composable
fun SilentMeshScreen(
    viewModel: MeshViewModel,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    
    // 1. Initialize the Negotiator
    val meshNegotiator = remember {
        MeshNegotiator(context) { hostIp ->
            // This triggers automatically when the P2P connection locks on
            viewModel.firePayloadToHost(hostIp)
        }
    }

    // 2. Safely manage the hardware lifecycle (CRITICAL to prevent memory leaks)
    DisposableEffect(Unit) {
        meshNegotiator.register()
        viewModel.receiver.startListening() // Open the TCP port 8988
        
        onDispose {
            meshNegotiator.unregister()
            viewModel.receiver.stopListening() // Close the TCP port
        }
    }

    // 3. The Tactical UI
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center
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
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = { meshNegotiator.discoverNodes() },
            modifier = Modifier.fillMaxWidth().height(60.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Text("INITIATE MESH SYNC (OFF-GRID)")
        }
    }
}
