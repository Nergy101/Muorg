package nl.muorg.android.data.local

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.muorg.android.data.db.LocalTrack
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalLibraryScanner @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val coverFileNames = setOf(
        "cover.jpg", "cover.jpeg", "cover.png",
        "folder.jpg", "folder.jpeg", "folder.png",
        "artwork.jpg", "artwork.jpeg", "artwork.png",
        "front.jpg", "front.jpeg", "front.png",
        "albumart.jpg", "albumart.jpeg", "albumart.png",
    )

    suspend fun scan(
        folderUris: Set<String>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): List<LocalTrack> = withContext(Dispatchers.IO) {
        // Phase 1: enumerate all audio files (fast — directory listing only)
        val audioFiles = mutableListOf<AudioFile>()
        for (uriString in folderUris) {
            val treeUri = Uri.parse(uriString)
            val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
            collectFiles(treeUri, rootDocId, audioFiles)
        }

        // Phase 2: read metadata from each file and report progress
        val total = audioFiles.size
        val results = mutableListOf<LocalTrack>()
        val artExtractedAlbums = mutableSetOf<String>()
        audioFiles.forEachIndexed { index, file ->
            readTrack(file.uri, file.name, file.size, artExtractedAlbums, file.dirCoverUri)
                ?.let { results.add(it) }
            onProgress(index + 1, total)
        }
        results
    }

    private data class Child(val docId: String, val name: String, val mime: String, val size: Long)

    private data class AudioFile(val uri: Uri, val name: String, val size: Long, val dirCoverUri: Uri?)

    private fun collectFiles(treeUri: Uri, docId: String, results: MutableList<AudioFile>) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
        )

        val children = mutableListOf<Child>()
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
            while (cursor.moveToNext()) {
                val childDocId = cursor.getString(idIdx) ?: continue
                val name = cursor.getString(nameIdx) ?: continue
                val mime = cursor.getString(mimeIdx) ?: ""
                val size = cursor.getLong(sizeIdx)
                children.add(Child(childDocId, name, mime, size))
            }
        }

        // Find a cover image file in this directory as fallback
        val dirCoverUri = children.firstOrNull { it.name.lowercase() in coverFileNames }
            ?.let { DocumentsContract.buildDocumentUriUsingTree(treeUri, it.docId) }

        for (child in children) {
            when {
                child.mime == DocumentsContract.Document.MIME_TYPE_DIR ->
                    collectFiles(treeUri, child.docId, results)
                isAudio(child.mime, child.name) -> {
                    val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, child.docId)
                    results.add(AudioFile(fileUri, child.name, child.size, dirCoverUri))
                }
            }
        }
    }

    private fun isAudio(mime: String, name: String): Boolean {
        if (mime.startsWith("audio/")) return true
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in setOf("mp3", "flac", "m4a", "aac", "ogg", "opus", "wav")
    }

    private fun readTrack(
        uri: Uri,
        name: String,
        size: Long,
        artExtractedAlbums: MutableSet<String>,
        dirCoverUri: Uri?,
    ): LocalTrack? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val ext = name.substringAfterLast('.', "").lowercase()

            // MediaMetadataRetriever doesn't reliably read Vorbis Comment tags from FLAC.
            // Parse the FLAC metadata blocks directly as fallback.
            val vorbis = if (ext == "flac") readFlacVorbisComments(uri) else emptyMap()

            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?: vorbis["ALBUM"]

            if (album != null && album !in artExtractedAlbums) {
                val artDir = File(context.cacheDir, "album_art").also { it.mkdirs() }
                val artFile = File(artDir, "${album.hashCode()}.jpg")
                if (artFile.exists()) {
                    artExtractedAlbums.add(album)
                } else {
                    val artBytes = retriever.embeddedPicture
                        ?: when (ext) {
                            "flac" -> extractFlacCover(uri)
                            "mp3" -> extractId3Cover(uri)
                            else -> null
                        }
                        ?: dirCoverUri?.let { readBytes(it) }
                    if (artBytes != null) {
                        artFile.writeBytes(artBytes)
                        artExtractedAlbums.add(album)
                    }
                }
            }

            LocalTrack(
                path = uri.toString(),
                contentUri = uri.toString(),
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    ?: vorbis["TITLE"],
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?: vorbis["ARTIST"],
                album = album,
                albumArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                    ?: vorbis["ALBUMARTIST"] ?: vorbis["ALBUM_ARTIST"],
                year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)?.parseYear()
                    ?: (vorbis["DATE"] ?: vorbis["YEAR"])?.parseYear(),
                genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
                    ?: vorbis["GENRE"],
                trackNumber = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                    ?.substringBefore('/')?.toIntOrNull()
                    ?: vorbis["TRACKNUMBER"]?.substringBefore('/')?.toIntOrNull(),
                discNumber = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)
                    ?.substringBefore('/')?.toIntOrNull()
                    ?: vorbis["DISCNUMBER"]?.substringBefore('/')?.toIntOrNull(),
                durationSecs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()?.let { it / 1000.0 },
                format = name.substringAfterLast('.', "AUDIO").uppercase(),
                fileSize = size,
            )
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    // Parses "2020", "2020-01-01", "2020/01" etc. — extracts the leading 4-digit year.
    private fun String.parseYear(): Int? =
        trim().toIntOrNull() ?: trim().take(4).toIntOrNull()

    // Reads all Vorbis Comment key=value pairs from a FLAC file's metadata blocks.
    private fun readFlacVorbisComments(uri: Uri): Map<String, String> {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val marker = ByteArray(4)
                if (!readFully(input, marker)) return emptyMap()
                if (marker[0] != 0x66.toByte() || marker[1] != 0x4C.toByte() ||
                    marker[2] != 0x61.toByte() || marker[3] != 0x43.toByte()) return emptyMap()

                val header = ByteArray(4)
                while (readFully(input, header)) {
                    val isLast = (header[0].toInt() and 0x80) != 0
                    val blockType = header[0].toInt() and 0x7F
                    val blockLen = ((header[1].toInt() and 0xFF) shl 16) or
                                   ((header[2].toInt() and 0xFF) shl 8) or
                                   (header[3].toInt() and 0xFF)

                    if (blockType == 4) { // VORBIS_COMMENT block
                        val data = ByteArray(blockLen)
                        if (!readFully(input, data)) break
                        val result = mutableMapOf<String, String>()
                        var pos = 0
                        if (pos + 4 > data.size) break
                        val vendorLen = readInt32LE(data, pos); pos += 4 + vendorLen
                        if (pos + 4 > data.size) break
                        val count = readInt32LE(data, pos); pos += 4
                        repeat(count) {
                            if (pos + 4 > data.size) return@repeat
                            val len = readInt32LE(data, pos); pos += 4
                            if (pos + len > data.size) return@repeat
                            val entry = String(data, pos, len, Charsets.UTF_8); pos += len
                            val eq = entry.indexOf('=')
                            if (eq > 0) result[entry.substring(0, eq).uppercase()] = entry.substring(eq + 1)
                        }
                        return@use result
                    } else {
                        skipFully(input, blockLen)
                    }
                    if (isLast) break
                }
                emptyMap()
            } ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    // Manually parse FLAC metadata blocks for PICTURE (type 6) and
    // VORBIS_COMMENT entries containing METADATA_BLOCK_PICTURE.
    private fun extractFlacCover(uri: Uri): ByteArray? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val marker = ByteArray(4)
                if (!readFully(input, marker)) return null
                // Verify "fLaC" marker
                if (marker[0] != 0x66.toByte() || marker[1] != 0x4C.toByte() ||
                    marker[2] != 0x61.toByte() || marker[3] != 0x43.toByte()) return null

                val header = ByteArray(4)
                var vorbisEmbedded: ByteArray? = null

                while (readFully(input, header)) {
                    val isLast = (header[0].toInt() and 0x80) != 0
                    val blockType = header[0].toInt() and 0x7F
                    val blockLen = ((header[1].toInt() and 0xFF) shl 16) or
                                   ((header[2].toInt() and 0xFF) shl 8) or
                                   (header[3].toInt() and 0xFF)

                    when (blockType) {
                        6 -> { // PICTURE block — preferred
                            val data = ByteArray(blockLen)
                            if (readFully(input, data)) {
                                parsePictureBlock(data)?.let { return it }
                            }
                        }
                        4 -> { // VORBIS_COMMENT — may contain METADATA_BLOCK_PICTURE
                            val data = ByteArray(blockLen)
                            if (readFully(input, data)) {
                                vorbisEmbedded = extractVorbisPicture(data)
                            }
                        }
                        else -> skipFully(input, blockLen)
                    }

                    if (isLast) break
                }

                vorbisEmbedded
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parsePictureBlock(data: ByteArray): ByteArray? {
        if (data.size < 32) return null
        val picType = readInt32BE(data, 0)
        // Accept CoverFront (3) first; on second pass accept any type
        if (picType != 3 && picType != 0) return null
        val mimeLen = readInt32BE(data, 4)
        var pos = 8 + mimeLen
        if (pos + 4 > data.size) return null
        val descLen = readInt32BE(data, pos)
        pos += 4 + descLen + 16 // skip desc + width + height + depth + colors
        if (pos + 4 > data.size) return null
        val dataLen = readInt32BE(data, pos)
        pos += 4
        if (pos + dataLen > data.size) return null
        return data.copyOfRange(pos, pos + dataLen)
    }

    private fun extractVorbisPicture(data: ByteArray): ByteArray? {
        // Vorbis comment format: little-endian lengths
        if (data.size < 8) return null
        val vendorLen = readInt32LE(data, 0)
        var pos = 4 + vendorLen
        if (pos + 4 > data.size) return null
        val commentCount = readInt32LE(data, pos)
        pos += 4
        repeat(commentCount) {
            if (pos + 4 > data.size) return null
            val commentLen = readInt32LE(data, pos)
            pos += 4
            if (pos + commentLen > data.size) return null
            val comment = String(data, pos, commentLen, Charsets.UTF_8)
            pos += commentLen
            if (comment.startsWith("METADATA_BLOCK_PICTURE=", ignoreCase = true)) {
                val b64 = comment.substringAfter('=')
                val decoded = try { Base64.decode(b64, Base64.DEFAULT) } catch (e: Exception) { return null }
                parsePictureBlock(decoded)?.let { return it }
            }
        }
        return null
    }

    // Parse ID3v2 tags for MP3 files and extract APIC (attached picture) frame.
    private fun extractId3Cover(uri: Uri): ByteArray? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val header = ByteArray(10)
                if (!readFully(input, header)) return null
                // "ID3" marker
                if (header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()) return null
                val majorVersion = header[3].toInt() and 0xFF
                val flags = header[5].toInt() and 0xFF
                var tagSize = ((header[6].toInt() and 0x7F) shl 21) or
                              ((header[7].toInt() and 0x7F) shl 14) or
                              ((header[8].toInt() and 0x7F) shl 7) or
                              (header[9].toInt() and 0x7F)

                // Skip extended header if present
                if (flags and 0x40 != 0) {
                    val extBuf = ByteArray(4)
                    if (!readFully(input, extBuf)) return null
                    val extSize = if (majorVersion >= 4)
                        ((extBuf[0].toInt() and 0x7F) shl 21) or ((extBuf[1].toInt() and 0x7F) shl 14) or
                        ((extBuf[2].toInt() and 0x7F) shl 7) or (extBuf[3].toInt() and 0x7F)
                    else
                        readInt32BE(extBuf, 0)
                    skipFully(input, extSize - 4)
                    tagSize -= extSize
                }

                val frameIdLen = if (majorVersion == 2) 3 else 4
                val frameSizeLen = if (majorVersion == 2) 3 else 4
                var remaining = tagSize

                while (remaining > frameIdLen + frameSizeLen + 2) {
                    val frameHeader = ByteArray(frameIdLen + frameSizeLen + (if (majorVersion == 2) 0 else 2))
                    if (!readFully(input, frameHeader)) break
                    remaining -= frameHeader.size

                    val frameId = String(frameHeader, 0, frameIdLen, Charsets.ISO_8859_1)
                    if (frameId.all { it == ' ' }) break // padding reached

                    val frameSize = if (majorVersion == 2)
                        ((frameHeader[3].toInt() and 0xFF) shl 16) or
                        ((frameHeader[4].toInt() and 0xFF) shl 8) or
                        (frameHeader[5].toInt() and 0xFF)
                    else if (majorVersion >= 4)
                        ((frameHeader[4].toInt() and 0x7F) shl 21) or
                        ((frameHeader[5].toInt() and 0x7F) shl 14) or
                        ((frameHeader[6].toInt() and 0x7F) shl 7) or
                        (frameHeader[7].toInt() and 0x7F)
                    else
                        readInt32BE(frameHeader, 4)

                    if (frameSize <= 0 || frameSize > remaining) break
                    remaining -= frameSize

                    if (frameId == "APIC" || frameId == "PIC") {
                        val frameData = ByteArray(frameSize)
                        if (!readFully(input, frameData)) break
                        parseApicFrame(frameData, majorVersion)?.let { return it }
                    } else {
                        skipFully(input, frameSize)
                    }
                }
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseApicFrame(data: ByteArray, version: Int): ByteArray? {
        if (data.isEmpty()) return null
        var pos = 1 // skip text encoding byte

        if (version == 2) {
            // PIC: 3-char image format + picture type byte + description + NUL + data
            if (pos + 3 >= data.size) return null
            pos += 3 // image format
            val picType = data[pos].toInt() and 0xFF
            pos++
            if (picType != 3 && picType != 0) return null
            while (pos < data.size && data[pos] != 0.toByte()) pos++
            pos++ // skip NUL
        } else {
            // APIC: MIME type (NUL-terminated) + picture type + description (NUL-terminated) + data
            var mimeEnd = pos
            while (mimeEnd < data.size && data[mimeEnd] != 0.toByte()) mimeEnd++
            if (mimeEnd >= data.size) return null
            pos = mimeEnd + 1
            if (pos >= data.size) return null
            val picType = data[pos].toInt() and 0xFF
            pos++
            if (picType != 3 && picType != 0) return null
            // Skip description (NUL-terminated; handle UTF-16 with double NUL)
            val encoding = data[0].toInt() and 0xFF
            if (encoding == 1 || encoding == 2) {
                while (pos + 1 < data.size && !(data[pos] == 0.toByte() && data[pos + 1] == 0.toByte())) pos++
                pos += 2
            } else {
                while (pos < data.size && data[pos] != 0.toByte()) pos++
                pos++
            }
        }

        if (pos >= data.size) return null
        return data.copyOfRange(pos, data.size)
    }

    private fun readBytes(uri: Uri): ByteArray? = try {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    } catch (e: Exception) {
        null
    }

    private fun readFully(input: InputStream, buf: ByteArray): Boolean {
        var offset = 0
        while (offset < buf.size) {
            val read = input.read(buf, offset, buf.size - offset)
            if (read == -1) return false
            offset += read
        }
        return true
    }

    private fun skipFully(input: InputStream, count: Int) {
        var remaining = count.toLong()
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped <= 0) break
            remaining -= skipped
        }
    }

    private fun readInt32BE(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 24) or
        ((data[offset + 1].toInt() and 0xFF) shl 16) or
        ((data[offset + 2].toInt() and 0xFF) shl 8) or
        (data[offset + 3].toInt() and 0xFF)

    private fun readInt32LE(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or
        ((data[offset + 1].toInt() and 0xFF) shl 8) or
        ((data[offset + 2].toInt() and 0xFF) shl 16) or
        ((data[offset + 3].toInt() and 0xFF) shl 24)
}
