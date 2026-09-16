package com.example.shiptracker.mesh

import android.content.Context
import android.net.wifi.p2p.WifiP2pDevice
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.shiptracker.data.ShipState
import com.example.shiptracker.data.VesselDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MeshViewModel(
    context: Context,
    private val vesselDao: VesselDao
) : ViewModel() {
    
    private val serializer = MeshSerializer()
    private val transmitter = MeshTransmitter()

    private val _selectedTarget = MutableStateFlow<ShipState?>(null)
    val selectedTarget: StateFlow<ShipState?> = _selectedTarget.asStateFlow()

    private val _selectedMmsi = MutableStateFlow<String?>(null)
    val selectedMmsi: StateFlow<String?> = _selectedMmsi.asStateFlow()

    fun lockOnTarget(mmsi: String?) {
        _selectedMmsi.value = mmsi
    }

    fun selectTarget(ship: ShipState?) {
        _selectedTarget.value = ship
        _selectedMmsi.value = ship?.mmsi?.toString()
    }

    private val _discoveredPeers = MutableStateFlow<List<WifiP2pDevice>>(emptyList())
    val discoveredPeers: StateFlow<List<WifiP2pDevice>> = _discoveredPeers.asStateFlow()

    // We need a function that the BroadcastReceiver can call to update this list
    fun updatePeers(peers: List<WifiP2pDevice>) {
        _discoveredPeers.value = peers
    }

    val meshNegotiator = MeshNegotiator(
        context = context.applicationContext,
        onHostIpDiscovered = { hostIp ->
            firePayloadToHost(hostIp)
        },
        onPeersChanged = { newPeers ->
            updatePeers(newPeers)
        }
    )

    fun startScan() {
        meshNegotiator.discoverNodes()
    }

    fun connectToTarget(device: WifiP2pDevice) {
        meshNegotiator.connectToDevice(device)
    }

    
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

    class Factory(
        private val context: Context,
        private val vesselDao: VesselDao
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MeshViewModel::class.java)) {
                return MeshViewModel(context, vesselDao) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
