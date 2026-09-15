package com.example.shiptracker.mesh

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.util.Log

class MeshNegotiator(
    private val context: Context,
    private val onHostIpDiscovered: (String) -> Unit
) {
    private val manager: WifiP2pManager? = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private val channel = manager?.initialize(context, context.mainLooper, null)

    // We only care about connection events for the data burst
    val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION) {
                
                @Suppress("DEPRECATION")
                val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
                
                @Suppress("DEPRECATION")
                if (networkInfo?.isConnected == true) {
                    Log.d("MeshNegotiator", "🤝 P2P CONNECTION ESTABLISHED: Requesting IP details...")
                    
                    // Extract the IP address of the Group Owner (Host)
                    manager?.requestConnectionInfo(channel) { info ->
                        if (info.groupFormed) {
                            val hostIp = info.groupOwnerAddress?.hostAddress
                            Log.d("MeshNegotiator", "🎯 TARGET LOCKED: Host IP is $hostIp")
                            
                            // If we are the client, pass the IP out to fire the Transmitter
                            if (!info.isGroupOwner && hostIp != null) {
                                onHostIpDiscovered(hostIp)
                            } else {
                                Log.d("MeshNegotiator", "🛡️ WE ARE THE HOST: Waiting for incoming burst on port 8988...")
                            }
                        }
                    }
                } else {
                    Log.d("MeshNegotiator", "🛑 P2P DISCONNECTED")
                }
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun register() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, intentFilter)
        }
    }

    fun unregister() {
        try {
            context.unregisterReceiver(receiver)
        } catch (_: Exception) {
            // Ignore if not registered
        }
    }

    @SuppressLint("MissingPermission")
    fun discoverNodes() {
        manager?.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d("MeshNegotiator", "📡 SCANNING FOR MESH NODES...")
            }
            override fun onFailure(reasonCode: Int) {
                Log.e("MeshNegotiator", "❌ SCAN FAILED: Code $reasonCode")
            }
        })
    }
}
