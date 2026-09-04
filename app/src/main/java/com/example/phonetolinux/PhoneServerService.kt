package com.example.phonetolinux.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.phonetolinux.ContactsEndpoint
import com.example.phonetolinux.PingEndpoint
import com.example.phonetolinux.endpoints.BluetoothAudioEndpoint
import com.example.phonetolinux.endpoints.CallEndpoint
import com.example.phonetolinux.endpoints.ChatHistoryEndpoint
import com.example.phonetolinux.endpoints.ConversationsEndpoint
import com.example.phonetolinux.endpoints.DeleteConversationEndpoint
import com.example.phonetolinux.endpoints.MessagesEndpoint
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
 * monitors telephony states (incoming calls) universally across all Android versions,
 * and delegates standard HTTP requests to appropriate endpoint handlers.
 *
 * @author Stanisław Tlołka
 */
class PhoneServerService : Service() {

    private var serverSocket: ServerSocket? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false
    private var telephonyManager: TelephonyManager? = null
    private var telephonyCallback: Any? = null
    private var legacyPhoneStateListener: Any? = null

    // Registry of all available server endpoint plugins
    private val endpoints = listOf(
        PingEndpoint(),
        ContactsEndpoint(),
        ConversationsEndpoint(),
        ChatHistoryEndpoint(),
        MessagesEndpoint(),
        DeleteConversationEndpoint(),
        CallEndpoint(),
        SendSmsEndpoint(),
        BluetoothAudioEndpoint()
    )

