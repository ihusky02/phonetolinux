package com.example.phonetolinux.endpoints

import android.content.Context
import com.example.phonetolinux.SmsHandler

/**
 * Wtyczka wysyłająca SMS przez wbudowany SmsManager.
 */
class SendSmsEndpoint : EndpointHandler {
    override val path = "/send_sms"

    override fun handle(requestLine: String, context: Context): EndpointResponse {
        val number = HttpUtils.extractQueryParam(requestLine, "number")
        val message = HttpUtils.extractQueryParam(requestLine, "message")
        val success = SmsHandler.sendSms(context, number, message)
        return EndpointResponse(body = if (success) "{\"status\":\"success\"}" else "{\"status\":\"error\"}")
    }
}