package com.example.shiptracker.data

import android.util.Log
import com.example.shiptracker.BuildConfig
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

                // Track when we sent the first subscription
                lastSubscriptionTime = System.currentTimeMillis()

                val subscription = """
                    {
                        "APIKey": "$currentApiKey",
                        "BoundingBoxes": [[[-90.0, -180.0], [90.0, 180.0]]],
                        "FilterMessageTypes": ["PositionReport", "ShipStaticData"]
                    }
                """.trimIndent()
                webSocket.send(subscription)
            }

            // 🚨 FIX 1: Catch the binary frames and convert them to text
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
                            latitude = if (metaLat != 0.0) metaLat else 53.65,
                            longitude = if (metaLng != 0.0) metaLng else 0.05
                        )

                        val updatedShip = when (envelope.messageType) {
                            "PositionReport" -> {
                                val pr = envelope.message?.positionReport
                                val prLat = pr?.latitude
                                val prLng = pr?.longitude

                                val reportLat = if (prLat != null && prLat != 0.0) prLat else metaLat
                                val reportLng = if (prLng != null && prLng != 0.0) prLng else metaLng

                                val finalLat = if (reportLat != 0.0) reportLat else existingShip.latitude
                                val finalLng = if (reportLng != 0.0) reportLng else existingShip.longitude

                                val rotationAngle = if (pr != null && pr.trueHeading != 511) {
                                    pr.trueHeading.toFloat()
                                } else {
                                    pr?.cog ?: existingShip.heading
                                }

                                if (finalLat != 0.0 && finalLng != 0.0) {
                                    handlePositionReport(mmsi, finalLat, finalLng)
                                }

                                existingShip.copy(
                                    latitude = finalLat,
                                    longitude = finalLng,
                                    name = metaData.shipName.ifEmpty { existingShip.name },
                                    heading = rotationAngle,
                                    speed = pr?.sog ?: existingShip.speed,
                                    navStatus = pr?.navStatus ?: existingShip.navStatus,
                                    lastSeenMillis = System.currentTimeMillis()
                                )
                            }
                            "ShipStaticData" -> {
                                val staticData = envelope.message?.shipStaticData
                                val length = if (staticData?.dimension != null) {
                                    staticData.dimension.toBow + staticData.dimension.toStern
                                } else existingShip.length

                                existingShip.copy(
                                    name = staticData?.name?.trim()?.ifEmpty { existingShip.name } ?: existingShip.name,
                                    shipType = if ((staticData?.type ?: 0) != 0) staticData!!.type else existingShip.shipType,
                                    length = length,
                                    destination = staticData?.destination?.trim()?.ifEmpty { existingShip.destination } ?: existingShip.destination,
                                    draught = staticData?.draught ?: existingShip.draught,
                                    lastSeenMillis = System.currentTimeMillis()
                                )
                            }
                            else -> existingShip.copy(lastSeenMillis = System.currentTimeMillis())
                        }

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
                    "Live AIS stream failed. HTTP=${status} message=${message} body=${body ?: "n/a"} cause=${t.message}. No mock vessel data will be injected."
                )
                this@ShipRepository.webSocket = null
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $reason")
                this@ShipRepository.webSocket = null
            }
        })

        return true
    }

    fun updateBoundingBox(north: Double, south: Double, east: Double, west: Double) {
        if (currentApiKey.isBlank()) {
            return
        }

        // 🚨 FIX 2: Enforce a hard 1.5-second gap between updates
        val now = System.currentTimeMillis()
        if (now - lastSubscriptionTime < 1500) {
            Log.w(TAG, "Throttling bounding box update to prevent AISStream disconnect")
            return 
        }
        lastSubscriptionTime = now

        val subscription = """
            {
                "APIKey": "$currentApiKey",
                "BoundingBoxes": [[[$south, $west], [$north, $east]]],
                "FilterMessageTypes": ["PositionReport", "ShipStaticData"]
            }
        """.trimIndent()

        webSocket?.send(subscription)
    }

    fun stopTracking() {
        webSocket?.cancel()
        webSocket = null

        pruningJob?.cancel()
        pruningJob = null

        currentApiKey = ""
    }
}
