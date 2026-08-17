package com.example.phonetolinux.endpoints

import android.content.Context
import android.media.AudioManager
import android.util.Log

/**
 * Wtyczka zarządzająca przekierowaniem dźwięku (Zestaw głośnomówiący).
 * Używa protokołu Bluetooth SCO (Synchronous Connection-Oriented) do
 * wysłania dźwięku połączenia na połączony z telefonem komputer.
 */
class BluetoothAudioEndpoint : EndpointHandler {
    override val path = "/bluetooth_audio"

    override fun handle(requestLine: String, context: Context): EndpointResponse {
        val state = HttpUtils.extractQueryParam(requestLine, "state")

        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

            if (state == "off") {
                // Wyłączamy przekierowanie - dźwięk wraca do głośnika telefonu
                audioManager.stopBluetoothSco()
                audioManager.isBluetoothScoOn = false
                Log.d("BluetoothEndpoint", "Dźwięk Bluetooth SCO wyłączony")
                EndpointResponse(body = "{\"status\":\"success\", \"message\":\"Bluetooth audio disabled\"}")
            } else {
                // Włączamy przekierowanie (SCO) na sparowane urządzenie (komputer)
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                audioManager.startBluetoothSco()
                audioManager.isBluetoothScoOn = true
                Log.d("BluetoothEndpoint", "Dźwięk Bluetooth SCO włączony")
                EndpointResponse(body = "{\"status\":\"success\", \"message\":\"Bluetooth audio enabled\"}")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            EndpointResponse(body = "{\"status\":\"error\", \"message\":\"${e.message}\"}")
        }
    }
}