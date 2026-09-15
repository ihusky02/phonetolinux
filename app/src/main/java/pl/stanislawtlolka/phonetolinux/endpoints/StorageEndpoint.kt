package pl.stanislawtlolka.phonetolinux.endpoints

import android.content.Context
import com.google.gson.Gson
import pl.stanislawtlolka.phonetolinux.EndpointHandler
import pl.stanislawtlolka.phonetolinux.EndpointResponse
import pl.stanislawtlolka.phonetolinux.utils.StorageManagerUtils

/**
 * HTTP endpoint handler for listing device internal storage directories.
 * Exposes safe whitelisted directories for Linux integration.
 */
class StorageEndpoint : EndpointHandler {
    override val path: String = "/storage/list"
    private val gson = Gson()

    override fun handle(requestLine: String, context: Context): EndpointResponse {
        val relativePath = if (requestLine.contains("?path=")) {
            requestLine.substringAfter("?path=").substringBefore(" ").trim()
        } else {
            ""
        }

        val fileList = StorageManagerUtils.listDirectory(relativePath)
        val jsonResponse = gson.toJson(fileList)

        return EndpointResponse("200 OK", jsonResponse)
    }
}