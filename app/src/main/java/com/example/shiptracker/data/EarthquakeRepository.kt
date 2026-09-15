package com.example.shiptracker.data

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

object EarthquakeRepository {
    private const val TAG = "EarthquakeRepository"
    
    // USGS endpoint: All earthquakes Mag 2.5 and higher over the past 7 days
    private const val USGS_FEED_URL = "https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/2.5_week.geojson"

    private val client = OkHttpClient()
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val _earthquakes = MutableStateFlow<List<Earthquake>>(emptyList())
    val earthquakes: StateFlow<List<Earthquake>> = _earthquakes

    private val scope = CoroutineScope(Dispatchers.IO)
    private var syncJob: Job? = null

    fun startSyncing() {
        if (syncJob?.isActive == true) return

        syncJob = scope.launch {
            while (isActive) {
                fetchEarthquakes()
                // Update every 5 minutes (USGS rate limit recommendation)
                delay(5 * 60 * 1000L)
            }
        }
    }

    fun stopSyncing() {
        syncJob?.cancel()
        syncJob = null
    }

    private fun fetchEarthquakes() {
        try {
            val request = Request.Builder().url(USGS_FEED_URL).build()
            client.newCall(request).execute().use { response ->
                val bodyText = response.body?.string()
                if (response.isSuccessful && !bodyText.isNullOrBlank()) {
                    
                    val parsedResponse = json.decodeFromString<UsgsEarthquakeResponse>(bodyText)
                    
                    // Map the raw GeoJSON into our clean UI Model
                    val mappedQuakes = parsedResponse.features.mapNotNull { feature ->
                        val props = feature.properties ?: return@mapNotNull null
                        val geom = feature.geometry ?: return@mapNotNull null
                        
                        // Geometry requires [Longitude, Latitude, Depth]
                        if (geom.coordinates.size < 3) return@mapNotNull null
                        
                        Earthquake(
                            id = feature.id,
                            magnitude = props.magnitude ?: 0.0,
                            place = props.place ?: "Unknown fault zone",
                            timeMillis = props.timeMillis ?: System.currentTimeMillis(),
                            longitude = geom.coordinates[0],
                            latitude = geom.coordinates[1],
                            depthKm = geom.coordinates[2],
                            isTsunamiWarning = props.tsunami == 1,
                            alertColor = props.alert
                        )
                    }

                    // Sort by newest first
                    _earthquakes.value = mappedQuakes.sortedByDescending { it.timeMillis }
                    Log.d(TAG, "Successfully synced ${mappedQuakes.size} earthquakes from USGS.")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch USGS earthquakes: ${e.message}")
        }
    }
}
