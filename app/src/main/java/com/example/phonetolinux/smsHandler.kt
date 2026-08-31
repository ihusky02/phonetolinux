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
 * Contains business logic for dispatching text messages via SmsManager,
 * querying the system database (ContentResolver) to retrieve chat history
 * and active conversation threads, and deleting messages upon desktop request.
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
            var searchNumber = queryParam
            if (queryParam.any { char -> char.isLetter() }) {
                searchNumber = getPhoneNumberByName(contentResolver, queryParam)
                Log.d(TAG, "Resolved name '$queryParam' to number: '$searchNumber'")
            }

            Log.d(TAG, "Fetching history for queryParam: '$queryParam'")

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

                    if (isAddressMatching(address, queryParam)) {
                        val rawBody = if (bodyIdx != -1) it.getString(bodyIdx) ?: "" else ""

                        // Sanitize newline characters and escape unescaped double quotes inside JSON
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
     * Deletes SMS conversation messages matching a specific recipient, phone number, or contact name
     * using the unified isAddressMatching logic and batch selection deletion.
     */
    fun deleteConversationByAddress(contentResolver: ContentResolver, queryParam: String): Boolean {
        try {
            Log.d(TAG, "Deleting messages for queryParam: '$queryParam'")

            val uri = Uri.parse("content://sms/")
            val cursor = contentResolver.query(
                uri,
                null,
                null,
                null,
                null
            )

            val idsToDelete = mutableListOf<String>()
            cursor?.use {
                val idIdx = it.getColumnIndex(Telephony.Sms._ID)
                val addressIdx = it.getColumnIndex("address")

                while (it.moveToNext()) {
                    val address = if (addressIdx != -1) it.getString(addressIdx) ?: "" else ""

                    if (isAddressMatching(address, queryParam)) {
                        val id = if (idIdx != -1) it.getString(idIdx) else continue
                        idsToDelete.add(id)
                    }
                }
            }

            Log.d(TAG, "Found ${idsToDelete.size} message IDs to delete for: $queryParam")

            if (idsToDelete.isEmpty()) {
                Log.d(TAG, "No messages found to delete for: $queryParam")
                return false
            }

            var deletedCount = 0
            val inClause = idsToDelete.joinToString(",")
            val selection = "${Telephony.Sms._ID} IN ($inClause)"

            try {
                // Batch deletion using selection clause
                deletedCount = contentResolver.delete(uri, selection, null)
            } catch (ex: Exception) {
                Log.w(TAG, "Batch deletion failed, falling back to individual deletes: ${ex.message}")
                for (id in idsToDelete) {
                    val singleUri = Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, id)
                    val deleted = contentResolver.delete(singleUri, null, null)
                    if (deleted > 0) deletedCount++
                }
            }

            Log.d(TAG, "Successfully deleted $deletedCount messages for: $queryParam")
            return deletedCount > 0
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e(TAG, "Error deleting messages: ${e.message}", e)
            return false
        }
    }

    /**
     * Universal matching helper handling standard numbers, short numbers, and alphanumeric sender IDs.
     */
    private fun isAddressMatching(dbAddress: String, query: String): Boolean {
        if (dbAddress.isBlank() || query.isBlank()) return false

        val trimmedDb = dbAddress.trim()
        val trimmedQuery = query.trim()

        // 1. Exact match (case-insensitive for alphanumeric senders like PLAY, LeroyMerlin, TFI Allianz)
        if (trimmedDb.equals(trimmedQuery, ignoreCase = true)) {
            return true
        }

        // 2. Substring containment for text identifiers (promotional/lottery names)
        if (trimmedDb.contains(trimmedQuery, ignoreCase = true) || trimmedQuery.contains(trimmedDb, ignoreCase = true)) {
            return true
        }

        // 3. Numeric comparison for phone numbers and short/special service numbers
        val dbDigits = trimmedDb.replace(Regex("[^0-9]"), "")
        val queryDigits = trimmedQuery.replace(Regex("[^0-9]"), "")

        if (dbDigits.isNotEmpty() && queryDigits.isNotEmpty()) {
            if (dbDigits == queryDigits) {
                return true
            }
            val takeCount = minOf(9, minOf(dbDigits.length, queryDigits.length))
            val subDb = dbDigits.takeLast(takeCount)
            val subQuery = queryDigits.takeLast(takeCount)

            if (subDb == subQuery || subDb.endsWith(subQuery) || subQuery.endsWith(subDb)) {
                return true
            }
        }

        return false
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