package com.example.phonetolinux

import java.net.URLDecoder

/**
 * Helper utilities for processing HTTP requests.
 * Extracts parameters from URL query strings (e.g., ?number=123&message=test).
 */
object HttpUtils {
    fun extractQueryParam(requestLine: String, paramName: String): String {
        try {
            val parts = requestLine.split(" ")
            if (parts.size < 2) return ""
            val pathAndQuery = parts[1]
            val queryParts = pathAndQuery.split("?")
            if (queryParts.size < 2) return ""

            val query = queryParts[1]
            val params = query.split("&")
            for (param in params) {
                val keyValue = param.split("=")
                if (keyValue.size == 2 && keyValue[0] == paramName) {
                    return URLDecoder.decode(keyValue[1], "UTF-8")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ""
    }
}