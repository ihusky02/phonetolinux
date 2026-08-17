package com.example.phonetolinux

import android.content.Context

/**
 * Wtyczka testowa.
 * Odpowiada na żądanie /ping, pozwalając aplikacji desktopowej
 * sprawdzić, czy telefon jest dostępny w sieci.
 */
class PingEndpoint : EndpointHandler {
    override val path = "/ping"

    override fun handle(requestLine: String, context: Context): EndpointResponse {
        return EndpointResponse(body = "phonetolinux-server-found")
    }
}