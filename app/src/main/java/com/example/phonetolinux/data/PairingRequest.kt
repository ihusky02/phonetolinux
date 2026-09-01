package com.example.phonetolinux.data

import com.google.gson.annotations.SerializedName

/**
 * Data model representing the payload sent from Android to the Linux desktop during pairing.
 * Includes the active server port to allow dynamic configuration on the Linux side.
 */
data class PairingRequest(
    @SerializedName("androidMacAddress")
    val androidMacAddress: String,

    @SerializedName("pairingPin")
    val pairingPin: String,

    @SerializedName("serverPort")
    val serverPort: Int
)