package com.phonetolinux.security

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs

/**
 * Lightweight security manager using HMAC-SHA256 signatures to secure Android endpoints.
 * Protects against payload tampering and replay attacks with minimal CPU overhead.
 */
class XSignature(private val sharedAesKey: ByteArray) {

    /**
     * Generates a unique HMAC-SHA256 signature for the given payload and timestamp.
     */
    fun generateSignature(payload: String, timestamp: Long): String {
        // Combine timestamp, payload, and static salt for signature computation
        val dataToSign = "$timestamp:$payload:PhoneToLinux_Salt2026"
        val sha256Hmac = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(sharedAesKey, "HmacSHA256")
        sha256Hmac.init(secretKey)

        val hashBytes = sha256Hmac.doFinal(dataToSign.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Validates incoming requests from the Linux desktop app.
     * Checks request freshness (anti-replay) and validates the HMAC signature.
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

        // Verify HMAC signature validity
        val expectedSignature = generateSignature(payload, requestTimestamp)
        return expectedSignature.equals(signatureHeader, ignoreCase = true)
    }
}