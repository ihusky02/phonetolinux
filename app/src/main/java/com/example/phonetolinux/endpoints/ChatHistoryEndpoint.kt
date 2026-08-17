package com.example.phonetolinux.endpoints

import android.content.Context
import com.example.phonetolinux.SmsHandler

/**
 * Wtyczka pobierająca historię konkretnego czatu.
 */
class ChatHistoryEndpoint : EndpointHandler {
    override val path = "/chathistory"

    override fun handle(requestLine: String, context: Context): EndpointResponse {
        val number = HttpUtils.extractQueryParam(requestLine, "number")
        val json = SmsHandler.fetchChatHistoryJson(context.contentResolver, number)
        return EndpointResponse(body = json)
    }
}