package com.example.shiptracker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "vessel_track_points",
    indices = [Index(value = ["mmsi", "timestamp"])]
)
data class VesselTrackPoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mmsi: Long,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long = System.currentTimeMillis()
)
