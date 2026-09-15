package com.example.shiptracker.ai

import com.example.shiptracker.data.ShipState
import com.example.shiptracker.util.VoyageCalculator
import kotlin.math.abs

data class SpoofBusterAlert(
    val ship: ShipState,
    val detectedClass: String,
    val confidence: Float,
    val warningMessage: String
)

object SpoofBusterEngine {

    /**
     * Checks detected bounding boxes from the ONNX camera feed against AIS vessels in the database
     * using phone compass heading and camera Field of View (FOV).
     *
     * @param boundingBoxes List of YOLO detected bounding boxes with confidence and vesselClass.
     * @param compassHeading Current phone compass heading in degrees [0, 360).
     * @param cameraFov Horizontal field of view of the camera in degrees (default e.g., 60.0f).
     * @param userLat Current user latitude.
     * @param userLon Current user longitude.
     * @param activeShips List of known ships from the Room database / repository.
     */
    fun evaluateAnomalies(
        boundingBoxes: List<BoundingBox>,
        compassHeading: Float,
        cameraFov: Float = 60.0f,
        userLat: Double,
        userLon: Double,
        activeShips: List<ShipState>
    ): List<SpoofBusterAlert> {
        val alerts = mutableListOf<SpoofBusterAlert>()
        if (boundingBoxes.isEmpty() || activeShips.isEmpty()) return alerts

        val halfFov = cameraFov / 2.0f

        for (box in boundingBoxes) {
            for (ship in activeShips) {
                val bearing = VoyageCalculator.initialBearing(userLat, userLon, ship.latitude, ship.longitude).toFloat()
                
                // Calculate angular difference accounting for 360 wrap-around
                val diff = (bearing - compassHeading + 540) % 360 - 180
                if (abs(diff) <= halfFov) {
                    val aisTypeCategory = getCategoryName(ship.shipType)
                    
                    if (isMismatched(box.vesselClass, aisTypeCategory)) {
                        alerts.add(
                            SpoofBusterAlert(
                                ship = ship,
                                detectedClass = box.vesselClass,
                                confidence = box.confidence,
                                warningMessage = "🚨 COVERT VESSEL ANOMALY: Visual signature '${box.vesselClass}' (${(box.confidence * 100).toInt()}%) contradicts AIS profile '${aisTypeCategory}' (MMSI: ${ship.mmsi})!"
                            )
                        )
                    }
                }
            }
        }

        return alerts
    }

    private fun getCategoryName(shipType: Int): String {
        return when (shipType) {
            in 30..39 -> "Fishing"
            in 40..49 -> "High Speed Craft"
            in 50..59 -> "Special Craft"
            in 60..69 -> "Passenger"
            in 70..79 -> "Cargo"
            in 80..89 -> "Tanker"
            in 90..99 -> "Other"
            else -> "Yacht / Pleasure Craft"
        }
    }

    private fun isMismatched(visualClass: String, aisCategory: String): Boolean {
        val v = visualClass.lowercase()
        val a = aisCategory.lowercase()
        if (v.contains("cargo") && !a.contains("cargo")) return true
        if (v.contains("tanker") && !a.contains("tanker")) return true
        if (v.contains("yacht") && (a.contains("cargo") || a.contains("tanker"))) return true
        return false
    }
}
