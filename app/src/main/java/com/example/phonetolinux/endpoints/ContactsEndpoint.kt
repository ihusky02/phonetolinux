package com.example.phonetolinux

import android.content.Context
import com.example.phonetolinux.EndpointHandler
import com.example.phonetolinux.EndpointResponse
import com.example.phonetolinux.HttpUtils

/**
 * Endpoint plugin responsible for fetching the device contacts list.
 * Returns the data in JSON format for the Linux desktop application.
 */
class ContactsEndpoint : EndpointHandler {
    override val path = "/contacts"

    override fun handle(requestLine: String, context: Context): EndpointResponse {
        val contactsList = mutableListOf<String>()
        val cursor = context.contentResolver.query(
            android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            null, null, null,
            android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )
        cursor?.use {
            val nameIdx = it.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIdx = it.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext()) {
                val name = if (nameIdx != -1) it.getString(nameIdx) ?: "Unknown" else "Unknown"
                val number = if (numberIdx != -1) it.getString(numberIdx) ?: "" else ""
                contactsList.add("{\"name\":\"$name\",\"phone\":\"$number\"}")
            }
        }
        return EndpointResponse(body = "[${contactsList.joinToString(",")}]")
    }
}