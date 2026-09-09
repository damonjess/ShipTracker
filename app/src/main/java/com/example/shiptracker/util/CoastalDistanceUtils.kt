package com.example.shiptracker.util

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class SatelliteTelemetryInfo(
    val isSatelliteAis: Boolean,
    val distanceFromShoreNm: Double,
    val trackingSource: String,
    val constellation: String,
    val signalQuality: String,
    val oceanZone: String,
    val terrestrialCutoffExceeded: Boolean
)

object CoastalDistanceUtils {

    // Major coastal land reference points (latitude, longitude)
    private val COASTAL_NODES = listOf(
        // UK & Western Europe
        Pair(51.12, 1.31),   // Dover UK
        Pair(50.10, -5.53),  // Penzance UK
        Pair(53.74, -0.33),  // Hull UK
        Pair(51.95, 4.14),   // Rotterdam NL
        Pair(53.55, 9.99),   // Hamburg DE
        Pair(48.39, -4.48),  // Brest France
        Pair(43.36, -8.41),  // A Coruna Spain
        Pair(38.72, -9.14),  // Lisbon Portugal

        // Mediterranean
        Pair(36.14, -5.35),  // Gibraltar
        Pair(43.29, 5.37),   // Marseille France
        Pair(40.85, 14.26),  // Naples Italy
        Pair(37.98, 23.72),  // Athens Greece

        // US East Coast & Gulf
        Pair(40.71, -74.00), // New York
        Pair(36.85, -75.97), // Virginia Beach
        Pair(25.76, -80.19), // Miami
        Pair(29.95, -90.07), // New Orleans
        Pair(29.30, -94.79), // Galveston

        // US West Coast
        Pair(33.74, -118.27), // Los Angeles / Long Beach
        Pair(37.77, -122.41), // San Francisco
        Pair(47.60, -122.33), // Seattle

        // Asia
        Pair(31.23, 121.47), // Shanghai China
        Pair(22.31, 114.16), // Hong Kong
        Pair(1.35, 103.82),  // Singapore
        Pair(35.67, 139.65), // Tokyo Japan
        Pair(18.97, 72.82),  // Mumbai India

        // Middle East & Red Sea
        Pair(25.20, 55.27),  // Dubai UAE
        Pair(29.93, 32.55),  // Suez Egypt

        // South America
        Pair(-22.90, -43.17), // Rio de Janeiro Brazil
        Pair(-33.04, -71.62), // Valparaiso Chile

        // Africa
        Pair(-33.92, 18.42),  // Cape Town South Africa
        Pair(4.05, 9.70),     // Douala Cameroon

        // Oceania
        Pair(-33.86, 151.20), // Sydney Australia
        Pair(-36.84, 174.76)  // Auckland NZ
    )

    // Standard cutoff for Terrestrial AIS station reception (15-20 Nautical Miles)
    const val TERRESTRIAL_AIS_LIMIT_NM = 18.0

    /**
     * Calculates distance in Nautical Miles using Haversine formula
     */
    fun calculateDistanceNm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val rEarthKm = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        val distanceKm = rEarthKm * c
        return distanceKm * 0.539957 // 1 km = 0.539957 nautical miles
    }

    /**
     * Estimates distance in Nautical Miles from the nearest major coastline
     */
    fun getDistanceFromNearestCoastNm(lat: Double, lng: Double): Double {
        var minDistance = Double.MAX_VALUE
        for ((cLat, cLng) in COASTAL_NODES) {
            val dist = calculateDistanceNm(lat, lng, cLat, cLng)
            if (dist < minDistance) {
                minDistance = dist
            }
        }
        return minDistance
    }

    /**
     * Determines Ocean Zone name based on coordinates
     */
    fun getOceanZoneName(lat: Double, lng: Double, distNm: Double): String {
        if (distNm <= TERRESTRIAL_AIS_LIMIT_NM) {
            return "Coastal Waters (Terrestrial AIS Range)"
        }
        return when {
            lat in 0.0..65.0 && lng in -80.0..0.0 -> "North Atlantic Ocean Basin"
            lat in -60.0..0.0 && lng in -70.0..20.0 -> "South Atlantic Deep Ocean"
            lat in 0.0..65.0 && lng in -180.0..-100.0 -> "North Pacific High Seas"
            lat in 0.0..65.0 && lng in 120.0..180.0 -> "West Pacific Ocean Basin"
            lat in -60.0..0.0 && lng in -180.0..-70.0 -> "South Pacific Ocean Basin"
            lat in -60.0..30.0 && lng in 20.0..110.0 -> "Indian Ocean Basin"
            lat in 50.0..75.0 && lng in -10.0..30.0 -> "North Sea / Norwegian Sea"
            lat in 10.0..30.0 && lng in -98.0..-80.0 -> "Gulf of Mexico Deep Waters"
            lat in 10.0..25.0 && lng in 35.0..45.0 -> "Red Sea Deep Channel"
            else -> "Deep-Sea International Waters"
        }
    }

    /**
     * Determines satellite constellation assigned to the message based on MMSI / region seed
     */
    fun getConstellationName(mmsi: Long, lat: Double): String {
        val constellations = listOf(
            "Spire Global S-AIS",
            "Orbcomm S-AIS Constellation",
            "exactEarth LEO Satellite",
            "Iridium NEXT S-AIS Network"
        )
        val index = (mmsi.hashCode() + lat.toInt()).let { if (it < 0) -it else it } % constellations.size
        return constellations[index]
    }

    /**
     * Generates complete Satellite Telemetry analysis for a vessel
     */
    fun getSatelliteTelemetry(
        lat: Double,
        lng: Double,
        mmsi: Long,
        isSatelliteModeEnabled: Boolean = true
    ): SatelliteTelemetryInfo {
        val distNm = getDistanceFromNearestCoastNm(lat, lng)
        val beyondTerrestrial = distNm > TERRESTRIAL_AIS_LIMIT_NM
        val isSat = beyondTerrestrial || isSatelliteModeEnabled

        val source = if (beyondTerrestrial) {
            "Satellite AIS (S-AIS)"
        } else if (isSatelliteModeEnabled) {
            "Satellite + Terrestrial AIS"
        } else {
            "Terrestrial Coastal AIS"
        }

        val constellation = if (isSat) getConstellationName(mmsi, lat) else "Terrestrial Coastal Stations"

        // Calculate deterministic quality % based on MMSI & distance
        val qualityBase = 90 + ((mmsi % 10).toInt())
        val qualityStr = if (isSat) "$qualityBase% (LEO Satellites in View)" else "99% (Direct Line-of-Sight)"

        return SatelliteTelemetryInfo(
            isSatelliteAis = isSat,
            distanceFromShoreNm = distNm,
            trackingSource = source,
            constellation = constellation,
            signalQuality = qualityStr,
            oceanZone = getOceanZoneName(lat, lng, distNm),
            terrestrialCutoffExceeded = beyondTerrestrial
        )
    }
}
