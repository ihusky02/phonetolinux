package com.example.phonetolinux.endpoints

import android.content.Context
import android.util.Log
import com.example.phonetolinux.EndpointHandler
import com.example.phonetolinux.EndpointResponse
import com.example.phonetolinux.HttpUtils
import com.example.phonetolinux.SmsHandler

/**
 * Endpoint plugin responsible for deleting conversation threads and messages via HTTP DELETE requests.
 */
class DeleteConversationEndpoint : EndpointHandler {
    override val path = "/delete_conversation"
    private val tag = "DeleteEndpoint"

    override fun handle(requestLine: String, context: Context): EndpointResponse {
        Log.d(tag, "Received requestLine: $requestLine")

        // Ensure the request method is DELETE
        if (!requestLine.startsWith("DELETE")) {
            Log.w(tag, "Rejected non-DELETE method in delete endpoint.")
            return EndpointResponse(statusCode = "405 Method Not Allowed", body = "{\"success\":false,\"error\":\"Method Not Allowed\"}")
        }

        // Extract the target phone number or address query parameter
        val address = HttpUtils.extractQueryParam(requestLine, "address")
        Log.d(tag, "Extracted address parameter for deletion: '$address'")

        if (address.isBlank()) {
            Log.w(tag, "Missing address parameter in DELETE request.")
            return EndpointResponse(statusCode = "400 Bad Request", body = "{\"success\":false,\"error\":\"Missing address parameter\"}")
        }

        // Execute physical deletion via SmsHandler matching the address/number
        val success = SmsHandler.deleteConversationByAddress(context.contentResolver, address)
        Log.d(tag, "Deletion result for '$address': $success")

        return if (success) {
            EndpointResponse(statusCode = "200 OK", body = "{\"success\":true}")
        } else {
            EndpointResponse(statusCode = "500 Internal Server Error", body = "{\"success\":false,\"error\":\"Failed to delete messages\"}")
        }
    }
}