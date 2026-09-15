package com.example.shiptracker.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// 1. The clean UI model we will use in Compose
data class Earthquake(
    val id: String,
    val magnitude: Double,
    val place: String,
    val timeMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val depthKm: Double,
    val isTsunamiWarning: Boolean,
    val alertColor: String? // Can be "green", "yellow", "orange", or "red"
)

// 2. The raw USGS GeoJSON API Models
@Serializable
data class UsgsEarthquakeResponse(
    @SerialName("features") val features: List<EarthquakeFeature> = emptyList()
)

@Serializable
data class EarthquakeFeature(
    @SerialName("id") val id: String = "",
    @SerialName("properties") val properties: EarthquakeProperties? = null,
    @SerialName("geometry") val geometry: EarthquakeGeometry? = null
)

@Serializable
data class EarthquakeProperties(
    @SerialName("mag") val magnitude: Double? = null,
    @SerialName("place") val place: String? = null,
    @SerialName("time") val timeMillis: Long? = null,
    @SerialName("tsunami") val tsunami: Int? = null, // 1 = Warning, 0 = Clear
    @SerialName("alert") val alert: String? = null
)

@Serializable
data class EarthquakeGeometry(
    // Coordinates are always formatted as: [longitude, latitude, depth]
    @SerialName("coordinates") val coordinates: List<Double> = emptyList()
)
