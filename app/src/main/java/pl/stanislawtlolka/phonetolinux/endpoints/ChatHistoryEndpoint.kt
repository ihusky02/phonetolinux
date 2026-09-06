package pl.stanislawtlolka.phonetolinux.endpoints

import android.content.Context
import pl.stanislawtlolka.phonetolinux.EndpointHandler
import pl.stanislawtlolka.phonetolinux.EndpointResponse
import pl.stanislawtlolka.phonetolinux.SmsHandler
import pl.stanislawtlolka.phonetolinux.HttpUtils

/**
 * Endpoint plugin that retrieves the chat history for a specific contact.
 */
class ChatHistoryEndpoint : EndpointHandler {
    override val path = "/chathistory"

    override fun handle(requestLine: String, context: Context): EndpointResponse {
        val number = HttpUtils.extractQueryParam(requestLine, "number")
        val json = SmsHandler.fetchChatHistoryJson(context.contentResolver, number)
        return EndpointResponse(body = json)
    }
}