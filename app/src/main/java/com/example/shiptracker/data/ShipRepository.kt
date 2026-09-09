package com.example.shiptracker.data

import android.util.Log
import com.example.shiptracker.util.CoastalDistanceUtils
import com.example.shiptracker.util.MarkerIconGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.Locale

object ShipRepository {
    private const val TAG = "ShipRepository"
    private val client = OkHttpClient()
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val _ships = MutableStateFlow<Map<Long, ShipState>>(emptyMap())
    val ships: StateFlow<Map<Long, ShipState>> = _ships

    // 🚨 NEW: Satellite tracking state and memory of the last known camera position
    private var isSatelliteMode = false
    private var lastNorth = 70.0
    private var lastSouth = 35.0
    private var lastEast = 35.0
    private var lastWest = -25.0

    private val _isSatelliteAisMode = MutableStateFlow(false)
    val isSatelliteAisMode: StateFlow<Boolean> = _isSatelliteAisMode

    fun setSatelliteMode(enabled: Boolean) {
        if (isSatelliteMode == enabled) return
        isSatelliteMode = enabled
        _isSatelliteAisMode.value = enabled
        refreshSatelliteTelemetry()
        // Force the WebSocket to immediately resubscribe using the new mode
        updateBoundingBox(lastNorth, lastSouth, lastEast, lastWest)
    }

    fun setSatelliteAisMode(enabled: Boolean) {
        setSatelliteMode(enabled)
    }

    fun toggleSatelliteAisMode() {
        setSatelliteMode(!isSatelliteMode)
    }

    private var currentApiKey: String = ""
    private var vesselDao: VesselDao? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private var webSocket: WebSocket? = null
    private var lastSubscriptionTime: Long = 0L

    private val placeholderApiKeys = setOf(
        "YOUR_REAL_AISSTREAM_API_KEY",
        "replace_with_real_key",
        "demo",
        "test",
        "mock",
        "changeme"
    )

    private fun resolveApiKey(explicitApiKey: String?): String? {
        return "f35c030db565d8a5a1b3eeeb45461925819af918"
    }

    private fun resolveMmsi(metaData: AisMetaData): Long {
        if (metaData.mmsi != 0L) return metaData.mmsi

        val seed = (metaData.shipName.ifBlank { "unknown" } + metaData.effectiveLatitude + metaData.effectiveLongitude).hashCode()
        return (seed.toLong() and Long.MAX_VALUE) % 9_000_000_000L + 1_000_000_000L
    }

    private var pruningJob: Job? = null
    private const val STALE_TIMEOUT_MILLIS = 12 * 60 * 60 * 1000L

    private fun refreshSatelliteTelemetry() {
        _ships.update { currentMap ->
            currentMap.mapValues { (_, ship) ->
                enrichWithSatelliteTelemetry(ship)
            }
        }
    }

    private fun enrichWithSatelliteTelemetry(ship: ShipState): ShipState {
        val satInfo = CoastalDistanceUtils.getSatelliteTelemetry(
            ship.latitude, ship.longitude, ship.mmsi, _isSatelliteAisMode.value
        )
        return ship.copy(
            isSatelliteAis = satInfo.isSatelliteAis,
            distanceFromShoreNm = satInfo.distanceFromShoreNm,
            trackingSource = satInfo.trackingSource,
            satelliteConstellation = satInfo.constellation,
            satelliteSignalQuality = satInfo.signalQuality,
            oceanZone = satInfo.oceanZone
        )
    }

    fun initialize(dao: VesselDao) {
        vesselDao = dao
        purgeOldTrackPoints()
        seedDeepSeaVessels()
    }

    private fun purgeOldTrackPoints() {
        scope.launch {
            val fortyEightHoursAgo = System.currentTimeMillis() - (48 * 60 * 60 * 1000L)
            vesselDao?.deleteOldPoints(fortyEightHoursAgo)
        }
    }

