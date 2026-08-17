package com.example.phonetolinux

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.phonetolinux.service.PhoneServerService

/**
 * Wtyczka wywołująca połączenie głosowe.
 * Tworzy powiadomienie o wysokim priorytecie i wysyła intencję do MainActivity.
 */
class CallEndpoint : EndpointHandler {
    override val path = "/call"

    override fun handle(requestLine: String, context: Context): EndpointResponse {
        val number = HttpUtils.extractQueryParam(requestLine, "number")
        if (number.isNotBlank()) {
            makeCall(context, number)
        }
        return EndpointResponse(body = "{\"status\":\"success\"}")
    }

    private fun makeCall(context: Context, number: String) {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                action = "ACTION_MAKE_CALL"
                putExtra("EXTRA_PHONE_NUMBER", number)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notificationManager = context.getSystemService(NotificationManager::class.java)
            val callNotification = NotificationCompat.Builder(context, PhoneServerService.CHANNEL_ID)
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
}