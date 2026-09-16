package pl.stanislawtlolka.phonetolinux.server

import android.os.Environment
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Embedded WebDAV server implementation handling file management operations (OPTIONS, PROPFIND, GET, PUT).
 * Runs on top of NanoHTTPD alongside the main sockets service.
 *
 * @author Stanisław Tlołka
 */
class WebDavServer(port: Int = 5000) : NanoHTTPD(port) {

    private val TAG = "WebDavServer"

    // WebDAV requires date formatting compliant with RFC 1123
    private val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("GMT")
    }

    override fun serve(session: IHTTPSession): Response {
        val method = session.method
        val uri = session.uri

        Log.d(TAG, "Incoming WebDAV Request: Method=${method.name}, URI=$uri")

        return try {
            when (method) {
                Method.OPTIONS -> handleOptions()
                Method.PROPFIND -> handlePropfind(uri)
                Method.GET -> handleGet(uri)
                Method.PUT -> handlePut(uri, session)
                else -> newFixedLengthResponse(
                    Response.Status.METHOD_NOT_ALLOWED,
                    MIME_PLAINTEXT,
                    "Method Not Allowed"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unhandled WebDAV server error processing $uri: ${e.message}", e)
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                "Internal Server Error: ${e.message}"
            )
        }
    }

    /**
     * Responds to WebDAV OPTIONS discovery queries.
     */
    private fun handleOptions(): Response {
        val response = newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "")
        response.addHeader("DAV", "1, 2")
        response.addHeader("Allow", "GET, PUT, OPTIONS, PROPFIND, MKCOL, DELETE")
        return response
    }

    /**
     * Responds to WebDAV PROPFIND directory listing queries with 207 Multi-Status XML payload.
     */
    private fun handlePropfind(uri: String): Response {
        val targetFile = resolveStorageFile(uri)

        if (!targetFile.exists()) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "File or directory not found")
        }

        val xml = StringBuilder().apply {
            append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
            append("<d:multistatus xmlns:d=\"DAV:\">\n")

            // Append response entry for the target resource itself
            append(buildItemXml(uri, targetFile))

            // If the target resource is a directory, append XML blocks for all contained children
            if (targetFile.isDirectory) {
                targetFile.listFiles()?.forEach { child ->
                    val childUri = if (uri.endsWith("/")) "$uri${child.name}" else "$uri/${child.name}"
                    append(buildItemXml(childUri, child))
                }
            }

            append("</d:multistatus>")
        }

        return newFixedLengthResponse(
            Response.Status.MULTI_STATUS,
            "application/xml; charset=utf-8",
            xml.toString()
        )
    }

    /**
     * Formats an individual file or directory entry into standard WebDAV XML syntax.
     */
    private fun buildItemXml(href: String, file: File): String {
        val lastMod = dateFormat.format(Date(file.lastModified()))
        val isDirectory = file.isDirectory
        val resourceType = if (isDirectory) "<d:collection/>" else ""
        val contentLength = if (isDirectory) "" else "<d:getcontentlength>${file.length()}</d:getcontentlength>"

        return """
            <d:response>
                <d:href>$href</d:href>
                <d:propstat>
                    <d:prop>
                        <d:resourcetype>$resourceType</d:resourcetype>
                        <d:getlastmodified>$lastMod</d:getlastmodified>
                        $contentLength
                    </d:prop>
                    <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
            </d:response>
        """.trimIndent()
    }

    /**
     * Streams requested file content back to the client desktop.
     */
    private fun handleGet(uri: String): Response {
        val file = resolveStorageFile(uri)
        if (!file.exists() || file.isDirectory) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "File not found")
        }

        return try {
            val fis = FileInputStream(file)
            newChunkedResponse(Response.Status.OK, "application/octet-stream", fis)
        } catch (e: Exception) {
            Log.e(TAG, "Error streaming file for download: ${e.message}", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error reading file")
        }
    }

    /**
     * Receives and saves uploaded files sent from the client desktop.
     */
    private fun handlePut(uri: String, session: IHTTPSession): Response {
        val destinationFile = resolveStorageFile(uri)
        return try {
            val bodyFiles = HashMap<String, String>()
            session.parseBody(bodyFiles)

            val tempPath = bodyFiles["content"] ?: bodyFiles["postData"]
            if (tempPath != null) {
                val tempFile = File(tempPath)
                if (tempFile.exists()) {
                    destinationFile.parentFile?.mkdirs()
                    tempFile.copyTo(destinationFile, overwrite = true)
                    return newFixedLengthResponse(Response.Status.CREATED, MIME_PLAINTEXT, "File Uploaded Successfully")
                }
            }
            newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Missing file body payload")
        } catch (e: Exception) {
            Log.e(TAG, "Error writing uploaded file stream: ${e.message}", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error writing file")
        }
    }

    /**
     * Resolves incoming URL request URIs directly to Android external storage directory paths.
     */
    private fun resolveStorageFile(uri: String): File {
        val cleanPath = uri.removePrefix("/storage")
            .removePrefix("/dav")
            .ifEmpty { "/" }

        val rootDirectory = Environment.getExternalStorageDirectory()
        return File(rootDirectory, cleanPath)
    }
}