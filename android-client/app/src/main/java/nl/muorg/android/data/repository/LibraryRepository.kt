package nl.muorg.android.data.repository

import nl.muorg.android.data.api.AlbumGroup
import nl.muorg.android.data.api.CatalogTrack
import nl.muorg.android.data.api.MetadataUpdateRequest
import nl.muorg.android.data.api.MuorgApiService
import nl.muorg.android.data.api.Stats
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryRepository @Inject constructor(
    private val api: MuorgApiService,
) {
    suspend fun getAllTracks(): Result<List<CatalogTrack>> = runCatching {
        api.getTracks()
    }

    suspend fun search(query: String): Result<List<CatalogTrack>> = runCatching {
        api.search(query)
    }

    suspend fun getStats(): Result<Stats> = runCatching {
        api.getStats()
    }

    suspend fun recordPlay(trackId: Int): Result<Unit> = runCatching {
        api.recordPlay(trackId)
        Unit
    }

    suspend fun getStreamToken(trackId: Int): Result<String> = runCatching {
        api.getStreamToken(trackId).token
    }

    suspend fun patchTrackMetadata(trackId: Int, update: MetadataUpdateRequest): Result<Unit> = runCatching {
        api.patchTrackMetadata(trackId, update)
        Unit
    }

    fun buildAlbumGroups(tracks: List<CatalogTrack>): List<AlbumGroup> {
        return tracks
            .groupBy { it.displayAlbum }
            .map { (albumName, albumTracks) ->
                val representative = albumTracks
                    .sortedWith(
                        compareBy(
                            { it.discNumber ?: 0 },
                            { it.trackNumber ?: 0 }
                        )
                    )
                    .first()
                AlbumGroup(
                    albumName = albumName,
                    artist = representative.albumArtist ?: representative.displayArtist,
                    year = representative.year,
                    trackCount = albumTracks.size,
                    coverTrackId = albumTracks.firstOrNull { it.hasCover }?.id ?: representative.id,
                )
            }
            .sortedWith(compareBy({ it.artist.lowercase() }, { it.albumName.lowercase() }))
    }
}