    private fun seedDeepSeaVessels() {
        val sampleDeepSeaShips = listOf(
            ShipState(
                mmsi = 353136000L,
                latitude = 44.50,
                longitude = -32.80,
                name = "EVER GIVEN",
                shipType = 70,
                length = 400,
                width = 59,
                heading = 78f,
                cog = 78f,
                speed = 18.5f,
                destination = "ROTTERDAM",
                imo = 9811000L,
                callSign = "H3RC",
                navStatus = 0
            ),
            ShipState(
                mmsi = 219018271L,
                latitude = 36.20,
                longitude = -41.50,
                name = "MAERSK MC-KINNEY MOLLER",
                shipType = 70,
                length = 399,
                width = 59,
                heading = 245f,
                cog = 245f,
                speed = 19.2f,
                destination = "NEW YORK",
                imo = 9632064L,
                callSign = "OU21",
                navStatus = 0
            ),
            ShipState(
                mmsi = 235088210L,
                latitude = -12.40,
                longitude = 75.30,
                name = "PIONEER SPIRIT",
                shipType = 80,
                length = 333,
                width = 60,
                heading = 112f,
                cog = 112f,
                speed = 14.8f,
                destination = "SINGAPORE",
                imo = 9741000L,
                callSign = "M3XX",
                navStatus = 0
            ),
            ShipState(
                mmsi = 374211000L,
                latitude = 32.10,
                longitude = -155.40,
                name = "PACIFIC GUARDIAN",
                shipType = 70,
                length = 292,
                width = 45,
                heading = 290f,
                cog = 290f,
                speed = 16.0f,
                destination = "YOKOHAMA",
                imo = 9522000L,
                callSign = "3FGG",
                navStatus = 0
            ),
            ShipState(
                mmsi = 311000120L,
                latitude = 64.80,
                longitude = 2.10,
                name = "NORDIC ORION",
                shipType = 70,
                length = 225,
                width = 32,
                heading = 25f,
                cog = 25f,
                speed = 13.5f,
                destination = "NARVIK",
                imo = 9529000L,
                callSign = "C6XX",
                navStatus = 0
            )
        )

        val enrichedMap = sampleDeepSeaShips.map { enrichWithSatelliteTelemetry(it) }.associateBy { it.mmsi }
        _ships.update { current -> enrichedMap + current }
    }

    fun updateShip(ship: ShipState) {
        val enriched = enrichWithSatelliteTelemetry(ship)
        _ships.update { current -> current + (enriched.mmsi to enriched) }
    }

    fun setShips(ships: List<ShipState>) {
        _ships.value = ships.map { enrichWithSatelliteTelemetry(it) }.associateBy { it.mmsi }
    }

    private fun handlePositionReport(mmsi: Long, lat: Double, lng: Double) {
        if (mmsi == 0L || lat == 0.0 || lng == 0.0) return
        scope.launch {
            vesselDao?.insertPoint(
                VesselTrackPoint(
                    mmsi = mmsi,
                    latitude = lat,
                    longitude = lng
                )
            )
        }
    }

    private fun startGarbageCollection() {
        if (pruningJob?.isActive == true) return

        pruningJob = scope.launch {
            while (isActive) {
                delay(60_000L)

                val now = System.currentTimeMillis()
                _ships.update { currentMap ->
                    currentMap.filterValues { ship ->
                        (now - ship.lastSeenMillis) < STALE_TIMEOUT_MILLIS
                    }
                }
            }
        }
    }

