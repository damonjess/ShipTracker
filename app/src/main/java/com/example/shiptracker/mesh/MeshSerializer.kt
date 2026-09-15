package com.example.shiptracker.mesh

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.example.shiptracker.data.VesselTrackPoint

class MeshSerializer {
    private val gson = Gson()

    // 1. Pack the local Room data into a JSON burst string
    fun packPayload(points: List<VesselTrackPoint>): String {
        val payload = gson.toJson(points)
        Log.d("MeshSerializer", "📦 Payload Packed: ${payload.length} bytes for ${points.size} track points")
        return payload
    }

    // 2. Unpack the incoming network string back into Room entities
    fun unpackPayload(payload: String): List<VesselTrackPoint> {
        return try {
            val listType = object : TypeToken<List<VesselTrackPoint>>() {}.type
            val points: List<VesselTrackPoint>? = gson.fromJson(payload, listType)
            val recovered = points ?: emptyList()
            Log.d("MeshSerializer", "🔓 Payload Unpacked: ${recovered.size} track points recovered")
            recovered
        } catch (e: Exception) {
            Log.e("MeshSerializer", "❌ Payload corrupted or unreadable", e)
            emptyList()
        }
    }
}
