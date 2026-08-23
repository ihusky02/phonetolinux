package com.example.phonetolinux.data

import com.google.gson.annotations.SerializedName

/**
 * Data model representing the payload sent from Android to the Linux desktop during pairing.
 */
data class PairingRequest(
    @SerializedName("androidMacAddress")
    val androidMacAddress: String,

    @SerializedName("pairingPin")
    val pairingPin: String
)