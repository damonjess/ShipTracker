package com.example.shiptracker

import com.example.shiptracker.data.ShipCategory
import com.example.shiptracker.data.ShipRepository
import com.example.shiptracker.data.ShipState
import com.example.shiptracker.data.VesselDao
import com.example.shiptracker.data.VesselTrackPoint
import com.example.shiptracker.ui.ShipViewModel
import com.example.shiptracker.ui.getVesselPhotoSearchTerms
import com.example.shiptracker.util.CpaCalculator
import com.example.shiptracker.util.DepartedLocationCalculator
import com.example.shiptracker.util.MarkerIconGenerator
import com.example.shiptracker.util.VoyageCalculator
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
        ShipRepository.setSatelliteAisMode(true)
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

        // Filter only Cargo ships
        viewModel.toggleFilter(ShipCategory.CARGO)
        testDispatcher.scheduler.advanceUntilIdle()

        val filteredShips = viewModel.visibleShips.value
        assertTrue(filteredShips.all { ShipCategory.CARGO.matches(it.shipType) })
    }

    @Test
    fun testSelectVesselAndClearSelection() = runTest {
        val testMmsi = 235009270L

        viewModel.selectVessel(testMmsi)
        assertEquals(testMmsi, viewModel.selectedMmsi.value)

        viewModel.clearSelection()
        assertNull(viewModel.selectedMmsi.value)
        assertNull(viewModel.followedMmsi.value)
    }

    @Test
    fun testSelectTargetUpdatesSelectedShipAndMmsi() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        val testShip = sampleShips[0]

        viewModel.selectTarget(testShip)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(testShip.mmsi, viewModel.selectedMmsi.value)
        val selected = viewModel.selectedShip.first { it != null }
        assertEquals(testShip.mmsi, selected?.mmsi)
        assertEquals(testShip.name, selected?.name)
    }

    @Test
    fun testToggleFollowAndStopFollowing() = runTest {
        val testMmsi = 235009270L

        // Toggle follow on
        viewModel.toggleFollow(testMmsi)
        assertEquals(testMmsi, viewModel.followedMmsi.value)
        assertEquals(testMmsi, viewModel.selectedMmsi.value)

        // Toggle follow off
        viewModel.toggleFollow(testMmsi)
        assertNull(viewModel.followedMmsi.value)

        // Toggle back on then stop following
        viewModel.toggleFollow(testMmsi)
        assertEquals(testMmsi, viewModel.followedMmsi.value)
        viewModel.stopFollowing()
        assertNull(viewModel.followedMmsi.value)

        // Clear selection also clears followed MMSI
        viewModel.toggleFollow(testMmsi)
        assertEquals(testMmsi, viewModel.followedMmsi.value)
        viewModel.clearSelection()
        assertNull(viewModel.followedMmsi.value)
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

    @Test
    fun testSatelliteAisModeAndCountsInViewModel() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.isSatelliteAisMode.value)

        viewModel.toggleSatelliteAisMode()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(false, viewModel.isSatelliteAisMode.value)
    }

    @Test
    fun testTotalActiveShipsCount() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        val count = viewModel.totalActiveShipsCount.first { it > 0 }
        assertEquals(ShipRepository.ships.value.size, count)
    }

    @Test
    fun testRnliLifeboatInferredShipType() {
        val rnliType = MarkerIconGenerator.inferShipTypeFromName("RNLI LIFEBOAT HUMBER")
        assertEquals(51, rnliType)

        val rescueType = MarkerIconGenerator.inferShipTypeFromName("RESCUE ONE")
        assertEquals(51, rescueType)

        val sarType = MarkerIconGenerator.inferShipTypeFromName("SAR COASTGUARD")
        assertEquals(51, sarType)
    }

    @Test
    fun testFollowedShipEmitsCorrectVessel() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        val testMmsi = 235009270L
        viewModel.toggleFollow(testMmsi)
        testDispatcher.scheduler.advanceUntilIdle()

        val followed = viewModel.followedShip.first { it != null }
        assertEquals(testMmsi, followed?.mmsi)
        assertEquals("PRIDE OF HULL", followed?.name)
    }

    @Test
    fun testSystemHealthMetricsInViewModel() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Disconnected", viewModel.webSocketState.value)

        val totalShips = viewModel.totalShipsInMemory.first { it > 0 }
        assertEquals(ShipRepository.ships.value.size, totalShips)

        val testMmsi = 235009270L
        fakeDao.insertPoint(VesselTrackPoint(mmsi = testMmsi, latitude = 53.74, longitude = -0.28))
        fakeDao.insertPoint(VesselTrackPoint(mmsi = testMmsi, latitude = 53.75, longitude = -0.27))

        testDispatcher.scheduler.advanceUntilIdle()
        val dbCount = viewModel.databasePointCount.first { it > 0 }
        assertEquals(2, dbCount)
    }

    @Test
    fun testCategoryCountsAllActiveShips() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        val counts = viewModel.categoryCounts.first { it.isNotEmpty() }
        assertEquals(1, counts[ShipCategory.PASSENGER])
        assertEquals(1, counts[ShipCategory.CARGO])
        assertEquals(1, counts[ShipCategory.TANKER])
    }

    @Test
    fun testDepartedLocationCalculationWithTrackPoints() = runTest {
        val testMmsi = 235009270L
        val now = System.currentTimeMillis()

        // Point 1: Docked at port (53.74, -0.28)
        val p1 = VesselTrackPoint(mmsi = testMmsi, latitude = 53.74, longitude = -0.28, timestamp = now - 3600000)
        // Point 2: Still docked at port (53.74, -0.28) 30 min later
        val p2 = VesselTrackPoint(mmsi = testMmsi, latitude = 53.74, longitude = -0.28, timestamp = now - 1800000)
        // Point 3: Underway at sea (53.80, -0.10)
        val p3 = VesselTrackPoint(mmsi = testMmsi, latitude = 53.80, longitude = -0.10, timestamp = now)

        val points = listOf(p1, p2, p3)
        val coords = DepartedLocationCalculator.findDepartureCoordinates(points, sampleShips[0])

        assertTrue(coords != null)
        assertEquals(53.74, coords!!.first, 0.001)
        assertEquals(-0.28, coords.second, 0.001)

        val name = DepartedLocationCalculator.getDepartedLocationName(null, coords.first, coords.second)
        assertEquals("Port at 53.74°, -0.28°", name)
    }

    @Test
    fun testToggleFavoritesAndFilter() = runTest {
        val testMmsi = 235009270L

        // Initially no favorites
        assertTrue(viewModel.favoriteMmsis.value.isEmpty())

        // Add to favorites
        viewModel.toggleFavorite(testMmsi)
        assertTrue(viewModel.favoriteMmsis.value.contains(testMmsi))

        // Toggle favorites-only filter
        viewModel.toggleFavoritesOnly()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.isFavoritesOnly.value)
        val visible = viewModel.visibleShips.value
        assertTrue(visible.all { it.mmsi == testMmsi || it.mmsi < 0L })

        // Toggle back off
        viewModel.toggleFavoritesOnly()
        viewModel.toggleFavorite(testMmsi)
        assertTrue(viewModel.favoriteMmsis.value.isEmpty())
    }

    @Test
    fun testVoyageCalculatorDistanceAndEta() = runTest {
        val result = VoyageCalculator.calculateDistanceAndEta(
            context = null,
            currentLat = 53.655,
            currentLng = 0.050,
            speedKnots = 10.0f,
            destinationName = "HULL"
        )

        assertTrue(result != null)
        assertTrue(result!!.distanceNm > 0.0)
        assertTrue(result.calculatedEtaText.contains("NM"))
    }

    @Test
    fun testCpaCalculation() = runTest {
        val shipA = ShipState(mmsi = 1001L, latitude = 53.00, longitude = 0.00, speed = 12.0f, heading = 90.0f, cog = 90.0f)
        val shipB = ShipState(mmsi = 1002L, latitude = 53.00, longitude = 0.10, speed = 12.0f, heading = 270.0f, cog = 270.0f)

        val result = CpaCalculator.calculateCpa(shipA, listOf(shipB))
        assertTrue(result != null)
        assertTrue(result!!.isRisk)
        assertTrue(result.cpaNm < 0.1)
    }

    private class FakeVesselDao : VesselDao {
        private val pointsMap = mutableMapOf<Long, MutableList<VesselTrackPoint>>()
        private val flowMap = mutableMapOf<Long, MutableStateFlow<List<VesselTrackPoint>>>()
        private val totalCountFlow = MutableStateFlow(0)

        private fun updateTotalCount() {
            totalCountFlow.value = pointsMap.values.sumOf { it.size }
        }

        override suspend fun insertPoint(point: VesselTrackPoint) {
            val list = pointsMap.getOrPut(point.mmsi) { mutableListOf() }
            list.add(point)
            flowMap.getOrPut(point.mmsi) { MutableStateFlow(emptyList()) }.value = list.toList()
            updateTotalCount()
        }

        override suspend fun insertPoints(points: List<VesselTrackPoint>) {
            points.forEach { point ->
                val list = pointsMap.getOrPut(point.mmsi) { mutableListOf() }
                list.add(point)
                flowMap.getOrPut(point.mmsi) { MutableStateFlow(emptyList()) }.value = list.toList()
            }
            updateTotalCount()
        }

        override suspend fun getAllPoints(): List<VesselTrackPoint> {
            return pointsMap.values.flatten()
        }

        override fun getTrackForVessel(mmsi: Long): Flow<List<VesselTrackPoint>> {
            return flowMap.getOrPut(mmsi) { MutableStateFlow(pointsMap[mmsi]?.toList() ?: emptyList()) }
        }

        override fun getTotalTrackPointCount(): Flow<Int> {
            return totalCountFlow
        }

        override suspend fun deleteOldPoints(cutoffTime: Long) {
            pointsMap.values.forEach { list -> list.removeAll { it.timestamp < cutoffTime } }
            updateTotalCount()
        }

        override suspend fun getLastPointForVessel(mmsi: Long): VesselTrackPoint? {
            return pointsMap[mmsi]?.maxByOrNull { it.timestamp }
        }
    }
}
