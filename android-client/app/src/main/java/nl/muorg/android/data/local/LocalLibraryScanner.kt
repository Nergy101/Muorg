package nl.muorg.android.data.local

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.muorg.android.data.db.LocalTrack
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalLibraryScanner @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val AUDIO_EXTENSIONS = setOf("mp3", "flac", "m4a", "aac", "ogg", "opus", "wav")

    suspend fun scan(folderUris: Set<String>): List<LocalTrack> = withContext(Dispatchers.IO) {
        val results = mutableListOf<LocalTrack>()
        for (uriString in folderUris) {
            val uri = Uri.parse(uriString)
            val docFile = DocumentFile.fromTreeUri(context, uri) ?: continue
            scanDirectory(docFile, results)
        }
        results
    }

    private fun scanDirectory(dir: DocumentFile, results: MutableList<LocalTrack>) {
        for (child in dir.listFiles()) {
            when {
                child.isDirectory -> scanDirectory(child, results)
                child.isFile -> {
                    val name = child.name ?: continue
                    val ext = name.substringAfterLast('.', "").lowercase()
                    if (ext in AUDIO_EXTENSIONS) {
                        readTrack(child)?.let { results.add(it) }
                    }
                }
            }
        }
    }

    private fun readTrack(file: DocumentFile): LocalTrack? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, file.uri)
            val track = LocalTrack(
                path = file.uri.toString(),
                contentUri = file.uri.toString(),
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                albumArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)?.toIntOrNull(),
                genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE),
                trackNumber = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                    ?.substringBefore('/')?.toIntOrNull(),
                discNumber = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)
                    ?.substringBefore('/')?.toIntOrNull(),
                durationSecs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()?.let { it / 1000.0 },
                format = file.name?.substringAfterLast('.')?.uppercase() ?: "AUDIO",
                fileSize = file.length(),
            )
            retriever.release()
            track
        } catch (e: Exception) {
            null
        }
    }
}
