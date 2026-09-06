package pl.stanislawtlolka.phonetolinux.endpoints

import android.content.Context
import pl.stanislawtlolka.phonetolinux.EndpointHandler
import pl.stanislawtlolka.phonetolinux.EndpointResponse
import pl.stanislawtlolka.phonetolinux.HttpUtils
import pl.stanislawtlolka.phonetolinux.SmsHandler

/**
 * Endpoint plugin responsible for retrieving message history for a specific address/number.
 */
class MessagesEndpoint : EndpointHandler {
    override val path = "/messages"

    override fun handle(requestLine: String, context: Context): EndpointResponse {
        // Extract the 'address' query parameter from the HTTP request line
        val address = HttpUtils.extractQueryParam(requestLine, "address")

        if (address.isBlank()) {
            return EndpointResponse(statusCode = "400 Bad Request", body = "[]")
        }

        // Fetch messages matching this address/sender from ContentResolver using fetchChatHistoryJson
        val json = SmsHandler.fetchChatHistoryJson(context.contentResolver, address)

        return EndpointResponse(statusCode = "200 OK", body = json)
    }
}