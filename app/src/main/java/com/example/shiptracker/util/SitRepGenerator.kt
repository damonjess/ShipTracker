package com.example.shiptracker.util

import com.example.shiptracker.data.MarineWeather
import com.example.shiptracker.data.ShipState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object SitRepGenerator {

    fun generateReport(
        ship: ShipState,
        weather: MarineWeather?,
        departedLocation: String?,
        calculatedEta: String?,
        cpaRisk: CpaResult?
    ): String {
        val sb = StringBuilder()

        // 1. HEADER (Military Date/Time Format)
        val sdf = SimpleDateFormat("dd MMM yyyy HH:mm'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        val timeString = sdf.format(Date())
        
        sb.append("TACTICAL SITREP • $timeString\n")
        sb.append("TARGET: ${ship.name.uppercase()} (MMSI: ${ship.mmsi})\n")
        sb.append("-----------------------------------\n\n")

        // 2. NAVIGATIONAL STATUS
        sb.append("NAV STATUS: ")
        if (ship.speed < 0.5f) {
            sb.append("Vessel is currently stationary/moored at ${"%.4f".format(ship.latitude)}, ${"%.4f".format(ship.longitude)}. ")
        } else {
            sb.append("Underway at ${"%.1f".format(ship.speed)} knots on heading ${ship.heading.toInt()}°. ")
        }

        // 3. VOYAGE & ROUTING
        if (!departedLocation.isNullOrBlank() || (!calculatedEta.isNullOrBlank() && ship.destination.isNotBlank())) {
            sb.append("\n\nROUTING: ")
            if (!departedLocation.isNullOrBlank()) {
                sb.append("Track originated near $departedLocation. ")
            }
            if (!calculatedEta.isNullOrBlank() && ship.destination.isNotBlank()) {
                sb.append("Inbound to ${ship.destination} with intercept in $calculatedEta. ")
            }
        }

        // 4. ENVIRONMENTAL / WEATHER IMPACT
        if (weather != null) {
            sb.append("\n\nENVIRONMENTAL: ")
            if (weather.windSpeedKnots > 30f) {
                sb.append("Target is battling severe ${weather.windSpeedKnots.toInt()}-knot gale conditions and ${"%.1f".format(weather.waveHeightMeters)}-meter swells. Speed may be impacted. ")
            } else if (weather.windSpeedKnots > 15f) {
                sb.append("Navigating through moderate seas (${weather.windSpeedKnots.toInt()} kn winds, ${"%.1f".format(weather.waveHeightMeters)}m waves). ")
            } else {
                sb.append("Favorable conditions. Calm seas with ${weather.windSpeedKnots.toInt()} kn winds. ")
            }
        }

        // 5. TACTICAL & ANOMALIES (The Dark Fleet / CPA integration)
        sb.append("\n\nTACTICAL ASSESSMENT: ")
        var hasTacticalAlert = false

        if (ship.isAnomalyFlagged) {
            sb.append("\n[!] ANOMALY: ${ship.anomalyReason} ")
            hasTacticalAlert = true
        }

        if (ship.isGhost) {
            sb.append("\n[!] SENSOR LOSS: Target is beyond physical AIS reception. Current location is a physics-based dead-reckoning projection. ")
            hasTacticalAlert = true
        }

        if (cpaRisk != null && cpaRisk.isRisk) {
            sb.append("\n[!] COLLISION ALERT: Close quarters situation developing with ${cpaRisk.targetShip.name}. CPA is ${"%.2f".format(cpaRisk.cpaNm)} NM in ${"%.0f".format(cpaRisk.tcpaMinutes)} minutes. ")
            hasTacticalAlert = true
        }

        if (!hasTacticalAlert) {
            sb.append("Signals nominal. No collision risks or tracking anomalies detected in the immediate sector.")
        }

        return sb.toString()
    }
}
