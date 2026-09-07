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

    // 2. The combined reactive stream for the UI
    val visibleShips: StateFlow<List<ShipState>> = combine(
        repository.ships,
        _selectedFilters
    ) { shipMap, filters ->
        val allShips = shipMap.values

        // If no filters are selected, show everything
        if (filters.isEmpty()) {
            allShips.toList()
        } else {
            // Otherwise, only keep ships whose AIS code falls into an active filter range
            allShips.filter { ship ->
                filters.any { filterCategory ->
                    ship.shipType in filterCategory.typeCodes
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
        viewportJob?.cancel() // Cancel the previous timer if the user is still swiping

        viewportJob = viewModelScope.launch {
            delay(800) // Wait 800 milliseconds for the map to settle
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
