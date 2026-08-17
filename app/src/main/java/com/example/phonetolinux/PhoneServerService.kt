package com.example.phonetolinux.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.phonetolinux.*
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections

/**
 * Główna usługa serwera HTTP działająca w tle na systemie Android.
 * Zarządza gniazdem sieciowym (Socket), strumieniem SSE (/sms_stream)
 * i deleguje standardowe żądania HTTP do odpowiednich wtyczek (EndpointHandler).
 *
 * @author Stanisław Tlołka
 */
class PhoneServerService : Service() {

    private var serverSocket: ServerSocket? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false

    // Rejestr wszystkich dostępnych wtyczek (endpointów) serwera
    private val endpoints = listOf(
        PingEndpoint(),
        ContactsEndpoint(),
        ConversationsEndpoint(),
        ChatHistoryEndpoint(),
        CallEndpoint(),
        SendSmsEndpoint()
    )

    companion object {
        const val CHANNEL_ID = "PhoneToLinuxChannel"
        const val PORT = 5000
        private const val TAG = "PhoneToLinuxServer"

        private val clients = Collections.synchronizedList(mutableListOf<PrintWriter>())

        /**
         * Wysyła powiadomienie o nowym SMS przez otwarty strumień SSE do komputera.
         */
        fun broadcastSms(sender: String, message: String) {
            Log.d(TAG, "broadcastSms wywołane dla nadawcy: $sender")
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
        val notification = createNotification("PhonetoLinux Serwer działa w tle")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }

        startHttpServer()
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "PhonetoLinux Serwer w tle", NotificationManager.IMPORTANCE_HIGH)
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
            Log.d(TAG, "Otrzymano żądanie: $requestLine")

            var headerLine = reader.readLine()
            while (!headerLine.isNullOrEmpty()) { headerLine = reader.readLine() }

            // Strumień SSE jest obsługiwany natywnie ze względu na pętlę podtrzymującą (keep-alive)
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

            // --- NOWY SYSTEM WTYCZEK ---
            // Szukamy wtyczki, która pasuje do otrzymanego adresu URL
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