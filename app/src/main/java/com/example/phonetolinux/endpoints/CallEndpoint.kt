package com.example.phonetolinux.endpoints

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.phonetolinux.EndpointHandler
import com.example.phonetolinux.EndpointResponse
import com.example.phonetolinux.HttpUtils

/**
 * Endpoint plugin responsible for managing voice calls.
 * Uses TelecomManager API to initiate, answer, and terminate calls directly,
 * bypassing Background Activity Launch (BAL) blocks on modern Android versions.
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
     * Answers an incoming call using TelecomManager.
     */
    private fun answerCall(context: Context): EndpointResponse {
        return try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            val hasAnswerPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ANSWER_PHONE_CALLS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasAnswerPermission) {
                Log.e(tag, "ANSWER_PHONE_CALLS permission not granted.")
                return EndpointResponse(
                    statusCode = "403 Forbidden",
                    body = "{\"success\":false,\"error\":\"ANSWER_PHONE_CALLS permission missing\"}"
                )
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && telecomManager != null) {
                @Suppress("MissingPermission")
                telecomManager.acceptRingingCall()
                Log.d(tag, "Incoming call accepted via TelecomManager.")
                EndpointResponse(statusCode = "200 OK", body = "{\"success\":true,\"action\":\"answered\"}")
            } else {
                EndpointResponse(
                    statusCode = "501 Not Implemented",
                    body = "{\"success\":false,\"error\":\"Requires Android 8.0+\"}"
                )
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to answer call: ${e.message}", e)
            EndpointResponse(
                statusCode = "500 Internal Server Error",
                body = "{\"success\":false,\"error\":\"${e.message}\"}"
            )
        }
    }

    /**
     * Resiliently terminates an active or ringing call using TelecomManager.
     */
    private fun endCall(context: Context): EndpointResponse {
        return try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            val hasAnswerPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ANSWER_PHONE_CALLS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasAnswerPermission) {
                Log.e(tag, "ANSWER_PHONE_CALLS permission not granted for ending call.")
                return EndpointResponse(
                    statusCode = "403 Forbidden",
                    body = "{\"success\":false,\"error\":\"ANSWER_PHONE_CALLS permission missing\"}"
                )
            }

            if (telecomManager != null) {
                @Suppress("MissingPermission")
                val success = telecomManager.endCall()
                Log.d(tag, "TelecomManager.endCall() result: $success")

                if (success) {
                    EndpointResponse(statusCode = "200 OK", body = "{\"success\":true,\"action\":\"ended\"}")
                } else {
                    EndpointResponse(
                        statusCode = "500 Internal Server Error",
                        body = "{\"success\":false,\"error\":\"TelecomManager returned false when ending call\"}"
                    )
                }
            } else {
                EndpointResponse(
                    statusCode = "500 Internal Server Error",
                    body = "{\"success\":false,\"error\":\"TelecomManager unavailable\"}"
                )
            }
        } catch (e: Exception) {
            Log.e(tag, "Error terminating call via TelecomManager: ${e.message}", e)
            EndpointResponse(
                statusCode = "500 Internal Server Error",
                body = "{\"success\":false,\"error\":\"${e.message}\"}"
            )
        }
    }
}