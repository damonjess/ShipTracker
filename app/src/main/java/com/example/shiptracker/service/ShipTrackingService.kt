package com.example.shiptracker.service

import android.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.Process
import androidx.core.app.NotificationCompat
import com.example.shiptracker.data.AppDatabase
import com.example.shiptracker.data.ShipRepository

class ShipTrackingService : Service() {

    private val channelId = "ShipTrackerChannel"
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val ACTION_STOP_SERVICE = "STOP_TRACKING_SERVICE"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val dao = AppDatabase.getDatabase(applicationContext).vesselDao()
        ShipRepository.initialize(dao)

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "ShipTracker::WebSocketWakeLock"
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 🚨 1. INTERCEPT THE KILL SWITCH
        if (intent?.action == ACTION_STOP_SERVICE) {
            shutDownEverything()
            return START_NOT_STICKY
        }

        // 🚨 2. BUILD THE STOP BUTTON FOR THE NOTIFICATION
        val stopIntent = Intent(this, ShipTrackingService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val pendingStopIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Live Tracking Active")
            .setContentText("Receiving AIS telemetry...")
            .setSmallIcon(R.drawable.ic_dialog_map)
            .setOngoing(true)
            .addAction(R.drawable.ic_menu_close_clear_cancel, "Stop Tracking", pendingStopIntent) // Adds the button!
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(1, notification)
        }

        wakeLock?.acquire(12 * 60 * 60 * 1000L)
        ShipRepository.startTracking()

        return START_NOT_STICKY
    }

    // 🚨 3. THE GRACEFUL SHUTDOWN SEQUENCE
    private fun shutDownEverything() {
        ShipRepository.stopTracking()

        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf() // Tells Android we are done. MagicOS will not restart it!

        // Optional: Force the Linux process to die completely so RAM clears instantly
        Process.killProcess(Process.myPid())
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        shutDownEverything()
    }

    override fun onDestroy() {
        super.onDestroy()
        shutDownEverything()
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
