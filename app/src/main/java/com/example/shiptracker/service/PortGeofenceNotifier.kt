package com.example.shiptracker.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.shiptracker.R
import com.example.shiptracker.data.ShipState
import com.example.shiptracker.util.VoyageCalculator
import java.util.concurrent.ConcurrentHashMap

data class PortZone(
    val id: String,
    val name: String,
    val centerLat: Double,
    val centerLng: Double,
    val radiusNm: Double = 5.0
)

object PortGeofenceNotifier {

    private const val CHANNEL_ID = "vessel_port_alerts"
    private const val CHANNEL_NAME = "Port Arrival & Departure Alerts"

    val majorPorts = listOf(
        PortZone("RTM", "Port of Rotterdam", 51.92, 4.47, 6.0),
        PortZone("HUL", "Port of Hull", 53.74, -0.28, 4.0),
        PortZone("HAM", "Port of Hamburg", 53.55, 9.99, 5.0),
        PortZone("SIN", "Port of Singapore", 1.29, 103.85, 8.0),
        PortZone("ANR", "Port of Antwerp", 51.22, 4.40, 5.0),
        PortZone("DOV", "Port of Dover", 51.12, 1.31, 3.0),
        PortZone("SHA", "Port of Shanghai", 31.23, 121.47, 10.0),
        PortZone("GRM", "Port of Grimsby", 53.58, -0.05, 3.0)
    )

    // Tracks MMSI -> current inside port ID
    private val vesselPortState = ConcurrentHashMap<Long, String>()

    fun checkGeofencesAndNotify(context: Context, ships: List<ShipState>, favoriteMmsis: Set<Long>) {
        if (ships.isEmpty() || favoriteMmsis.isEmpty()) return

        createNotificationChannel(context)

        ships.forEach { ship ->
            // Send alerts for favorited vessels
            if (favoriteMmsis.contains(ship.mmsi)) {
                val currentPort = majorPorts.firstOrNull { port ->
                    val dist = VoyageCalculator.haversineNm(ship.latitude, ship.longitude, port.centerLat, port.centerLng)
                    dist <= port.radiusNm
                }

                val previousPortId = vesselPortState[ship.mmsi]
                val currentPortId = currentPort?.id

                if (currentPortId != previousPortId) {
                    val shipName = ship.name.ifBlank { "Vessel ${ship.mmsi}" }

                    if (currentPortId != null && previousPortId == null) {
                        // Arrival notification
                        vesselPortState[ship.mmsi] = currentPortId
                        sendNotification(
                            context,
                            notificationId = ship.mmsi.toInt() + 1000,
                            title = "⚓ Port Arrival Alert",
                            message = "$shipName has arrived at ${currentPort.name}."
                        )
                    } else if (currentPortId == null && previousPortId != null) {
                        // Departure notification
                        vesselPortState.remove(ship.mmsi)
                        val prevPortName = majorPorts.firstOrNull { it.id == previousPortId }?.name ?: "port"
                        sendNotification(
                            context,
                            notificationId = ship.mmsi.toInt() + 2000,
                            title = "🚢 Port Departure Alert",
                            message = "$shipName has departed from $prevPortName."
                        )
                    }
                }
            }
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifies when tracked vessels enter or leave major port boundaries."
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun sendNotification(context: Context, notificationId: Int, title: String, message: String) {
        try {
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(notificationId, builder.build())
        } catch (e: Exception) {
            // Suppress notification permission errors if user declined
        }
    }
}
