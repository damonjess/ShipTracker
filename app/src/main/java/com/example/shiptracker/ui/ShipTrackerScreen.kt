package com.example.shiptracker.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.shiptracker.R
import com.example.shiptracker.data.ShipCategory
import com.example.shiptracker.data.ShipState
import com.example.shiptracker.util.MarkerIconGenerator
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.launch
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class Vessel(
    val mmsi: Long,
    val name: String,
    val type: String,
    val lat: Double,
    val lng: Double,
    val length: String = "",
    val speed: String = "",
    val course: String = "",
    val destination: String = "",
    val draught: String = "",
    val status: String = "",
    val navStatusText: String = "",
    val navStatusCode: Int = 15,
    val heading: Float = 0f,
    val lastReport: String = "",
    val flagEmoji: String = "🇬🇧"
)

fun getShipTypeString(aisTypeCode: Int): String {
    return when (aisTypeCode) {
        in 20..29 -> "Wing in Ground"
        30 -> "Fishing vessel"
        in 31..32 -> "Towing"
        in 36..37 -> "Pleasure Craft / Yacht"
        in 40..49 -> "High Speed Craft"
        in 60..69 -> "Passenger vessel"
        in 70..79 -> "Cargo vessel"
        in 80..89 -> "Tanker"
        else -> "Other ($aisTypeCode)"
    }
}

fun getNavStatusString(navStatus: Int): String {
    return when (navStatus) {
        0 -> "Under way using engine"
        1 -> "At anchor"
        2 -> "Not under command"
        3 -> "Restricted manoeuvrability"
        4 -> "Constrained by draught"
        5 -> "Moored"
        6 -> "Aground"
        7 -> "Engaged in fishing"
        8 -> "Under way sailing"
        14 -> "AIS-SART active"
        15 -> "Undefined"
        else -> "Status $navStatus"
    }
}

fun getCountryFlag(mmsi: Long): String {
    val mid = (mmsi / 1_000_000).toInt()
    return when (mid) {
        in 232..235 -> "🇬🇧"
        in 244..246 -> "🇳🇱"
        205 -> "🇧🇪"
        211, 218 -> "🇩🇪"
        in 226..228 -> "🇫🇷"
        in 257..259 -> "🇳🇴"
        in 219..220 -> "🇩🇰"
        in 265..266 -> "🇸🇪"
        in 366..369 -> "🇺🇸"
        else -> "🌐"
    }
}

fun formatLastReportTimestamp(lastSeenMillis: Long): String {
    val formatter = SimpleDateFormat("MMM dd, yyyy HH:mm 'UTC'", Locale.US)
    formatter.timeZone = TimeZone.getTimeZone("UTC")
    return formatter.format(Date(lastSeenMillis))
}

// Esri Light Map Tile Source
val EsriWorldStreetMapTileSource = object : OnlineTileSourceBase(
    "EsriWorldStreetMap",
    0, 19, 256, ".jpg",
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/")
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        val zoom = MapTileIndex.getZoom(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        val x = MapTileIndex.getX(pMapTileIndex)
        return "$baseUrl$zoom/$y/$x$mImageFilenameEnding"
    }
}

// Esri Dark Map Tile Source
val EsriDarkGrayCanvasTileSource = object : OnlineTileSourceBase(
    "EsriDarkGrayCanvas",
    0, 19, 256, ".png",
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Base/MapServer/tile/")
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        val zoom = MapTileIndex.getZoom(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        val x = MapTileIndex.getX(pMapTileIndex)
        return "$baseUrl$zoom/$y/$x$mImageFilenameEnding"
    }
}

