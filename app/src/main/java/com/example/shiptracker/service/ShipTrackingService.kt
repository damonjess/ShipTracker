package com.example.shiptracker.service

import android.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.shiptracker.data.AppDatabase
import com.example.shiptracker.data.ShipRepository

class ShipTrackingService : Service() {

    private val channelId = "ShipTrackerChannel"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val dao = AppDatabase.getDatabase(applicationContext).vesselDao()
        ShipRepository.initialize(dao)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 1. Build the persistent notification
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Live Tracking Active")
            .setContentText("Receiving AIS telemetry...")
            .setSmallIcon(R.drawable.ic_dialog_map)
            .setOngoing(true)
            .build()

        // 2. Start Foreground (Android 14+ requires the type)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(1, notification)
        }

        // 3. Open the WebSocket
        ShipRepository.startTracking()

        // 4. START_STICKY tells the OS to recreate this service if it gets killed for memory
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        ShipRepository.stopTracking()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            channelId,
            "Ship Tracking Status",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }
}
