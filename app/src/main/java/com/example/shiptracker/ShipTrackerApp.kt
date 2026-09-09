package com.example.shiptracker

import android.app.Application
import com.example.shiptracker.data.AppDatabase
import com.example.shiptracker.data.ShipRepository
import org.osmdroid.config.Configuration

class ShipTrackerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        val osmdroidConfig = Configuration.getInstance()
        osmdroidConfig.load(
            this,
            getSharedPreferences("osmdroid", MODE_PRIVATE)
        )
        
        // Identify the app and provide a real contact URL to tile providers.
        // Do not use a placeholder address such as contact@example.com.
        osmdroidConfig.userAgentValue = "ShipTrackerApp-v1.0 (damonjess-at-github)"

        val dao = AppDatabase.getDatabase(this).vesselDao()
        ShipRepository.initialize(dao)
    }
}
