package com.example.shiptracker.util

import com.example.shiptracker.data.ShipState
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class CpaResult(
    val targetShip: ShipState,
    val cpaNm: Double,
    val tcpaMinutes: Double,
    val isRisk: Boolean
)

object CpaCalculator {

    fun calculateCpa(selectedShip: ShipState, otherShips: List<ShipState>): CpaResult? {
        if (selectedShip.speed < 0.5f) return null

        var highestRiskResult: CpaResult? = null
        var minCpa = Double.MAX_VALUE

        val selLatRad = Math.toRadians(selectedShip.latitude)
        val selCogRad = Math.toRadians((if (selectedShip.cog != 0f) selectedShip.cog else selectedShip.heading).toDouble())

        val vAx = selectedShip.speed * sin(selCogRad)
        val vAy = selectedShip.speed * cos(selCogRad)

        otherShips.forEach { other ->
            if (other.mmsi != selectedShip.mmsi && other.mmsi > 0L && other.speed > 0.5f) {
                val othCogRad = Math.toRadians((if (other.cog != 0f) other.cog else other.heading).toDouble())
                val vBx = other.speed * sin(othCogRad)
                val vBy = other.speed * cos(othCogRad)

                val vRelX = vAx - vBx
                val vRelY = vAy - vBy
                val vRelSq = vRelX * vRelX + vRelY * vRelY

                if (vRelSq > 0.01) {
                    val dLatNm = (other.latitude - selectedShip.latitude) * 60.0
                    val dLonNm = (other.longitude - selectedShip.longitude) * 60.0 * cos(selLatRad)

                    val tcpaHours = (dLonNm * vRelX + dLatNm * vRelY) / vRelSq

                    if (tcpaHours in 0.0..2.0) { // Look ahead up to 2 hours
                        val xCpa = dLonNm - vRelX * tcpaHours
                        val yCpa = dLatNm - vRelY * tcpaHours
                        val cpaNm = sqrt(xCpa * xCpa + yCpa * yCpa)

                        if (cpaNm < minCpa) {
                            minCpa = cpaNm
                            highestRiskResult = CpaResult(
                                targetShip = other,
                                cpaNm = cpaNm,
                                tcpaMinutes = tcpaHours * 60.0,
                                isRisk = cpaNm < 1.0
                            )
                        }
                    }
                }
            }
        }

        return highestRiskResult
    }
}
