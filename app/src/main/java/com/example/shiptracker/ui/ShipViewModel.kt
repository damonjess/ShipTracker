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

    // Zoom state for map clustering
    private val _currentZoom = MutableStateFlow(10.0)

    fun updateZoom(zoom: Double) {
        _currentZoom.value = zoom
    }

    // 2. The combined reactive stream for the UI
    val visibleShips: StateFlow<List<ShipState>> = combine(
        repository.ships,
        _selectedFilters,
        _currentZoom
    ) { shipMap, filters, zoom ->
        // 🚨 FIX: Bring back Type 0 ships, but filter out radio towers and buoys mathematically
        val allShips = shipMap.values.filter { ship ->
            // Base stations start with "00" (which becomes a 7-digit number when saved as a Long)
            val isBaseStation = ship.mmsi < 100_000_000L
            // Navigational Aids (Buoys/Lighthouses) always start with "99"
            val isBuoy = ship.mmsi in 990_000_000L..999_999_999L
            
            val isRealVessel = !isBaseStation && !isBuoy

            // 🚨 FIX: Allow type 0 (Unknowns) to show immediately, 
            // while still respecting category filters for known ships
            isRealVessel && (ship.shipType == 0 || filters.isEmpty() || filters.any { filterCategory ->
                ship.shipType in filterCategory.typeCodes
            })
        }

        // Determine grid square size based on zoom level
        val gridSize = when {
            zoom < 5.0 -> 2.0     // Europe view -> Huge grid
            zoom < 7.0 -> 1.0     // Country view -> Large grid
            zoom < 9.0 -> 0.25    // Regional view -> Small grid
            else -> 0.0           // Zoomed in -> No clustering
        }

        if (gridSize == 0.0) {
            allShips
        } else {
            // Group ships by their nearest mathematical grid coordinate
            val grouped = allShips.groupBy {
                val gridLat = (it.latitude / gridSize).toInt() * gridSize
                val gridLng = (it.longitude / gridSize).toInt() * gridSize
                Pair(gridLat, gridLng)
            }

            grouped.map { (coords, shipsInCluster) ->
                if (shipsInCluster.size == 1) {
                    shipsInCluster.first()
                } else {
                    // Create a pseudo-ship to represent the cluster
                    ShipState(
                        mmsi = -shipsInCluster.size.toLong(), // Negative MMSI stores the ship count
                        latitude = coords.first + (gridSize / 2),
                        longitude = coords.second + (gridSize / 2),
                        name = "CLUSTER",
                        shipType = -1 // Special flag to tell the UI to draw a circle
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
        viewportJob?.cancel() 

        viewportJob = viewModelScope.launch {
            // INCREASED to 1500ms to safely respect the 1-second rate limit
            delay(1500) 
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
