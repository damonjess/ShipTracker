package com.example.shiptracker.util

import com.example.shiptracker.data.ShipState
import com.example.shiptracker.data.VesselTrackPoint

object AnomalyDetector {

    data class AnomalyResult(
        val isFlagged: Boolean,
        val reason: String
    )

    fun analyzeVesselJump(
        currentShip: ShipState,
        lastKnownPoint: VesselTrackPoint?
    ): AnomalyResult {
        if (lastKnownPoint == null) {
            return AnomalyResult(false, "")
        }

        val currentTime = System.currentTimeMillis()
        val timeElapsedHours = (currentTime - lastKnownPoint.timestamp) / 3_600_000.0

        if (timeElapsedHours <= 0.001) {
            return AnomalyResult(false, "")
        }

        // 1. Calculate distance traveled between last recorded point and new point
        val distanceNm = VoyageCalculator.haversineNm(
            lastKnownPoint.latitude, lastKnownPoint.longitude,
            currentShip.latitude, currentShip.longitude
        )

        val calculatedSpeedKnots = distanceNm / timeElapsedHours

        // 2. Check for Impossible Speed / GPS Spoofing (> 55 knots)
        if (calculatedSpeedKnots > 55.0 && currentShip.speed < 40.0) {
            return AnomalyResult(
                isFlagged = true,
                reason = "🚨 GPS Spoofing Suspected: Teleportation jump calculated at ${calculatedSpeedKnots.toInt()} knots!"
            )
        }

        // 3. Check for "Dark Fleet" Behavior (Went dark for > 4 hours, moved < 2 NM)
        if (timeElapsedHours >= 4.0 && distanceNm < 2.0) {
            return AnomalyResult(
                isFlagged = true,
                reason = "🏴‍☠️ Dark Fleet Activity: Transponder disabled for ${"%.1f".format(timeElapsedHours)}h with near-zero displacement (STS Transfer Suspected)."
            )
        }

        return AnomalyResult(false, "")
    }
}