@Composable
fun OpenShipMap(
    ships: List<ShipState>,
    modifier: Modifier = Modifier,
    trackPoints: List<LatLng> = emptyList(),
    panTarget: ShipState? = null,
    recenterTrigger: Int = 0,
    onPanConsumed: () -> Unit = {},
    onViewportChanged: (north: Double, south: Double, east: Double, west: Double) -> Unit = { _, _, _, _ -> },
    onShipClick: (ShipState) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isDark = isSystemInDarkTheme()
    val currentOnShipClick = rememberUpdatedState(onShipClick)
    val currentOnViewportChanged = rememberUpdatedState(onViewportChanged)

    val activeTileSource = if (isDark) EsriDarkGrayCanvasTileSource else EsriWorldStreetMapTileSource

    val mapView = remember {
        MapView(context).apply {
            setMultiTouchControls(true)
            controller.setZoom(10.0)
            controller.setCenter(GeoPoint(53.6, -0.1))

            addMapListener(object : MapListener {
                override fun onScroll(event: ScrollEvent?): Boolean {
                    val box = boundingBox
                    currentOnViewportChanged.value(box.latNorth, box.latSouth, box.lonEast, box.lonWest)
                    return false
                }

                override fun onZoom(event: ZoomEvent?): Boolean {
                    val box = boundingBox
                    currentOnViewportChanged.value(box.latNorth, box.latSouth, box.lonEast, box.lonWest)
                    return false
                }
            })
        }
    }

    LaunchedEffect(isDark) {
        mapView.setTileSource(activeTileSource)
        mapView.invalidate()
    }

    LaunchedEffect(panTarget) {
        panTarget?.let { target ->
            mapView.controller.animateTo(GeoPoint(target.latitude, target.longitude), 14.0, 1000L)
            onPanConsumed()
        }
    }

    LaunchedEffect(recenterTrigger) {
        if (recenterTrigger > 0) {
            mapView.controller.animateTo(GeoPoint(53.6, -0.1), 10.0, 800L)
        }
    }

    val shipOverlay = remember { FolderOverlay().also { mapView.overlays.add(it) } }
    val trackOverlay = remember { FolderOverlay().also { mapView.overlays.add(it) } }
    val markersMap = remember { mutableMapOf<Long, Marker>() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_DESTROY -> mapView.onDetach()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { mapView }
    )

    val trackColorHex = if (isDark) "#00E5FF" else "#0077CC"
    LaunchedEffect(ships, trackPoints, isDark) {
        trackOverlay.items.clear()
        if (trackPoints.size > 1) {
            val polyline = Polyline(mapView).apply {
                outlinePaint.color = android.graphics.Color.parseColor(trackColorHex)
                outlinePaint.strokeWidth = 8f
                setPoints(trackPoints.map { GeoPoint(it.latitude, it.longitude) })
            }
            trackOverlay.add(polyline)
        }

        val activeMmsis = ships.map { it.mmsi }.toSet()
        val removedMmsis = markersMap.keys - activeMmsis
        removedMmsis.forEach { mmsi ->
            markersMap.remove(mmsi)?.let { shipOverlay.remove(it) }
        }

        ships.forEach { ship ->
            val existingMarker = markersMap[ship.mmsi]
            if (existingMarker != null) {
                existingMarker.position = GeoPoint(ship.latitude, ship.longitude)
                existingMarker.rotation = ship.heading
                existingMarker.relatedObject = ship
            } else {
                val colorInt = MarkerIconGenerator.getShipAndroidColor(ship.shipType)
                val shipIcon = MarkerIconGenerator.getTintedShipIcon(context, colorInt)

                val newMarker = Marker(mapView).apply {
                    position = GeoPoint(ship.latitude, ship.longitude)
                    icon = shipIcon
                    rotation = ship.heading
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    isFlat = true
                    relatedObject = ship

                    setOnMarkerClickListener { clickedMarker, _ ->
                        val clickedShip = clickedMarker.relatedObject as? ShipState
                        if (clickedShip != null) {
                            currentOnShipClick.value(clickedShip)
                        }
                        true
                    }
                }
                markersMap[ship.mmsi] = newMarker
                shipOverlay.add(newMarker)
            }
        }

        mapView.invalidate()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShipTrackerMainScreen(
    context: Context = LocalContext.current,
    viewModel: ShipViewModel = viewModel(
        factory = ShipViewModel.provideFactory(context)
    )
) {
    var selectedNavIndex by remember { mutableIntStateOf(0) }

    val visibleShips by viewModel.visibleShips.collectAsState()
    val activeFilters by viewModel.selectedFilters.collectAsState()
    val trackPoints by viewModel.activeTrackPoints.collectAsState()

    var selectedVessel by remember { mutableStateOf<Vessel?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    var panTarget by remember { mutableStateOf<ShipState?>(null) }
    var recenterTrigger by remember { mutableIntStateOf(0) }

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
            OpenShipMap(
                ships = visibleShips,
                trackPoints = trackPoints,
                panTarget = panTarget,
                recenterTrigger = recenterTrigger,
                onPanConsumed = { panTarget = null },
                onViewportChanged = { north, south, east, west ->
                    viewModel.updateViewport(north, south, east, west)
                },
                onShipClick = { ship ->
                    selectedVessel = Vessel(
                        mmsi = ship.mmsi,
                        name = ship.name.ifEmpty { "MMSI: ${ship.mmsi}" },
                        type = getShipTypeString(ship.shipType),
                        lat = ship.latitude,
                        lng = ship.longitude,
                        length = if (ship.length > 0) "${ship.length} m" else "",
                        speed = if (ship.speed > 0f) "${ship.speed} kn" else "-",
                        course = "${ship.heading.toInt()}°",
                        destination = ship.destination,
                        draught = if (ship.draught > 0f) "${ship.draught} m" else "-",
                        status = "Live AIS position",
                        navStatusText = getNavStatusString(ship.navStatus),
                        navStatusCode = ship.navStatus,
                        heading = ship.heading,
                        lastReport = formatLastReportTimestamp(ship.lastSeenMillis),
                        flagEmoji = getCountryFlag(ship.mmsi)
                    )
                    viewModel.selectVessel(ship.mmsi)
                }
            )

            Text(
                text = "© Esri & OpenStreetMap contributors",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            )

            FloatingActionButton(
                onClick = { recenterTrigger++ },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 40.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(
                    imageVector = Icons.Default.CenterFocusStrong,
                    contentDescription = "Recenter Fleet Map"
                )
            }

            MapHeader(
                activeFilters = activeFilters,
                onFilterToggle = { viewModel.toggleFilter(it) },
                ships = visibleShips,
                onShipSearchSelected = { ship ->
                    panTarget = ship
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
            )
        }
    }

    if (selectedVessel != null) {
        ModalBottomSheet(
            onDismissRequest = {
                selectedVessel = null
                viewModel.clearSelection()
            },
            sheetState = sheetState,
            dragHandle = null,
            containerColor = Color.White,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        ) {
            VesselDetailsPanel(
                vessel = selectedVessel!!,
                onClose = {
                    selectedVessel = null
                    viewModel.clearSelection()
                },
                onTrackClick = {
                    val shipToTrack = visibleShips.find { it.mmsi == selectedVessel?.mmsi }
                    if (shipToTrack != null) {
                        panTarget = shipToTrack
                    } else {
                        panTarget = ShipState(
                            mmsi = selectedVessel!!.mmsi,
                            latitude = selectedVessel!!.lat,
                            longitude = selectedVessel!!.lng,
                            name = selectedVessel!!.name
                        )
                    }
                    selectedVessel = null
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapHeader(
    activeFilters: Set<ShipCategory>,
    onFilterToggle: (ShipCategory) -> Unit,
    ships: List<ShipState>,
    onShipSearchSelected: (ShipState) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    val searchResults = if (searchQuery.isBlank()) {
        emptyList()
    } else {
        ships.filter { it.name.contains(searchQuery, ignoreCase = true) }.take(5)
    }

    val categoryCounts = remember(ships) {
        ShipCategory.entries.associateWith { cat ->
            ships.count { ship -> ship.shipType in cat.typeCodes }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        SearchBar(
            inputField = {
                SearchBarDefaults.InputField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onSearch = { isSearchActive = false },
                    expanded = isSearchActive,
                    onExpandedChange = { isSearchActive = it },
                    placeholder = { Text("Search ${ships.size} active vessels...") },
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
            searchResults.forEach { ship ->
                ListItem(
                    headlineContent = { Text(ship.name.ifEmpty { "MMSI: ${ship.mmsi}" }) },
                    supportingContent = { Text(getShipTypeString(ship.shipType)) },
                    leadingContent = { Icon(Icons.Default.DirectionsBoat, contentDescription = null) },
                    modifier = Modifier.clickable {
                        isSearchActive = false
                        searchQuery = ""
                        onShipSearchSelected(ship)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ShipCategory.entries.toTypedArray()) { category ->
                val isSelected = activeFilters.contains(category)
                val count = categoryCounts[category] ?: 0

                FilterChip(
                    selected = isSelected,
                    onClick = { onFilterToggle(category) },
                    label = { Text("${category.displayName} ($count)") },
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
fun VesselDetailsPanel(
    vessel: Vessel,
    onClose: () -> Unit = {},
    onTrackClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var portCallsExpanded by remember { mutableStateOf(false) }
    var weatherExpanded by remember { mutableStateOf(false) }
    var isInFleet by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .background(Color.White)
    ) {
        // 1. TOP BLUE BANNER
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF235DB2))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = vessel.flagEmoji,
                fontSize = 24.sp,
                modifier = Modifier.padding(end = 10.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = vessel.name.uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = vessel.type,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White
                )
            }
        }

        // 2. HERO IMAGE
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .background(Color(0xFF1E293B))
        ) {
            val imageRequest = remember(vessel.mmsi) {
                ImageRequest.Builder(context)
                    .data("https://photos.marinetraffic.com/ais/showphoto.aspx?mmsi=${vessel.mmsi}")
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .crossfade(true)
                    .build()
            }

            AsyncImage(
                model = imageRequest,
                contentDescription = "Photo of ${vessel.name}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                error = painterResource(id = R.drawable.ic_ship_arrow)
            )

            Text(
                text = "ShipTracker",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.6f),
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
            )
        }

        // 3. ACTION BUTTONS ROW
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ActionButton(
                icon = Icons.Default.Info,
                label = "Details",
                color = Color(0xFF235DB2),
                onClick = {
                    portCallsExpanded = true
                    weatherExpanded = true
                    coroutineScope.launch {
                        scrollState.animateScrollTo(scrollState.maxValue)
                    }
                    Toast.makeText(context, "Expanded details for ${vessel.name}", Toast.LENGTH_SHORT).show()
                }
            )

            ActionButton(
                icon = Icons.Default.DirectionsBoat,
                label = "Track",
                color = Color(0xFF235DB2),
                onClick = {
                    Toast.makeText(context, "Tracking ${vessel.name} on map", Toast.LENGTH_SHORT).show()
                    onTrackClick()
                }
            )

            ActionButton(
                icon = Icons.Default.AddAPhoto,
                label = "Add photo",
                color = Color.Gray,
                onClick = {
                    Toast.makeText(context, "Photo upload for ${vessel.name} coming soon!", Toast.LENGTH_SHORT).show()
                }
            )

            ActionButton(
                icon = if (isInFleet) Icons.Default.Star else Icons.Default.StarOutline,
                label = if (isInFleet) "In fleet" else "Add to fleet",
                color = if (isInFleet) Color(0xFFFFB300) else Color.Gray,
                onClick = {
                    isInFleet = !isInFleet
                    val msg = if (isInFleet) "Added ${vessel.name} to My Fleets" else "Removed ${vessel.name} from My Fleets"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            )
        }

        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))

        // 4. DESTINATION SECTION
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                color = Color.LightGray.copy(alpha = 0.3f),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = "?", fontWeight = FontWeight.Bold, color = Color.DarkGray)
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(text = "Destination", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                Text(
                    text = if (vessel.destination.isNotBlank() && vessel.destination != "UNKNOWN") vessel.destination else "Destination not available",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Text(text = "ETA: -", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color.Black)
            }
        }

        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))

        // 5. TELEMETRY GRID
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Navigation,
                contentDescription = null,
                tint = Color(0xFF235DB2),
                modifier = Modifier
                    .size(24.dp)
                    .rotate(vessel.heading)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(text = "Speed:", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text(text = vessel.speed.ifEmpty { "-" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color.Black)
                }
                Column {
                    Text(text = "Course:", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text(text = vessel.course.ifEmpty { "${vessel.heading.toInt()}°" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color.Black)
                }
                Column {
                    Text(text = "Draught:", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text(text = vessel.draught.ifEmpty { "-" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color.Black)
                }
            }
        }

        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(text = "Status:", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Text(text = vessel.navStatusText.ifEmpty { "-" }, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = Color.Black)
            }
            Column {
                Text(text = "Last report:", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Text(text = vessel.lastReport, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color.Black)
            }
        }

        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))

        // 6. POSITION & REPORT BLOCK
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = "Current Position", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = vessel.flagEmoji, fontSize = 16.sp, modifier = Modifier.padding(end = 6.dp))
                Text(
                    text = "${String.format(Locale.US, "%.4f", vessel.lat)}°, ${String.format(Locale.US, "%.4f", vessel.lng)}°",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF235DB2)
                )
            }
            Text(
                text = "Last AIS report: ${vessel.lastReport}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        }

        // 7. BLUE SECTION ACCORDION BANNERS
        AccordionHeader(
            title = "PORT CALLS",
            expanded = portCallsExpanded,
            onClick = { portCallsExpanded = !portCallsExpanded }
        )
        if (portCallsExpanded) {
            Text(
                text = "No recent port calls recorded",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(12.dp)
            )
        }

        AccordionHeader(
            title = "WEATHER",
            expanded = weatherExpanded,
            onClick = { weatherExpanded = !weatherExpanded }
        )
        if (weatherExpanded) {
            Text(
                text = "Wind: 12 kn NE • Waves: 0.8 m • Temp: 16°C",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Black,
                modifier = Modifier.padding(12.dp)
            )
        }

        AccordionHeader(title = "VESSEL PARTICULARS", expanded = true, onClick = {})

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ParticularItem(label = "Gross Tonnage:", value = "-")
                ParticularItem(label = "Built:", value = "-")
                ParticularItem(label = "IMO:", value = "-")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ParticularItem(label = "Deadweight:", value = "-")
                ParticularItem(label = "Size:", value = vessel.length.ifEmpty { "-" })
                ParticularItem(label = "MMSI:", value = vessel.mmsi.toString())
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun ActionButton(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit = {}
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = color, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
fun AccordionHeader(
    title: String,
    expanded: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF235DB2))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Icon(
            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = Color.White
        )
    }
}

@Composable
fun ParticularItem(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color.Black)
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

@Preview(showBackground = true)
@Composable
fun VesselDetailsPanelPreview() {
    val sampleVessel = Vessel(
        mmsi = 232048202L,
        name = "BRAVE GRIFF",
        type = "Fishing vessel",
        lat = 53.6,
        lng = -0.1,
        length = "-",
        speed = "4.9 kn",
        course = "103°",
        destination = "Destination not available",
        draught = "-",
        status = "Live AIS position",
        navStatusText = "Under way using engine",
        navStatusCode = 0,
        heading = 103f,
        lastReport = "Sep 07, 2026 12:50 UTC",
        flagEmoji = "🇬🇧"
    )
    VesselDetailsPanel(vessel = sampleVessel)
}
