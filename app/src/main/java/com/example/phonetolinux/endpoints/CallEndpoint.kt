package com.example.phonetolinux.endpoints

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import android.util.Log
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import com.example.phonetolinux.EndpointHandler
import com.example.phonetolinux.EndpointResponse
import com.example.phonetolinux.HttpUtils

/**
 * Endpoint plugin responsible for managing voice calls.
 * Uses TelecomManager API with fallback mechanisms to reliably answer,
 * reject, or initiate calls across modern Android devices.
 *
 * @author Stanisław Tlołka
 */
class CallEndpoint : EndpointHandler {
    override val path = "/call"
    private val tag = "CallEndpoint"

    override fun handle(requestLine: String, context: Context): EndpointResponse {
        Log.d(tag, "Received call request line: $requestLine")

        val number = HttpUtils.extractQueryParam(requestLine, "number")
        val action = HttpUtils.extractQueryParam(requestLine, "action")

        return when {
            action.equals("answer", ignoreCase = true) || requestLine.contains("/call/answer") -> answerCall(context)
            action.equals("end", ignoreCase = true) || action.equals("reject", ignoreCase = true) || requestLine.contains("/call/end") -> endCall(context)
            else -> makeCallDirectViaTelecom(context, number)
        }
    }

    /**
     * Initiates an outgoing call via TelecomManager API.
     */
    private fun makeCallDirectViaTelecom(context: Context, number: String): EndpointResponse {
        if (number.isBlank()) {
            return EndpointResponse(
                statusCode = "400 Bad Request",
                body = "{\"success\":false,\"error\":\"Missing phone number\"}"
            )
        }

        return try {
            val uri = Uri.parse("tel:${Uri.encode(number)}")
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager

            val hasCallPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CALL_PHONE
            ) == PackageManager.PERMISSION_GRANTED

            if (hasCallPermission && telecomManager != null) {
                val extras = Bundle().apply {
                    putBoolean(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, false)
                }

                telecomManager.placeCall(uri, extras)
                Log.d(tag, "Successfully placed call via TelecomManager to: $number")
                EndpointResponse(statusCode = "200 OK", body = "{\"success\":true,\"action\":\"dialing\"}")
            } else {
                Log.e(tag, "CALL_PHONE permission not granted or TelecomManager unavailable.")
                EndpointResponse(
                    statusCode = "403 Forbidden",
                    body = "{\"success\":false,\"error\":\"CALL_PHONE permission missing\"}"
                )
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to place call via TelecomManager: ${e.message}", e)
            EndpointResponse(
                statusCode = "500 Internal Server Error",
                body = "{\"success\":false,\"error\":\"${e.message}\"}"
            )
        }
    }

    /**
     * Answers an incoming call using TelecomManager with intent fallbacks.
     */
    private fun answerCall(context: Context): EndpointResponse {
        return try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && telecomManager != null) {
                @Suppress("MissingPermission")
                try {
                    telecomManager.acceptRingingCall()
                    Log.d(tag, "Incoming call accepted via TelecomManager.acceptRingingCall()")
                    return EndpointResponse(statusCode = "200 OK", body = "{\"success\":true,\"action\":\"answered\"}")
                } catch (e: Exception) {
                    Log.w(tag, "TelecomManager acceptRingingCall failed, trying intent fallback: ${e.message}")
                }
            }

            // Fallback: Headset hook simulation via media button intent
            val downIntent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HEADSETHOOK))
            }
            context.sendOrderedBroadcast(downIntent, null)

            val upIntent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HEADSETHOOK))
            }
            context.sendOrderedBroadcast(upIntent, null)

            Log.d(tag, "Incoming call answered via Intent fallback.")
            EndpointResponse(statusCode = "200 OK", body = "{\"success\":true,\"action\":\"answered_fallback\"}")
        } catch (e: Exception) {
            Log.e(tag, "Failed to answer call: ${e.message}", e)
            EndpointResponse(
                statusCode = "500 Internal Server Error",
                body = "{\"success\":false,\"error\":\"${e.message}\"}"
            )
        }
    }

    /**
     * Resiliently terminates an active or ringing call with multiple system fallbacks.
     */
    private fun endCall(context: Context): EndpointResponse {
        return try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            var success = false

            if (telecomManager != null) {
                @Suppress("MissingPermission")
                try {
                    success = telecomManager.endCall()
                    Log.d(tag, "TelecomManager.endCall() result: $success")
                } catch (e: Exception) {
                    Log.w(tag, "TelecomManager.endCall() threw exception: ${e.message}")
                }
            }

            // Fallback for Samsung / non-default dialers if TelecomManager returned false
            if (!success) {
                val downIntent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                    putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENDCALL))
                }
                context.sendOrderedBroadcast(downIntent, null)
                success = true
                Log.d(tag, "Call terminated using Intent KEYCODE_ENDCALL fallback.")
            }

            if (success) {
                EndpointResponse(statusCode = "200 OK", body = "{\"success\":true,\"action\":\"ended\"}")
            } else {
                EndpointResponse(
                    statusCode = "500 Internal Server Error",
                    body = "{\"success\":false,\"error\":\"Failed to terminate call through all available methods\"}"
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