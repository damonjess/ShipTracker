package com.example.shiptracker.data

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

object VesselPhotoStore {

    private val photosMap = ConcurrentHashMap<Long, MutableList<String>>()

    fun getPhotosForVessel(context: Context, mmsi: Long): List<String> {
        val list = photosMap[mmsi]
        if (list != null) return list.toList()

        val prefs = context.getSharedPreferences("vessel_photos", Context.MODE_PRIVATE)
        val raw = prefs.getString("mmsi_$mmsi", "") ?: ""
        val loaded = if (raw.isBlank()) mutableListOf() else raw.split("|").toMutableList()
        photosMap[mmsi] = loaded
        return loaded.toList()
    }

    fun addPhotoForVessel(context: Context, mmsi: Long, uriString: String) {
        val list = photosMap.getOrPut(mmsi) { mutableListOf() }
        if (!list.contains(uriString)) {
            list.add(0, uriString) // Add newest at front
            saveToPrefs(context, mmsi, list)
        }
    }

    fun removePhotoForVessel(context: Context, mmsi: Long, uriString: String) {
        val list = photosMap[mmsi] ?: return
        if (list.remove(uriString)) {
            saveToPrefs(context, mmsi, list)
        }
    }

    private fun saveToPrefs(context: Context, mmsi: Long, list: List<String>) {
        val prefs = context.getSharedPreferences("vessel_photos", Context.MODE_PRIVATE)
        prefs.edit().putString("mmsi_$mmsi", list.joinToString("|")).apply()
    }
}
