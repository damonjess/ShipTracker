package com.example.shiptracker.mesh

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.util.concurrent.Executors

class MeshReceiver(private val onPayloadReceived: (String) -> Unit) {
    
    // Push socket operations off the UI thread to prevent freezing
    private val executor = Executors.newSingleThreadExecutor()
    private var isListening = false
    private var serverSocket: ServerSocket? = null

    fun startListening(port: Int = 8988) {
        isListening = true
        executor.execute {
            try {
                serverSocket = ServerSocket(port)
                Log.d("MeshReceiver", "📡 SOCKET OPEN: Listening on port $port")

                while (isListening) {
                    // This line blocks until another ShipTracker connects
                    val clientSocket = serverSocket!!.accept()
                    Log.d("MeshReceiver", "🔗 DATALINK ESTABLISHED: Client connected")

                    // Read the incoming burst transmission
                    val reader = BufferedReader(InputStreamReader(clientSocket.getInputStream()))
                    val payload = reader.readText()

                    Log.d("MeshReceiver", "📦 PAYLOAD RECEIVED: ${payload.length} bytes")
                    
                    // Send the data back to the UI/ViewModel to merge into the Room DB
                    onPayloadReceived(payload)

                    // Sever the connection instantly after the burst
                    clientSocket.close()
                }
            } catch (e: Exception) {
                if (isListening) Log.e("MeshReceiver", "❌ SOCKET ERROR", e)
            }
        }
    }

    fun stopListening() {
        isListening = false
        serverSocket?.close()
        Log.d("MeshReceiver", "🛑 SOCKET CLOSED")
    }
}
