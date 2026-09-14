package com.example.shiptracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.shiptracker.data.ShipState

@Composable
fun MyFleetsScreen(
    viewModel: ShipViewModel,
    onNavigateToMap: () -> Unit
) {
    val favoriteMmsis by viewModel.favoriteMmsis.collectAsState()
    val allShips by viewModel.allShips.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF8FAFC))
    ) {
        // 1. Header
        Surface(
            color = Color.White,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "My Fleets",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                Text(
                    text = "${favoriteMmsis.size} Saved Vessels (Port Geofencing Active)",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF64748B)
                )
            }
        }

        // 2. Main Content
        if (favoriteMmsis.isEmpty()) {
            // Empty State
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.StarBorder,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color(0xFF94A3B8)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Your watchlist is empty", 
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = "Tap the star icon on any vessel to save it.", 
                        color = Color(0xFF64748B),
                        modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
                    )
                    Button(
                        onClick = onNavigateToMap,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                    ) {
                        Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Explore Map")
                    }
                }
            }
        } else {
            // Watchlist Feed
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(favoriteMmsis.toList()) { mmsi ->
                    val ship = allShips.find { it.mmsi == mmsi }
                    
                    FleetVesselCard(
                        mmsi = mmsi,
                        ship = ship,
                        onRemove = { viewModel.toggleFavorite(mmsi) },
                        onClick = {
                            // Select it, lock the camera to it, and jump to the map!
                            viewModel.selectVessel(mmsi)
                            viewModel.toggleFollow(mmsi) 
                            onNavigateToMap()
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FleetVesselCard(
    mmsi: Long,
    ship: ShipState?,
    onRemove: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Colored Status Icon
            Surface(
                shape = CircleShape,
                color = if (ship != null) Color(0xFFE0F2FE) else Color(0xFFFEF3C7),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DirectionsBoat,
                    contentDescription = null,
                    tint = if (ship != null) Color(0xFF0284C7) else Color(0xFFD97706),
                    modifier = Modifier.padding(12.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Ship Details
            Column(modifier = Modifier.weight(1f)) {
                if (ship != null) {
                    Text(
                        text = ship.name.ifBlank { "Vessel $mmsi" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    
                    val destText = if (ship.destination.isNotBlank() && ship.destination != "UNKNOWN") {
                        "Going to ${ship.destination}"
                    } else "Destination not set"
                    
                    Text(
                        text = "${"%.1f".format(ship.speed)} kn • $destText",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF64748B)
                    )
                } else {
                    Text(
                        text = "MMSI: $mmsi",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = "Waiting for live satellite signal...",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFD97706),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Un-star Button
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "Remove from Favorites",
                    tint = Color(0xFFFFD700)
                )
            }
        }
    }
}
