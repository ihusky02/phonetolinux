package com.example.phonetolinux.endpoints

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.phonetolinux.CallBridgeActivity
import com.example.phonetolinux.EndpointHandler
import com.example.phonetolinux.EndpointResponse
import com.example.phonetolinux.HttpUtils

/**
 * Endpoint plugin responsible for managing voice calls.
 * Explicitly grants BAL (Background Activity Launch) permissions on Android 14/15 via PendingIntent.
 */
class CallEndpoint : EndpointHandler {
    override val path = "/call"
    private val tag = "CallEndpoint"

    override fun handle(requestLine: String, context: Context): EndpointResponse {
        Log.d(tag, "Received call request line: $requestLine")

        val number = HttpUtils.extractQueryParam(requestLine, "number")

        if (number.isBlank()) {
            return EndpointResponse(
                statusCode = "400 Bad Request",
                body = "{\"success\":false,\"error\":\"Missing phone number\"}"
            )
        }

        return makeCallDirect(context, number)
    }

    /**
     * Bypasses Android 14/15 BAL restrictions using explicit ActivityOptions exemptions.
     */
    private fun makeCallDirect(context: Context, number: String): EndpointResponse {
        return try {
            val bridgeIntent = Intent(context, CallBridgeActivity::class.java).apply {
                putExtra("EXTRA_PHONE_NUMBER", number)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                System.currentTimeMillis().toInt(),
                bridgeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Grant explicit BAL permission for PendingIntent execution on Android 14/15 (API 34+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val options = ActivityOptions.makeBasic().apply {
                    pendingIntentBackgroundActivityStartMode = ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                }
                pendingIntent.send(context, 0, null, null, null, null, options.toBundle())
            } else {
                pendingIntent.send()
            }

            Log.d(tag, "Successfully dispatched direct pending call intent to: $number")
            EndpointResponse(statusCode = "200 OK", body = "{\"success\":true,\"action\":\"dialing\"}")
        } catch (e: Exception) {
            Log.e(tag, "Error triggering direct call: ${e.message}", e)
            EndpointResponse(
                statusCode = "500 Internal Server Error",
                body = "{\"success\":false,\"error\":\"${e.message}\"}"
            )
        }
    }
}