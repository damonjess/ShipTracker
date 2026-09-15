package com.example.shiptracker.mesh

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import java.util.UUID

class BleMeshBeacon(context: Context) {
    
    // This is ShipTracker's classified "Handshake" UUID. 
    // Only devices scanning for this exact UUID will see the beacon.
    private val SHIP_TRACKER_SERVICE_UUID = UUID.fromString("A1B2C3D4-E5F6-47A8-B9C0-D1E2F3A4B5C6")
    
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter = bluetoothManager.adapter
    private val advertiser = bluetoothAdapter.bluetoothLeAdvertiser

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.d("BleMesh", "📡 SILENT MESH BEACON ACTIVE: Broadcasting ShipTracker UUID")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e("BleMesh", "❌ BEACON FAILED: Error Code $errorCode")
        }
    }

    @SuppressLint("MissingPermission") // We will handle permissions in the UI layer
    fun startBroadcasting() {
        if (advertiser == null) return

        // Optimize for low power so this can run indefinitely in the background
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_LOW)
            .setConnectable(true)
            .build()

        // Attach our secret handshake UUID to the broadcast payload
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false) // Keep it stealthy
            .addServiceUuid(ParcelUuid(SHIP_TRACKER_SERVICE_UUID))
            .build()

        advertiser.startAdvertising(settings, data, advertiseCallback)
    }

    @SuppressLint("MissingPermission")
    fun stopBroadcasting() {
        advertiser?.stopAdvertising(advertiseCallback)
        Log.d("BleMesh", "🛑 SILENT MESH BEACON OFFLINE")
    }
}
