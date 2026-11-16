package nl.muorg.android.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Auto-search for an album cover on Wikipedia (port of the legacy desktop
 * app's `utils/wikipediaCover.ts`). Three MediaWiki API calls: search the
 * album page, list its images, resolve the best-scoring `File:` title to a
 * thumbnail URL. `origin=*` is required for CORS on anonymous API calls.
 */
object WikipediaCover {

    private const val WIKI_API = "https://en.wikipedia.org/w/api.php"
    private val client = OkHttpClient()

    private fun apiGet(params: Map<String, String>): JSONObject {
        val url = WIKI_API + "?" + params.entries.joinToString("&") { (k, v) ->
            "$k=${java.net.URLEncoder.encode(v, "UTF-8")}"
        }
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("Wikipedia API ${resp.code}")
            return JSONObject(resp.body!!.string())
        }
    }

    private fun normalizeFileTitle(title: String): Pair<String, String> {
        val name = title.removePrefix("File:").substringBeforeLast('.', "")
        val ext = title.substringAfterLast('.', "").lowercase()
        return name to ext
    }

    private fun scoreImageAsAlbumArt(fileTitle: String, albumName: String): Double {
        val (name, ext) = normalizeFileTitle(fileTitle)
        val albumNorm = albumName.lowercase().replace(Regex("[^a-z0-9]+"), "")
        var score = 0.0
        if (albumNorm.isNotEmpty() && name.contains(albumNorm)) score += 2
        if (Regex("cover|albumcover|albumart|albumartwork", RegexOption.IGNORE_CASE).containsMatchIn(fileTitle)) score += 1
        if (Regex("album").containsMatchIn(fileTitle)) score += 0.5
        // Icons and SVGs are the page's own furniture, never the artwork.
        if (Regex("icon|edit|button|star|arrow|progressive|\\.svg$", RegexOption.IGNORE_CASE).containsMatchIn(fileTitle) || ext == "svg") score -= 2
        if (ext == "svg") score -= 1
        if (ext in listOf("jpg", "jpeg", "png", "webp")) score += 0.5
        return score
    }

    /**
     * Look up to [limit] cover candidate URLs for [album] by [artist],
     * best-scoring first. Same search flow as [findAlbumCoverUrl], but the
     * imageinfo call is batched over all top titles so callers can show a
     * selection strip instead of committing to the single best guess.
     */
    suspend fun findAlbumCoverUrls(album: String, artist: String, limit: Int = 6): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val albumName = album.trim()
            val query = listOf(albumName, artist.trim()).filter { it.isNotEmpty() }.joinToString(" ")
            if (query.isEmpty()) error("Enter an album (or artist) name first.")
            // "(album)" steers the search away from the song or film of the same name.
            val searchQuery = if (albumName.isNotEmpty()) "$albumName (album)" else query
            val searchData = apiGet(
                mapOf(
                    "action" to "query", "generator" to "search", "gsrsearch" to searchQuery,
                    "gsrlimit" to "5", "format" to "json", "origin" to "*",
                ),
            )
            val pages = searchData.optJSONObject("query")?.optJSONObject("pages") ?: error("No Wikipedia page found for this album.")
            val firstPage = pages.keys().asSequence()
                .map { pages.getJSONObject(it) }
                .minByOrNull { it.optInt("index", 99) }
                ?: error("No Wikipedia page found for this album.")
            val pageId = firstPage.getInt("pageid")

            val imagesData = apiGet(
                mapOf(
                    "action" to "query", "pageids" to pageId.toString(), "prop" to "images",
                    "format" to "json", "origin" to "*",
                ),
            )
            val page = imagesData.optJSONObject("query")?.optJSONObject("pages")?.optJSONObject(pageId.toString())
            val images = page?.optJSONArray("images")?.let { arr ->
                List(arr.length()) { arr.getJSONObject(it).getString("title") }
            } ?: emptyList()
            val scored = images
                .filter { it.startsWith("File:") }
                .map { it to scoreImageAsAlbumArt(it, albumName.ifEmpty { query }) }
                .sortedByDescending { it.second }
                .take(limit)
                .map { it.first }
            if (scored.isEmpty()) error("No image found on this Wikipedia page.")

            // Batch-resolve all candidate titles to thumbnail URLs in one call.
            val infoData = apiGet(
                mapOf(
                    "action" to "query", "titles" to scored.joinToString("|"), "prop" to "imageinfo",
                    "iiprop" to "url", "iiurlwidth" to "800", "format" to "json", "origin" to "*",
                ),
            )
            val infoPages = infoData.optJSONObject("query")?.optJSONObject("pages")
            val byTitle = mutableMapOf<String, String>()
            infoPages?.keys()?.asSequence()?.forEach { key ->
                val filePage = infoPages.getJSONObject(key)
                val title = filePage.optString("title")
                val url = filePage.optJSONArray("imageinfo")?.optJSONObject(0)?.optString("url")
                if (title.isNotEmpty() && !url.isNullOrEmpty()) byTitle[title] = url
            }
            val urls = scored.mapNotNull { byTitle[it] }
            urls.ifEmpty { error("Could not get image URLs.") }
        }
    }

    /** Look up the single best-scoring cover URL for [album] by [artist]. */
    suspend fun findAlbumCoverUrl(album: String, artist: String): Result<String> =
        findAlbumCoverUrls(album, artist, limit = 1).map { urls -> urls.first() }

    /**
     * Download the cover at [url], center-crop it to a perfect square, downscale
     * to at most 1024px and return it as a base64 JPEG — ready for
     * `picture_base64`. [headers] carries auth when the URL points at the
     * Muorg server itself (the album's current cover) instead of Wikimedia.
     */
    suspend fun downloadSquareCoverBase64(url: String, headers: Map<String, String> = emptyMap()): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url)
                .apply { headers.forEach { (k, v) -> header(k, v) } }
                .build()
            val bytes = client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("Image download ${resp.code}")
                resp.body!!.bytes()
            }
            val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: error("Could not decode image.")
            val size = minOf(src.width, src.height)
            val cropped = Bitmap.createBitmap(src, (src.width - size) / 2, (src.height - size) / 2, size, size)
            val out = if (size > 1024) Bitmap.createScaledBitmap(cropped, 1024, 1024, true) else cropped
            val baos = java.io.ByteArrayOutputStream()
            out.compress(Bitmap.CompressFormat.JPEG, 90, baos)
            Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        }
    }
}
