package com.example.phonetolinux

import android.content.Context

/**
 * Reprezentuje odpowiedź wygenerowaną przez wtyczkę (endpoint).
 */
data class EndpointResponse(val statusCode: String = "200 OK", val body: String)

/**
 * Wspólny interfejs dla wszystkich wtyczek (endpointów) serwera HTTP.
 * Każda nowa funkcjonalność serwera powinna implementować ten interfejs.
 */
interface EndpointHandler {
    /** Ścieżka URL, na którą reaguje ten endpoint (np. "/ping") */
    val path: String

    /** Główna funkcja wykonująca logikę endpointu */
    fun handle(requestLine: String, context: Context): EndpointResponse
}