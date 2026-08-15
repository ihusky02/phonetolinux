package com.example.phonetolinux.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.telephony.PhoneStateListener
import android.telephony.SmsManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.Executors

class PhoneServerService : Service() {

    private val executor = Executors.newSingleThreadExecutor()
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private lateinit var telephonyManager: TelephonyManager

    companion object {
        const val CHANNEL_ID = "PhoneToLinuxChannel"
        const val PORT = 5000
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = createNotification("PhonetoLinux działa w tle")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }

        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        setupCallStateListener()
        startHttpServer()

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "PhonetoLinux Serwer w tle",
                NotificationManager.IMPORTANCE_LOW
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
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun setupCallStateListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                telephonyManager.registerTelephonyCallback(
                    mainExecutor,
                    object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                        override fun onCallStateChanged(state: Int) {
                            handleCallState(state)
                        }
                    }
                )
            } catch (e: Exception) {
                fallbackPhoneStateListener()
            }
        } else {
            fallbackPhoneStateListener()
        }
    }

    private fun fallbackPhoneStateListener() {
        @Suppress("DEPRECATION")
        telephonyManager.listen(object : PhoneStateListener() {
            @Deprecated("Deprecated in Java")
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                super.onCallStateChanged(state, phoneNumber)
                handleCallState(state)
            }
        }, PhoneStateListener.LISTEN_CALL_STATE)
    }

    private fun handleCallState(state: Int) {
        val status = when (state) {
            TelephonyManager.CALL_STATE_RINGING -> "RINGING"
            TelephonyManager.CALL_STATE_OFFHOOK -> "ACTIVE"
            TelephonyManager.CALL_STATE_IDLE -> "IDLE"
            else -> "UNKNOWN"
        }
        Log.d("PhoneToLinux", "Stan połączenia: $status")
    }

    private fun startHttpServer() {
        if (isRunning) return
        isRunning = true
        executor.execute {
            try {
                serverSocket = ServerSocket(PORT)
                while (isRunning) {
                    val socket = serverSocket?.accept()
                    socket?.let { handleClient(it) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun handleClient(socket: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val requestLine = reader.readLine() ?: return

            // Odczyt nagłówków, aby wyłapać ewentualny Content-Length przy zapytaniach POST
            var contentLength = 0
            var line: String?
            while (reader.readLine().let { line = it; !line.isNullOrEmpty() }) {
                if (line!!.startsWith("Content-Length:", true)) {
                    contentLength = line!!.substring(15).trim().toIntOrNull() ?: 0
                }
            }

            var body = ""
            if (contentLength > 0) {
                val cbuf = CharArray(contentLength)
                reader.read(cbuf)
                body = String(cbuf)
            }

            val output = socket.getOutputStream()
            var responseJson = ""
            var statusCode = "200 OK"

            when {
                requestLine.startsWith("GET /contacts") -> {
                    responseJson = fetchContactsJson()
                }
                requestLine.startsWith("GET /conversations") -> {
                    responseJson = fetchConversationsJson()
                }
                requestLine.startsWith("GET /chathistory") -> {
                    val number = extractQueryParam(requestLine, "number")
                    responseJson = fetchChatHistoryJson(number)
                }
                requestLine.startsWith("GET /call") -> {
                    val number = extractQueryParam(requestLine, "number")
                    makeCall(number)
                    responseJson = "{\"status\":\"success\"}"
                }
                requestLine.startsWith("POST /sendsms") -> {
                    sendSmsFromBody(body)
                    responseJson = "{\"status\":\"success\"}"
                }
                else -> {
                    statusCode = "404 Not Found"
                    responseJson = "Not Found"
                }
            }

            val httpResponse = "HTTP/1.1 $statusCode\r\nContent-Type: application/json; charset=utf-8\r\n\r\n$responseJson"
            output.write(httpResponse.toByteArray(Charsets.UTF_8))
            output.flush()
            socket.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun extractQueryParam(requestLine: String, paramName: String): String {
        try {
            val parts = requestLine.split(" ")
            if (parts.size > 1) {
                val urlParts = parts[1].split("?")
                if (urlParts.size > 1) {
                    val queryParams = urlParts[1].split("&")
                    for (param in queryParams) {
                        val keyValue = param.split("=")
                        if (keyValue.size == 2 && keyValue[0] == paramName) {
                            return URLDecoder.decode(keyValue[1], "UTF-8")
                        }
                    }
                }
            }
        } catch (e: Exception) { }
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

    private fun fetchConversationsJson(): String {
        // Prosta implementacja zwracająca puste lub ostatnie konwersacje z bazy SMS
        return "[]"
    }

    private fun fetchChatHistoryJson(phoneNumber: String): String {
        val messages = mutableListOf<String>()
        try {
            val uri = Uri.parse("content://sms/")
            val cursor = contentResolver.query(uri, null, "address = ?", arrayOf(phoneNumber), "date ASC")
            cursor?.use {
                val bodyIdx = it.getColumnIndex("body")
                val typeIdx = it.getColumnIndex("type")
                while (it.moveToNext()) {
                    val body = if (bodyIdx != -1) it.getString(bodyIdx) ?: "" else ""
                    val type = if (typeIdx != -1) it.getInt(typeIdx) else 1
                    val isOutgoing = (type == 2) // 2 to zazwyczaj wysłane (SENT)
                    messages.add("{\"text\":\"$body\",\"isOutgoing\":$isOutgoing}")
                }
            }
        } catch (e: Exception) { }
        return "[${messages.joinToString(",")}]"
    }

    private fun makeCall(number: String) {
        if (number.isBlank()) return
        try {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun sendSmsFromBody(body: String) {
        try {
            // Proste wyciągnięcie danych z JSON typu {"phoneNumber":"...","message":"..."}
            var phone = ""
            var message = ""

            if (body.contains("phoneNumber") && body.contains("message")) {
                // Wyciąganie wartości za pomocą prostej manipulacji stringami lub regexem
                val phoneMatch = Regex("\"phoneNumber\"\\s*:\\s*\"([^\"]*)\"").find(body)
                val msgMatch = Regex("\"message\"\\s*:\\s*\"([^\"]*)\"").find(body)

                phone = phoneMatch?.groupValues?.get(1) ?: ""
                message = msgMatch?.groupValues?.get(1) ?: ""
            }

            if (phone.isNotBlank() && message.isNotBlank()) {
                val smsManager = SmsManager.getDefault()
                smsManager.sendTextMessage(phone, null, message, null, null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        serverSocket?.close()
    }
}