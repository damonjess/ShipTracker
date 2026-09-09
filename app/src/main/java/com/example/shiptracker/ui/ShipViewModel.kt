package com.example.shiptracker.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.shiptracker.data.AppDatabase
import com.example.shiptracker.data.ShipCategory
import com.example.shiptracker.data.ShipRepository
import com.example.shiptracker.data.ShipState
import com.example.shiptracker.data.VesselDao
import com.example.shiptracker.util.MarkerIconGenerator
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ShipViewModel(
    private val repository: ShipRepository = ShipRepository,
    private val vesselDao: VesselDao
) : ViewModel() {

    init {
        repository.startTracking()
    }

    // 1. State for active UI filters
    private val _selectedFilters = MutableStateFlow<Set<ShipCategory>>(emptySet())
    val selectedFilters: StateFlow<Set<ShipCategory>> = _selectedFilters.asStateFlow()

    // Zoom & Bounds state for map viewport & clustering
    private val _currentZoom = MutableStateFlow(10.0)

    // 🚨 FIX 1: Add a memory for the screen boundaries
    data class MapViewport(val north: Double, val south: Double, val east: Double, val west: Double)
    private val _currentViewport = MutableStateFlow<MapViewport?>(null)

    fun updateZoom(zoom: Double) {
        _currentZoom.value = zoom
    }

    // 2. Unclustered real vessels in viewport matching active filters
    val unclusteredShips: StateFlow<List<ShipState>> = combine(
        repository.ships,
        _selectedFilters,
        _currentViewport
    ) { shipMap, filters, viewport ->
        shipMap.values.filter { ship ->
            val isBaseStation = ship.mmsi < 100_000_000L
            val isBuoy = ship.mmsi in 990_000_000L..999_999_999L
            val isRealVessel = !isBaseStation && !isBuoy

            val isInsideViewport = if (viewport != null) {
                val minLat = minOf(viewport.south, viewport.north) - 1.5
                val maxLat = maxOf(viewport.south, viewport.north) + 1.5
                val minLng = minOf(viewport.west, viewport.east) - 1.5
                val maxLng = maxOf(viewport.west, viewport.east) + 1.5

                ship.latitude in minLat..maxLat && ship.longitude in minLng..maxLng
            } else {
                true
            }

            val effectiveType = if (ship.shipType == 0 && ship.name.isNotEmpty()) {
                MarkerIconGenerator.inferShipTypeFromName(ship.name)
            } else {
                ship.shipType
            }

            isRealVessel && isInsideViewport && (filters.isEmpty() || filters.any { filterCategory ->
                effectiveType in filterCategory.typeCodes
            })
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // 🚨 FIX 2: Add _currentViewport to the combine function
    val visibleShips: StateFlow<List<ShipState>> = combine(
        repository.ships,
        _selectedFilters,
        _currentZoom,
        _currentViewport
    ) { shipMap, filters, zoom, viewport ->
        val allShips = shipMap.values.filter { ship ->
            val effectiveType = if (ship.shipType == 0 && ship.name.isNotEmpty()) {
                MarkerIconGenerator.inferShipTypeFromName(ship.name)
            } else {
                ship.shipType
            }

            // Filter A: Check category rules
            val validCategory = effectiveType == 0 || filters.isEmpty() || filters.any { filterCategory ->
                effectiveType in filterCategory.typeCodes
            }

            // Filter B: Spatial Culling (Only process if ship is inside the screen)
            // We add a 1.5 degree buffer around the edges so ships don't abruptly pop in
            val inViewport = if (viewport != null && zoom >= 7.0) {
                val minLat = minOf(viewport.south, viewport.north) - 1.5
                val maxLat = maxOf(viewport.south, viewport.north) + 1.5
                val minLng = minOf(viewport.west, viewport.east) - 1.5
                val maxLng = maxOf(viewport.west, viewport.east) + 1.5

                ship.latitude in minLat..maxLat && ship.longitude in minLng..maxLng
            } else {
                // If zoomed way out to see the whole country, keep all ships and let 
                // the grid clusterer handle the performance optimization
                true
            }

            validCategory && inViewport
        }

        val gridSize = when {
            zoom < 5.0 -> 1.0     // Very broad global view
            zoom < 7.0 -> 0.3     // Country view
            zoom < 8.5 -> 0.08    // Regional view
            else -> 0.0           // Zoom >= 8.5 -> Show individual vessels directly
        }

        if (gridSize == 0.0) {
            allShips
        } else {
            val grouped = allShips.groupBy {
                val gridLat = (it.latitude / gridSize).toInt() * gridSize
                val gridLng = (it.longitude / gridSize).toInt() * gridSize
                Pair(gridLat, gridLng)
            }

            grouped.map { (coords, shipsInCluster) ->
                if (shipsInCluster.size == 1) {
                    shipsInCluster.first()
                } else {
                    ShipState(
                        mmsi = -shipsInCluster.size.toLong(),
                        latitude = coords.first + (gridSize / 2),
                        longitude = coords.second + (gridSize / 2),
                        name = "CLUSTER",
                        shipType = -1
                    )
                }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // 3. Selected vessel MMSI for track retrieval
    val selectedMmsi = MutableStateFlow<Long?>(null)

    // Automatically queries Room and converts points into Google Maps LatLng coordinates
    @OptIn(ExperimentalCoroutinesApi::class)
    val activeTrackPoints: StateFlow<List<LatLng>> = selectedMmsi
        .flatMapLatest { mmsi ->
            if (mmsi == null) flowOf(emptyList())
            else vesselDao.getTrackForVessel(mmsi).map { list ->
                list.map { LatLng(it.latitude, it.longitude) }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun selectVessel(mmsi: Long) {
        selectedMmsi.value = mmsi
    }

    fun clearSelection() {
        selectedMmsi.value = null
    }

    // 4. Toggle function for the UI filters
    fun toggleFilter(category: ShipCategory) {
        _selectedFilters.update { current ->
            if (current.contains(category)) {
                current - category // Remove if already active
            } else {
                current + category // Add if not active
            }
        }
    }

    // Job to track the debounce timer
    private var viewportJob: Job? = null

    fun updateViewport(north: Double, south: Double, east: Double, west: Double) {
        // Save the current screen boundaries to our flow
        _currentViewport.value = MapViewport(north, south, east, west)

        viewportJob?.cancel()
        viewportJob = viewModelScope.launch {
            delay(800)
            repository.updateBoundingBox(north, south, east, west)
        }
    }

    companion object {
        fun provideFactory(
            context: Context,
            repository: ShipRepository = ShipRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val dao = AppDatabase.getDatabase(context.applicationContext).vesselDao()
                @Suppress("UNCHECKED_CAST")
                return ShipViewModel(repository = repository, vesselDao = dao) as T
            }
        }
    }
}
