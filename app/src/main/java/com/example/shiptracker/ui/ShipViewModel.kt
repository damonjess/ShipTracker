package com.example.shiptracker.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.shiptracker.data.AppDatabase
import com.example.shiptracker.data.Earthquake
import com.example.shiptracker.data.EarthquakeRepository
import com.example.shiptracker.data.MarineWeather
import com.example.shiptracker.data.ShipCategory
import com.example.shiptracker.data.ShipRepository
import com.example.shiptracker.data.ShipState
import com.example.shiptracker.data.VesselDao
import com.example.shiptracker.data.VesselTrackPoint
import com.example.shiptracker.data.WeatherRepository
import com.example.shiptracker.service.PortGeofenceNotifier
import com.example.shiptracker.util.CpaCalculator
import com.example.shiptracker.util.CpaResult
import com.example.shiptracker.util.DepartedLocationCalculator
import com.example.shiptracker.util.MarkerIconGenerator
import com.example.shiptracker.util.VoyageCalculator
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ShipViewModel(
    private val repository: ShipRepository = ShipRepository,
    private val vesselDao: VesselDao,
    private val context: Context? = null
) : ViewModel() {

    // 🚨 NEW: System Health Metrics
    val webSocketState: StateFlow<String> = repository.webSocketState
    val messagesPerSecond: StateFlow<Int> = repository.messagesPerSecond

    // Track total ships currently held in active RAM
    val totalShipsInMemory: StateFlow<Int> = repository.ships
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Track the physical SQLite storage size via our new Dao Flow
    val databasePointCount: StateFlow<Int> = vesselDao.getTotalTrackPointCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // 🚨 NEW: StateFlow for the UI Switch
    private val _isSatelliteMode = MutableStateFlow(repository.isSatelliteAisMode.value)
    val isSatelliteMode: StateFlow<Boolean> = _isSatelliteMode.asStateFlow()

    fun toggleSatelliteMode(enabled: Boolean) {
        _isSatelliteMode.value = enabled
        repository.setSatelliteMode(enabled)
    }

    val isSatelliteAisMode: StateFlow<Boolean> = repository.isSatelliteAisMode

    fun toggleSatelliteAisMode() {
        val nextState = !_isSatelliteMode.value
        toggleSatelliteMode(nextState)
    }

    val satelliteVesselsCount: StateFlow<Int> = repository.ships
        .map { map -> map.values.count { it.isSatelliteAis } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val terrestrialVesselsCount: StateFlow<Int> = repository.ships
        .map { map -> map.values.count { !it.isSatelliteAis } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val deepSeaVesselsCount: StateFlow<Int> = repository.ships
        .map { map -> map.values.count { it.distanceFromShoreNm > 18.0 } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalActiveShipsCount: StateFlow<Int> = repository.ships
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val allShips: StateFlow<List<ShipState>> = repository.ships
        .map { it.values.toList() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 1. State for active UI filters
    private val _selectedFilters = MutableStateFlow<Set<ShipCategory>>(emptySet())
    val selectedFilters: StateFlow<Set<ShipCategory>> = _selectedFilters.asStateFlow()

    // 🚨 NEW: Favorites Watchlist ("My Fleet") state
    private val _favoriteMmsis = MutableStateFlow<Set<Long>>(emptySet())
    val favoriteMmsis: StateFlow<Set<Long>> = _favoriteMmsis.asStateFlow()

    private val _isFavoritesOnly = MutableStateFlow(false)
    val isFavoritesOnly: StateFlow<Boolean> = _isFavoritesOnly.asStateFlow()

    // 🚨 1. Add Earthquake StateFlows
    val earthquakes: StateFlow<List<Earthquake>> = EarthquakeRepository.earthquakes

    // 🚨 NEW: Global Tsunami Watchdog
    val activeTsunamiThreat: StateFlow<Earthquake?> = earthquakes.map { list ->
        val twentyFourHoursAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000L)
        // Find the most recent quake that has a tsunami warning AND happened recently
        list.firstOrNull { it.isTsunamiWarning && it.magnitude >= 6.0 && it.timeMillis > twentyFourHoursAgo }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    
    // Active filter: Defaults to 2.5 (USGS minimum, shows everything)
    private val _minQuakeMag = MutableStateFlow(2.5)
    val minQuakeMag: StateFlow<Double> = _minQuakeMag.asStateFlow()

    fun setMinQuakeMagnitude(magnitude: Double) {
        _minQuakeMag.value = magnitude
    }

    private val _selectedEarthquake = MutableStateFlow<Earthquake?>(null)
    val selectedEarthquake: StateFlow<Earthquake?> = _selectedEarthquake.asStateFlow()

    fun selectEarthquake(earthquake: Earthquake?) {
        _selectedEarthquake.value = earthquake
    }

    init {
        // NOTE: repository.startTracking() was removed from here.
        // Tracking is started by ShipTrackingService.onStartCommand(), which is
        // launched from MainActivity. Having the ViewModel also start tracking
        // was redundant and could start the WebSocket before the foreground
        // service notification was shown, violating Android's foreground
        // service requirements. It also meant that if the system recreated
        // the ViewModel (e.g. after a process death), tracking would restart
        // in the background even if the user had closed the app.
        viewModelScope.launch {
            combine(repository.ships, _favoriteMmsis) { shipMap, favorites ->
                Pair(shipMap.values.toList(), favorites)
            }.collect { (ships, favorites) ->
                context?.applicationContext?.let { appContext ->
                    PortGeofenceNotifier.checkGeofencesAndNotify(appContext, ships, favorites)
                }
            }
        }
    }

    fun toggleFavorite(mmsi: Long) {
        _favoriteMmsis.update { current ->
            if (current.contains(mmsi)) current - mmsi else current + mmsi
        }
    }

    fun toggleFavoritesOnly() {
        _isFavoritesOnly.update { !it }
    }

    // Zoom & Bounds state for map viewport & clustering
    private val _currentZoom = MutableStateFlow(10.0)

    // 🚨 FIX 1: Add a memory for the screen boundaries
    data class MapViewport(val north: Double, val south: Double, val east: Double, val west: Double)
    private val _currentViewport = MutableStateFlow<MapViewport?>(null)

    // Category counters across all active vessels in memory
    val categoryCounts: StateFlow<Map<ShipCategory, Int>> = repository.ships
        .map { shipMap ->
            val counts = mutableMapOf<ShipCategory, Int>()
            ShipCategory.entries.forEach { counts[it] = 0 }

            shipMap.values.forEach { ship ->
                val effectiveType = if (ship.shipType == 0 && ship.name.isNotEmpty()) {
                    MarkerIconGenerator.inferShipTypeFromName(ship.name)
                } else ship.shipType

                ShipCategory.entries.forEach { category ->
                    if (category.matches(effectiveType)) {
                        counts[category] = (counts[category] ?: 0) + 1
                    }
                }
            }
            counts
        }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun updateZoom(zoom: Double) {
        _currentZoom.value = zoom
    }

    // 2. Unclustered real vessels in viewport matching active filters
    val unclusteredShips: StateFlow<List<ShipState>> = combine(
        repository.ships,
        _selectedFilters,
        _currentViewport,
        _favoriteMmsis,
        _isFavoritesOnly
    ) { shipMap, filters, viewport, favorites, favOnly ->
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

            val passesCategory = filters.isEmpty() || filters.any { filterCategory ->
                filterCategory.matches(effectiveType)
            }

            val passesFavorite = !favOnly || favorites.contains(ship.mmsi)

            isRealVessel && isInsideViewport && passesCategory && passesFavorite
        }
    }.flowOn(Dispatchers.Default)
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val visibleShips: StateFlow<List<ShipState>> = combine(
        unclusteredShips,
        _currentZoom
    ) { ships, zoom ->
        val gridSize = when {
            zoom < 5.0 -> 1.0     // Very broad global view
            zoom < 7.0 -> 0.3     // Country view
            zoom < 8.5 -> 0.08    // Regional view
            else -> 0.0           // Zoom >= 8.5 -> Show individual vessels directly
        }

        if (gridSize == 0.0) {
            ships
        } else {
            val grouped = ships.groupBy {
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
    }.flowOn(Dispatchers.Default)
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // 3. Selected vessel MMSI for track retrieval
    val selectedMmsi = MutableStateFlow<Long?>(null)

    // 🚨 NEW: Track which vessel the camera is locked onto
    val followedMmsi = MutableStateFlow<Long?>(null)

    val followedShip: StateFlow<ShipState?> = combine(
        repository.ships,
        followedMmsi
    ) { shipMap, mmsi ->
        if (mmsi != null) shipMap[mmsi] else null
    }.flowOn(Dispatchers.Default)
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

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

    // 🚨 NEW: Automatically calculates and reverse-geocodes the departure location
    @OptIn(ExperimentalCoroutinesApi::class)
    val departedLocation: StateFlow<String?> = selectedMmsi
        .flatMapLatest { mmsi ->
            if (mmsi == null) flowOf(null)
            else vesselDao.getTrackForVessel(mmsi).map { points ->
                val currentShip = repository.ships.value[mmsi]
                val coords = DepartedLocationCalculator.findDepartureCoordinates(points, currentShip)
                if (coords != null) {
                    DepartedLocationCalculator.getDepartedLocationName(
                        context?.applicationContext,
                        coords.first,
                        coords.second
                    )
                } else {
                    null
                }
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    // 🚨 NEW: Raw track points flow for GPX export
    @OptIn(ExperimentalCoroutinesApi::class)
    val rawSelectedTrackPoints: StateFlow<List<VesselTrackPoint>> = selectedMmsi
        .flatMapLatest { mmsi ->
            if (mmsi == null) flowOf(emptyList())
            else vesselDao.getTrackForVessel(mmsi)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // 🚨 NEW: Calculated Distance to Destination & ETA
    @OptIn(ExperimentalCoroutinesApi::class)
    val calculatedEta: StateFlow<String?> = selectedMmsi
        .flatMapLatest { mmsi ->
            if (mmsi == null) flowOf(null)
            else repository.ships.map { shipMap ->
                val ship = shipMap[mmsi]
                if (ship != null && ship.destination.isNotBlank() && ship.destination != "UNKNOWN" && ship.destination != "-") {
                    val result = VoyageCalculator.calculateDistanceAndEta(
                        context,
                        ship.latitude,
                        ship.longitude,
                        ship.speed,
                        ship.destination
                    )
                    result?.calculatedEtaText
                } else null
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    // 🚨 NEW: Closest Point of Approach (CPA / TCPA) collision risk flow
    val cpaRisk: StateFlow<CpaResult?> = combine(
        selectedMmsi,
        repository.ships
    ) { mmsi, shipMap ->
        if (mmsi == null) null
        else {
            val selectedShip = shipMap[mmsi]
            if (selectedShip != null) {
                CpaCalculator.calculateCpa(selectedShip, shipMap.values.toList())
            } else null
        }
    }.flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // 🚨 NEW: Live Marine Weather for viewport center
    @OptIn(ExperimentalCoroutinesApi::class)
    val currentMarineWeather: StateFlow<MarineWeather?> = _currentViewport
        .flatMapLatest { viewport ->
            if (viewport == null) flowOf(null)
            else {
                val centerLat = (viewport.north + viewport.south) / 2.0
                val centerLng = (viewport.east + viewport.west) / 2.0
                val weather = WeatherRepository.getWeatherForLocation(centerLat, centerLng)
                flowOf(weather)
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun selectVessel(mmsi: Long) {
        selectedMmsi.value = mmsi
    }

    fun clearSelection() {
        selectedMmsi.value = null
        followedMmsi.value = null // Stop following if we close the sheet
    }

    // 🚨 NEW: Toggle the follow state
    fun toggleFollow(mmsi: Long) {
        if (followedMmsi.value == mmsi) {
            followedMmsi.value = null
        } else {
            followedMmsi.value = mmsi
            selectedMmsi.value = mmsi // Ensure it's selected so the wake trail draws
        }
    }

    // 🚨 NEW: Break the lock when the user touches the map
    fun stopFollowing() {
        followedMmsi.value = null
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
                val appContext = context.applicationContext
                val dao = AppDatabase.getDatabase(appContext).vesselDao()
                @Suppress("UNCHECKED_CAST")
                return ShipViewModel(repository = repository, vesselDao = dao, context = appContext) as T
            }
        }
    }
}
