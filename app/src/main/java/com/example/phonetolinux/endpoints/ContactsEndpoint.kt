package com.example.phonetolinux

import android.content.Context

/**
 * Wtyczka odpowiedzialna za pobieranie listy kontaktów z telefonu.
 * Zwraca dane w formacie JSON dla aplikacji linuksowej.
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
                val name = if (nameIdx != -1) it.getString(nameIdx) ?: "Nieznany" else "Nieznany"
                val number = if (numberIdx != -1) it.getString(numberIdx) ?: "" else ""
                contactsList.add("{\"name\":\"$name\",\"phone\":\"$number\"}")
            }
        }
        return EndpointResponse(body = "[${contactsList.joinToString(",")}]")
    }
}