    companion object {
        const val CHANNEL_ID = "PhoneToLinuxChannel"
        const val PORT = 5000
        private const val TAG = "PhoneToLinuxServer"

        private val clients = Collections.synchronizedList(mutableListOf<PrintWriter>())

        /**
         * Broadcasts a new SMS notification over the open SSE stream to the computer safely in IO scope.
         */
        fun broadcastSms(sender: String, message: String) {
            CoroutineScope(Dispatchers.IO).launch {
                Log.d(TAG, "broadcastSms invoked for sender: $sender. Active SSE clients: ${clients.size}")
                synchronized(clients) {
                    val jsonPayload = "{\"event\":\"incoming_sms\",\"sender\":\"$sender\",\"message\":\"$message\"}"
                    val deadClients = mutableListOf<PrintWriter>()
                    for (writer in clients) {
                        try {
                            writer.write("data: $jsonPayload\n\n")
                            writer.flush()
                            if (writer.checkError()) {
                                deadClients.add(writer)
                                Log.w(TAG, "Detected broken SSE client stream during SMS broadcast.")
                            } else {
                                Log.d(TAG, "Successfully sent SMS SSE packet to client.")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error sending SMS SSE packet: ${e.javaClass.simpleName} - ${e.message}")
                            deadClients.add(writer)
                        }
                    }
                    clients.removeAll(deadClients)
                }
            }
        }

        /**
         * Broadcasts a voice call state event over the SSE stream to the connected Linux desktop safely in IO scope.
         */
        fun broadcastCallEvent(event: String, number: String) {
            CoroutineScope(Dispatchers.IO).launch {
                Log.d(TAG, "broadcastCallEvent: event=$event, number=$number. Active SSE clients: ${clients.size}")
                synchronized(clients) {
                    val jsonPayload = "{\"event\":\"$event\",\"number\":\"$number\"}"
                    val deadClients = mutableListOf<PrintWriter>()
                    for (writer in clients) {
                        try {
                            writer.write("data: $jsonPayload\n\n")
                            writer.flush()
                            if (writer.checkError()) {
                                deadClients.add(writer)
                                Log.w(TAG, "Detected broken SSE client stream during Call broadcast.")
                            } else {
                                Log.d(TAG, "Successfully sent Call SSE packet to client!")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error sending Call SSE packet: ${e.javaClass.simpleName} - ${e.message}")
                            deadClients.add(writer)
                        }
                    }
                    clients.removeAll(deadClients)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = createNotification("PhonetoLinux Server running in background")

        if (!hasRequiredPermissions()) {
            Log.e(TAG, "Missing required FGS permissions for connectedDevice. Stopping service to prevent crash.")
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            } else {
                startForeground(1, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service: ${e.message}", e)
            stopSelf()
            return START_NOT_STICKY
        }

        registerCallStateListener()
        startHttpServer()
        return START_STICKY
    }

    private fun registerCallStateListener() {
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "READ_PHONE_STATE permission missing, incoming call events will not be captured.")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val callback = CallStateCallback()
                telephonyCallback = callback
                telephonyManager?.registerTelephonyCallback(mainExecutor, callback)
                Log.d(TAG, "TelephonyCallback registered successfully (Android 12+).")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register TelephonyCallback: ${e.message}")
            }
        } else {
            try {
                @Suppress("DEPRECATION")
                val listener = object : PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                        super.onCallStateChanged(state, phoneNumber)
                        handleCallStateChange(state)
                    }
                }
                legacyPhoneStateListener = listener
                @Suppress("DEPRECATION")
                telephonyManager?.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
                Log.d(TAG, "PhoneStateListener registered successfully (Legacy Android).")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register PhoneStateListener: ${e.message}")
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private inner class CallStateCallback : TelephonyCallback(), TelephonyCallback.CallStateListener {
        override fun onCallStateChanged(state: Int) {
            handleCallStateChange(state)
        }
    }

    private fun handleCallStateChange(state: Int) {
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                Log.d(TAG, "Call state changed: RINGING")
                broadcastCallEvent("incoming_call", "Unknown")
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                Log.d(TAG, "Call state changed: OFFHOOK (Active Call)")
                broadcastCallEvent("call_active", "")
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                Log.d(TAG, "Call state changed: IDLE (Call Ended)")
                broadcastCallEvent("call_ended", "")
            }
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasBtConnect = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            if (!hasBtConnect) return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val hasFgsPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE) == PackageManager.PERMISSION_GRANTED
            if (!hasFgsPermission) return false
        }
        return true
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "PhonetoLinux Background Server", NotificationManager.IMPORTANCE_HIGH)
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
        val clientIp = socket.inetAddress?.hostAddress ?: "Unknown"
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val writer = PrintWriter(socket.getOutputStream(), true)

            val requestLine = reader.readLine() ?: return
            Log.d(TAG, "Received request from $clientIp: $requestLine")

            var headerLine = reader.readLine()
            while (!headerLine.isNullOrEmpty()) { headerLine = reader.readLine() }

            // SSE stream handler with strict connection tracking
            if (requestLine.startsWith("GET /sms_stream")) {
                Log.d(TAG, ">>> SSE Client Connected from IP: $clientIp")
                writer.println("HTTP/1.1 200 OK")
                writer.println("Content-Type: text/event-stream")
                writer.println("Connection: keep-alive")
                writer.println()
                writer.flush()

                synchronized(clients) { clients.add(writer) }
                Log.d(TAG, "Total active SSE clients now: ${clients.size}")

                var pingCounter = 0
                try {
                    while (isRunning && !socket.isClosed) {
                        Thread.sleep(5000)
                        pingCounter++
                        if (pingCounter >= 3) {
                            writer.write(": ping\n\n")
                            writer.flush()
                            if (writer.checkError()) {
                                Log.w(TAG, "SSE ping failed, client $clientIp disconnected.")
                                break
                            }
                            pingCounter = 0
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "SSE stream loop exception for $clientIp: ${e.message}")
                } finally {
                    synchronized(clients) { clients.remove(writer) }
                    Log.d(TAG, "<<< SSE Client Disconnected: $clientIp. Remaining clients: ${clients.size}")
                }
                return
            }

            // --- PLUGIN SYSTEM ---
            val handler = endpoints.find { requestLine.contains(it.path) }
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
            writer.println("Connection: close")
            writer.println()
            writer.println(responseBody)

            socket.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error handling client $clientIp: ${e.message}")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && telephonyCallback != null) {
            try {
                telephonyManager?.unregisterTelephonyCallback(telephonyCallback as TelephonyCallback)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else if (legacyPhoneStateListener != null) {
            try {
                @Suppress("DEPRECATION")
                telephonyManager?.listen(legacyPhoneStateListener as PhoneStateListener, PhoneStateListener.LISTEN_NONE)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        try { serverSocket?.close() } catch (e: Exception) { e.printStackTrace() }
        serviceScope.cancel()
    }
}