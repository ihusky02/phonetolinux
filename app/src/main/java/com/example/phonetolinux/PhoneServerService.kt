package com.example.phonetolinux.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.phonetolinux.MainActivity
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.Collections

class PhoneServerService : Service() {

    private var serverSocket: ServerSocket? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false

    companion object {
        const val CHANNEL_ID = "PhoneToLinuxChannel"
        const val PORT = 5000
        private const val TAG = "PhoneToLinuxServer"

        // Lista aktywnych połączeń klientów (strumień powiadomień)
        private val clients = Collections.synchronizedList(mutableListOf<PrintWriter>())

        fun broadcastSms(sender: String, message: String) {
            Log.d(TAG, "broadcastSms wywołane dla nadawcy: $sender, treść: $message")
            synchronized(clients) {
                Log.d(TAG, "Aktualna liczba nasłuchujących klientów: ${clients.size}")
                val jsonPayload = "{\"event\":\"incoming_sms\",\"sender\":\"$sender\",\"message\":\"$message\"}"
                val deadClients = mutableListOf<PrintWriter>()
                for (writer in clients) {
                    try {
                        writer.println(jsonPayload)
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
            val channel = NotificationChannel(
                CHANNEL_ID,
                "PhonetoLinux Serwer w tle",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
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
                        launch(Dispatchers.IO) {
                            handleClient(clientSocket)
                        }
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

            // Obsługa nasłuchu w czasie rzeczywistym (Streaming / persystentne połączenie dla SMS)
            if (requestLine.startsWith("GET /sms_stream")) {
                synchronized(clients) {
                    clients.add(writer)
                    Log.d(TAG, "Dodano klienta do strumienia /sms_stream! Łącznie klientów: ${clients.size}")
                }
                // Utrzymujemy socket otwarty dla powiadomień w czasie rzeczywistym
                while (isRunning && !socket.isClosed) {
                    Thread.sleep(1000)
                }
                return
            }

            var responseBody = "Not Found"
            var statusCode = "404 Not Found"

            when {
                requestLine.contains("GET /ping") -> {
                    statusCode = "200 OK"
                    responseBody = "phonetolinux-server-found"
                }
                requestLine.contains("GET /contacts") -> {
                    statusCode = "200 OK"
                    responseBody = fetchContactsJson()
                }
                requestLine.contains("GET /chathistory") -> {
                    val number = extractQueryParam(requestLine, "number")
                    statusCode = "200 OK"
                    responseBody = fetchChatHistoryJson(number)
                }
                requestLine.contains("GET /call") -> {
                    val number = extractQueryParam(requestLine, "number")
                    makeCall(number)
                    statusCode = "200 OK"
                    responseBody = "{\"status\":\"success\"}"
                }
                requestLine.contains("GET /send_sms") -> {
                    val number = extractQueryParam(requestLine, "number")
                    val message = extractQueryParam(requestLine, "message")
                    val success = SmsHandler.sendSms(this, number, message)
                    statusCode = "200 OK"
                    responseBody = if (success) "{\"status\":\"success\"}" else "{\"status\":\"error\"}"
                }
            }

            writer.println("HTTP/1.1 $statusCode")
            writer.println("Content-Type: application/json; charset=UTF-8")
            writer.println("Content-Length: ${responseBody.toByteArray().size}")
            writer.println("Connection: close")
            writer.println()
            writer.println(responseBody)

            socket.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun extractQueryParam(requestLine: String, paramName: String): String {
        try {
            val parts = requestLine.split(" ")
            if (parts.size < 2) return ""
            val pathAndQuery = parts[1]
            val queryParts = pathAndQuery.split("?")
            if (queryParts.size < 2) return ""

            val query = queryParts[1]
            val params = query.split("&")
            for (param in params) {
                val keyValue = param.split("=")
                if (keyValue.size == 2 && keyValue[0] == paramName) {
                    return URLDecoder.decode(keyValue[1], "UTF-8")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ""
    }

    private fun fetchContactsJson(): String {
        val contactsList = mutableListOf<String>()
        val cursor = contentResolver.query(
            android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            null, null, null,
            android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )
        cursor?.use {
            val nameIdx = it.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIdx = it.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext()) {
                val name = if (nameIdx != -1) it.getString(nameIdx) ?: "Nieznany" else "Nieznany"
                val number = if (numberIdx != -1) it.getString(numberIdx) ?: "" else ""
                contactsList.add("{\"name\":\"$name\",\"phone\":\"$number\"}")
            }
        }
        return "[${contactsList.joinToString(",")}]"
    }

    private fun fetchChatHistoryJson(phoneNumber: String): String {
        val messages = mutableListOf<String>()
        try {
            val uri = Uri.parse("content://sms/")
            val cursor = contentResolver.query(
                uri,
                null,
                "address LIKE ?",
                arrayOf("%$phoneNumber"),
                "date ASC"
            )
            cursor?.use {
                val bodyIdx = it.getColumnIndex("body")
                val typeIdx = it.getColumnIndex("type")
                while (it.moveToNext()) {
                    val body = if (bodyIdx != -1) it.getString(bodyIdx) ?: "" else ""
                    val type = if (typeIdx != -1) it.getInt(typeIdx) else 1
                    val isOutgoing = (type == 2)
                    messages.add("{\"text\":\"$body\",\"isOutgoing\":$isOutgoing}")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "[${messages.joinToString(",")}]"
    }

    private fun makeCall(number: String) {
        if (number.isBlank()) return
        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                action = "ACTION_MAKE_CALL"
                putExtra("EXTRA_PHONE_NUMBER", number)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notificationManager = getSystemService(NotificationManager::class.java)
            val callNotification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("PhonetoLinux - Połączenie")
                .setContentText("Inicjowanie połączenia z: $number")
                .setSmallIcon(android.R.drawable.ic_menu_call)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setContentIntent(pendingIntent)
                .setFullScreenIntent(pendingIntent, true)
                .setAutoCancel(true)
                .build()

            notificationManager?.notify(99, callNotification)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        serviceScope.cancel()
    }
}