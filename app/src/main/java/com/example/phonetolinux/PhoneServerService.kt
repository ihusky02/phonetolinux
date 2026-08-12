package com.example.phonetolinux.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
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

    override fun onCreate() {
        super.onCreate()
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

            var contentLength = 0
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line.isNullOrEmpty()) break
                if (line.startsWith("Content-Length:", ignoreCase = true)) {
                    contentLength = line.substring(15).trim().toIntOrNull() ?: 0
                }
            }

            val output = socket.getOutputStream()
            var response: String

            if (requestLine.contains("/contacts")) {
                val jsonContacts = fetchContactsJson()
                response = "HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=utf-8\r\n\r\n$jsonContacts"
            }
            else if (requestLine.contains("/send-sms")) {
                val charBuffer = CharArray(contentLength)
                reader.read(charBuffer)
                val body = String(charBuffer)

                val phone = extractJsonValue(body, "phone")
                val message = extractJsonValue(body, "message")
                val success = sendSmsToPhone(phone, message)

                response = if (success) "HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=utf-8\r\n\r\n{\"status\":\"success\"}"
                else "HTTP/1.1 500 Internal Server Error\r\nContent-Type: application/json; charset=utf-8\r\n\r\n{\"status\":\"failed\"}"
            }
            else if (requestLine.contains("/wifi")) {
                val charBuffer = CharArray(contentLength)
                reader.read(charBuffer)
                val body = String(charBuffer)
                val enable = extractJsonValue(body, "enable").lowercase() == "true"

                val success = setWifiEnabled(enable)
                response = if (success) "HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=utf-8\r\n\r\n{\"status\":\"success\"}"
                else "HTTP/1.1 500 Internal Server Error\r\nContent-Type: application/json; charset=utf-8\r\n\r\n{\"status\":\"failed\"}"
            }
            else if (requestLine.contains("/audio")) {
                val charBuffer = CharArray(contentLength)
                reader.read(charBuffer)
                val body = String(charBuffer)
                val mode = extractJsonValue(body, "mode") // "silent", "vibrate", "normal"

                val success = setAudioMode(mode)
                response = if (success) "HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=utf-8\r\n\r\n{\"status\":\"success\"}"
                else "HTTP/1.1 500 Internal Server Error\r\nContent-Type: application/json; charset=utf-8\r\n\r\n{\"status\":\"failed\"}"
            }
            else if (requestLine.contains("/cellular")) {
                val charBuffer = CharArray(contentLength)
                reader.read(charBuffer)
                val body = String(charBuffer)
                val enable = extractJsonValue(body, "enable").lowercase() == "true"

                val success = setCellularDataEnabled(enable)
                response = if (success) "HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=utf-8\r\n\r\n{\"status\":\"success\"}"
                else "HTTP/1.1 500 Internal Server Error\r\nContent-Type: application/json; charset=utf-8\r\n\r\n{\"status\":\"failed\"}"
            }
            else if (requestLine.contains("/storage")) {
                val fileListJson = getStorageFilesJson()
                response = "HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=utf-8\r\n\r\n$fileListJson"
            }
            else {
                response = "HTTP/1.1 404 Not Found\r\n\r\nNot Found"
            }

            output.write(response.toByteArray(Charsets.UTF_8))
            output.flush()
            socket.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun extractJsonValue(json: String, key: String): String {
        return try {
            val regex = "\"$key\"\\s*:\\s*\"([^\"]*)\"".toRegex()
            val match = regex.find(json)
            match?.groups?.get(1)?.value ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun setWifiEnabled(enable: Boolean): Boolean {
        return try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                @Suppress("DEPRECATION")
                val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                @Suppress("DEPRECATION")
                wifiManager.isWifiEnabled = enable
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun setAudioMode(mode: String): Boolean {
        return try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            when (mode.lowercase()) {
                "silent" -> audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                "vibrate" -> audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                "normal" -> audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                else -> return false
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun setCellularDataEnabled(enable: Boolean): Boolean {
        return try {
            val intent = Intent(android.provider.Settings.ACTION_DATA_ROAMING_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            applicationContext.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun getStorageFilesJson(): String {
        return try {
            val path = Environment.getExternalStorageDirectory()
            val files = path.listFiles() ?: arrayOf()
            val jsonList = files.map { "{\"name\":\"${it.name}\",\"isDirectory\":${it.isDirectory}}" }
            "[${jsonList.joinToString(",")}]"
        } catch (e: Exception) {
            "[]"
        }
    }

    private fun sendSmsToPhone(phoneNumber: String, message: String): Boolean {
        return try {
            if (phoneNumber.isEmpty() || message.isEmpty()) return false

            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                applicationContext.getSystemService(android.telephony.SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                android.telephony.SmsManager.getDefault()
            }

            smsManager?.sendTextMessage(phoneNumber, null, message, null, null)
            true
        } catch (e: Exception) {
            Log.e("PhoneToLinux", "Błąd wysyłania SMS: ${e.message}")
            false
        }
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
                val name = it.getString(nameIdx) ?: "Nieznany"
                val number = it.getString(numberIdx) ?: ""
                contactsList.add("{\"name\":\"$name\",\"phone\":\"$number\"}")
            }
        }

        return "[${contactsList.joinToString(",")}]"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        serverSocket?.close()
    }
}