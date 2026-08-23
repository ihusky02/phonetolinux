package com.example.phonetolinux.endpoints

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.example.phonetolinux.EndpointHandler
import com.example.phonetolinux.EndpointResponse

/**
 * Plugin responsible for managing audio routing (Hands-Free profile).
 * Uses the Bluetooth SCO (Synchronous Connection-Oriented) protocol to
 * route call audio to the computer connected to the phone.
 */
class BluetoothAudioEndpoint : EndpointHandler {
    override val path = "/bluetooth_audio"

    override fun handle(requestLine: String, context: Context): EndpointResponse {
        val state = HttpUtils.extractQueryParam(requestLine, "state")

        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

            if (state == "off") {
                // Disable routing - audio returns to the phone's speaker
                audioManager.stopBluetoothSco()
                audioManager.isBluetoothScoOn = false
                Log.d("BluetoothEndpoint", "Bluetooth SCO audio disabled")
                EndpointResponse(body = "{\"status\":\"success\", \"message\":\"Bluetooth audio disabled\"}")
            } else {
                // Enable routing (SCO) to the paired device (computer)
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                audioManager.startBluetoothSco()
                audioManager.isBluetoothScoOn = true
                Log.d("BluetoothEndpoint", "Bluetooth SCO audio enabled")
                EndpointResponse(body = "{\"status\":\"success\", \"message\":\"Bluetooth audio enabled\"}")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            EndpointResponse(body = "{\"status\":\"error\", \"message\":\"${e.message}\"}")
        }
    }
}