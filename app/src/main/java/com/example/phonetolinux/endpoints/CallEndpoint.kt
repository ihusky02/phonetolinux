package com.example.phonetolinux.endpoints

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telecom.TelecomManager
import android.util.Log
import com.example.phonetolinux.EndpointHandler
import com.example.phonetolinux.EndpointResponse
import com.example.phonetolinux.HttpUtils

/**
 * Endpoint plugin responsible for managing voice calls (initiating, answering, and terminating/rejecting calls).
 */
class CallEndpoint : EndpointHandler {
    override val path = "/call"
    private val tag = "CallEndpoint"

    override fun handle(requestLine: String, context: Context): EndpointResponse {
        Log.d(tag, "Received call request line: $requestLine")

        val number = HttpUtils.extractQueryParam(requestLine, "number")
        val action = HttpUtils.extractQueryParam(requestLine, "action")

        return when {
            action.equals("answer", ignoreCase = true) -> answerCall(context)
            action.equals("end", ignoreCase = true) || action.equals("reject", ignoreCase = true) -> endCall(context)
            else -> makeCall(context, number)
        }
    }

    /**
     * Initiates an outgoing voice call to the specified phone number.
     */
    private fun makeCall(context: Context, number: String): EndpointResponse {
        if (number.isBlank()) {
            return EndpointResponse(
                statusCode = "400 Bad Request",
                body = "{\"success\":false,\"error\":\"Missing phone number\"}"
            )
        }

        return try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:${Uri.encode(number)}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(tag, "Successfully triggered outgoing call to: $number")
            EndpointResponse(statusCode = "200 OK", body = "{\"success\":true,\"action\":\"dialing\"}")
        } catch (e: SecurityException) {
            Log.e(tag, "Missing CALL_PHONE permission: ${e.message}")
            EndpointResponse(
                statusCode = "403 Forbidden",
                body = "{\"success\":false,\"error\":\"Missing CALL_PHONE permission\"}"
            )
        } catch (e: Exception) {
            Log.e(tag, "Failed to initiate call: ${e.message}", e)
            EndpointResponse(
                statusCode = "500 Internal Server Error",
                body = "{\"success\":false,\"error\":\"${e.message}\"}"
            )
        }
    }

    /**
     * Answers an incoming voice call using TelecomManager.
     */
    private fun answerCall(context: Context): EndpointResponse {
        return try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                @Suppress("MissingPermission")
                telecomManager?.acceptRingingCall()
                Log.d(tag, "Incoming call accepted successfully.")
                EndpointResponse(statusCode = "200 OK", body = "{\"success\":true,\"action\":\"answered\"}")
            } else {
                EndpointResponse(
                    statusCode = "501 Not Implemented",
                    body = "{\"success\":false,\"error\":\"Answering calls requires Android 8.0 or higher\"}"
                )
            }
        } catch (e: SecurityException) {
            Log.e(tag, "Missing ANSWER_PHONE_CALLS permission: ${e.message}")
            EndpointResponse(
                statusCode = "403 Forbidden",
                body = "{\"success\":false,\"error\":\"Missing ANSWER_PHONE_CALLS permission\"}"
            )
        } catch (e: Exception) {
            Log.e(tag, "Failed to answer call: ${e.message}", e)
            EndpointResponse(
                statusCode = "500 Internal Server Error",
                body = "{\"success\":false,\"error\":\"${e.message}\"}"
            )
        }
    }

    /**
     * Terminates an active call or rejects an incoming ringing call.
     */
    private fun endCall(context: Context): EndpointResponse {
        return try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            @Suppress("MissingPermission")
            val success = telecomManager?.endCall() ?: false

            if (success) {
                Log.d(tag, "Call ended or rejected successfully.")
                EndpointResponse(statusCode = "200 OK", body = "{\"success\":true,\"action\":\"ended\"}")
            } else {
                EndpointResponse(
                    statusCode = "500 Internal Server Error",
                    body = "{\"success\":false,\"error\":\"Failed to end call via TelecomManager\"}"
                )
            }
        } catch (e: Exception) {
            Log.e(tag, "Error terminating call: ${e.message}", e)
            EndpointResponse(
                statusCode = "500 Internal Server Error",
                body = "{\"success\":false,\"error\":\"${e.message}\"}"
            )
        }
    }
}