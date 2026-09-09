package com.example.shiptracker

import com.example.shiptracker.util.CoastalDistanceUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoastalDistanceUtilsTest {

    @Test
    fun testCoastalPositionIsWithinTerrestrialRange() {
        // Dover UK coastal coordinate
        val lat = 51.12
        val lng = 1.35

        val dist = CoastalDistanceUtils.getDistanceFromNearestCoastNm(lat, lng)
        assertTrue("Dover coastal coordinate should be within 18 NM of shore", dist <= 18.0)

        val telemetry = CoastalDistanceUtils.getSatelliteTelemetry(lat, lng, 235009270L, isSatelliteModeEnabled = true)
        assertFalse("Coastal position should not exceed terrestrial cutoff", telemetry.terrestrialCutoffExceeded)
    }

    @Test
    fun testMidOceanPositionExceedsTerrestrialRangeAndUsesSatelliteAis() {
        // Mid North Atlantic Ocean (lat: 44.5, lng: -32.8)
        val lat = 44.50
        val lng = -32.80

        val dist = CoastalDistanceUtils.getDistanceFromNearestCoastNm(lat, lng)
        assertTrue("Mid-Atlantic position should be far off coast (>100 NM)", dist > 100.0)

        val telemetry = CoastalDistanceUtils.getSatelliteTelemetry(lat, lng, 353136000L, isSatelliteModeEnabled = true)
        assertTrue("Deep ocean vessel should exceed terrestrial range cutoff", telemetry.terrestrialCutoffExceeded)
        assertTrue("Deep ocean vessel should use Satellite AIS", telemetry.isSatelliteAis)
        assertEquals("Satellite AIS (S-AIS)", telemetry.trackingSource)
        assertEquals("North Atlantic Ocean Basin", telemetry.oceanZone)
    }

    @Test
    fun testHaversineDistanceCalculation() {
        // Distance between Dover UK (51.12, 1.31) and Rotterdam NL (51.95, 4.14)
        val distNm = CoastalDistanceUtils.calculateDistanceNm(51.12, 1.31, 51.95, 4.14)
        // Approx 115-125 NM
        assertTrue("Distance from Dover to Rotterdam should be roughly ~118 NM", distNm in 100.0..140.0)
    }
}
