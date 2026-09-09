package com.example.shiptracker

import com.example.shiptracker.data.ShipCategory
import com.example.shiptracker.data.ShipRepository
import com.example.shiptracker.data.ShipState
import com.example.shiptracker.data.VesselDao
import com.example.shiptracker.data.VesselTrackPoint
import com.example.shiptracker.ui.ShipViewModel
import com.example.shiptracker.ui.getVesselPhotoSearchTerms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShipViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeDao: FakeVesselDao
    private lateinit var viewModel: ShipViewModel

    private val sampleShips = listOf(
        ShipState(mmsi = 235009270L, latitude = 53.655, longitude = 0.050, name = "PRIDE OF HULL", shipType = 60, length = 215),
        ShipState(mmsi = 235123889L, latitude = 53.610, longitude = 0.250, name = "NORTH SEA FREIGHTER", shipType = 70, length = 180),
        ShipState(mmsi = 235443322L, latitude = 53.450, longitude = 0.600, name = "OCEAN TANKER", shipType = 80, length = 240)
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeDao = FakeVesselDao()
        ShipRepository.setShips(sampleShips)
        viewModel = ShipViewModel(repository = ShipRepository, vesselDao = fakeDao)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialVisibleShipsNotEmpty() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        val ships = viewModel.visibleShips.first { it.isNotEmpty() }
        assertTrue(ships.isNotEmpty())
    }

    @Test
    fun testToggleFilterAddsAndRemovesCategory() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        // Initially no filters
        assertEquals(emptySet<ShipCategory>(), viewModel.selectedFilters.value)

        // Toggle Cargo
        viewModel.toggleFilter(ShipCategory.CARGO)
        assertTrue(viewModel.selectedFilters.value.contains(ShipCategory.CARGO))

        // Toggle Cargo again removes it
        viewModel.toggleFilter(ShipCategory.CARGO)
        assertTrue(viewModel.selectedFilters.value.isEmpty())
    }

    @Test
    fun testFilteringShipsByCategory() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        // Filter only Cargo ships (type codes 70..79)
        viewModel.toggleFilter(ShipCategory.CARGO)
        testDispatcher.scheduler.advanceUntilIdle()

        val filteredShips = viewModel.visibleShips.value
        assertTrue(filteredShips.all { it.shipType in ShipCategory.CARGO.typeCodes })
    }

    @Test
    fun testSelectVesselAndClearSelection() = runTest {
        val testMmsi = 235009270L

        viewModel.selectVessel(testMmsi)
        assertEquals(testMmsi, viewModel.selectedMmsi.value)

        viewModel.clearSelection()
        assertNull(viewModel.selectedMmsi.value)
    }

    @Test
    fun testTrackPointsUpdatedForSelectedVessel() = runTest {
        val testMmsi = 235009270L
        fakeDao.insertPoint(VesselTrackPoint(mmsi = testMmsi, latitude = 53.74, longitude = -0.28))
        fakeDao.insertPoint(VesselTrackPoint(mmsi = testMmsi, latitude = 53.75, longitude = -0.27))

        viewModel.selectVessel(testMmsi)
        testDispatcher.scheduler.advanceUntilIdle()

        val track = viewModel.activeTrackPoints.first { it.isNotEmpty() }
        assertEquals(2, track.size)
        assertEquals(53.74, track[0].latitude, 0.001)
        assertEquals(-0.28, track[0].longitude, 0.001)
    }

    @Test
    fun testPhotoSearchTermsIncludeShipNameWhenImoMissing() {
        val terms = getVesselPhotoSearchTerms(
            imo = "-",
            shipName = "North Sea Freighter"
        )

        assertEquals(listOf("North Sea Freighter", "North Sea Freighter ship"), terms)
    }

    private class FakeVesselDao : VesselDao {
        private val pointsMap = mutableMapOf<Long, MutableList<VesselTrackPoint>>()
        private val flowMap = mutableMapOf<Long, MutableStateFlow<List<VesselTrackPoint>>>()

        override suspend fun insertPoint(point: VesselTrackPoint) {
            val list = pointsMap.getOrPut(point.mmsi) { mutableListOf() }
            list.add(point)
            flowMap.getOrPut(point.mmsi) { MutableStateFlow(emptyList()) }.value = list.toList()
        }

        override fun getTrackForVessel(mmsi: Long): Flow<List<VesselTrackPoint>> {
            return flowMap.getOrPut(mmsi) { MutableStateFlow(pointsMap[mmsi]?.toList() ?: emptyList()) }
        }

        override suspend fun deleteOldPoints(cutoffTime: Long) {
            pointsMap.values.forEach { list -> list.removeAll { it.timestamp < cutoffTime } }
        }
    }
}
