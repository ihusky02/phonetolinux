package pl.stanislawtlolka.phonetolinux

import pl.stanislawtlolka.phonetolinux.EndpointHandler
import pl.stanislawtlolka.phonetolinux.EndpointResponse
import android.content.Context
import pl.stanislawtlolka.phonetolinux.HttpUtils

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