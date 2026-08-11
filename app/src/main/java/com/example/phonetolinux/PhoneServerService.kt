package com.example.phonetolinux.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
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
        // Natychmiast rejestrujemy usługę jako Foreground, żeby Android jej nie zamknął
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
            val line = reader.readLine()

            val output = socket.getOutputStream()
            val response: String

            if (line != null && line.contains("/contacts")) {
                val jsonContacts = fetchContactsJson()
                response = "HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=utf-8\r\n\r\n$jsonContacts"
            } else {
                response = "HTTP/1.1 404 Not Found\r\n\r\nNot Found"
            }

            output.write(response.toByteArray(Charsets.UTF_8))
            output.flush()
            socket.close()
        } catch (e: Exception) {
            e.printStackTrace()
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