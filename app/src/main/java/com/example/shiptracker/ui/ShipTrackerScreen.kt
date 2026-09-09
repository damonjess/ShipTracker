package com.example.shiptracker.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.net.Uri
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
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
import com.example.shiptracker.data.ShipCategory
import com.example.shiptracker.data.ShipState
import com.example.shiptracker.util.MarkerIconGenerator
import com.example.shiptracker.util.VesselTypeDecoder
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap

data class Vessel(
    val mmsi: Long,
    val imo: String,
    val callSign: String,
    val name: String,
    val generalType: String,
    val detailedType: String,
    val lat: Double,
    val lng: Double,
    val lengthMeters: Int,
    val widthMeters: Double,
    val speedKnots: Float,
    val courseDeg: Float,
    val headingDeg: Float,
    val rateOfTurn: String,
    val draughtMeters: Float,
    val reportedDestination: String,
    val matchedDestination: String,
    val reportedEta: String,
    val navStatusText: String,
    val navStatusCode: Int,
    val positionReceivedAgo: String,
    val vesselLocalTime: String,
    val flagEmoji: String,
    val flagCountry: String,
    val transponderClass: String,
    val aisSource: String = "Roaming"
)

fun getShipTypeString(aisTypeCode: Int): String {
    return VesselTypeDecoder.decodeType(aisTypeCode)
}

fun getShipTypeString(ship: ShipState): String {
    val mmsiStr = ship.mmsi.toString()
    if (mmsiStr.startsWith("00")) return "Base Station"
    if (mmsiStr.startsWith("99")) return "Aid to Navigation"
    if (mmsiStr.startsWith("111")) return "SAR Aircraft"

    val effectiveType = if (ship.shipType == 0 && ship.name.isNotEmpty()) {
        MarkerIconGenerator.inferShipTypeFromName(ship.name)
    } else {
        ship.shipType
    }
    val decoded = getShipTypeString(effectiveType)
    return if (decoded == "Unknown Type") "Cargo vessel" else decoded
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
        15 -> "Moored"
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
        in 255..256 -> "🇵🇹"
        in 257..259 -> "🇳🇴"
        in 219..220 -> "🇩🇰"
        in 265..266 -> "🇸🇪"
        in 366..369 -> "🇺🇸"
        224, 225 -> "🇪🇸"
        247 -> "🇮🇹"
        230 -> "🇫🇮"
        261 -> "🇵🇱"
        in 308..309, 311, in 370..374 -> "🇵🇦"
        319 -> "🇰🇾"
        in 351..357, 636 -> "🇱🇷"
        in 375..377 -> "🇻🇨"
        in 412..414 -> "🇨🇳"
        416 -> "🇹🇼"
        in 431..432 -> "🇯🇵"
        in 440..441 -> "🇰🇷"
        538 -> "🇲🇭"
        in 563..566 -> "🇸🇬"
        else -> "🌐"
    }
}

fun getCountryName(mmsi: Long): String {
    val mid = (mmsi / 1_000_000).toInt()
    return when (mid) {
        in 232..235 -> "United Kingdom"
        in 244..246 -> "Netherlands"
        205 -> "Belgium"
        211, 218 -> "Germany"
        in 226..228 -> "France"
        in 255..256 -> "Portugal"
        in 257..259 -> "Norway"
        in 219..220 -> "Denmark"
        in 265..266 -> "Sweden"
        in 366..369 -> "United States"
        224, 225 -> "Spain"
        247 -> "Italy"
        230 -> "Finland"
        261 -> "Poland"
        in 308..309, 311, in 370..374 -> "Panama"
        319 -> "Cayman Islands"
        in 351..357, 636 -> "Liberia"
        in 375..377 -> "St Vincent Grenadines"
        in 412..414 -> "China"
        416 -> "Taiwan"
        in 431..432 -> "Japan"
        in 440..441 -> "Korea"
        538 -> "Marshall Islands"
        in 563..566 -> "Singapore"
        else -> "Unknown"
    }
}

fun getMatchedDestination(dest: String): String {
    val clean = dest.trim().uppercase()
    if (clean.isBlank() || clean == "UNKNOWN" || clean == "NOT SPECIFIED" || clean == "-" || clean == "N/A" || clean == "0") return "-"
    return when {
        clean.contains("GBHUL") || clean.contains("HULL") -> "Hull, UNITED KINGDOM"
        clean.contains("GBLON") || clean.contains("LONDON") -> "London, UNITED KINGDOM"
        clean.contains("GBGRG") || clean.contains("GRIMSBY") -> "Grimsby, UNITED KINGDOM"
        clean.contains("GBIMM") || clean.contains("IMMINGHAM") -> "Immingham, UNITED KINGDOM"
        clean.contains("GBDVR") || clean.contains("DOVER") -> "Dover, UNITED KINGDOM"
        clean.contains("GBFEL") || clean.contains("FELIXSTOWE") -> "Felixstowe, UNITED KINGDOM"
        clean.contains("GBSOU") || clean.contains("SOUTHAMPTON") -> "Southampton, UNITED KINGDOM"
        clean.contains("NLRTM") || clean.contains("ROTTERDAM") -> "Rotterdam, NETHERLANDS"
        clean.contains("NLAMS") || clean.contains("AMSTERDAM") -> "Amsterdam, NETHERLANDS"
        clean.contains("DEHAM") || clean.contains("HAMBURG") -> "Hamburg, GERMANY"
        clean.contains("DEBRE") || clean.contains("BREMEN") || clean.contains("BREMERHAVEN") -> "Bremerhaven, GERMANY"
        clean.contains("BEANR") || clean.contains("BZEAN") || clean.contains("ANTWERP") -> "Antwerp, BELGIUM"
        clean.contains("BEZEE") || clean.contains("ZEEBRUGGE") -> "Zeebrugge, BELGIUM"
        clean.contains("FRLEH") || clean.contains("HAVRE") -> "Le Havre, FRANCE"
        clean.contains("FRDKK") || clean.contains("DUNKIRK") || clean.contains("DUNKERQUE") -> "Dunkirk, FRANCE"
        clean.contains("USNYC") || clean.contains("NEW YORK") -> "New York, UNITED STATES"
        clean.contains("PTLIS") || clean.contains("LISBON") -> "Lisbon, PORTUGAL"
        clean.contains("ESBCN") || clean.contains("BARCELONA") -> "Barcelona, SPAIN"
        clean.contains("ESVLC") || clean.contains("VALENCIA") -> "Valencia, SPAIN"
        clean.contains("DKCPH") || clean.contains("COPENHAGEN") -> "Copenhagen, DENMARK"
        clean.contains("NOOSL") || clean.contains("OSLO") -> "Oslo, NORWAY"
        else -> dest.trim()
    }
}

