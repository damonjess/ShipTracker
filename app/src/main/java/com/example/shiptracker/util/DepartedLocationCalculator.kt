package com.example.shiptracker.util

import android.content.Context
import android.location.Geocoder
import com.example.shiptracker.data.ShipState
import com.example.shiptracker.data.VesselTrackPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object DepartedLocationCalculator {

    /**
     * Finds the departure point coordinates from historical track points and current ship state.
     * Looks for the last point where the vessel was stopped/docked (speed < 0.5 knots).
     * If no stopped point is found, falls back to the earliest recorded track point or current position.
     */
    fun findDepartureCoordinates(
        trackPoints: List<VesselTrackPoint>,
        currentShip: ShipState?
    ): Pair<Double, Double>? {
        if (trackPoints.isEmpty()) {
            return currentShip?.let { Pair(it.latitude, it.longitude) }
        }

        // Points ordered by timestamp ASC
        val sortedPoints = trackPoints.sortedBy { it.timestamp }

        // Find the last stationary point before moving
        var lastStoppedCoords: Pair<Double, Double>? = null

        for (i in 1 until sortedPoints.size) {
            val p1 = sortedPoints[i - 1]
            val p2 = sortedPoints[i]

            val distNm = haversineNm(p1.latitude, p1.longitude, p2.latitude, p2.longitude)
            val timeHours = (p2.timestamp - p1.timestamp) / 3_600_000.0

            val speedKnots = if (timeHours > 0) distNm / timeHours else 0.0

            if (speedKnots < 0.5 || distNm < 0.05) {
                lastStoppedCoords = Pair(p2.latitude, p2.longitude)
            }
        }

        return lastStoppedCoords
            ?: sortedPoints.firstOrNull()?.let { Pair(it.latitude, it.longitude) }
            ?: currentShip?.let { Pair(it.latitude, it.longitude) }
    }

    /**
     * Reverse geocodes coordinates to a human-readable city/port name.
     */
    suspend fun getDepartedLocationName(
        context: Context?,
        lat: Double,
        lng: Double
    ): String = withContext(Dispatchers.IO) {
        if (context == null) {
            return@withContext "Port at ${"%.2f°".format(lat)}, ${"%.2f°".format(lng)}"
        }

        try {
            @Suppress("DEPRECATION")
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lng, 1)

            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                val city = addr.locality ?: addr.subAdminArea ?: addr.subLocality ?: addr.featureName
                val country = addr.countryName

                if (!city.isNullOrBlank() && !country.isNullOrBlank()) {
                    "$city, $country"
                } else if (!city.isNullOrBlank()) {
                    city
                } else if (!country.isNullOrBlank()) {
                    "Port in $country"
                } else {
                    "Port at ${"%.2f°".format(lat)}, ${"%.2f°".format(lng)}"
                }
            } else {
                "Port at ${"%.2f°".format(lat)}, ${"%.2f°".format(lng)}"
            }
        } catch (e: Exception) {
            "Port at ${"%.2f°".format(lat)}, ${"%.2f°".format(lng)}"
        }
    }

    private fun haversineNm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val rNm = 3440.065 // Earth radius in nautical miles
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return rNm * c
    }
}
