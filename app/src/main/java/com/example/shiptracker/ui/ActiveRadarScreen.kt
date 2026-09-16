package com.example.shiptracker.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.example.shiptracker.data.ShipState
import kotlin.math.*

data class Ship(
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val mmsi: Long = 0L,
    val name: String = "",
)

fun ShipState.toShip(): Ship = Ship(
    lat = latitude,
    lon = longitude,
    mmsi = mmsi,
    name = name
)

// Returns a Pair: first = Distance (meters), second = Bearing (degrees)
fun calculateRadarTelemetry(
    centerLat: Double, centerLon: Double,
    targetLat: Double, targetLon: Double
): Pair<Float, Float> {
    val earthRadius = 6371000.0 // meters
    val dLat = Math.toRadians(targetLat - centerLat)
    val dLon = Math.toRadians(targetLon - centerLon)
    val originLat = Math.toRadians(centerLat)
    val destLat = Math.toRadians(targetLat)

    // Distance Math (Haversine)
    val a = sin(dLat / 2).pow(2) + (sin(dLon / 2).pow(2) * cos(originLat) * cos(destLat))
    val c = 2 * asin(sqrt(a))
    val distance = (earthRadius * c).toFloat()

    // Bearing Math
    val y = sin(dLon) * cos(destLat)
    val x = cos(originLat) * sin(destLat) - sin(originLat) * cos(destLat) * cos(dLon)
    var bearing = Math.toDegrees(atan2(y, x)).toFloat()
    bearing = (bearing + 360) % 360 // Normalize to 0-360

    return Pair(distance, bearing)
}

@Composable
fun ActiveRadarScreen(
    ships: List<Ship> = emptyList(),
    myLat: Double = 53.58,
    myLon: Double = -0.65,
    // 1. MASSIVE RANGE INCREASE: Boosted to 300 KM to scan coast-to-coast
    maxRangeMeters: Float = 300_000f
) {
    val infiniteTransition = rememberInfiniteTransition()
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        // Slowed down slightly to 4 seconds for a heavier, cinematic radar feel
        animationSpec = infiniteRepeatable(animation = tween(4000, easing = LinearEasing))
    )

    // Calculate the math ONCE, not 120 times a second!
    val radarTargets = remember(ships, myLat, myLon, maxRangeMeters) {
        ships.mapNotNull { ship ->
            val (distance, bearing) = calculateRadarTelemetry(myLat, myLon, ship.lat, ship.lon)

            // Only keep ships within max range to save memory
            if (distance <= maxRangeMeters) {
                Pair(distance, bearing)
            } else {
                null
            }
        }
    }

    // Required to draw text directly onto a Canvas
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = Modifier.fillMaxSize().background(Color(0xFF00140A))) {
        val center = Offset(size.width / 2, size.height / 2)
        val maxRadius = (minOf(size.width, size.height) / 2) * 0.9f
        val ringColor = Color(0xFF00FF41).copy(alpha = 0.3f)

        // --- LAYER 1: RINGS & CROSSHAIRS ---
        drawCircle(color = ringColor, radius = maxRadius, center = center, style = Stroke(2f))
        drawCircle(color = ringColor, radius = maxRadius * 0.66f, center = center, style = Stroke(2f))
        drawCircle(color = ringColor, radius = maxRadius * 0.33f, center = center, style = Stroke(2f))
        drawLine(color = ringColor, start = Offset(center.x, 0f), end = Offset(center.x, size.height))
        drawLine(color = ringColor, start = Offset(0f, center.y), end = Offset(size.width, center.y))

        // --- LAYER 2: TEXT RANGE LABELS ---
        val labelStyle = TextStyle(color = Color(0xFF00FF41).copy(alpha = 0.8f), fontSize = 10.sp)
        val maxRangeKm = (maxRangeMeters / 1000).toInt()

        // Draw text slightly offset from the vertical crosshair
        val textOffsetX = center.x + 12f
        drawText(textMeasurer, "${maxRangeKm / 3} KM", Offset(textOffsetX, center.y - (maxRadius * 0.33f)), labelStyle)
        drawText(textMeasurer, "${(maxRangeKm * 2) / 3} KM", Offset(textOffsetX, center.y - (maxRadius * 0.66f)), labelStyle)
        drawText(textMeasurer, "$maxRangeKm KM", Offset(textOffsetX, center.y - maxRadius), labelStyle)

        // --- LAYER 3: SHIP BLIPS (OPTIMIZED) ---
        radarTargets.forEach { (distance, bearing) ->
            val pixelDistance = (distance / maxRangeMeters) * maxRadius
            val canvasAngleDegrees = bearing - 90f
            val canvasAngleRadians = Math.toRadians(canvasAngleDegrees.toDouble())

            val normalizedSweep = (sweepAngle % 360 + 360) % 360
            val normalizedShip = (bearing % 360 + 360) % 360
            val angleDiff = (normalizedSweep - normalizedShip + 360) % 360
            val decayDegrees = 90f

            if (angleDiff <= decayDegrees) {
                val blipAlpha = 1f - (angleDiff / decayDegrees)
                val blipX = center.x + (pixelDistance * cos(canvasAngleRadians)).toFloat()
                val blipY = center.y + (pixelDistance * sin(canvasAngleRadians)).toFloat()

                drawCircle(
                    color = Color(0xFF00FF41).copy(alpha = blipAlpha),
                    radius = 8f,
                    center = Offset(blipX, blipY)
                )
            }
        }

        // --- LAYER 4: THE PHOSPHOR WAKE & SWEEP ---
        // We rotate the entire canvas by the sweep angle, offset by -90 so 0 degrees is UP (North)
        rotate(degrees = sweepAngle - 90f, pivot = center) {

            // The Fading Wake (SweepGradient)
            drawArc(
                brush = Brush.sweepGradient(
                    0.0f to Color.Transparent,
                    0.85f to Color.Transparent, // Keeps the first 85% of the circle totally empty
                    1.0f to Color(0xFF00FF41).copy(alpha = 0.6f), // The glowing tail
                    center = center
                ),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = true,
                size = Size(maxRadius * 2, maxRadius * 2),
                topLeft = Offset(center.x - maxRadius, center.y - maxRadius)
            )

            // The Hard Leading Edge Line
            drawLine(
                color = Color(0xFF00FF41),
                start = center,
                // Because the canvas is rotated, we just draw straight to the "right" (0 degrees)
                end = Offset(center.x + maxRadius, center.y),
                strokeWidth = 6f
            )
        }
    }
}

@Composable
@JvmName("ActiveRadarScreenShipState")
fun ActiveRadarScreen(
    ships: List<ShipState>,
    myLat: Double = 53.58,
    myLon: Double = -0.65,
    maxRangeMeters: Float = 300_000f
) {
    val domainShips = remember(ships) { ships.map { it.toShip() } }
    ActiveRadarScreen(
        ships = domainShips,
        myLat = myLat,
        myLon = myLon,
        maxRangeMeters = maxRangeMeters
    )
}

@Preview
@Composable
fun ActiveRadarScreenPreview() {
    val sampleShips = listOf(
        Ship(lat = 51.95, lon = 4.50, name = "Ship A"),
        Ship(lat = 51.90, lon = 4.40, name = "Ship B"),
        Ship(lat = 52.00, lon = 4.47, name = "Ship C")
    )
    ActiveRadarScreen(
        ships = sampleShips,
        myLat = 51.92,
        myLon = 4.47
    )
}

@Composable
fun RadarScreen() {
    ActiveRadarScreen()
}
