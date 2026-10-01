package pl.stanislawtlolka.phonetolinux.network

import pl.stanislawtlolka.phonetolinux.data.PairingRequest
import okhttp3.ResponseBody
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
    ): Response<ResponseBody>
}