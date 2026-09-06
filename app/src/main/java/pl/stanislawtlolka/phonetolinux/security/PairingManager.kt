package com.phonetolinux.security

import java.net.NetworkInterface
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import android.net.Uri

/**
 * Handles QR code parsing, hardware identifier extraction,
 * and symmetric AES-256 key derivation matching the Linux desktop application.
 */
class PairingManager {

    data class DesktopPairingInfo(
        val ipAddress: String,
        val port: Int,
        val desktopMac: String
    )

    /**
     * Parses the scanned QR code URI string (phonetolinux://pair?ip=...&port=...&mac=...)
     */
    fun parseQrCodePayload(qrContent: String): DesktopPairingInfo? {
        return try {
            val uri = Uri.parse(qrContent)
            if (uri.scheme != "phonetolinux" || uri.host != "pair") return null

            val ip = uri.getQueryParameter("ip") ?: return null
            val port = uri.getQueryParameter("port")?.toIntOrNull() ?: return null
            val mac = uri.getQueryParameter("mac") ?: return null

            DesktopPairingInfo(ipAddress = ip, port = port, desktopMac = mac)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Derives the exact same 256-bit AES master key as the C# desktop app.
     * Combines desktop and Android MAC addresses with SHA-256 and the shared salt.
     */
    fun deriveAesKey(desktopMac: String, androidMac: String): ByteArray {
        val normalizedDesktop = desktopMac.replace(":", "").uppercase()
        val normalizedAndroid = androidMac.replace(":", "").uppercase()

        // Salt must match the C# DevicePairingService implementation exactly
        val combinedHardwareIdentifiers = "${normalizedDesktop}_${normalizedAndroid}_PhoneToLinux_Salt2026"

        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(combinedHardwareIdentifiers.toByteArray(Charsets.UTF_8))
    }

    /**
     * Retrieves the active MAC address of the Android device.
     */
    fun getAndroidMacAddress(): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces().toList()
            for (networkInterface in interfaces) {
                if (!networkInterface.name.equals("wlan0", ignoreCase = true)) continue

                val macBytes = networkInterface.hardwareAddress ?: return "02:00:00:00:00:00"
                val sb = StringBuilder()
                for (b in macBytes) {
                    sb.append(String.format("%02X:", b))
                }
                if (sb.isNotEmpty()) {
                    sb.deleteCharAt(sb.length - 1)
                }
                return sb.toString()
            }
            "02:00:00:00:00:00"
        } catch (ex: Exception) {
            ex.printStackTrace()
            "02:00:00:00:00:00"
        }
    }
}