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
        // Extract URI from GET line (e.g. "GET /storage/list?path=DCIM HTTP/1.1" -> "/storage/list?path=DCIM")
        val uri = requestLine.split(" ").getOrNull(1) ?: ""

        val relativePath = if (uri.contains("?path=")) {
            uri.substringAfter("?path=").trim()
        } else {
            ""
        }

        val fileList = StorageManagerUtils.listDirectory(relativePath)
        val jsonResponse = gson.toJson(fileList)

        return EndpointResponse("200 OK", jsonResponse)
    }
}