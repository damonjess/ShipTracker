package com.example.shiptracker.service

import android.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.shiptracker.data.AppDatabase
import com.example.shiptracker.data.ShipRepository

class ShipTrackingService : Service() {

    private val channelId = "ShipTrackerChannel"
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val dao = AppDatabase.getDatabase(applicationContext).vesselDao()
        ShipRepository.initialize(dao)

        // 🚨 HONOR OPTIMIZATION: Acquire a WakeLock so MagicOS doesn't freeze the WebSocket
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "ShipTracker::WebSocketWakeLock"
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Live Tracking Active")
            .setContentText("Receiving AIS telemetry...")
            .setSmallIcon(R.drawable.ic_dialog_map)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(1, notification)
        }

        // Engage the lock and start tracking
        wakeLock?.acquire(12 * 60 * 60 * 1000L) // 12-hour safety timeout
        ShipRepository.startTracking()

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        ShipRepository.stopTracking()

        // Release the CPU when the service dies
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
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
