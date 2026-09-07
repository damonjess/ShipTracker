package com.example.shiptracker.ui

import android.content.Context
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.shiptracker.data.ShipCategory
import com.example.shiptracker.data.ShipState
import com.example.shiptracker.util.MarkerIconGenerator
import com.google.android.gms.maps.model.LatLng
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

// Mock data model for the UI
data class Vessel(
    val mmsi: Long,
    val name: String,
    val type: String,
    val lat: Double,
    val lng: Double,
    val length: String = ""
)

fun getShipTypeString(aisTypeCode: Int): String {
    return when (aisTypeCode) {
        in 20..29 -> "Wing in Ground"
        30 -> "Fishing"
        in 31..32 -> "Towing"
        in 36..37 -> "Pleasure Craft / Yacht"
        in 40..49 -> "High Speed Craft"
        in 60..69 -> "Passenger"
        in 70..79 -> "Cargo"
        in 80..89 -> "Tanker"
        else -> "Other ($aisTypeCode)"
    }
}

fun getShipComposeColor(aisTypeCode: Int): Color {
    return when (aisTypeCode) {
        in 30..30 -> Color.Cyan
        in 31..32 -> Color.Magenta
        in 36..37 -> Color.Yellow
        in 60..69 -> Color.Blue
        in 70..79 -> Color.Green
        in 80..89 -> Color.Red
        else -> Color.Gray
    }
}

