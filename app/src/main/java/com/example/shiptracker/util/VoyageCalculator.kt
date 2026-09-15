package com.example.shiptracker.util

import android.content.Context
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class DistanceAndEtaResult(
    val distanceNm: Double,
    val calculatedEtaText: String
)

object VoyageCalculator {

    fun haversineNm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val rNm = 3440.065 // Earth radius in nautical miles
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return rNm * c
    }

    /**
     * Attempts to resolve destination coordinates and compute Distance to Destination + Calculated ETA.
     */
    suspend fun calculateDistanceAndEta(
        context: Context?,
        currentLat: Double,
        currentLng: Double,
        speedKnots: Float,
        destinationName: String
    ): DistanceAndEtaResult? = withContext(Dispatchers.IO) {
        if (destinationName.isBlank() || destinationName == "-" || destinationName == "UNKNOWN") {
            return@withContext null
        }

        val destCoords = resolveDestinationCoordinates(context, destinationName) ?: return@withContext null
        val distanceNm = haversineNm(currentLat, currentLng, destCoords.first, destCoords.second)

        val etaText = if (speedKnots > 0.5f) {
            val totalHours = distanceNm / speedKnots
            val days = (totalHours / 24).toInt()
            val hours = (totalHours % 24).toInt()
            if (days > 0) {
                "${"%.0f".format(distanceNm)} NM (~${days}d ${hours}h)"
            } else {
                "${"%.0f".format(distanceNm)} NM (~${hours}h remaining)"
            }
        } else {
            "${"%.0f".format(distanceNm)} NM (Stationary)"
        }

        DistanceAndEtaResult(distanceNm, etaText)
    }

    private fun resolveDestinationCoordinates(context: Context?, destination: String): Pair<Double, Double>? {
        // Known port fallbacks for instant lookup
        val knownPorts = mapOf(
            "ROTTERDAM" to Pair(51.92, 4.47),
            "HULL" to Pair(53.74, -0.28),
            "GBHUL" to Pair(53.74, -0.28),
            "NLRTM" to Pair(51.92, 4.47),
            "HAMBURG" to Pair(53.55, 9.99),
            "DEHAM" to Pair(53.55, 9.99),
            "SINGAPORE" to Pair(1.29, 103.85),
            "SGSIN" to Pair(1.29, 103.85),
            "ANTWERP" to Pair(51.22, 4.40),
            "BEANR" to Pair(51.22, 4.40),
            "DOVER" to Pair(51.12, 1.31),
            "SHANGHAI" to Pair(31.23, 121.47),
            "CNSHA" to Pair(31.23, 121.47),
            "GRIMSBY" to Pair(53.58, -0.05)
        )

        val upper = destination.uppercase().trim()
        for ((key, coords) in knownPorts) {
            if (upper.contains(key)) return coords
        }

        if (context != null) {
            try {
                @Suppress("DEPRECATION")
                val geocoder = Geocoder(context, Locale.getDefault())
                val addresses = geocoder.getFromLocationName(destination, 1)
                if (!addresses.isNullOrEmpty()) {
                    return Pair(addresses[0].latitude, addresses[0].longitude)
                }
            } catch (e: Exception) {
                // Ignore geocode failure
            }
        }

        return null
    }

    /**
     * Projects a new Lat/Lng based on current position, speed, course, and elapsed time.
     */
    fun predictNextPosition(
        lat: Double, 
        lng: Double, 
        speedKnots: Float, 
        courseDeg: Float, 
        timeElapsedMs: Long
    ): Pair<Double, Double> {
        // Distance = Speed * Time
        val distNm = speedKnots * (timeElapsedMs / 3_600_000.0)
        val rNm = 3440.065 // Earth radius in nautical miles
        
        val lat1 = Math.toRadians(lat)
        val lon1 = Math.toRadians(lng)
        val brng = Math.toRadians(courseDeg.toDouble())

        val lat2 = Math.asin(
            Math.sin(lat1) * Math.cos(distNm / rNm) +
            Math.cos(lat1) * Math.sin(distNm / rNm) * Math.cos(brng)
        )
        
        val lon2 = lon1 + Math.atan2(
            Math.sin(brng) * Math.sin(distNm / rNm) * Math.cos(lat1),
            Math.cos(distNm / rNm) - Math.sin(lat1) * Math.sin(lat2)
        )

        return Pair(Math.toDegrees(lat2), Math.toDegrees(lon2))
    }

    fun initialBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dLambda = Math.toRadians(lon2 - lon1)
        val y = sin(dLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLambda)
        val brng = Math.toDegrees(atan2(y, x))
        return (brng + 360) % 360
    }
}
