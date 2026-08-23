package com.example.phonetolinux.endpoints

import android.content.Context
import com.example.phonetolinux.EndpointHandler
import com.example.phonetolinux.EndpointResponse
import com.example.phonetolinux.HttpUtils

/**
 * Endpoint plugin responsible for retrieving conversation summaries.
 */
class ConversationsEndpoint : EndpointHandler {
    override val path = "/conversations"

    override fun handle(requestLine: String, context: Context): EndpointResponse {
        return EndpointResponse(body = "[]")
    }
}