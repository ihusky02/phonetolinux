package pl.stanislawtlolka.phonetolinux.endpoints

import pl.stanislawtlolka.phonetolinux.EndpointHandler
import pl.stanislawtlolka.phonetolinux.EndpointResponse
import android.content.Context
import pl.stanislawtlolka.phonetolinux.SmsHandler
import pl.stanislawtlolka.phonetolinux.HttpUtils

/**
 * Endpoint plugin responsible for sending SMS messages via the built-in SmsManager.
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