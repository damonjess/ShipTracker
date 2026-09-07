package com.example.shiptracker.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

object ShipRepository {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    // Change the StateFlow to use our new unified ShipState
    private val _ships = MutableStateFlow<Map<Long, ShipState>>(emptyMap())
    val ships: StateFlow<Map<Long, ShipState>> = _ships

    private var vesselDao: VesselDao? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    fun initialize(dao: VesselDao) {
        vesselDao = dao
    }

    private fun handlePositionReport(metaData: AisMetaData) {
        scope.launch {
            vesselDao?.insertPoint(
                VesselTrackPoint(
                    mmsi = metaData.mmsi,
                    latitude = metaData.latitude,
                    longitude = metaData.longitude
                )
            )
        }
    }

    private var webSocket: WebSocket? = null

    fun startTracking(apiKey: String = "YOUR_AISSTREAM_KEY") {
        if (webSocket != null) return
        val request = Request.Builder().url("wss://stream.aisstream.io/v0/stream").build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val subscription = """
                    {
                        "APIKey": "$apiKey",
                        "BoundingBoxes": [[[49.0, -5.0], [52.0, 2.0]]],
                        "FilterMessageTypes": ["PositionReport", "ShipStaticData"]
                    }
                """.trimIndent()
                webSocket.send(subscription)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val envelope = json.decodeFromString<AisStreamMessage>(text)
                    val mmsi = envelope.metaData.mmsi

                    _ships.update { currentMap ->
                        // Grab the existing ship, or create a new one if this is the first time seeing it
                        val existingShip = currentMap[mmsi] ?: ShipState(
                            mmsi = mmsi,
                            latitude = envelope.metaData.latitude,
                            longitude = envelope.metaData.longitude
                        )

                        // Merge the new data based on what type of message just arrived
                        val updatedShip = when (envelope.messageType) {
                            "PositionReport" -> {
                                val pr = envelope.message?.positionReport

                                // Calculate the best available rotation angle
                                val rotationAngle = if (pr != null && pr.trueHeading != 511) {
                                    pr.trueHeading.toFloat()
                                } else {
                                    pr?.cog ?: existingShip.heading
                                }

                                handlePositionReport(envelope.metaData)
                                existingShip.copy(
                                    latitude = envelope.metaData.latitude,
                                    longitude = envelope.metaData.longitude,
                                    name = envelope.metaData.shipName.ifEmpty { existingShip.name },
                                    heading = rotationAngle
                                )
                            }
                            "ShipStaticData" -> {
                                val staticData = envelope.message?.shipStaticData
                                val length = if (staticData?.dimension != null) {
                                    staticData.dimension.toBow + staticData.dimension.toStern
                                } else existingShip.length

                                existingShip.copy(
                                    name = staticData?.name?.trim()?.ifEmpty { existingShip.name } ?: existingShip.name,
                                    shipType = staticData?.type ?: existingShip.shipType,
                                    length = length
                                )
                            }
                            else -> existingShip
                        }

                        currentMap + (mmsi to updatedShip)
                    }
                } catch (e: Exception) {
                    // Ignore parse errors for unhandled message types
                }
            }
        })
    }

    fun stopTracking() {
        webSocket?.close(1000, "Tracking stopped")
        webSocket = null
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }
}
