package com.example.phonetolinux.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Notification listener service for Android.
 * Captures incoming SMS messages and active phone call notifications directly from the status bar
 * and broadcasts them in real-time to the connected Linux client via the SSE stream.
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

            // Forward incoming call notifications
            if (packageName.contains("dialer") || packageName.contains("telecom") || packageName.contains("incallui")) {
                PhoneServerService.broadcastCallEvent(event = "incoming_call", number = title)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        sbn?.let {
            val packageName = it.packageName
            if (packageName.contains("dialer") || packageName.contains("telecom") || packageName.contains("incallui")) {
                PhoneServerService.broadcastCallEvent(event = "call_ended", number = "")
            }
        }
    }
}