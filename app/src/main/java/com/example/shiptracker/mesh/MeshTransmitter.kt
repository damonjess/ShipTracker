package com.example.shiptracker.mesh

import android.util.Log
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors

class MeshTransmitter {
    
    private val executor = Executors.newSingleThreadExecutor()

    fun burstTransmit(hostIpAddress: String, payload: String, port: Int = 8988) {
        executor.execute {
            val socket = Socket()
            try {
                Log.d("MeshTransmitter", "🚀 INITIATING BURST TRANSMISSION to $hostIpAddress")
                
                // Bind to any local port, but connect to the Host's specific port 8988
                socket.bind(null)
                // 5-second timeout. If they aren't listening, abort.
                socket.connect(InetSocketAddress(hostIpAddress, port), 5000)

                // Blast the payload through the socket
                val writer = OutputStreamWriter(socket.getOutputStream())
                writer.write(payload)
                writer.flush()

                Log.d("MeshTransmitter", "✅ TRANSMISSION COMPLETE: ${payload.length} bytes sent")

            } catch (e: Exception) {
                Log.e("MeshTransmitter", "❌ TRANSMISSION FAILED", e)
            } finally {
                // Close the socket to clear the network layer
                if (socket.isConnected) {
                    socket.close()
                }
            }
        }
    }
}
