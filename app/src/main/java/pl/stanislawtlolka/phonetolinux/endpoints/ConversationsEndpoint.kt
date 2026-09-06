package pl.stanislawtlolka.phonetolinux.endpoints

import android.content.Context
import pl.stanislawtlolka.phonetolinux.EndpointHandler
import pl.stanislawtlolka.phonetolinux.EndpointResponse
import pl.stanislawtlolka.phonetolinux.SmsHandler

/**
 * Endpoint plugin responsible for retrieving conversation summaries.
 */
class ConversationsEndpoint : EndpointHandler {
    override val path = "/conversations"

    override fun handle(requestLine: String, context: Context): EndpointResponse {
        val json = SmsHandler.fetchConversationsJson(context.contentResolver)
        return EndpointResponse(body = json)
    }
}