package com.example.shiptracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VesselDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoint(point: VesselTrackPoint)

    // Retrieve points in chronological order for drawing the Polyline
    @Query("SELECT * FROM vessel_track_points WHERE mmsi = :mmsi ORDER BY timestamp ASC")
    fun getTrackForVessel(mmsi: Long): Flow<List<VesselTrackPoint>>

    // Optional cleanup: purge points older than 48 hours to save device storage
    @Query("DELETE FROM vessel_track_points WHERE timestamp < :cutoffTime")
    suspend fun deleteOldPoints(cutoffTime: Long)
}
