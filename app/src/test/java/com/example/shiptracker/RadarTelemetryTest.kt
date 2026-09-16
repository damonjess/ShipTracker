package com.example.shiptracker

import com.example.shiptracker.ui.calculateRadarTelemetry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarTelemetryTest {

    @Test
    fun testSameLocationHasZeroDistance() {
        val centerLat = 51.92
        val centerLon = 4.47
        val (distance, _) = calculateRadarTelemetry(centerLat, centerLon, centerLat, centerLon)
        assertEquals(0f, distance, 0.01f)
    }

    @Test
    fun testBearingNorth() {
        val centerLat = 51.0
        val centerLon = 4.0
        val targetLat = 52.0
        val targetLon = 4.0
        val (distance, bearing) = calculateRadarTelemetry(centerLat, centerLon, targetLat, targetLon)
        assertTrue("Distance should be ~111km (111000m)", distance in 110000f..112000f)
        assertEquals(0f, bearing, 1.0f)
    }

    @Test
    fun testBearingEast() {
        val centerLat = 51.0
        val centerLon = 4.0
        val targetLat = 51.0
        val targetLon = 5.0
        val (distance, bearing) = calculateRadarTelemetry(centerLat, centerLon, targetLat, targetLon)
        assertTrue("Distance should be positive", distance > 0)
        assertEquals(90f, bearing, 2.0f)
    }

    @Test
    fun testBearingSouth() {
        val centerLat = 51.0
        val centerLon = 4.0
        val targetLat = 50.0
        val targetLon = 4.0
        val (distance, bearing) = calculateRadarTelemetry(centerLat, centerLon, targetLat, targetLon)
        assertTrue("Distance should be ~111km", distance in 110000f..112000f)
        assertEquals(180f, bearing, 1.0f)
    }

    @Test
    fun testBearingWest() {
        val centerLat = 51.0
        val centerLon = 4.0
        val targetLat = 51.0
        val targetLon = 3.0
        val (distance, bearing) = calculateRadarTelemetry(centerLat, centerLon, targetLat, targetLon)
        assertTrue("Distance should be positive", distance > 0)
        assertEquals(270f, bearing, 2.0f)
    }
}
