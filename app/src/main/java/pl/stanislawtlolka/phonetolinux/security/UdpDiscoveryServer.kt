package pl.stanislawtlolka.phonetolinux.security

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import android.util.Log

class UdpDiscoveryServer(private val pairingSecret: String) {

    private var socket: DatagramSocket? = null
    private var isRunning = false

    fun start(port: Int = 8889) {
        if (isRunning) return
        isRunning = true

        Thread {
            try {
                socket = DatagramSocket(port)
                val buffer = ByteArray(1024)
                Log.d("UDP_DISCOVERY", "UDP Discovery Listener started on port $port")

                while (isRunning) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket?.receive(packet) // Blocking call

                    val receivedMessage = String(packet.data, 0, packet.length).trim()

                    // Validate message prefix
                    if (receivedMessage.startsWith("DISCOVER_PHONETOLINUX_REQUEST")) {
                        handleDiscoveryRequest(packet, receivedMessage)
                    }
                }
            } catch (e: Exception) {
                if (isRunning) {
                    Log.e("UDP_DISCOVERY", "Error in UDP Discovery Listener: ${e.message}")
                }
            }
        }.start()
    }

    private fun handleDiscoveryRequest(packet: DatagramPacket, message: String) {
        try {
            // Expected format: DISCOVER_PHONETOLINUX_REQUEST:<NONCE>
            val parts = message.split(":")
            val nonce = if (parts.size > 1) parts[1] else ""

            // Calculate HMAC-SHA256 signature using shared pairing secret
            val signature = generateHmacSha256(nonce, pairingSecret)

            // Response format: PHONETOLINUX_RESPONSE:<SIGNATURE>
            val responseText = "PHONETOLINUX_RESPONSE:$signature"
            val responseData = responseText.toByteArray(Charsets.UTF_8)

            val responsePacket = DatagramPacket(
                responseData,
                responseData.size,
                packet.address,
                packet.port
            )

            socket?.send(responsePacket)
            Log.d("UDP_DISCOVERY", "Responded to discovery request from ${packet.address.hostAddress}")
        } catch (e: Exception) {
            Log.e("UDP_DISCOVERY", "Failed to process discovery request: ${e.message}")
        }
    }

    private fun generateHmacSha256(data: String, key: String): String {
        val secretKeySpec = SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256")
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(secretKeySpec)
        val hmacBytes = mac.doFinal(data.toByteArray(Charsets.UTF_8))

        return hmacBytes.joinToString("") { "%02x".format(it) }
    }

    fun stop() {
        isRunning = false
        socket?.close()
        socket = null
        Log.d("UDP_DISCOVERY", "UDP Discovery Listener stopped")
    }
}