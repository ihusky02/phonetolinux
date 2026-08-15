package com.example.phonetolinux.service

import android.content.Context
import android.os.Build
import android.telephony.SmsManager

object SmsHandler {

    fun sendSms(context: Context, number: String, message: String): Boolean {
        if (number.isBlank() || message.isBlank()) return false
        return try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(number, null, parts, null, null)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}