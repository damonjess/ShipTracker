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
import androidx.core.app.NotificationCompat
import com.example.shiptracker.data.AppDatabase
import com.example.shiptracker.data.EarthquakeRepository
import com.example.shiptracker.data.ShipRepository

class ShipTrackingService : Service() {

    private val channelId = "ShipTrackerChannel"
    private var wakeLock: PowerManager.WakeLock? = null
    private var isShuttingDown = false

    companion object {
        const val ACTION_STOP_SERVICE = "STOP_TRACKING_SERVICE"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val dao = AppDatabase.getDatabase(applicationContext).vesselDao()
        ShipRepository.initialize(dao)
        EarthquakeRepository.startSyncing()

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "ShipTracker::WebSocketWakeLock"
        )
        // Reference-counted wake locks can cause issues if acquire() is called
        // multiple times (e.g. if onStartCommand fires again while the service
        // is already running). Disable reference counting so release() fully
        // releases regardless of how many times acquire() was called.
        wakeLock?.setReferenceCounted(false)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Guard against system-initiated restarts (null intent).
        // START_NOT_STICKY should prevent this, but some OEM ROMs (e.g. Honor MagicOS)
        // may still try to restart a killed foreground service.
        if (intent == null) {
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        // INTERCEPT THE KILL SWITCH
        if (intent.action == ACTION_STOP_SERVICE) {
            shutDownEverything()
            return START_NOT_STICKY
        }

        // BUILD THE STOP BUTTON FOR THE NOTIFICATION
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
            .addAction(R.drawable.ic_menu_close_clear_cancel, "Stop Tracking", pendingStopIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(1, notification)
        }

        if (wakeLock?.isHeld != true) {
            wakeLock?.acquire(12 * 60 * 60 * 1000L)
        }
        ShipRepository.startTracking()

        return START_NOT_STICKY
    }

    // THE GRACEFUL SHUTDOWN SEQUENCE (idempotent)
    private fun shutDownEverything() {
        if (isShuttingDown) return
        isShuttingDown = true
        cleanupResources()
        stopSelf()
        // NOTE: Process.killProcess(Process.myPid()) was removed.
        // It was killing the process before the system could process stopForeground()
        // and stopSelf(), causing MagicOS to treat the foreground service as having
        // crashed and automatically restarting it in the background.
    }

    // Shared cleanup used by both shutDownEverything() and onDestroy().
    // Safe to call multiple times due to null/held checks.
    private fun cleanupResources() {
        ShipRepository.stopTracking()
        EarthquakeRepository.stopSyncing()

        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        shutDownEverything()
    }

    override fun onDestroy() {
        // Release any remaining resources if we haven't already shut down.
        // This covers the case where the system destroys the service without
        // onTaskRemoved being called first (e.g. android:stopWithTask="true").
        cleanupResources()
        super.onDestroy()
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
