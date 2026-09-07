package com.example.shiptracker

import android.app.Application
import org.osmdroid.config.Configuration
import java.io.File

class ShipTrackerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        val osmdroidConfig = Configuration.getInstance()
        osmdroidConfig.load(
            this,
            getSharedPreferences("osmdroid", MODE_PRIVATE)
        )
        
        // Compliant OpenStreetMap Tile Usage Policy User-Agent
        osmdroidConfig.userAgentValue = "ShipTracker/1.0 (com.example.shiptracker; contact@example.com)"

        // Thoroughly wipe all potential osmdroid cache locations and tiles.sqlite databases containing 403 error tiles
        try {
            val basePath = osmdroidConfig.osmdroidBasePath
            if (basePath != null && basePath.exists()) {
                basePath.deleteRecursively()
            }
            val tileCache = osmdroidConfig.getOsmdroidTileCache(this)
            if (tileCache != null && tileCache.exists()) {
                tileCache.deleteRecursively()
            }
            File(filesDir, "osmdroid").apply { if (exists()) deleteRecursively() }
            File(cacheDir, "osmdroid").apply { if (exists()) deleteRecursively() }
            File(filesDir, "tiles.sqlite").delete()
            File(cacheDir, "tiles.sqlite").delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
