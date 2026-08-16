package com.example.phonetolinux

import android.content.Context
import android.telephony.SmsManager
import android.util.Log

object SmsHandler {
    private const val TAG = "SmsHandler"

    fun sendSms(context: Context, number: String, message: String): Boolean {
        return try {
            if (number.isBlank() || message.isBlank()) return false
            val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            smsManager.sendTextMessage(number, null, message, null, null)
            Log.d(TAG, "Wysłano SMS do: $number")
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // Bezpieczne wyłuskanie numeru (poprawka dla sklejonych parametrów typu number=...&name=...)
    fun parseQueryNumber(rawQueryParam: String): String {
        if (rawQueryParam.isBlank()) return ""
        return rawQueryParam.split("&").first()
    }
}