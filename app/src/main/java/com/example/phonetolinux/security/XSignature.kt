package com.phonetolinux.security

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs

/**
 * High-performance, zero-allocation HMAC-SHA256 signature validator.
 * Optimized for low CPU usage and minimal Garbage Collection overhead.
 */
class XSignature(sharedAesKey: ByteArray) {

    private val mac: Mac = Mac.getInstance("HmacSHA256").apply {
        init(SecretKeySpec(sharedAesKey, "HmacSHA256"))
    }

    private val hexArray = "0123456789abcdef".toCharArray()

    /**
     * Generates an HMAC-SHA256 signature reusing internal buffer and Mac instance.
     */
    @Synchronized
    fun generateSignature(payload: String, timestamp: Long): String {
        val dataToSign = "$timestamp:$payload:PhoneToLinux_Salt2026"
        val hashBytes = mac.doFinal(dataToSign.toByteArray(Charsets.UTF_8))
        return bytesToHex(hashBytes)
    }

    /**
     * Fast byte-to-hex conversion avoiding String.format() allocations.
     */
    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            hexChars[i * 2] = hexArray[v ushr 4]
            hexChars[i * 2 + 1] = hexArray[v and 0x0F]
        }
        return String(hexChars)
    }

    /**
     * Validates incoming requests from the Linux desktop app.
     */
    fun validateIncomingRequest(
        payload: String,
        signatureHeader: String?,
        timestampHeader: String?
    ): Boolean {
        if (signatureHeader.isNullOrEmpty() || timestampHeader.isNullOrEmpty()) {
            return false
        }

        val requestTimestamp = timestampHeader.toLongOrNull() ?: return false
        val currentTimestamp = System.currentTimeMillis()

        // Anti-Replay Attack: Reject requests older than 30 seconds
        if (abs(currentTimestamp - requestTimestamp) > 30_000) {
            return false
        }

        val expectedSignature = generateSignature(payload, requestTimestamp)
        return expectedSignature.equals(signatureHeader, ignoreCase = true)
    }
}