@Composable
fun OpenShipMap(
    ships: List<ShipState>,
    modifier: Modifier = Modifier,
    trackPoints: List<LatLng> = emptyList(),
    onShipClick: (ShipState) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    // 1. Maintain an overlay layer and a reference map for active markers
    val shipOverlay = remember { FolderOverlay() }
    val trackOverlay = remember { FolderOverlay() }
    val markersMap = remember { mutableMapOf<Long, Marker>() }

    // 2. Manage MapView lifecycle to prevent memory leaks when navigating away
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapViewRef?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapViewRef?.onPause()
                Lifecycle.Event.ON_DESTROY -> mapViewRef?.onDetach()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapViewRef?.onDetach()
        }
    }

    // 3. The MapView container
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            val appCtx = ctx.applicationContext
            Configuration.getInstance().load(appCtx, appCtx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
            Configuration.getInstance().userAgentValue = "DamonShipTracker/1.0"

            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(8.0)
                controller.setCenter(GeoPoint(50.5, -1.5)) // Centered on English Channel / North Sea

                // Attach our dedicated vessel and track overlays on top of the tiles
                overlays.add(trackOverlay)
                overlays.add(shipOverlay)
                mapViewRef = this
            }
        },
        update = { mapView ->
            // Update Track Polyline
            trackOverlay.items.clear()
            if (trackPoints.size > 1) {
                val polyline = Polyline(mapView).apply {
                    outlinePaint.color = android.graphics.Color.parseColor("#00E5FF")
                    outlinePaint.strokeWidth = 8f
                    setPoints(trackPoints.map { GeoPoint(it.latitude, it.longitude) })
                }
                trackOverlay.add(polyline)
            }

            // Update Ship Markers via Object Pooling / Cache
            val activeMmsis = ships.map { it.mmsi }.toSet()

            // Remove markers for vessels that were filtered out or disappeared
            val removedMmsis = markersMap.keys - activeMmsis
            removedMmsis.forEach { mmsi ->
                markersMap.remove(mmsi)?.let { marker ->
                    shipOverlay.remove(marker)
                }
            }

            // Update existing markers or instantiate new ones
            ships.forEach { ship ->
                val existingMarker = markersMap[ship.mmsi]
                val colorInt = MarkerIconGenerator.getShipAndroidColor(ship.shipType)
                val shipIcon = MarkerIconGenerator.getTintedShipIcon(mapView.context, colorInt)

                if (existingMarker != null) {
                    // Smoothly shift position and rotation without recreating the object
                    existingMarker.position = GeoPoint(ship.latitude, ship.longitude)
                    existingMarker.icon = shipIcon
                    existingMarker.rotation = ship.heading
                    existingMarker.title = ship.name.ifEmpty { "MMSI: ${ship.mmsi}" }
                    existingMarker.snippet = getShipTypeString(ship.shipType)
                } else {
                    // Create marker for a newly detected vessel
                    val newMarker = Marker(mapView).apply {
                        position = GeoPoint(ship.latitude, ship.longitude)
                        title = ship.name.ifEmpty { "MMSI: ${ship.mmsi}" }
                        snippet = getShipTypeString(ship.shipType)
                        icon = shipIcon
                        rotation = ship.heading

                        // CRITICAL properties for directional vehicle vectors
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        isFlat = true

                        setOnMarkerClickListener { _, _ ->
                            onShipClick(ship)
                            true // Intercept event to trigger our Compose bottom sheet
                        }
                    }

                    markersMap[ship.mmsi] = newMarker
                    shipOverlay.add(newMarker)
                }
            }

            // Redraw map with updated marker positions
            mapView.invalidate()
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShipTrackerMainScreen(
    context: Context = LocalContext.current,
    viewModel: ShipViewModel = viewModel(
        factory = ShipViewModel.provideFactory(context)
    )
) {
    // Navigation State
    var selectedNavIndex by remember { mutableIntStateOf(0) }

    // Observe state from ViewModel
    val visibleShips by viewModel.visibleShips.collectAsState()
    val activeFilters by viewModel.selectedFilters.collectAsState()
    val trackPoints by viewModel.activeTrackPoints.collectAsState()

    var selectedVessel by remember { mutableStateOf<Vessel?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    Scaffold(
        bottomBar = {
            AppBottomBar(
                selectedIndex = selectedNavIndex,
                onItemSelected = { selectedNavIndex = it }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Render the OpenStreetMap with live telemetry and marker cache
            OpenShipMap(
                ships = visibleShips,
                trackPoints = trackPoints,
                onShipClick = { ship ->
                    selectedVessel = Vessel(
                        mmsi = ship.mmsi,
                        name = ship.name.ifEmpty { "MMSI: ${ship.mmsi}" },
                        type = getShipTypeString(ship.shipType),
                        lat = ship.latitude,
                        lng = ship.longitude,
                        length = if (ship.length > 0) "${ship.length}m" else ""
                    )
                    viewModel.selectVessel(ship.mmsi)
                }
            )

            // The floating search bar and filter chips sit directly on top
            MapHeader(
                activeFilters = activeFilters,
                onFilterToggle = { viewModel.toggleFilter(it) },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
            )
        }
    }

    // Modal Bottom Sheet appears when a ship marker is tapped
    if (selectedVessel != null) {
        ModalBottomSheet(
            onDismissRequest = {
                selectedVessel = null
                viewModel.clearSelection()
            },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            VesselDetailsPanel(vessel = selectedVessel!!)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapHeader(
    activeFilters: Set<ShipCategory>,
    onFilterToggle: (ShipCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        // Search Bar
        SearchBar(
            inputField = {
                SearchBarDefaults.InputField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onSearch = { isSearchActive = false },
                    expanded = isSearchActive,
                    onExpandedChange = { isSearchActive = it },
                    placeholder = { Text("Search vessels, ports...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                    trailingIcon = {
                        if (isSearchActive) {
                            IconButton(onClick = {
                                if (searchQuery.isNotEmpty()) {
                                    searchQuery = ""
                                } else {
                                    isSearchActive = false
                                }
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        }
                    }
                )
            },
            expanded = isSearchActive,
            onExpandedChange = { isSearchActive = it },
            modifier = Modifier.fillMaxWidth()
        ) {
            ListItem(
                headlineContent = { Text("Search suggestions appear here") },
                leadingContent = { Icon(Icons.Default.DirectionsBoat, contentDescription = null) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Category Filter Chips
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ShipCategory.entries.toTypedArray()) { category ->
                val isSelected = activeFilters.contains(category)

                FilterChip(
                    selected = isSelected,
                    onClick = { onFilterToggle(category) },
                    label = { Text(category.displayName) },
                    leadingIcon = if (isSelected) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        }
    }
}

@Composable
fun VesselDetailsPanel(vessel: Vessel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .padding(bottom = 32.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.LightGray),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.DirectionsBoat, contentDescription = "Ship Image")
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = vessel.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                val detailsText = if (vessel.length.isNotEmpty()) {
                    "${vessel.type} • ${vessel.length} • Last seen a minute ago"
                } else {
                    "${vessel.type} • Last seen a minute ago"
                }
                Text(
                    text = detailsText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            IconButton(onClick = { /* Follow ship */ }) {
                Icon(Icons.Default.Notifications, contentDescription = "Alerts", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = { /* Add to list */ }) {
                Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Add to List", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = { /* History */ }) {
                Icon(Icons.Default.History, contentDescription = "History", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun AppBottomBar(
    selectedIndex: Int = 0,
    onItemSelected: (Int) -> Unit = {}
) {
    NavigationBar {
        NavigationBarItem(
            selected = selectedIndex == 0,
            onClick = { onItemSelected(0) },
            icon = { Icon(Icons.Default.Public, contentDescription = "Map") },
            label = { Text("Map") }
        )
        NavigationBarItem(
            selected = selectedIndex == 1,
            onClick = { onItemSelected(1) },
            icon = { Icon(Icons.Default.Folder, contentDescription = "Lists") },
            label = { Text("My Fleets") }
        )
        NavigationBarItem(
            selected = selectedIndex == 2,
            onClick = { onItemSelected(2) },
            icon = { Icon(Icons.Default.Person, contentDescription = "Account") },
            label = { Text("Account") }
        )
    }
}
