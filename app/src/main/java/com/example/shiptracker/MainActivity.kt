package com.example.shiptracker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import com.example.shiptracker.service.ShipTrackingService
import com.example.shiptracker.ui.ShipTrackerMainScreen
import com.example.shiptracker.ui.theme.ShipTrackerTheme
import org.osmdroid.config.Configuration

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure osmdroid userAgent and configuration before loading UI
        Configuration.getInstance().load(applicationContext, getSharedPreferences("osmdroid", MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = "DamonShipTracker/1.0"

        enableEdgeToEdge()

        // Start the foreground tracking service
        val serviceIntent = Intent(this, ShipTrackingService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)

        setContent {
            ShipTrackerTheme {
                ShipTrackerMainScreen()
            }
        }
    }
}
