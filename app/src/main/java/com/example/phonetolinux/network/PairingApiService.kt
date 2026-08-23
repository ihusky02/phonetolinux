package com.example.phonetolinux.network

import com.example.phonetolinux.data.PairingRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * Retrofit API interface for handling device pairing requests over HTTP.
 */
interface PairingApiService {
    @POST
    suspend fun sendPairingRequest(
        @Url url: String,
        @Body request: PairingRequest
    ): Response<PairingResponse>
}

/**
 * Response model received from the Linux desktop listener service.
 */
data class PairingResponse(
    val status: String,
    val message: String
)