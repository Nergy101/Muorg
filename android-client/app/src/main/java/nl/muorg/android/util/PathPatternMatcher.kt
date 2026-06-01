package nl.muorg.android.util

import nl.muorg.android.data.api.MetadataUpdateRequest

object PathPatternMatcher {

    fun extractMetadataFromPath(pattern: String, path: String): Map<String, String>? {
        val patternParts = pattern.split("/").filter { it.isNotEmpty() }
        val pathParts = path.split("/").filter { it.isNotEmpty() }
        if (pathParts.size < patternParts.size) return null

        val relevantParts = pathParts.takeLast(patternParts.size)
        val extracted = mutableMapOf<String, String>()
        for (i in patternParts.indices) {
            val segment = parseSegment(patternParts[i], relevantParts[i]) ?: return null
            extracted.putAll(segment)
        }
        return extracted
    }

    fun buildMetadataUpdate(fields: Map<String, String>): MetadataUpdateRequest {
        val lower = fields.mapKeys { it.key.lowercase() }
        fun get(vararg keys: String): String? = keys.firstNotNullOfOrNull { lower[it] }
        return MetadataUpdateRequest(
            title = get("tracktitle", "title"),
            artist = get("artist"),
            album = get("album", "albumname"),
            albumArtist = get("albumartist", "album_artist"),
            featuring = get("featuring"),
            year = get("year")?.trim()?.toIntOrNull(),
            genre = get("genre"),
            trackNumber = get("tracknumber", "track_number")?.substringBefore('/')?.trim()?.toIntOrNull(),
            discNumber = get("discnumber", "disc_number")?.substringBefore('/')?.trim()?.toIntOrNull(),
        )
    }

    fun decodeLocalPath(safUri: String): String {
        return try {
            val uri = android.net.Uri.parse(safUri)
            val lastSegment = uri.lastPathSegment ?: return safUri
            val decoded = android.net.Uri.decode(lastSegment)
            val colonIdx = decoded.indexOf(':')
            if (colonIdx >= 0) decoded.substring(colonIdx + 1) else decoded
        } catch (e: Exception) {
            safUri
        }
    }

    fun fieldDisplayName(key: String): String = when (key.lowercase()) {
        "tracktitle", "title" -> "Title"
        "artist" -> "Artist"
        "album" -> "Album"
        "albumartist", "album_artist" -> "Album Artist"
        "year" -> "Year"
        "genre" -> "Genre"
        "tracknumber", "track_number" -> "Track #"
        "discnumber", "disc_number" -> "Disc #"
        "featuring" -> "Featuring"
        "format", "ext" -> "Format"
        else -> key.replaceFirstChar { it.uppercase() }
    }

    private fun parseSegment(patternSegment: String, pathSegment: String): Map<String, String>? {
        val placeholderRe = Regex("<([^>]+)>")
        var regexStr = ""
        var lastIndex = 0
        val fields = mutableListOf<String>()

        for (match in placeholderRe.findAll(patternSegment)) {
            val literal = patternSegment.substring(lastIndex, match.range.first)
            regexStr += Regex.escape(literal)
            regexStr += "(.+?)"
            fields.add(match.groupValues[1].lowercase())
            lastIndex = match.range.last + 1
        }
        regexStr += Regex.escape(patternSegment.substring(lastIndex))

        val re = Regex("^$regexStr$", RegexOption.IGNORE_CASE)
        val result = re.find(pathSegment) ?: return null

        return fields.mapIndexedNotNull { i, field ->
            val value = result.groupValues.getOrNull(i + 1)?.trim() ?: return@mapIndexedNotNull null
            if (value.isNotEmpty()) field to value else null
        }.toMap()
    }
}
