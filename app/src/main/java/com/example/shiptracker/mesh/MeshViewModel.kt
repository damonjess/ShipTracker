package com.example.shiptracker.mesh

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.shiptracker.data.VesselDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MeshViewModel(private val vesselDao: VesselDao) : ViewModel() {
    
    private val serializer = MeshSerializer()
    private val transmitter = MeshTransmitter()
    
    // Start the receiver and define what happens when a payload hits the socket
    val receiver = MeshReceiver(onPayloadReceived = { payload ->
        viewModelScope.launch(Dispatchers.IO) {
            // 1. Unpack the JSON back into VesselTrackPoint entities
            val points = serializer.unpackPayload(payload)
            // 2. Upsert into Room DB (REPLACE strategy overwrites old data)
            vesselDao.insertPoints(points) 
        }
    })

    fun firePayloadToHost(hostIp: String) {
        viewModelScope.launch(Dispatchers.IO) {
            // 1. Grab all track points from the local SQLite Room DB
            val allPoints = vesselDao.getAllPoints() 
            // 2. Serialize them into the JSON burst string
            val jsonPayload = serializer.packPayload(allPoints)
            // 3. Fire the payload through the TCP socket
            transmitter.burstTransmit(hostIp, jsonPayload)
        }
    }

    class Factory(private val vesselDao: VesselDao) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MeshViewModel::class.java)) {
                return MeshViewModel(vesselDao) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
