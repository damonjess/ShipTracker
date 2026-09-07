package com.example.shiptracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.shiptracker.service.ShipTrackingService
import com.example.shiptracker.ui.ShipTrackerMainScreen
import com.example.shiptracker.ui.theme.ShipTrackerTheme

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        startTrackingService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startTrackingService()
        }

        setContent {
            ShipTrackerTheme {
                ShipTrackerMainScreen()
            }
        }
    }

    private fun startTrackingService() {
        val serviceIntent = Intent(this, ShipTrackingService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
    }
}
