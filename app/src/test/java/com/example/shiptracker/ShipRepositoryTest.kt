package com.example.shiptracker

import com.example.shiptracker.data.ShipRepository
import com.example.shiptracker.data.ShipState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

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
}
