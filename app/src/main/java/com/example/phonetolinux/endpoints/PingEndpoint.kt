package com.example.phonetolinux

import com.example.phonetolinux.EndpointHandler
import com.example.phonetolinux.EndpointResponse
import android.content.Context
import com.example.phonetolinux.HttpUtils

/**
 * Test ping endpoint plugin.
 * Responds to the /ping request, allowing the desktop application
 * to verify whether the phone is reachable on the network.
 */
class PingEndpoint : EndpointHandler {
    override val path = "/ping"

    override fun handle(requestLine: String, context: Context): EndpointResponse {
        return EndpointResponse(body = "phonetolinux-server-found")
    }
}