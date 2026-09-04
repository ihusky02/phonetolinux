package com.example.phonetolinux.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Notification listener service for Android.
 * Universally captures incoming SMS messages and active phone call alerts from the status bar
 * and broadcasts them in real-time to the connected Linux client via the SSE stream.
 *
 * @author Stanisław Tlołka
 */
class NotificationBridgeService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn?.let {
            val packageName = it.packageName
            val extras = it.notification.extras

            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                ?: ""

            Log.d("NotificationBridge", "Package: $packageName | Title: $title | Text: $text")

            // Forward incoming SMS notifications
            if (packageName.contains("messaging") || packageName.contains("sms")) {
                if (text.isNotBlank()) {
                    PhoneServerService.broadcastSms(sender = title, message = text)
                }
            }

            // Universally forward incoming call notifications from any system dialer app
            if (packageName.contains("dialer") || packageName.contains("telecom") || packageName.contains("incallui") || packageName.contains("phone")) {
                if (text.contains("Przychodzące") || text.contains("Incoming") || text.contains("połączenie") || text.contains("Call") || text.isNotBlank()) {
                    // Title contains the caller's name or number provided by the system dialer
                    val callerInfo = if (title.isNotBlank()) title else "Unknown"
                    PhoneServerService.broadcastCallEvent(event = "incoming_call", number = callerInfo)
                }
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        sbn?.let {
            val packageName = it.packageName
            if (packageName.contains("dialer") || packageName.contains("telecom") || packageName.contains("incallui") || packageName.contains("phone")) {
                PhoneServerService.broadcastCallEvent(event = "call_ended", number = "")
            }
        }
    }
}