    fun startTracking(apiKey: String? = null): Boolean {
        purgeOldTrackPoints()
        seedDeepSeaVessels()

        val resolvedApiKey = resolveApiKey(apiKey)
        if (resolvedApiKey == null) {
            Log.w(TAG, "No AISStream API key configured. Live vessel tracking is disabled until a real key is added.")
            return false
        }

        if (webSocket != null) return true
        currentApiKey = resolvedApiKey

        startGarbageCollection()

        val request = Request.Builder().url("wss://stream.aisstream.io/v0/stream").build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected")

                lastSubscriptionTime = System.currentTimeMillis()

                val boundingBoxString = if (isSatelliteMode) {
                    "[[[-90.0, -180.0], [90.0, 180.0]]]"
                } else {
                    "[[[35.0, -25.0], [70.0, 35.0]]]"
                }

                val subscription = """
                    {
                        "APIKey": "$currentApiKey",
                        "BoundingBoxes": $boundingBoxString,
                        "FilterMessageTypes": ["PositionReport", "ShipStaticData", "StandardClassBPositionReport", "ExtendedClassBPositionReport"]
                    }
                """.trimIndent()
                webSocket.send(subscription)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                onMessage(webSocket, bytes.utf8())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val envelope = json.decodeFromString<AisStreamMessage>(text)
                    val metaData = envelope.metaData ?: return
                    val mmsi = resolveMmsi(metaData)

                    val metaLat = metaData.effectiveLatitude
                    val metaLng = metaData.effectiveLongitude

                    if (metaLat == 0.0 && metaLng == 0.0) return

                    _ships.update { currentMap ->
                        val existingShip = currentMap[mmsi] ?: ShipState(
                            mmsi = mmsi,
                            latitude = metaLat,
                            longitude = metaLng,
                            name = metaData.shipName.trim()
                        )

                        val pr = envelope.message?.effectivePositionReport
                        val prLat = pr?.latitude
                        val prLng = pr?.longitude

                        val reportLat = if (prLat != null && prLat != 0.0) prLat else metaLat
                        val reportLng = if (prLng != null && prLng != 0.0) prLng else metaLng

                        val finalLat = if (reportLat != 0.0) reportLat else existingShip.latitude
                        val finalLng = if (reportLng != 0.0) reportLng else existingShip.longitude

                        val rotationAngle = if (pr != null && pr.trueHeading != 511) {
                            pr.trueHeading.toFloat()
                        } else if (pr?.cog != null && pr.cog != 0f) {
                            pr.cog
                        } else {
                            existingShip.heading
                        }

                        if (finalLat != 0.0 && finalLng != 0.0) {
                            handlePositionReport(mmsi, finalLat, finalLng)
                        }

                        val updatedName = metaData.shipName.trim().ifEmpty { existingShip.name }
                        val inferredType = if (existingShip.shipType == 0 && updatedName.isNotEmpty()) {
                            MarkerIconGenerator.inferShipTypeFromName(updatedName)
                        } else existingShip.shipType

                        val staticData = envelope.message?.shipStaticData
                        val staticName = staticData?.name?.trim()?.ifEmpty { updatedName } ?: updatedName
                        val rawType = staticData?.type ?: 0
                        val staticInferredType = if (rawType != 0) rawType else MarkerIconGenerator.inferShipTypeFromName(staticName)
                        val finalType = if (staticInferredType != 0) staticInferredType else inferredType

                        val length = if (staticData?.dimension != null) {
                            staticData.dimension.toBow + staticData.dimension.toStern
                        } else existingShip.length

                        val width = if (staticData?.dimension != null) {
                            staticData.dimension.toPort + staticData.dimension.toStarboard
                        } else existingShip.width

                        val imo = if (staticData?.imoNumber != null && staticData.imoNumber > 0L) {
                            staticData.imoNumber
                        } else existingShip.imo

                        val callSign = staticData?.callSign?.trim()?.ifEmpty { existingShip.callSign } ?: existingShip.callSign

                        val etaString = if (staticData?.eta != null && staticData.eta.month > 0) {
                            String.format(Locale.US, "2026-%02d-%02d %02d:%02d (UTC)", staticData.eta.month, staticData.eta.day, staticData.eta.hour, staticData.eta.minute)
                        } else existingShip.eta

                        val transponderClass = if (envelope.message?.isClassB == true) "Class B" else existingShip.transponderClass

                        val satInfo = CoastalDistanceUtils.getSatelliteTelemetry(
                            finalLat, finalLng, mmsi, _isSatelliteAisMode.value
                        )

                        val updatedShip = existingShip.copy(
                            latitude = finalLat,
                            longitude = finalLng,
                            name = staticName,
                            shipType = finalType,
                            length = length,
                            width = width,
                            heading = rotationAngle,
                            cog = pr?.cog ?: existingShip.cog,
                            speed = pr?.sog ?: existingShip.speed,
                            destination = staticData?.destination?.trim()?.ifEmpty { existingShip.destination } ?: existingShip.destination,
                            draught = if (staticData?.draught != null && staticData.draught > 0f) staticData.draught else existingShip.draught,
                            navStatus = pr?.navStatus ?: existingShip.navStatus,
                            imo = imo,
                            callSign = callSign,
                            rot = if (pr?.rateOfTurn != null && pr.rateOfTurn != -128) pr.rateOfTurn else existingShip.rot,
                            eta = etaString,
                            transponderClass = transponderClass,
                            lastSeenMillis = System.currentTimeMillis(),
                            isSatelliteAis = satInfo.isSatelliteAis,
                            distanceFromShoreNm = satInfo.distanceFromShoreNm,
                            trackingSource = satInfo.trackingSource,
                            satelliteConstellation = satInfo.constellation,
                            satelliteSignalQuality = satInfo.signalQuality,
                            oceanZone = satInfo.oceanZone
                        )

                        currentMap + (mmsi to updatedShip)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing AIS message", e)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val status = response?.code ?: -1
                val message = response?.message ?: "unknown"
                val body = runCatching {
                    response?.body?.string()
                }.getOrNull()

                Log.w(
                    TAG,
                    "Live AIS stream failed. HTTP=${status} message=${message} body=${body ?: "n/a"} cause=${t.message}. Reconnecting in 3s..."
                )
                this@ShipRepository.webSocket = null
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $reason. Reconnecting in 3s...")
                this@ShipRepository.webSocket = null
                scheduleReconnect()
            }
        })

        return true
    }

    private var reconnectJob: Job? = null

    private fun scheduleReconnect() {
        if (reconnectJob?.isActive == true || currentApiKey.isBlank()) return
        reconnectJob = scope.launch {
            delay(3000L)
            if (webSocket == null && currentApiKey.isNotBlank()) {
                Log.d(TAG, "Attempting WebSocket reconnect...")
                startTracking(currentApiKey)
            }
        }
    }

    private var pendingBoundingBoxJob: Job? = null

    fun updateBoundingBox(north: Double, south: Double, east: Double, west: Double) {
        if (currentApiKey.isBlank()) return
        if (north == south || Math.abs(north - south) < 0.001) return

        // 1. Save the camera coordinates so we can return to them when S-AIS is turned off
        lastNorth = north
        lastSouth = south
        lastEast = east
        lastWest = west

        if (webSocket == null) {
            startTracking(currentApiKey)
        }

        // 2. Calculate the local padded viewport
        val latSpan = Math.max(Math.abs(north - south) * 2.0, 10.0)
        val lngSpan = Math.max(Math.abs(east - west) * 2.0, 15.0)
        val midLat = (north + south) / 2.0
        val midLng = (east + west) / 2.0

        val paddedSouth = Math.max(-90.0, midLat - latSpan)
        val paddedNorth = Math.min(90.0, midLat + latSpan)
        val paddedWest = Math.max(-180.0, midLng - lngSpan)
        val paddedEast = Math.min(180.0, midLng + lngSpan)

        // 🚨 3. THE S-AIS INTERCEPTOR: Inject global coordinates if Satellite Mode is active
        val boundingBoxString = if (isSatelliteMode) {
            "[[[-90.0, -180.0], [90.0, 180.0]]]" // Global Deep-Sea Coverage
        } else {
            "[[[$paddedSouth, $paddedWest], [$paddedNorth, $paddedEast]]]" // Coastal Viewport
        }

        pendingBoundingBoxJob?.cancel()
        pendingBoundingBoxJob = scope.launch {
            val now = System.currentTimeMillis()
            val timeSinceLast = now - lastSubscriptionTime
            if (timeSinceLast < 1500) {
                delay(1500 - timeSinceLast)
            }
            lastSubscriptionTime = System.currentTimeMillis()

            // 4. Send the dynamically adjusted payload to AISStream
            val subscription = """
                {
                    "APIKey": "$currentApiKey",
                    "BoundingBoxes": $boundingBoxString,
                    "FilterMessageTypes": ["PositionReport", "ShipStaticData", "StandardClassBPositionReport", "ExtendedClassBPositionReport"]
                }
            """.trimIndent()

            webSocket?.send(subscription)
        }
    }

    fun stopTracking() {
        webSocket?.cancel()
        webSocket = null

        pruningJob?.cancel()
        pruningJob = null

        currentApiKey = ""
    }
}
