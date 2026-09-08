package com.example.shiptracker.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.Domain
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WbSunny
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.example.shiptracker.R
import com.example.shiptracker.data.ShipCategory
import com.example.shiptracker.data.ShipState
import com.example.shiptracker.util.MarkerIconGenerator
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
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
    val flagEmoji: String = "🇬🇧",
    val yearBuilt: String = "N/A",
    val passengers: String = "N/A"
)

fun getShipTypeString(aisTypeCode: Int): String {
    return when (aisTypeCode) {
        in 20..29 -> "Wing in Ground (WIG)"
        30 -> "Fishing vessel"
        in 31..32 -> "Towing / Tug"
        in 33..35 -> "Dredging / Military ops"
        in 36..37 -> "Pleasure Craft / Sailing"
        in 40..49 -> "High Speed Craft (HSC)"
        in 50..59 -> "Pilot / SAR / Special"
        in 60..69 -> "Passenger vessel"
        in 70..79 -> "Cargo vessel"
        in 80..89 -> "Tanker"
        in 90..99 -> "Other / Special"
        else -> "Unspecified ($aisTypeCode)"
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
    onViewportChanged: (north: Double, south: Double, east: Double, west: Double, zoom: Double) -> Unit = { _, _, _, _, _ -> },
    onShipClick: (ShipState) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isDark = isSystemInDarkTheme()
    val currentOnShipClick = rememberUpdatedState(onShipClick)
    val currentOnViewportChanged = rememberUpdatedState(onViewportChanged)

    // Remove the `if (isDark)` check to force the nautical chart look
    val activeTileSource = EsriWorldStreetMapTileSource

    val mapView = remember {
        MapView(context).apply {
            setMultiTouchControls(true)
            controller.setZoom(10.0)
            controller.setCenter(GeoPoint(53.6, -0.1))

            addMapListener(object : MapListener {
                override fun onScroll(event: ScrollEvent?): Boolean {
                    val box = boundingBox
                    currentOnViewportChanged.value(box.latNorth, box.latSouth, box.lonEast, box.lonWest, zoomLevelDouble)
                    return false
                }

                override fun onZoom(event: ZoomEvent?): Boolean {
                    val box = boundingBox
                    currentOnViewportChanged.value(box.latNorth, box.latSouth, box.lonEast, box.lonWest, zoomLevelDouble)
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

            val colorInt = MarkerIconGenerator.getShipAndroidColor(ship.shipType)
            // 🚨 Pass the whole 'ship' object into the new generator
            val shipIcon = MarkerIconGenerator.getTintedShipIcon(context, ship, colorInt)

            if (existingMarker != null) {
                existingMarker.position = GeoPoint(ship.latitude, ship.longitude)
                existingMarker.rotation = if (ship.shipType == -1) 0f else ship.heading // Stop clusters from spinning
                existingMarker.relatedObject = ship
                existingMarker.icon = shipIcon // Refresh icon so clusters update their numbers
            } else {
                val newMarker = Marker(mapView).apply {
                    position = GeoPoint(ship.latitude, ship.longitude)
                    icon = shipIcon
                    rotation = if (ship.shipType == -1) 0f else ship.heading
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    isFlat = true
                    relatedObject = ship

                    setOnMarkerClickListener { clickedMarker, _ ->
                        val clickedShip = clickedMarker.relatedObject as? ShipState
                        if (clickedShip != null) {
                            if (clickedShip.shipType == -1) {
                                // 4. If they tap a cluster, smoothly zoom in closer!
                                mapView.controller.animateTo(clickedMarker.position, mapView.zoomLevelDouble + 2.0, 500L)
                            } else {
                                // 5. If they tap a normal ship, open the bottom sheet
                                currentOnShipClick.value(clickedShip)
                            }
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
                onViewportChanged = { north, south, east, west, zoom ->
                    viewModel.updateViewport(north, south, east, west)
                    viewModel.updateZoom(zoom)
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
                        flagEmoji = getCountryFlag(ship.mmsi),
                        yearBuilt = "N/A",
                        passengers = if (ship.shipType in 60..69) "Available" else "N/A"
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
    val scrollState = rememberScrollState()
    
    // The exact blue from the screenshot
    val marineBlue = Color(0xFF3483C4)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .verticalScroll(scrollState)
            .navigationBarsPadding()
    ) {
        // 1. TOP BLUE BANNER (Flat)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(marineBlue)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = vessel.flagEmoji, fontSize = 28.sp, modifier = Modifier.padding(end = 12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = vessel.name, // Not uppercase, matching screenshot
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = vessel.type,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White
                )
            }
            IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }

        // 2. HERO IMAGE (No gradient, no text overlay)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(Color(0xFFE0E0E0)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.DirectionsBoat,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(80.dp)
            )

            val imageRequest = remember(vessel.mmsi) {
                ImageRequest.Builder(context)
                    .data("https://photos.marinetraffic.com/ais/showphoto.aspx?mmsi=${vessel.mmsi}")
                    .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13; SM-S918B) AppleWebKit/537.36")
                    .addHeader("Referer", "https://www.marinetraffic.com/")
                    .crossfade(true)
                    .build()
            }

            AsyncImage(
                model = imageRequest,
                contentDescription = "Photo of ${vessel.name}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 3. BLUE ACTION BAR
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(marineBlue)
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            BlueActionButton(icon = Icons.Default.Star, label = "Review", modifier = Modifier.weight(1f))
            BlueActionButton(icon = Icons.Default.Domain, label = "Deckplans", modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 4. THE 3-COLUMN DATA GRID
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            // Top Row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BlueGridCell(
                    icon = Icons.Default.DateRange,
                    label = "Year of built",
                    value = vessel.yearBuilt.ifEmpty { "N/A" },
                    modifier = Modifier.weight(1f)
                )
                BlueGridCell(
                    icon = Icons.Default.Straighten,
                    label = "Length (LOA)",
                    value = vessel.length.ifEmpty { "n/a" },
                    modifier = Modifier.weight(1f)
                )
                BlueGridCell(
                    icon = Icons.Default.People,
                    label = "Passengers",
                    value = vessel.passengers.ifEmpty { "N/A" },
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Bottom Row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BlueGridCell(
                    icon = Icons.Default.NearMe,
                    label = "Destination",
                    value = vessel.destination.ifEmpty { "n/a" },
                    modifier = Modifier.weight(1f)
                )
                BlueGridCell(
                    icon = Icons.Default.Schedule,
                    label = "Last Report", // Swapped ETA for Last Report since we have that data
                    value = vessel.lastReport,
                    modifier = Modifier.weight(1f)
                )
                BlueGridCell(
                    icon = Icons.Default.Speed,
                    label = "Speed",
                    value = vessel.speed.ifEmpty { "n/a" },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(16.dp))

        // 5. WEATHER FOOTER (Visual Placeholder matching your screenshot)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            WeatherCell(icon = Icons.Default.WbSunny, iconTint = Color(0xFFFFC107), topText = "63°F", bottomText = "17.3°C")
            WeatherCell(icon = Icons.Default.Explore, iconTint = Color.Gray, topText = "Fresh breeze", bottomText = "8.2 m/s")
            WeatherCell(icon = Icons.Default.Thermostat, iconTint = Color.Gray, topText = "65 °F / 19 °C", bottomText = "54 °F / 12 °C", topColor = Color.Red, bottomColor = marineBlue)
        }

        Spacer(modifier = Modifier.height(48.dp))
    }
}

// --- NEW HELPER COMPOSABLES ---

@Composable
fun BlueActionButton(icon: ImageVector, label: String, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.clickable { /* Handle click */ }
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(24.dp))
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = Color.White)
    }
}

@Composable
fun BlueGridCell(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    val marineBlue = Color(0xFF3483C4)
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, tint = marineBlue, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = marineBlue)
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = marineBlue, thickness = 1.5.dp)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.DarkGray,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun WeatherCell(
    icon: ImageVector, 
    iconTint: Color, 
    topText: String, 
    bottomText: String, 
    topColor: Color = Color.DarkGray, 
    bottomColor: Color = Color.Gray
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(36.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(text = topText, style = MaterialTheme.typography.labelMedium, color = topColor)
            Text(text = bottomText, style = MaterialTheme.typography.labelMedium, color = bottomColor)
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

@Preview(showBackground = true)
@Composable
fun VesselDetailsPanelPreview() {
    val sampleVessel = Vessel(
        mmsi = 232048202L,
        name = "BRAVE GRIFF",
        type = "Passenger vessel",
        lat = 53.6,
        lng = -0.1,
        length = "120 m",
        speed = "14.9 kn",
        course = "103°",
        destination = "Rotterdam",
        draught = "4.2 m",
        status = "Live AIS position",
        navStatusText = "Under way using engine",
        navStatusCode = 0,
        heading = 103f,
        lastReport = "Sep 07, 2026 12:50 UTC",
        flagEmoji = "🇬🇧",
        yearBuilt = "2018",
        passengers = "450"
    )
    VesselDetailsPanel(vessel = sampleVessel)
}

suspend fun fetchWikipediaImageFallback(shipName: String): String? = withContext(Dispatchers.IO) {
    val client = OkHttpClient()
    // Append "ship" to the query to heavily weight the search towards vessels
    val query = URLEncoder.encode("$shipName ship", "UTF-8")
    
    // The Wikipedia API endpoint to search and return primary page thumbnails
    val url = "https://en.wikipedia.org/w/api.php?action=query&generator=search&gsrsearch=$query&prop=pageimages&pithumbsize=800&format=json"
    
    try {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            val jsonString = response.body?.string() ?: return@withContext null
            val json = JSONObject(jsonString)
            
            // Navigate the JSON tree: query -> pages -> {first_page} -> thumbnail -> source
            val pages = json.optJSONObject("query")?.optJSONObject("pages")
            if (pages != null && pages.keys().hasNext()) {
                val firstPageKey = pages.keys().next()
                val page = pages.optJSONObject(firstPageKey)
                return@withContext page?.optJSONObject("thumbnail")?.optString("source")
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return@withContext null
}
