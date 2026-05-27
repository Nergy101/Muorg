package nl.muorg.android.cast

import android.content.Context
import android.net.Uri
import android.net.wifi.WifiManager
import dagger.hilt.android.qualifiers.ApplicationContext
import fi.iki.elonen.NanoHTTPD
import nl.muorg.android.data.api.CatalogTrack
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

private const val PORT = 54321

@Singleton
class LocalCastServer @Inject constructor(
    @ApplicationContext private val context: Context,
) : NanoHTTPD(PORT) {

    private val tracks = mutableMapOf<Int, CatalogTrack>()

    fun register(track: CatalogTrack): String {
        val id = (-track.id) // local track ids are negative; normalise to positive key
        tracks[id] = track
        return "http://${getLocalIp()}:$PORT/track/$id"
    }

    override fun serve(session: IHTTPSession): Response {
        val id = session.uri.removePrefix("/track/").toIntOrNull()
            ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")

        val track = tracks[id]
            ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Track not registered")

        val uri = Uri.parse(track.localFilePath ?: return newFixedLengthResponse(
            Response.Status.NOT_FOUND, MIME_PLAINTEXT, "No local path"
        ))

        val contentType = when (track.format.lowercase()) {
            "mp3" -> "audio/mpeg"
            "flac" -> "audio/flac"
            "aac", "m4a" -> "audio/mp4"
            "ogg" -> "audio/ogg"
            "opus" -> "audio/ogg;codecs=opus"
            "wav" -> "audio/wav"
            else -> "audio/mpeg"
        }

        val fileSize = runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
        }.getOrNull() ?: -1L

        val rangeHeader = session.headers["range"]
        val inputStream: InputStream = context.contentResolver.openInputStream(uri)
            ?: return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Cannot open file")

        if (rangeHeader != null && fileSize > 0) {
            val match = Regex("bytes=(\\d+)-(\\d*)").find(rangeHeader)
            val start = match?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val end = match?.groupValues?.get(2)?.toLongOrNull()?.takeIf { it > 0 } ?: (fileSize - 1)
            val length = end - start + 1

            inputStream.skip(start)
            val response = newFixedLengthResponse(
                Response.Status.PARTIAL_CONTENT, contentType, inputStream, length
            )
            response.addHeader("Content-Range", "bytes $start-$end/$fileSize")
            response.addHeader("Accept-Ranges", "bytes")
            return response
        }

        val response = if (fileSize > 0) {
            newFixedLengthResponse(Response.Status.OK, contentType, inputStream, fileSize)
        } else {
            newChunkedResponse(Response.Status.OK, contentType, inputStream)
        }
        response.addHeader("Accept-Ranges", "bytes")
        return response
    }

    private fun getLocalIp(): String {
        val wifi = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ip = wifi.connectionInfo.ipAddress
        return String.format("%d.%d.%d.%d", ip and 0xff, ip shr 8 and 0xff, ip shr 16 and 0xff, ip shr 24 and 0xff)
    }
}
