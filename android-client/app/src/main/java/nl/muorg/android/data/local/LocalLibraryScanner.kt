package nl.muorg.android.data.local

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.muorg.android.data.db.LocalTrack
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalLibraryScanner @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun scan(folderUris: Set<String>): List<LocalTrack> = withContext(Dispatchers.IO) {
        val results = mutableListOf<LocalTrack>()
        val artExtractedAlbums = mutableSetOf<String>()
        for (uriString in folderUris) {
            val treeUri = Uri.parse(uriString)
            val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
            scanDir(treeUri, rootDocId, results, artExtractedAlbums)
        }
        results
    }

    private fun scanDir(treeUri: Uri, docId: String, results: MutableList<LocalTrack>, artExtractedAlbums: MutableSet<String>) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
        )
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
                when {
                    mime == DocumentsContract.Document.MIME_TYPE_DIR ->
                        scanDir(treeUri, childDocId, results, artExtractedAlbums)
                    isAudio(mime, name) -> {
                        val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)
                        readTrack(fileUri, name, size, artExtractedAlbums)?.let { results.add(it) }
                    }
                }
            }
        }
    }

    private fun isAudio(mime: String, name: String): Boolean {
        if (mime.startsWith("audio/")) return true
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in setOf("mp3", "flac", "m4a", "aac", "ogg", "opus", "wav")
    }

    private fun readTrack(uri: Uri, name: String, size: Long, artExtractedAlbums: MutableSet<String>): LocalTrack? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            if (album != null && album !in artExtractedAlbums) {
                retriever.embeddedPicture?.let { bytes ->
                    val artDir = File(context.cacheDir, "album_art").also { it.mkdirs() }
                    val artFile = File(artDir, "${album.hashCode()}.jpg")
                    if (!artFile.exists()) artFile.writeBytes(bytes)
                    artExtractedAlbums.add(album)
                }
            }
            LocalTrack(
                path = uri.toString(),
                contentUri = uri.toString(),
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                album = album,
                albumArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)?.toIntOrNull(),
                genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE),
                trackNumber = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                    ?.substringBefore('/')?.toIntOrNull(),
                discNumber = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)
                    ?.substringBefore('/')?.toIntOrNull(),
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
}
