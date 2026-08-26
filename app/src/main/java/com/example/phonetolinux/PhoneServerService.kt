package com.example.phonetolinux.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.phonetolinux.CallEndpoint
import com.example.phonetolinux.ContactsEndpoint
import com.example.phonetolinux.PingEndpoint
import com.example.phonetolinux.endpoints.BluetoothAudioEndpoint
import com.example.phonetolinux.endpoints.ChatHistoryEndpoint
import com.example.phonetolinux.endpoints.ConversationsEndpoint
import com.example.phonetolinux.endpoints.SendSmsEndpoint
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections

/**
 * Main HTTP server background service running on Android.
 * Manages the network socket, SSE stream (/sms_stream),
 * and delegates standard HTTP requests to appropriate endpoint handlers.
 *
 * @author Stanisław Tlołka
 */
class PhoneServerService : Service() {

    private var serverSocket: ServerSocket? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false

    // Registry of all available server endpoint plugins
    private val endpoints = listOf(
        PingEndpoint(),
        ContactsEndpoint(),
        ConversationsEndpoint(),
        ChatHistoryEndpoint(),
        CallEndpoint(),
        SendSmsEndpoint(),
        BluetoothAudioEndpoint() // <-- Bluetooth hands-free audio routing plugin for PC
    )

    companion object {
        const val CHANNEL_ID = "PhoneToLinuxChannel"
        const val PORT = 5000
        private const val TAG = "PhoneToLinuxServer"

        private val clients = Collections.synchronizedList(mutableListOf<PrintWriter>())

        /**
         * Broadcasts a new SMS notification over the open SSE stream to the computer.
         */
        fun broadcastSms(sender: String, message: String) {
            Log.d(TAG, "broadcastSms invoked for sender: $sender")
            synchronized(clients) {
                val jsonPayload = "{\"event\":\"incoming_sms\",\"sender\":\"$sender\",\"message\":\"$message\"}"
                val deadClients = mutableListOf<PrintWriter>()
                for (writer in clients) {
                    try {
                        writer.println("data: $jsonPayload\n")
                        writer.flush()
                    } catch (e: Exception) {
                        deadClients.add(writer)
                    }
                }
                clients.removeAll(deadClients)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = createNotification("PhonetoLinux Server running in background")

        // Guard check: Validate required permissions for Android 15 FGS connectedDevice
        if (!hasRequiredPermissions()) {
            Log.e(TAG, "Missing required FGS permissions for connectedDevice. Stopping service to prevent crash.")
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    1,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(1, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service: ${e.message}", e)
            stopSelf()
            return START_NOT_STICKY
        }

        startHttpServer()
        return START_STICKY
    }

    /**
     * Checks whether all required Android 12+ / Android 15 runtime permissions
     * for running a connectedDevice foreground service are currently granted.
     */
    private fun hasRequiredPermissions(): Boolean {
        // Bluetooth permissions check for Android 12+ (API 31+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasBtConnect = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasBtConnect) return false
        }

        // FGS connectedDevice permission check for Android 14+ / 15 (API 34+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val hasFgsPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasFgsPermission) return false
        }

        return true
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "PhonetoLinux Background Server",
                NotificationManager.IMPORTANCE_HIGH
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PhonetoLinux Bridge")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun startHttpServer() {
        if (isRunning) return
        isRunning = true

        serviceScope.launch {
            try {
                serverSocket = ServerSocket(PORT)
                while (isRunning) {
                    try {
                        val clientSocket = serverSocket?.accept() ?: break
                        launch(Dispatchers.IO) { handleClient(clientSocket) }
                    } catch (e: Exception) {
                        if (!isRunning) break
                        e.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun handleClient(socket: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val writer = PrintWriter(socket.getOutputStream(), true)

            val requestLine = reader.readLine() ?: return
            Log.d(TAG, "Received request: $requestLine")

            var headerLine = reader.readLine()
            while (!headerLine.isNullOrEmpty()) { headerLine = reader.readLine() }

            // SSE stream is handled natively due to the keep-alive loop requirement
            if (requestLine.startsWith("GET /sms_stream")) {
                writer.println("HTTP/1.1 200 OK")
                writer.println("Content-Type: text/event-stream")
                writer.println("Connection: keep-alive\n")
                writer.flush()

                synchronized(clients) { clients.add(writer) }

                var pingCounter = 0
                while (isRunning && !socket.isClosed) {
                    Thread.sleep(5000)
                    pingCounter++
                    if (pingCounter >= 3) {
                        try {
                            writer.println(": ping\n")
                            writer.flush()
                        } catch (e: Exception) { break }
                        pingCounter = 0
                    }
                }
                return
            }

            // --- PLUGIN SYSTEM ---
            // Search for an endpoint plugin matching the requested URL path
            val handler = endpoints.find { requestLine.contains("GET ${it.path}") }

            val statusCode: String
            val responseBody: String

            if (handler != null) {
                val response = handler.handle(requestLine, this)
                statusCode = response.statusCode
                responseBody = response.body
            } else {
                statusCode = "404 Not Found"
                responseBody = "Not Found"
            }

            writer.println("HTTP/1.1 $statusCode")
            writer.println("Content-Type: application/json; charset=UTF-8")
            writer.println("Content-Length: ${responseBody.toByteArray().size}")
            writer.println("Connection: close\n")
            writer.println(responseBody)

            socket.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        try { serverSocket?.close() } catch (e: Exception) { e.printStackTrace() }
        serviceScope.cancel()
    }
}