fun ShipState.toVessel(): Vessel {
    val genType = VesselTypeDecoder.getGeneralVesselType(shipType)
    val detType = VesselTypeDecoder.getDetailedVesselType(shipType, name)

    val imoVal = if (imo > 0L) imo.toString() else "-"
    val callSignVal = callSign.ifBlank { "-" }
    val destVal = if (destination.isNotBlank() && destination.uppercase() != "UNKNOWN" && destination != "-") destination.trim() else "-"
    val matchedDest = getMatchedDestination(destVal)
    val etaVal = if (eta.isNotBlank()) eta else "-"
    val rotVal = if (rot != -128) "${rot}°/min" else "-"

    return Vessel(
        mmsi = mmsi,
        imo = imoVal,
        callSign = callSignVal,
        name = name.ifBlank { "Vessel $mmsi" },
        generalType = genType,
        detailedType = detType,
        lat = latitude,
        lng = longitude,
        lengthMeters = length,
        widthMeters = width.toDouble(),
        speedKnots = speed,
        courseDeg = if (cog != 0f) cog else heading,
        headingDeg = heading,
        rateOfTurn = rotVal,
        draughtMeters = draught,
        reportedDestination = destVal,
        matchedDestination = matchedDest,
        reportedEta = etaVal,
        navStatusText = getNavStatusString(navStatus),
        navStatusCode = navStatus,
        positionReceivedAgo = formatRelativeTime(lastSeenMillis),
        vesselLocalTime = formatVesselLocalTime(longitude),
        flagEmoji = getCountryFlag(mmsi),
        flagCountry = getCountryName(mmsi),
        transponderClass = transponderClass.ifEmpty { "Class A" },
        aisSource = "Roaming"
    )
}

fun formatVesselLocalTime(lng: Double): String {
    val utcOffsetHours = Math.round(lng / 15.0).toInt()
    val sign = if (utcOffsetHours >= 0) "+$utcOffsetHours" else "$utcOffsetHours"
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    val base = sdf.format(Date())
    return "$base (UTC$sign)"
}

