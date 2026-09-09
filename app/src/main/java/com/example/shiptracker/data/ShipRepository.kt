package com.example.shiptracker.data

import android.util.Log
import com.example.shiptracker.BuildConfig
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

    private fun isPlaceholderApiKey(value: String): Boolean {
        val normalized = value.trim()
        if (normalized.isEmpty()) return true
        return normalized.lowercase() in placeholderApiKeys || normalized.contains("example", ignoreCase = true)
    }

    private fun resolveApiKey(explicitApiKey: String?): String? {
        // Force the app to use your key, bypassing the VSCode build config
        return "f35c030db565d8a5a1b3eeeb45461925819af918"
    }

    private fun resolveMmsi(metaData: AisMetaData): Long {
        if (metaData.mmsi != 0L) return metaData.mmsi

        val seed = (metaData.shipName.ifBlank { "unknown" } + metaData.effectiveLatitude + metaData.effectiveLongitude).hashCode()
        return (seed.toLong() and Long.MAX_VALUE) % 9_000_000_000L + 1_000_000_000L
    }

    // Garbage collection variables
    private var pruningJob: Job? = null
    // 🚨 UPDATE: Change this from 15 minutes to 12 hours
    // This keeps parked/sleeping ships on your map even if they stop transmitting
    private const val STALE_TIMEOUT_MILLIS = 12 * 60 * 60 * 1000L

    fun initialize(dao: VesselDao) {
        vesselDao = dao
        purgeOldTrackPoints()
    }

    private fun purgeOldTrackPoints() {
        scope.launch {
            val fortyEightHoursAgo = System.currentTimeMillis() - (48 * 60 * 60 * 1000L)
            vesselDao?.deleteOldPoints(fortyEightHoursAgo)
        }
    }

    fun updateShip(ship: ShipState) {
        _ships.update { current -> current + (ship.mmsi to ship) }
    }

    fun setShips(ships: List<ShipState>) {
        _ships.value = ships.associateBy { it.mmsi }
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

                val subscription = """
                    {
                        "APIKey": "$currentApiKey",
                        "BoundingBoxes": [[[35.0, -25.0], [70.0, 35.0]]],
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
                            lastSeenMillis = System.currentTimeMillis()
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

        if (webSocket == null) {
            startTracking(currentApiKey)
        }

        // Pad to ensure wide regional coverage (at least 20 deg lat x 30 deg lon)
        val latSpan = Math.max(Math.abs(north - south) * 2.0, 10.0)
        val lngSpan = Math.max(Math.abs(east - west) * 2.0, 15.0)
        val midLat = (north + south) / 2.0
        val midLng = (east + west) / 2.0

        val paddedSouth = Math.max(-90.0, midLat - latSpan)
        val paddedNorth = Math.min(90.0, midLat + latSpan)
        val paddedWest = Math.max(-180.0, midLng - lngSpan)
        val paddedEast = Math.min(180.0, midLng + lngSpan)

        pendingBoundingBoxJob?.cancel()
        pendingBoundingBoxJob = scope.launch {
            val now = System.currentTimeMillis()
            val timeSinceLast = now - lastSubscriptionTime
            if (timeSinceLast < 1500) {
                delay(1500 - timeSinceLast)
            }
            lastSubscriptionTime = System.currentTimeMillis()

            val subscription = """
                {
                    "APIKey": "$currentApiKey",
                    "BoundingBoxes": [[[$paddedSouth, $paddedWest], [$paddedNorth, $paddedEast]]],
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
