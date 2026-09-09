package com.example.shiptracker

import com.example.shiptracker.data.AisStreamMessage
import com.example.shiptracker.data.ShipRepository
import com.example.shiptracker.data.ShipState
import com.example.shiptracker.ui.getMatchedDestination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class ShipRepositoryTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        ShipRepository.setShips(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testUpdateShipAddsVessel() = runTest {
        val testShip = ShipState(
            mmsi = 235009270L,
            latitude = 53.655,
            longitude = 0.050,
            name = "PRIDE OF HULL",
            shipType = 60,
            length = 215
        )
        ShipRepository.updateShip(testShip)

        val shipsMap = ShipRepository.ships.value
        assertTrue("Ship repository should contain added vessel", shipsMap.isNotEmpty())

        val retrieved = shipsMap[235009270L]
        assertNotNull("Added vessel should exist in repository", retrieved)
        assertEquals("PRIDE OF HULL", retrieved?.name)
        assertEquals(60, retrieved?.shipType)
        assertEquals(215, retrieved?.length)
    }

    @Test
    fun testStartTrackingAcceptsRealApiKey() {
        ShipRepository.stopTracking()

        val started = ShipRepository.startTracking("aisstream-live-key-123456")

        assertTrue(
            "A real AIS API key should start tracking",
            started
        )
    }

    @Test
    fun testAisStreamMetadataParsesActualPayloadCoordinates() {
        val payload = """
            {
              "MessageType": "PositionReport",
              "MetaData": {
                "MMSI": 123456789,
                "ShipName": "EVER GIVEN",
                "Latitude": 30.002,
                "Longitude": 32.583
              },
              "Message": {
                "PositionReport": {
                  "Sog": 9.4,
                  "Cog": 318.2
                }
              }
            }
        """.trimIndent()

        val message = kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }.decodeFromString<AisStreamMessage>(payload)

        assertEquals(30.002, message.metaData?.effectiveLatitude ?: 0.0, 0.0001)
        assertEquals(32.583, message.metaData?.effectiveLongitude ?: 0.0, 0.0001)
    }

    @Test
    fun testMatchedDestinationDoesNotDefaultToHull() {
        val dest1 = getMatchedDestination("NLRTM")
        assertEquals("Rotterdam, NETHERLANDS", dest1)

        val dest2 = getMatchedDestination("GBLON")
        assertEquals("London, UNITED KINGDOM", dest2)

        val destUnknown = getMatchedDestination("UNKNOWN")
        assertEquals("-", destUnknown)

        val destEmpty = getMatchedDestination("")
        assertEquals("-", destEmpty)

        val destHull = getMatchedDestination("GBHUL")
        assertEquals("Hull, UNITED KINGDOM", destHull)
    }
}
