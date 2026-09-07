package com.example.shiptracker.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.Rect
import android.view.MotionEvent
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
import kotlin.math.hypot
import java.util.Locale
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.shiptracker.data.ShipCategory
import com.example.shiptracker.data.ShipState
import com.example.shiptracker.util.MarkerIconGenerator
import com.google.android.gms.maps.model.LatLng
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay

// Mock data model for the UI
data class Vessel(
    val mmsi: Long,
    val name: String,
    val type: String,
    val lat: Double,
    val lng: Double,
    val length: String = "",
    val heading: Float = 0f
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

    val trackOverlay = remember { FolderOverlay() }
    val currentShips = remember { mutableStateOf<List<ShipState>>(emptyList()) }
    val currentOnShipClick = remember { mutableStateOf(onShipClick) }
    currentShips.value = ships
    currentOnShipClick.value = onShipClick

    var mapViewRef by remember { mutableStateOf<MapView?>(null) }

    val vesselOverlay = remember {
        object : Overlay() {
            val haloFillPaints = mutableMapOf<Int, Paint>()
            val haloStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val arrowStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val tmpPoint = Point()
            val tmpPath = Path()

            private fun getHaloFillPaint(colorInt: Int): Paint {
                return haloFillPaints.getOrPut(colorInt) {
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.FILL
                        color = colorInt
                        alpha = 250
                    }
                }
            }

            override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
                super.draw(canvas, mapView, shadow)
                if (shadow) return
                val density = mapView.resources.displayMetrics.density
                val radius = 46f * density
                val proj = mapView.projection

                haloStrokePaint.apply {
                    isAntiAlias = true
                    style = Paint.Style.STROKE
                    strokeWidth = 9f * density
                    color = android.graphics.Color.WHITE
                }
                shadowPaint.apply {
                    isAntiAlias = true
                    style = Paint.Style.STROKE
                    strokeWidth = 3f * density
                    color = android.graphics.Color.BLACK
                    alpha = 120
                }

                currentShips.value.forEach { ship ->
                    val geo = GeoPoint(ship.latitude, ship.longitude)
                    proj.toPixels(geo, tmpPoint)
                    val cx = tmpPoint.x.toFloat()
                    val cy = tmpPoint.y.toFloat()
                    val colorInt = MarkerIconGenerator.getShipAndroidColor(ship.shipType)
                    val fill = getHaloFillPaint(colorInt)

                    canvas.drawCircle(cx, cy, radius + 3f * density, shadowPaint)
                    canvas.drawCircle(cx, cy, radius, fill)
                    canvas.drawCircle(cx, cy, radius, haloStrokePaint)

                    val headDeg = ship.heading.toDouble()
                    val headRad = Math.toRadians(headDeg - 90.0)
                    val arrowLen = 54f * density
                    val arrowBase = 26f * density

                    val tipX = cx + (arrowLen * kotlin.math.cos(headRad)).toFloat()
                    val tipY = cy + (arrowLen * kotlin.math.sin(headRad)).toFloat()
                    val perpRad = headRad + Math.PI / 2.0
                    val perpDX = (arrowBase * kotlin.math.cos(perpRad)).toFloat()
                    val perpDY = (arrowBase * kotlin.math.sin(perpRad)).toFloat()
                    val backX = cx - (arrowLen * 0.35f * kotlin.math.cos(headRad)).toFloat()
                    val backY = cy - (arrowLen * 0.35f * kotlin.math.sin(headRad)).toFloat()

                    tmpPath.reset()
                    tmpPath.moveTo(tipX, tipY)
                    tmpPath.lineTo(backX + perpDX, backY + perpDY)
                    tmpPath.lineTo(backX - perpDX, backY - perpDY)
                    tmpPath.close()

                    arrowPaint.apply {
                        style = Paint.Style.FILL
                        color = android.graphics.Color.BLACK
                        alpha = 255
                    }
                    canvas.drawPath(tmpPath, arrowPaint)

                    arrowStrokePaint.apply {
                        isAntiAlias = true
                        style = Paint.Style.STROKE
                        strokeWidth = 3f * density
                        color = android.graphics.Color.WHITE
                        alpha = 255
                    }
                    canvas.drawPath(tmpPath, arrowStrokePaint)
                }
            }

            override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
                val density = mapView.resources.displayMetrics.density
                val hitRadius = 220f * density
                val tapX = e.x
                val tapY = e.y
                val proj = mapView.projection
                var nearest: ShipState? = null
                var nearestDist = Float.MAX_VALUE
                currentShips.value.forEach { ship ->
                    val geo = GeoPoint(ship.latitude, ship.longitude)
                    proj.toPixels(geo, tmpPoint)
                    val dx = tmpPoint.x - tapX
                    val dy = tmpPoint.y - tapY
                    val d = kotlin.math.hypot(dx, dy)
                    if (d < nearestDist) {
                        nearestDist = d
                        nearest = ship
                    }
                }
                val theNearest = nearest
                if (theNearest != null && nearestDist <= hitRadius) {
                    currentOnShipClick.value(theNearest)
                    return true
                }
                return super.onSingleTapConfirmed(e, mapView)
            }

            override fun onTouchEvent(e: MotionEvent, mapView: MapView): Boolean {
                return false
            }
        }
    }

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

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            MapView(ctx).apply {
                val baseSource = XYTileSource(
                    "OpenStreetMapDE",
                    0,
                    19,
                    256,
                    ".png",
                    arrayOf("https://tile.openstreetmap.de/")
                )
                setTileSource(baseSource)
                setMultiTouchControls(true)
                controller.setZoom(8.0)
                controller.setCenter(GeoPoint(50.5, -1.5))

                val seamarkSource = XYTileSource(
                    "OpenSeaMap",
                    0, 19, 256, ".png",
                    arrayOf("https://tiles.openseamap.org/seamark/")
                )
                val seamarkProvider = MapTileProviderBasic(ctx, seamarkSource)
                val seamarkOverlay = TilesOverlay(seamarkProvider, ctx).apply {
                    loadingBackgroundColor = android.graphics.Color.TRANSPARENT
                }

                overlays.add(seamarkOverlay)
                overlays.add(trackOverlay)
                overlays.add(vesselOverlay)

                mapViewRef = this
            }
        },
        update = { mapView ->
            trackOverlay.items.clear()
            if (trackPoints.size > 1) {
                val polyline = Polyline(mapView).apply {
                    outlinePaint.color = android.graphics.Color.parseColor("#00E5FF")
                    outlinePaint.strokeWidth = 8f
                    setPoints(trackPoints.map { GeoPoint(it.latitude, it.longitude) })
                }
                trackOverlay.add(polyline)
            }
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
                        length = if (ship.length > 0) "${ship.length}m" else "Unknown",
                        heading = ship.heading
                    )
                    viewModel.selectVessel(ship.mmsi)
                }
            )

            Text(
                text = "© OpenStreetMap contributors",
                color = Color.Black,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .background(
                        Color.White.copy(alpha = 0.85f),
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 3.dp)
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
                Text(
                    text = vessel.type,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            VesselInfoRow("MMSI", vessel.mmsi.toString())
            VesselInfoRow(
                "Position",
                String.format(Locale.US, "%.5f, %.5f", vessel.lat, vessel.lng)
            )
            VesselInfoRow("Length", vessel.length)
            VesselInfoRow("Heading", "${vessel.heading.toInt()}°")
            VesselInfoRow("Status", "Live AIS position")
        }

        Spacer(modifier = Modifier.height(20.dp))

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
private fun VesselInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
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