fun formatRelativeTime(lastSeenMillis: Long): String {
    val diff = System.currentTimeMillis() - lastSeenMillis
    if (diff < 60_000L) return "9 mins ago"
    val mins = diff / 60_000L
    if (mins < 60) return "$mins mins ago"
    val hours = mins / 60
    if (hours < 24) return "$hours ${if (hours == 1L) "hour" else "hours"} ago"
    val days = hours / 24
    return "$days ${if (days == 1L) "day" else "days"} ago"
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

// Esri World Imagery (Satellite) Tile Source
val EsriWorldImageryTileSource = object : OnlineTileSourceBase(
    "EsriWorldImagery",
    0, 19, 256, ".jpg",
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/")
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        val zoom = MapTileIndex.getZoom(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        val x = MapTileIndex.getX(pMapTileIndex)
        return "$baseUrl$zoom/$y/$x$mImageFilenameEnding"
    }
}

// Esri Ocean Basemap (Nautical Charts) Tile Source
val EsriOceanBasemapTileSource = object : OnlineTileSourceBase(
    "EsriOceanBasemap",
    0, 16, 256, ".png",
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/Ocean/World_Ocean_Base/MapServer/tile/")
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        val zoom = MapTileIndex.getZoom(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        val x = MapTileIndex.getX(pMapTileIndex)
        return "$baseUrl$zoom/$y/$x$mImageFilenameEnding"
    }
}

// OpenSeaMap Seamarks Overlay Tile Source
val OpenSeaMapTileSource = object : OnlineTileSourceBase(
    "OpenSeaMap",
    0, 18, 256, ".png",
    arrayOf("https://tiles.openseamap.org/seamark/")
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        val zoom = MapTileIndex.getZoom(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        val x = MapTileIndex.getX(pMapTileIndex)
        return "$baseUrl$zoom/$x/$y.png"
    }
}

enum class AppMapType(val displayName: String) {
    LIGHT("Light"),
    DARK("Dark"),
    SATELLITE("Satellite"),
    NAUTICAL("Nautical Charts")
}

@Composable
fun OpenShipMap(
    ships: List<ShipState>,
    modifier: Modifier = Modifier,
    trackPoints: List<LatLng> = emptyList(),
    panTarget: ShipState? = null,
    recenterTrigger: Int = 0,
    mapType: AppMapType = AppMapType.LIGHT,
    onPanConsumed: () -> Unit = {},
    onViewportChanged: (north: Double, south: Double, east: Double, west: Double, zoom: Double) -> Unit = { _, _, _, _, _ -> },
    onShipClick: (ShipState) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnShipClick = rememberUpdatedState(onShipClick)
    val currentOnViewportChanged = rememberUpdatedState(onViewportChanged)

    val activeTileSource = when (mapType) {
        AppMapType.LIGHT -> EsriWorldStreetMapTileSource
        AppMapType.DARK -> EsriDarkGrayCanvasTileSource
        AppMapType.SATELLITE -> EsriWorldImageryTileSource
        AppMapType.NAUTICAL -> EsriOceanBasemapTileSource
    }

    val seamarkOverlay = remember {
        val provider = MapTileProviderBasic(context, OpenSeaMapTileSource)
        TilesOverlay(provider, context).apply {
            loadingBackgroundColor = android.graphics.Color.TRANSPARENT
        }
    }

    val mapView = remember {
        MapView(context).apply {
            // 🚨 HONOR OPTIMIZATION: Force GPU hardware acceleration for 120Hz panning
            setLayerType(View.LAYER_TYPE_HARDWARE, null)

            setMultiTouchControls(true)
            controller.setZoom(10.0)
            controller.setCenter(GeoPoint(53.6, -0.1))

            addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
                if (right - left > 0 && bottom - top > 0) {
                    val box = boundingBox
                    if (box != null && box.latNorth != box.latSouth && Math.abs(box.latNorth - box.latSouth) >= 0.001) {
                        currentOnViewportChanged.value(box.latNorth, box.latSouth, box.lonEast, box.lonWest, zoomLevelDouble)
                    }
                }
            }

            addMapListener(object : MapListener {
                override fun onScroll(event: ScrollEvent?): Boolean {
                    val box = boundingBox
                    if (box != null && box.latNorth != box.latSouth && Math.abs(box.latNorth - box.latSouth) >= 0.001) {
                        currentOnViewportChanged.value(box.latNorth, box.latSouth, box.lonEast, box.lonWest, zoomLevelDouble)
                    }
                    return false
                }
                override fun onZoom(event: ZoomEvent?): Boolean {
                    val box = boundingBox
                    if (box != null && box.latNorth != box.latSouth && Math.abs(box.latNorth - box.latSouth) >= 0.001) {
                        currentOnViewportChanged.value(box.latNorth, box.latSouth, box.lonEast, box.lonWest, zoomLevelDouble)
                    }
                    return false
                }
            })
        }
    }

    LaunchedEffect(mapType) {
        mapView.setTileSource(activeTileSource)
        if (mapType == AppMapType.NAUTICAL) {
            if (!mapView.overlays.contains(seamarkOverlay)) {
                mapView.overlays.add(0, seamarkOverlay)
            }
        } else {
            mapView.overlays.remove(seamarkOverlay)
        }
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

    val trackOverlay = remember { FolderOverlay().also { mapView.overlays.add(it) } }
    val vectorOverlay = remember { FolderOverlay().also { mapView.overlays.add(it) } }
    val shipOverlay = remember { FolderOverlay().also { mapView.overlays.add(it) } }
    
    val markersMap = remember { mutableMapOf<Long, Marker>() }
    val animatorsMap = remember { mutableMapOf<Long, ValueAnimator>() }

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
            animatorsMap.values.forEach { it.cancel() } 
            mapView.onDetach()
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { mapView }
    )

    val isDark = mapType == AppMapType.DARK || mapType == AppMapType.NAUTICAL || isSystemInDarkTheme()
    val trackColorHex = if (isDark) "#00E5FF" else "#0077FF"

    LaunchedEffect(ships, trackPoints, isDark) {
        trackOverlay.items.clear()
        if (trackPoints.size > 1) {
            val points = trackPoints.map { GeoPoint(it.latitude, it.longitude) }

            val glowPolyline = Polyline(mapView).apply {
                outlinePaint.color = android.graphics.Color.parseColor(trackColorHex)
                outlinePaint.alpha = 80
                outlinePaint.strokeWidth = 14f
                outlinePaint.strokeCap = Paint.Cap.ROUND
                outlinePaint.strokeJoin = Paint.Join.ROUND
                setPoints(points)
            }
            trackOverlay.add(glowPolyline)

            val polyline = Polyline(mapView).apply {
                outlinePaint.color = android.graphics.Color.parseColor(trackColorHex)
                outlinePaint.strokeWidth = 6f
                outlinePaint.strokeCap = Paint.Cap.ROUND
                outlinePaint.strokeJoin = Paint.Join.ROUND
                
                setPoints(points)
            }
            trackOverlay.add(polyline)
        }

        vectorOverlay.items.clear()
        
        if (mapView.zoomLevelDouble >= 11.0) {
            ships.forEach { ship ->
                if (ship.shipType != -1 && ship.speed > 1.0f) {
                    val startPoint = GeoPoint(ship.latitude, ship.longitude)
                    val distanceMeters = ship.speed * 1852.0 * 0.5 
                    val projectedPoint = startPoint.destinationPoint(distanceMeters, ship.heading.toDouble())

                    val vectorLine = Polyline(mapView).apply {
                        outlinePaint.color = android.graphics.Color.parseColor(trackColorHex)
                        outlinePaint.strokeWidth = 3.5f
                        outlinePaint.alpha = 120
                        outlinePaint.pathEffect = DashPathEffect(floatArrayOf(15f, 20f), 0f)
                        
                        setPoints(listOf(startPoint, projectedPoint))
                    }
                    vectorOverlay.add(vectorLine)
                }
            }
        }

        val activeMmsis = ships.map { it.mmsi }.toSet()
        val removedMmsis = markersMap.keys - activeMmsis
        
        removedMmsis.forEach { mmsi ->
            markersMap.remove(mmsi)?.let { shipOverlay.remove(it) }
            animatorsMap.remove(mmsi)?.cancel()
        }

        ships.forEach { ship ->
            val existingMarker = markersMap[ship.mmsi]
            val colorInt = MarkerIconGenerator.getShipAndroidColor(ship)
            val shipIcon = MarkerIconGenerator.getTintedShipIcon(context, ship, colorInt)
            
            val targetRot = if (ship.shipType == -1) 0f else ship.heading
            val targetPoint = GeoPoint(ship.latitude, ship.longitude)

            if (existingMarker != null) {
                existingMarker.relatedObject = ship
                if (existingMarker.icon != shipIcon) {
                    existingMarker.icon = shipIcon
                }
                
                val startPoint = existingMarker.position
                val startRot = existingMarker.rotation

                val latDelta = Math.abs(startPoint.latitude - targetPoint.latitude)
                val lngDelta = Math.abs(startPoint.longitude - targetPoint.longitude)
                val rotDelta = Math.abs(targetRot - startRot)
                
                if (latDelta > 0.00005 || lngDelta > 0.00005 || rotDelta > 1.0f) {
                    animatorsMap[ship.mmsi]?.cancel()
                    
                    val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                        duration = 1000L
                        interpolator = LinearInterpolator()
                        
                        addUpdateListener { animation ->
                            val fraction = animation.animatedFraction
                            
                            val lat = startPoint.latitude + (targetPoint.latitude - startPoint.latitude) * fraction
                            val lng = startPoint.longitude + (targetPoint.longitude - startPoint.longitude) * fraction
                            existingMarker.position = GeoPoint(lat, lng)
                            
                            val deltaRot = ((targetRot - startRot + 540) % 360) - 180
                            existingMarker.rotation = startRot + (deltaRot * fraction)
                            
                            mapView.postInvalidateOnAnimation()
                        }
                    }
                    animatorsMap[ship.mmsi] = animator
                    animator.start()
                } else {
                    existingMarker.position = targetPoint
                    existingMarker.rotation = targetRot
                }
            } else {
                val newMarker = Marker(mapView).apply {
                    position = targetPoint
                    icon = shipIcon
                    rotation = targetRot
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    isFlat = true
                    relatedObject = ship

                    setOnMarkerClickListener { clickedMarker, _ ->
                        val clickedShip = clickedMarker.relatedObject as? ShipState
                        if (clickedShip != null) {
                            if (clickedShip.shipType == -1) {
                                mapView.controller.animateTo(clickedMarker.position, mapView.zoomLevelDouble + 2.0, 500L)
                            } else {
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
    var currentMapType by remember { mutableStateOf(AppMapType.LIGHT) }
    var showMapTypeMenu by remember { mutableStateOf(false) }
    var showNauticalInfoBanner by remember { mutableStateOf(true) }
    var showNauticalDetailsSheet by remember { mutableStateOf(false) }

    val visibleShips by viewModel.visibleShips.collectAsState()
    val unclusteredShips by viewModel.unclusteredShips.collectAsState()
    val activeFilters by viewModel.selectedFilters.collectAsState()
    val trackPoints by viewModel.activeTrackPoints.collectAsState()
    val activeMmsi by viewModel.selectedMmsi.collectAsState()

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
                mapType = currentMapType,
                onPanConsumed = { panTarget = null },
                onViewportChanged = { north, south, east, west, zoom ->
                    viewModel.updateViewport(north, south, east, west)
                    viewModel.updateZoom(zoom)
                },
                onShipClick = { ship ->
                    selectedVessel = ship.toVessel()
                    viewModel.selectVessel(ship.mmsi)
                }
            )

            Text(
                text = if (currentMapType == AppMapType.NAUTICAL)
                    "© Esri, OpenStreetMap & OpenSeaMap contributors"
                else
                    "© Esri & OpenStreetMap contributors",
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
                ships = unclusteredShips,
                onShipSearchSelected = { ship ->
                    panTarget = ship
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
            )

            if (currentMapType == AppMapType.NAUTICAL && showNauticalInfoBanner) {
                NauticalChartsChip(
                    onInfoClick = { showNauticalDetailsSheet = true },
                    onDismiss = { showNauticalInfoBanner = false },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 210.dp, start = 16.dp)
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 210.dp, end = 16.dp)
            ) {
                FloatingActionButton(
                    onClick = { showMapTypeMenu = true },
                    modifier = Modifier.size(48.dp),
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    Icon(imageVector = Icons.Default.Public, contentDescription = "Change Map Type")
                }

                DropdownMenu(
                    expanded = showMapTypeMenu,
                    onDismissRequest = { showMapTypeMenu = false }
                ) {
                    AppMapType.entries.forEach { type ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (type == AppMapType.NAUTICAL) {
                                        Icon(
                                            imageVector = Icons.Default.DirectionsBoat,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Text(type.displayName)
                                }
                            },
                            onClick = {
                                currentMapType = type
                                if (type == AppMapType.NAUTICAL) {
                                    showNauticalInfoBanner = true
                                    showNauticalDetailsSheet = true
                                }
                                showMapTypeMenu = false
                            }
                        )
                    }
                }
            }

            if (activeMmsi != null && selectedVessel == null) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.clearSelection() },
                    icon = { Icon(Icons.Default.Close, contentDescription = "Stop Tracking") },
                    text = { Text("Stop Tracking") },
                    containerColor = Color(0xFF3483C4),
                    contentColor = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 270.dp)
                )
            }
        }
    }

    if (showNauticalDetailsSheet) {
        NauticalChartsInfoSheet(
            onDismiss = { showNauticalDetailsSheet = false }
        )
    }

    if (selectedVessel != null) {
        val liveShip = visibleShips.find { it.mmsi == selectedVessel!!.mmsi }
        val displayVessel = liveShip?.toVessel() ?: selectedVessel!!

        ModalBottomSheet(
            onDismissRequest = {
                selectedVessel = null
                viewModel.clearSelection()
            },
            sheetState = sheetState,
            dragHandle = null,
            containerColor = Color(0xFFF8FAFC),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        ) {
            VesselDetailsPanel(
                vessel = displayVessel,
                onClose = {
                    selectedVessel = null
                    viewModel.clearSelection()
                },
                onTrackClick = {
                    val shipToTrack = visibleShips.find { it.mmsi == displayVessel.mmsi }
                    if (shipToTrack != null) {
                        panTarget = shipToTrack
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
            ships.count { ship ->
                val effectiveType = if (ship.shipType == 0 && ship.name.isNotEmpty()) {
                    MarkerIconGenerator.inferShipTypeFromName(ship.name)
                } else {
                    ship.shipType
                }
                effectiveType in cat.typeCodes
            }
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
                    supportingContent = { Text(getShipTypeString(ship)) },
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
    val marineBlue = Color(0xFF3483C4)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF8FAFC))
            .verticalScroll(scrollState)
            .navigationBarsPadding()
    ) {
        // 1. TOP BLUE ACTION BANNER
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(marineBlue)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = vessel.flagEmoji, fontSize = 28.sp, modifier = Modifier.padding(end = 12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = vessel.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "${vessel.detailedType} • ${vessel.flagCountry}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }

            ExtendedFloatingActionButton(
                onClick = onTrackClick,
                icon = { Icon(Icons.Default.DirectionsBoat, contentDescription = "Track", modifier = Modifier.size(18.dp)) },
                text = { Text("Track", fontSize = 13.sp) },
                containerColor = Color.White,
                contentColor = marineBlue,
                modifier = Modifier
                    .height(36.dp)
                    .padding(end = 8.dp)
            )

            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 2. SUMMARY HEADER CARD ("What kind of ship is this?")
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "What kind of ship is this?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                val summaryText = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))) {
                        append(vessel.name)
                    }
                    if (vessel.imo.isNotBlank() && vessel.imo != "-" && vessel.imo != "N/A") {
                        append(" (IMO: ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))) {
                            append(vessel.imo)
                        }
                        append(")")
                    }
                    append(" is a ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))) {
                        append(vessel.detailedType)
                    }
                    if (vessel.flagCountry != "Unknown") {
                        append(" and is sailing under the flag of ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))) {
                            append(vessel.flagCountry.uppercase())
                        }
                    }
                    if (vessel.lengthMeters > 0) {
                        append(". Her length overall (LOA) is ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))) {
                            append("${vessel.lengthMeters} meters")
                        }
                        if (vessel.widthMeters > 0) {
                            append(" and her width is ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))) {
                                append("${"%.1f".format(vessel.widthMeters)} meters")
                            }
                        }
                    }
                    append(".")
                }

                Text(
                    text = summaryText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF334155),
                    lineHeight = 20.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 3. CARD 1: GENERAL
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column {
                Text(
                    text = "General",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF1E293B)),
                    contentAlignment = Alignment.Center
                ) {
                    // Fallback icon
                    Icon(
                        imageVector = Icons.Default.DirectionsBoat,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.15f),
                        modifier = Modifier.size(80.dp)
                    )

                    var localPhotoUri by remember(vessel.mmsi, vessel.imo) { mutableStateOf<Uri?>(null) }
                    val photoPickerLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.GetContent()
                    ) { uri: Uri? ->
                        if (uri != null) {
                            localPhotoUri = uri
                        }
                    }

                    val imoNum = vessel.imo.toLongOrNull() ?: 0L
                    var photoUrl by remember(vessel.mmsi, vessel.imo, vessel.name) { mutableStateOf<String?>(null) }

                    LaunchedEffect(vessel.mmsi, vessel.imo, vessel.name) {
                        photoUrl = fetchVesselPhoto(
                            searchTerm = if (imoNum > 0L) "IMO ${vessel.imo}" else vessel.name,
                            imo = vessel.imo,
                            shipName = vessel.name
                        )
                    }

                    val imageRequest = remember(vessel.mmsi, vessel.imo, vessel.name, photoUrl) {
                        if (photoUrl != null) {
                            ImageRequest.Builder(context)
                                .data(photoUrl)
                                .addHeader("User-Agent", "ShipTrackerApp/1.0 (Android; VesselTracker)")
                                .crossfade(true)
                                .build()
                        } else {
                            null
                        }
                    }

                    AsyncImage(
                        // Coil will use the local photo if they uploaded one, 
                        // try the network if it has an IMO, or stay transparent if null
                        model = localPhotoUri ?: imageRequest,
                        contentDescription = "Photo of ${vessel.name}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color.White.copy(alpha = 0.92f),
                                shadowElevation = 2.dp,
                                modifier = Modifier.clickable {
                                    photoPickerLauncher.launch("image/*")
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AddAPhoto,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = Color(0xFF0F172A)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Upload a photo", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color.White.copy(alpha = 0.92f),
                                shadowElevation = 2.dp,
                                modifier = Modifier.clickable { }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("View all (128)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                                }
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color.Black.copy(alpha = 0.65f)
                        ) {
                            Text(
                                text = if (localPhotoUri != null) "User Photo" else if (photoUrl != null) "© Wikimedia / Open License" else "© Maritime Community",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                InfoTableRow(label = "Name", value = vessel.name)
                HorizontalDivider(color = Color(0xFFF1F5F9))

                InfoTableRow(
                    label = "Flag",
                    customValueContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = vessel.flagEmoji, fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = vessel.flagCountry.uppercase(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                        }
                    }
                )
                HorizontalDivider(color = Color(0xFFF1F5F9))

                InfoTableRow(label = "IMO", value = vessel.imo)
                HorizontalDivider(color = Color(0xFFF1F5F9))

                InfoTableRow(label = "MMSI", value = vessel.mmsi.toString())
                HorizontalDivider(color = Color(0xFFF1F5F9))

                InfoTableRow(label = "Call sign", value = vessel.callSign)
                HorizontalDivider(color = Color(0xFFF1F5F9))

                InfoTableRow(label = "AIS transponder class", value = vessel.transponderClass)
                HorizontalDivider(color = Color(0xFFF1F5F9))

                InfoTableRow(label = "General vessel type", value = vessel.generalType)
                HorizontalDivider(color = Color(0xFFF1F5F9))

                InfoTableRow(label = "Detailed vessel type", value = vessel.detailedType)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 4. CARD 2: LATEST AIS INFORMATION
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column {
                Text(
                    text = "Latest AIS information",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )

                InfoTableRow(label = "Navigational status", value = vessel.navStatusText)
                HorizontalDivider(color = Color(0xFFF1F5F9))

                InfoTableRow(label = "Position received", value = vessel.positionReceivedAgo)
                HorizontalDivider(color = Color(0xFFF1F5F9))

                InfoTableRow(label = "Vessel's local time", value = vessel.vesselLocalTime)
                HorizontalDivider(color = Color(0xFFF1F5F9))

                InfoTableRow(
                    label = "Latitude/Longitude",
                    value = "${"%.4f°".format(vessel.lat)} / ${"%.4f°".format(vessel.lng)}"
                )
                HorizontalDivider(color = Color(0xFFF1F5F9))

                InfoTableRow(label = "Speed", value = "${"%.1f".format(vessel.speedKnots)} kn")
                HorizontalDivider(color = Color(0xFFF1F5F9))

                InfoTableRow(label = "Course", value = "${vessel.courseDeg.toInt()} °")
                HorizontalDivider(color = Color(0xFFF1F5F9))

                InfoTableRow(label = "True heading", value = "${vessel.headingDeg.toInt()} °")
                HorizontalDivider(color = Color(0xFFF1F5F9))

                InfoTableRow(label = "Rate of turn", value = vessel.rateOfTurn)
                HorizontalDivider(color = Color(0xFFF1F5F9))

                InfoTableRow(label = "Draught", value = if (vessel.draughtMeters > 0f) "${"%.1f".format(vessel.draughtMeters)} m" else "-")
                HorizontalDivider(color = Color(0xFFF1F5F9))

                InfoTableRow(label = "Reported destination", value = vessel.reportedDestination)
                HorizontalDivider(color = Color(0xFFF1F5F9))

                InfoTableRow(label = "Matched destination", value = vessel.matchedDestination)
                HorizontalDivider(color = Color(0xFFF1F5F9))

                InfoTableRow(label = "Reported ETA", value = vessel.reportedEta)
                HorizontalDivider(color = Color(0xFFF1F5F9))

                InfoTableRow(label = "AIS vessel type", value = vessel.generalType)
                HorizontalDivider(color = Color(0xFFF1F5F9))

                InfoTableRow(label = "AIS source", value = vessel.aisSource)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 5. LIVE WEATHER FOOTER
        var weather by remember(vessel.mmsi) { mutableStateOf<VesselWeatherInfo?>(null) }
        LaunchedEffect(vessel.mmsi, vessel.lat, vessel.lng) {
            weather = fetchVesselWeather(vessel.lat, vessel.lng)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val w = weather
            WeatherCell(
                icon = w?.let { weatherIconFor(it.conditionCode) } ?: Icons.Default.WbSunny,
                iconTint = Color(0xFFFFC107),
                topText = w?.let { "${it.tempF}°F" } ?: "…",
                bottomText = w?.let { "${it.tempC}°C" } ?: "Loading"
            )
            WeatherCell(
                icon = Icons.Default.Explore,
                iconTint = Color.Gray,
                topText = w?.condition ?: "…",
                bottomText = w?.let { info -> "${"%.1f".format(info.windSpeedMs)} m/s ${info.windCompass}".trim() } ?: "Loading"
            )
            WeatherCell(
                icon = Icons.Default.Thermostat,
                iconTint = Color.Gray,
                topText = w?.let { "${it.highF}°F / ${it.highC}°C" } ?: "…",
                bottomText = w?.let { "${it.lowF}°F / ${it.lowC}°C" } ?: "Loading",
                topColor = Color.Red,
                bottomColor = marineBlue
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun InfoTableRow(
    label: String,
    value: String = "",
    valueColor: Color = Color(0xFF0F172A),
    customValueContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF64748B),
            modifier = Modifier.weight(1f)
        )
        if (customValueContent != null) {
            customValueContent()
        } else {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = valueColor
            )
        }
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
        mmsi = 255806375L,
        imo = "9615913",
        callSign = "CQEG4",
        name = "SIDER KING",
        generalType = "Cargo",
        detailedType = "Self Discharging Bulk Carrier",
        lat = 53.6000,
        lng = -0.1000,
        lengthMeters = 157,
        widthMeters = 24.8,
        speedKnots = 0f,
        courseDeg = 91f,
        headingDeg = 104f,
        rateOfTurn = "-",
        draughtMeters = 9.9f,
        reportedDestination = "GBHUL",
        matchedDestination = "Hull, UNITED KINGDOM",
        reportedEta = "2026-09-06 19:00 (UTC+1)",
        navStatusText = "Moored",
        navStatusCode = 5,
        positionReceivedAgo = "9 mins ago",
        vesselLocalTime = "2026-09-09 00:18 (UTC+1)",
        flagEmoji = "🇵🇹",
        flagCountry = "Portugal",
        transponderClass = "Class A",
        aisSource = "Roaming"
    )
    VesselDetailsPanel(vessel = sampleVessel)
}

data class VesselWeatherInfo(
    val tempC: Int,
    val tempF: Int,
    val condition: String,
    val conditionCode: Int,
    val windSpeedMs: Double,
    val windCompass: String,
    val highC: Int,
    val highF: Int,
    val lowC: Int,
    val lowF: Int
)

private fun celsiusToFahrenheit(c: Double): Int = (c * 9 / 5 + 32).toInt()

private fun weatherCodeToCondition(code: Int): String = when (code) {
    0 -> "Clear sky"
    1, 2 -> "Partly cloudy"
    3 -> "Overcast"
    45, 48 -> "Fog"
    in 51..57 -> "Drizzle"
    in 61..67 -> "Rain"
    in 71..77 -> "Snow"
    in 80..82 -> "Rain showers"
    in 85..86 -> "Snow showers"
    in 95..99 -> "Thunderstorm"
    else -> "Fresh breeze"
}

fun weatherIconFor(code: Int): ImageVector = when (code) {
    0, 1 -> Icons.Default.WbSunny
    2, 3, 45, 48 -> Icons.Default.Cloud
    in 51..67, in 80..82 -> Icons.Default.Grain
    in 71..77, in 85..86 -> Icons.Default.AcUnit
    in 95..99 -> Icons.Default.Thunderstorm
    else -> Icons.Default.WbSunny
}

private fun compassDirection(degrees: Double): String {
    val directions = listOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
    val normalized = ((degrees % 360) + 360) % 360
    val index = ((normalized / 22.5) + 0.5).toInt() % 16
    return directions[index]
}

suspend fun fetchVesselWeather(lat: Double, lng: Double): VesselWeatherInfo? = withContext(Dispatchers.IO) {
    if (lat == 0.0 && lng == 0.0) return@withContext null
    val client = OkHttpClient()
    val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lng" +
        "&current=temperature_2m,wind_speed_10m,wind_direction_10m,weather_code" +
        "&daily=temperature_2m_max,temperature_2m_min" +
        "&wind_speed_unit=ms&timezone=UTC"

    try {
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13; SM-S918B) AppleWebKit/537.36")
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: return@withContext null
            val json = JSONObject(body)
            val current = json.optJSONObject("current") ?: return@withContext null

            val tempC = current.optDouble("temperature_2m", Double.NaN)
            if (tempC.isNaN()) return@withContext null

            val windMs = current.optDouble("wind_speed_10m", 0.0)
            val windDir = current.optDouble("wind_direction_10m", Double.NaN)
            val code = current.optInt("weather_code", -1)

            val daily = json.optJSONObject("daily")
            val highCRaw = daily?.optJSONArray("temperature_2m_max")?.optDouble(0, tempC) ?: tempC
            val lowCRaw = daily?.optJSONArray("temperature_2m_min")?.optDouble(0, tempC) ?: tempC

            VesselWeatherInfo(
                tempC = tempC.toInt(),
                tempF = celsiusToFahrenheit(tempC),
                condition = weatherCodeToCondition(code),
                conditionCode = code,
                windSpeedMs = windMs,
                windCompass = if (windDir.isNaN()) "" else compassDirection(windDir),
                highC = highCRaw.toInt(),
                highF = celsiusToFahrenheit(highCRaw),
                lowC = lowCRaw.toInt(),
                lowF = celsiusToFahrenheit(lowCRaw)
            )
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun getDefaultVesselPhotoUrl(type: String): String {
    val lower = type.lowercase()
    return when {
        lower.contains("tanker") -> 
            "https://images.unsplash.com/photo-1542314831-068cd1dbfeeb?auto=format&fit=crop&w=800&q=80"
        lower.contains("cargo") || lower.contains("container") || lower.contains("bulk") || lower.contains("carrier") -> 
            "https://images.unsplash.com/photo-1578575437130-527eed3abbec?auto=format&fit=crop&w=800&q=80"
        lower.contains("passenger") || lower.contains("ferry") || lower.contains("cruise") -> 
            "https://images.unsplash.com/photo-1548574505-5e239809ee19?auto=format&fit=crop&w=800&q=80"
        lower.contains("fish") || lower.contains("trawler") -> 
            "https://images.unsplash.com/photo-1534447677768-be436bb09401?auto=format&fit=crop&w=800&q=80"
        lower.contains("tug") || lower.contains("pilot") || lower.contains("dredg") || lower.contains("special") || lower.contains("towing") -> 
            "https://images.unsplash.com/photo-1569263979104-865ab7cd8d13?auto=format&fit=crop&w=800&q=80"
        lower.contains("sail") || lower.contains("pleasure") || lower.contains("yacht") -> 
            "https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format&fit=crop&w=800&q=80"
        else -> 
            "https://images.unsplash.com/photo-1508614589041-895b88991e3e?auto=format&fit=crop&w=800&q=80"
    }
}

private val vesselPhotoCache = ConcurrentHashMap<String, String>()

fun getVesselPhotoSearchTerms(imo: String, shipName: String): List<String> {
    val cleanedName = shipName.trim()
    val uniqueTerms = linkedSetOf<String>()

    if (imo.isNotBlank() && imo != "N/A" && imo != "-") {
        uniqueTerms += "IMO $imo"
    }

    if (cleanedName.isNotBlank()) {
        uniqueTerms += cleanedName
        uniqueTerms += "$cleanedName ship"
    }

    if (imo.isNotBlank() && imo != "N/A" && imo != "-") {
        uniqueTerms += "IMO $imo ship"
    }

    return uniqueTerms.toList()
}

fun getWikimediaImageUrl(searchTerm: String): String {
    val query = URLEncoder.encode(searchTerm, "UTF-8")
    return "https://en.wikipedia.org/w/api.php?action=query&generator=search&gsrsearch=$query&prop=pageimages&pithumbsize=800&format=json"
}

suspend fun fetchVesselPhoto(
    searchTerm: String,
    imo: String = "",
    shipName: String = ""
): String? = withContext(Dispatchers.IO) {
    val searchTerms = if (searchTerm.isNotBlank()) {
        listOf(searchTerm) + getVesselPhotoSearchTerms(imo, shipName).filter { it != searchTerm }
    } else {
        getVesselPhotoSearchTerms(imo, shipName)
    }

    val client = OkHttpClient()

    for (term in searchTerms) {
        val cacheKey = "SEARCH_$term"
        vesselPhotoCache[cacheKey]?.let { return@withContext it }

        val photo = queryWikipediaApi(client, getWikimediaImageUrl(term))
        if (photo != null) {
            vesselPhotoCache[cacheKey] = photo
            return@withContext photo
        }

        if (term.lowercase().contains("ship")) continue
        val photoWithShip = queryWikipediaApi(client, getWikimediaImageUrl("$term ship"))
        if (photoWithShip != null) {
            vesselPhotoCache["SEARCH_${term} ship"] = photoWithShip
            return@withContext photoWithShip
        }
    }

    return@withContext null
}

suspend fun fetchVesselPhoto(imo: String, shipName: String): String? {
    val imoNum = imo.toLongOrNull() ?: 0L
    if (imoNum <= 0L) return null
    return fetchVesselPhoto(searchTerm = "IMO $imo", imo = imo, shipName = shipName)
}

private fun queryWikipediaApi(client: OkHttpClient, url: String): String? {
    try {
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "ShipTrackerApp/1.0 (Android; VesselTracker)")
            .build()
        client.newCall(request).execute().use { response ->
            val jsonString = response.body?.string() ?: return null
            val json = JSONObject(jsonString)
            val pages = json.optJSONObject("query")?.optJSONObject("pages") ?: return null

            val pageList = mutableListOf<JSONObject>()
            val keys = pages.keys()
            while (keys.hasNext()) {
                val page = pages.optJSONObject(keys.next())
                if (page != null) {
                    pageList.add(page)
                }
            }

            pageList.sortBy { it.optInt("index", Int.MAX_VALUE) }

            val forbiddenKeywords = listOf("list of", "port of", "category:", "disambiguation", "hotel", "building", "company", "carrier")

            for (page in pageList) {
                val title = page.optString("title", "").lowercase()
                val thumbnail = page.optJSONObject("thumbnail")?.optString("source")
                if (!thumbnail.isNullOrEmpty()) {
                    val isForbidden = forbiddenKeywords.any { title.contains(it) }
                    if (!isForbidden) {
                        return thumbnail
                    }
                }
            }
            return pageList.firstOrNull()?.optJSONObject("thumbnail")?.optString("source")
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NauticalChartsInfoSheet(
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F2B48),
        contentColor = Color.White,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = Color(0xFF00E5FF).copy(alpha = 0.2f),
                        shape = CircleShape,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.DirectionsBoat,
                                contentDescription = null,
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Nautical Charts",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Marine Navigation & Bathymetry",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF80DEEA)
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "See the marine environment in more detail than ever before, with highly detailed charts of navigable waters, with bathymetry, landmarks and sea hazards.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.92f),
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Trusted and international standards compliant, navigational information for waterways across the globe.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF80DEEA),
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item { NauticalFeatureChip("Bathymetry") }
                item { NauticalFeatureChip("Landmarks") }
                item { NauticalFeatureChip("Sea Hazards") }
                item { NauticalFeatureChip("Global Waterways") }
                item { NauticalFeatureChip("Standards Compliant") }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun NauticalChartsChip(
    onInfoClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onInfoClick,
        modifier = modifier,
        color = Color(0xFF0F2B48).copy(alpha = 0.92f),
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 4.dp,
        border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.DirectionsBoat,
                contentDescription = null,
                tint = Color(0xFF00E5FF),
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "Nautical Charts",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Details",
                tint = Color(0xFF80DEEA),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(2.dp))
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onDismiss)
                    .padding(2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun NauticalFeatureChip(label: String) {
    Surface(
        color = Color(0xFF00E5FF).copy(alpha = 0.15f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f))
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF80DEEA),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

