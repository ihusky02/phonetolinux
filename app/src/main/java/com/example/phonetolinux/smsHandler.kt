package com.example.phonetolinux

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.util.Log

object SmsHandler {
    private const val TAG = "SmsHandler"

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
            Log.d(TAG, "Wysłano SMS do: $number")
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun fetchChatHistoryJson(contentResolver: ContentResolver, queryParam: String): String {
        val messages = mutableListOf<String>()
        try {
            // Jeśli queryParam zawiera litery (np. nazwa kontaktu), próbujemy zamienić ją na numer z kontaktów
            var searchNumber = queryParam
            if (queryParam.any { char -> char.isLetter() }) {
                searchNumber = getPhoneNumberByName(contentResolver, queryParam)
                Log.d(TAG, "Zamieniono nazwę '$queryParam' na numer: '$searchNumber'")
            }

            // Zostawiamy same cyfry ze szukanego numeru (np. ostatnie 9 cyfr)
            val cleanQueryNumber = searchNumber.replace(Regex("[^0-9]"), "").takeLast(9)
            Log.d(TAG, "Szukam historii dla wejściowego numeru: '$queryParam' -> oczyszczony: '$cleanQueryNumber'")

            val uri = Uri.parse("content://sms/")
            val cursor = contentResolver.query(
                uri,
                null,
                null,
                null,
                "date ASC"
            )

            cursor?.use {
                val bodyIdx = it.getColumnIndex("body")
                val typeIdx = it.getColumnIndex("type")
                val addressIdx = it.getColumnIndex("address")

                while (it.moveToNext()) {
                    val address = if (addressIdx != -1) it.getString(addressIdx) ?: "" else ""
                    val cleanAddress = address.replace(Regex("[^0-9]"), "").takeLast(9)

                    // Porównujemy końcówki numerów (odporne na +48, spacje i formatowanie)
                    if (cleanQueryNumber.isNotEmpty() && cleanAddress.isNotEmpty() &&
                        (cleanAddress == cleanQueryNumber || cleanAddress.endsWith(cleanQueryNumber) || cleanQueryNumber.endsWith(cleanAddress))) {

                        val rawBody = if (bodyIdx != -1) it.getString(bodyIdx) ?: "" else ""

                        // Zabezpieczenie przed znakami nowej linii (0x0A) oraz cudzysłowami wewnątrz JSON-a
                        val body = rawBody
                            .replace("\\", "\\\\")
                            .replace("\"", "\\\"")
                            .replace("\r", "")
                            .replace("\n", "\\n")

                        val type = if (typeIdx != -1) it.getInt(typeIdx) else 1

                        // type == 2 to wiadomość wychodząca, wszystko inne (np. type == 1) to przychodząca
                        val isOutgoing = (type == 2)

                        messages.add("{\"text\":\"$body\",\"isOutgoing\":$isOutgoing}")
                    }
                }
                Log.d(TAG, "Znaleziono dopasowanych wiadomości w bazie (przychodzące i wychodzące): ${messages.size}")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "[${messages.joinToString(",")}]"
    }

    private fun getPhoneNumberByName(contentResolver: ContentResolver, name: String): String {
        try {
            val uri = android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                val nameIdx = it.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIdx = it.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (it.moveToNext()) {
                    val contactName = if (nameIdx != -1) it.getString(nameIdx) else ""
                    if (contactName.equals(name.trim(), ignoreCase = true)) {
                        return if (numberIdx != -1) it.getString(numberIdx) ?: "" else ""
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return name
    }
}