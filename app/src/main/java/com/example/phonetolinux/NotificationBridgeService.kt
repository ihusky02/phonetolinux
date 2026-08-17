package com.example.phonetolinux.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Usługa nasłuchująca powiadomień systemowych Androida.
 * Wychwytuje przychodzące wiadomości SMS bezpośrednio z paska powiadomień
 * i przekazuje je do serwera HTTP, aby natychmiast wysłać je
 * do podłączonego klienta (Linux) przez strumień SSE.
 */

class NotificationBridgeService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn?.let {
            val packageName = it.packageName // np. com.google.android.apps.messaging (SMS)
            val extras = it.notification.extras

            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""

            // Bezpieczne wyciąganie treści powiadomienia
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                ?: ""

            Log.d("PhoneToLinuxNotif", "Pakiet: $packageName | Od: $title | Treść: $text")

            // Wysyłamy powiadomienie przez WebSocket do komputera w czasie rzeczywistym
            if (packageName.contains("messaging") || packageName.contains("sms") || text.isNotBlank()) {
                PhoneServerService.broadcastSms(sender = title, message = text)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
    }
}