package nl.muorg.android.data.repository

import nl.muorg.android.data.api.Playlist
import nl.muorg.android.data.api.bodyOrThrow
import nl.muorg.android.data.api.orThrow
import nl.muorg.android.data.api.schema.CreateBody
import nl.muorg.android.data.api.schema.MuorgApi
import nl.muorg.android.data.api.schema.ReorderBody
import nl.muorg.android.data.api.schema.SmartCreateBody
import nl.muorg.android.data.api.schema.TrackIdsBody
import nl.muorg.android.data.api.schema.UpdateBody
import nl.muorg.android.data.api.toDomain
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thrown when something tries to put a track into a rule-driven playlist.
 *
 * A smart playlist's membership is computed from its rules; the join table an
 * add writes to is never read for it. The server accepts the write, so without
 * this the row lands in the database, the playlist does not change, and nothing
 * anywhere reports a problem.
 */
class SmartPlaylistNotEditable(playlistName: String) :
    IllegalArgumentException("\"$playlistName\" is a smart playlist — its tracks come from its rules")

@Singleton
class PlaylistRepository @Inject constructor(
    private val api: MuorgApi,
) {
    suspend fun getPlaylists(): Result<List<Playlist>> = runCatching {
        api.listPlaylists().bodyOrThrow("GET /api/playlists").toDomain()
    }

    suspend fun createPlaylist(name: String, icon: String): Result<Playlist> = runCatching {
        val created = api.createPlaylist(CreateBody(name = name))
            .bodyOrThrow("POST /api/playlists").toDomain()
        if (icon.isNotBlank()) {
            // The create route takes a name only; the icon is a follow-up patch.
            api.updatePlaylist(created.id.toLong(), UpdateBody(icon = icon))
                .orThrow("PATCH /api/playlists/${created.id}")
            created.copy(icon = icon)
        } else {
            created
        }
    }

    suspend fun updatePlaylist(id: Int, name: String?, icon: String?): Result<Unit> = runCatching {
        api.updatePlaylist(id.toLong(), UpdateBody(name = name, icon = icon))
            .orThrow("PATCH /api/playlists/$id")
    }

    suspend fun deletePlaylist(id: Int): Result<Unit> = runCatching {
        api.deletePlaylist(id.toLong()).orThrow("DELETE /api/playlists/$id")
    }

    /**
     * A playlist's track ids, taking the smart route when it has rules.
     *
     * This is the one to call. A smart playlist stores rules, not rows, so the
     * plain `/tracks` endpoint reads an empty join table and returns nothing —
     * the playlist opens and no songs appear. Prefer this over
     * [getPlaylistTracks] unless you have already established the playlist is
     * not smart.
     */
    suspend fun getTracksFor(playlist: Playlist): Result<List<Int>> =
        if (playlist.smartRules != null) getSmartTracks(playlist.id) else getPlaylistTracks(playlist.id)

    /**
     * Same, for a caller that has only the id.
     *
     * Costs one extra request to learn whether the playlist is smart; if you
     * already hold the [Playlist], use [getTracksFor].
     */
    suspend fun getTracksForId(playlistId: Int): Result<List<Int>> = runCatching {
        val playlist = getPlaylists().getOrThrow().find { it.id == playlistId }
            ?: error("No playlist with id $playlistId")
        getTracksFor(playlist).getOrThrow()
    }

    suspend fun createSmartPlaylist(name: String, rulesJson: String): Result<Playlist> = runCatching {
        api.createSmartPlaylist(SmartCreateBody(name = name, rulesJson = rulesJson))
            .bodyOrThrow("POST /api/playlists/smart").toDomain()
    }

    /** The raw smart endpoint. Callers usually want [getTracksFor] instead. */
    suspend fun getSmartTracks(id: Int): Result<List<Int>> = runCatching {
        api.getSmartPlaylistTracks(id.toLong())
            .bodyOrThrow("GET /api/playlists/smart/$id/tracks").map { it.toInt() }
    }

    /**
     * The raw join-table endpoint — rows explicitly added to the playlist.
     *
     * Returns an empty list for a smart playlist, whose membership is computed
     * from rules and lives nowhere in that table. Callers usually want
     * [getTracksFor] or [getTracksForId] instead.
     */
    suspend fun getPlaylistTracks(id: Int): Result<List<Int>> = runCatching {
        api.getPlaylistTracks(id.toLong())
            .bodyOrThrow("GET /api/playlists/$id/tracks").map { it.toInt() }
    }

    /**
     * Add tracks to a playlist, refusing one whose membership comes from rules.
     *
     * This is the one to call. See [SmartPlaylistNotEditable] for why adding to
     * a smart playlist is worse than an error: it silently does nothing.
     */
    suspend fun addTracks(playlist: Playlist, trackIds: List<Int>): Result<Unit> =
        if (playlist.smartRules != null) Result.failure(SmartPlaylistNotEditable(playlist.name))
        else addTracksUnchecked(playlist.id, trackIds)

    /**
     * Same, for a caller that has only the id.
     *
     * Costs one extra request to learn whether the playlist is smart; if you
     * already hold the [Playlist], use the overload above. Mirrors
     * [getTracksFor] / [getTracksForId].
     */
    suspend fun addTracks(playlistId: Int, trackIds: List<Int>): Result<Unit> = runCatching {
        val playlist = getPlaylists().getOrThrow().find { it.id == playlistId }
            ?: error("No playlist with id $playlistId")
        if (playlist.smartRules != null) throw SmartPlaylistNotEditable(playlist.name)
        addTracksUnchecked(playlistId, trackIds).getOrThrow()
    }

    /** The raw join-table endpoint. Callers want [addTracks]. */
    private suspend fun addTracksUnchecked(playlistId: Int, trackIds: List<Int>): Result<Unit> = runCatching {
        api.addPlaylistTracks(playlistId.toLong(), TrackIdsBody(trackIds.map { it.toLong() }))
            .orThrow("POST /api/playlists/$playlistId/tracks")
    }

    suspend fun removeTracks(playlistId: Int, trackIds: List<Int>): Result<Unit> = runCatching {
        api.removePlaylistTracks(playlistId.toLong(), TrackIdsBody(trackIds.map { it.toLong() }))
            .orThrow("DELETE /api/playlists/$playlistId/tracks")
    }

    suspend fun reorderTracks(playlistId: Int, trackIds: List<Int>): Result<Unit> = runCatching {
        api.reorderPlaylistTracks(playlistId.toLong(), ReorderBody(trackIds.map { it.toLong() }))
            .orThrow("PUT /api/playlists/$playlistId/tracks/order")
    }
}
