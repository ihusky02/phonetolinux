package com.example.phonetolinux

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Main module for SMS operations.
 * Contains business logic for dispatching text messages via SmsManager
 * and querying the system database (ContentResolver) to retrieve chat history
 * and active conversation threads.
 */
object SmsHandler {
    private const val TAG = "SmsHandler"

    /**
     * Sends an SMS message to the specified recipient using the device's GSM module.
     */
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
            Log.d(TAG, "SMS sent to: $number")
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Fetches recent SMS conversation summaries from the Android database and formats them as a JSON array string.
     */
    fun fetchConversationsJson(contentResolver: ContentResolver): String {
        val jsonArray = JSONArray()
        try {
            // Query the SMS content provider sorted by date in descending order (most recent first)
            val cursor = contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE,
                    Telephony.Sms.READ
                ),
                null,
                null,
                "${Telephony.Sms.DATE} DESC"
            )

            val seenAddresses = HashSet<String>()

            cursor?.use {
                val addressIdx = it.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIdx = it.getColumnIndex(Telephony.Sms.BODY)
                val dateIdx = it.getColumnIndex(Telephony.Sms.DATE)
                val readIdx = it.getColumnIndex(Telephony.Sms.READ)

                while (it.moveToNext()) {
                    val address = if (addressIdx != -1) it.getString(addressIdx) ?: "" else ""

                    // Grouping: retain only the latest message for each unique phone address
                    if (address.isBlank() || seenAddresses.contains(address)) {
                        continue
                    }
                    seenAddresses.add(address)

                    val body = if (bodyIdx != -1) it.getString(bodyIdx) ?: "" else ""
                    val date = if (dateIdx != -1) it.getLong(dateIdx) else 0L
                    val read = if (readIdx != -1) it.getInt(readIdx) else 1

                    val convJson = JSONObject().apply {
                        put("number", address)
                        put("address", address) // Included for DTO backward compatibility
                        put("lastMessage", body)
                        put("date", date)
                        put("isRead", read == 1)
                    }
                    jsonArray.put(convJson)
                }
            }
            Log.d(TAG, "Fetched ${jsonArray.length()} conversation threads")
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return jsonArray.toString()
    }

    /**
     * Fetches the full chat history for a specific recipient/number.
     */
    fun fetchChatHistoryJson(contentResolver: ContentResolver, queryParam: String): String {
        val messages = mutableListOf<String>()
        try {
            // If queryParam contains letters (e.g., contact display name), resolve it to a phone number
            var searchNumber = queryParam
            if (queryParam.any { char -> char.isLetter() }) {
                searchNumber = getPhoneNumberByName(contentResolver, queryParam)
                Log.d(TAG, "Resolved name '$queryParam' to number: '$searchNumber'")
            }

            // Extract numeric digits (normalizing phone format to last 9 digits)
            val cleanQueryNumber = searchNumber.replace(Regex("[^0-9]"), "").takeLast(9)
            Log.d(TAG, "Fetching history for: '$queryParam' -> normalized: '$cleanQueryNumber'")

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

                    // Compare normalized phone suffixes (resilient to formatting differences, country codes, spaces)
                    if (cleanQueryNumber.isNotEmpty() && cleanAddress.isNotEmpty() &&
                        (cleanAddress == cleanQueryNumber || cleanAddress.endsWith(cleanQueryNumber) || cleanQueryNumber.endsWith(cleanAddress))) {

                        val rawBody = if (bodyIdx != -1) it.getString(bodyIdx) ?: "" else ""

                        // Sanitize newline characters (0x0A) and escape unescaped double quotes inside JSON
                        val body = rawBody
                            .replace("\\", "\\\\")
                            .replace("\"", "\\\"")
                            .replace("\r", "")
                            .replace("\n", "\\n")

                        val type = if (typeIdx != -1) it.getInt(typeIdx) else 1

                        // Type 2 indicates an outgoing message; all other values (e.g., Type 1) are incoming
                        val isOutgoing = (type == 2)

                        messages.add("{\"text\":\"$body\",\"isOutgoing\":$isOutgoing}")
                    }
                }
                Log.d(TAG, "Matched messages count in database: ${messages.size}")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "[${messages.joinToString(",")}]"
    }

    /**
     * Helper method to map a contact name to its corresponding primary phone number.
     */
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