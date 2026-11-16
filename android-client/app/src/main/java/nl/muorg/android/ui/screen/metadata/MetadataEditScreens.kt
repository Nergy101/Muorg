package nl.muorg.android.ui.screen.metadata

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.muorg.android.data.api.CatalogTrack
import nl.muorg.android.data.api.MetadataUpdateRequest
import nl.muorg.android.data.api.schema.BatchMetadataItem
import nl.muorg.android.data.preferences.AppPreferences
import nl.muorg.android.data.repository.LibraryRepository
import nl.muorg.android.data.repository.LocalLibraryRepository
import nl.muorg.android.ui.OrcMood
import nl.muorg.android.ui.UiMessenger
import nl.muorg.android.ui.component.LocalBottomInset
import nl.muorg.android.ui.component.OrcIllustration
import nl.muorg.android.ui.component.OrcLoading
import nl.muorg.android.ui.component.OrcVariant
import nl.muorg.android.ui.friendlyMessage
import nl.muorg.android.ui.icon.MageIcon
import nl.muorg.android.ui.icon.mageIconRes
import nl.muorg.android.util.AlbumRenames
import nl.muorg.android.data.WikipediaCover
import javax.inject.Inject

/** The value shared by every track of the album, or "" when they differ. */
private fun shared(values: List<String?>): String {
    if (values.isEmpty()) return ""
    val first = values[0] ?: ""
    return if (values.all { (it ?: "") == first }) first else ""
}

/** One cover candidate in the picker: a Wikipedia image URL, or the album's
 *  current cover (isOriginal) served by the Muorg server. */
data class CoverCandidate(
    val url: String,
    val isOriginal: Boolean = false,
)

data class AlbumEditUiState(
    val albumName: String = "",
    val tracks: List<CatalogTrack> = emptyList(),
    val isLoading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
    val album: String = "",
    val albumArtist: String = "",
    val year: String = "",
    val genre: String = "",
    val coverSearching: Boolean = false,
    val coverPickerVisible: Boolean = false,
    val coverCandidates: List<CoverCandidate> = emptyList(),
    val applyingCover: Boolean = false,
)

@HiltViewModel
class AlbumEditViewModel @Inject constructor(
    private val repository: LibraryRepository,
    private val localRepository: LocalLibraryRepository,
    private val preferences: AppPreferences,
    private val uiMessenger: UiMessenger,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlbumEditUiState())
    val uiState: StateFlow<AlbumEditUiState> = _uiState.asStateFlow()

    /** Emits the album's NEW name when a save renames it, so the screen can
     *  re-open this editor on the renamed album instead of showing nothing. */
    private val _renamed = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val renamed: SharedFlow<String> = _renamed.asSharedFlow()

    /** The form as loaded, so save() can tell which fields the user changed. */
    private var snapshot: AlbumEditUiState? = null

    /**
     * The album's track ids, resolved from the route's album name. From then on
     * the album is followed BY ID: the album name is part of the identity, so
     * renaming the album in this form must not lose the track list.
     */
    private var trackIds: Set<Int> = emptySet()

    fun load(albumName: String) {
        val current = _uiState.value
        if (current.albumName == albumName && current.tracks.isNotEmpty()) return
        // A route holding the pre-rename name forwards to the renamed album.
        val resolved = AlbumRenames.resolve(albumName)
        if (resolved != albumName) {
            _renamed.tryEmit(resolved)
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, albumName = albumName, error = null) }
            val local = preferences.musicMode.first() == "local"
            val result = if (local) runCatching { localRepository.getAllTracks() }
            else repository.getAllTracks()
            result.fold(
                onSuccess = { all ->
                    val tracks = all
                        .filter { it.displayAlbum == albumName }
                        .sortedWith(compareBy({ it.discNumber ?: 0 }, { it.trackNumber ?: 0 }))
                    trackIds = tracks.map { it.id }.toSet()
                    applyTracks(albumName, tracks)
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.friendlyMessage()) }
                },
            )
        }
    }

    private fun applyTracks(albumName: String, tracks: List<CatalogTrack>) {
        val state = AlbumEditUiState(
            albumName = albumName,
            tracks = tracks,
            isLoading = false,
            album = shared(tracks.map { it.album }),
            albumArtist = shared(tracks.map { it.albumArtist }),
            year = shared(tracks.map { it.year?.toString() }),
            genre = shared(tracks.map { it.genre }),
        )
        snapshot = state
        _uiState.value = state
    }

    /** Re-resolves the album's tracks by id after a save (the catalog cache was
     *  invalidated by the write) and reports a rename via [renamed]. */
    private fun refreshAfterSave() {
        viewModelScope.launch {
            val oldName = _uiState.value.albumName
            val local = preferences.musicMode.first() == "local"
            val result = if (local) runCatching { localRepository.getAllTracks() }
            else repository.getAllTracks()
            result.onSuccess { all ->
                val ids = trackIds
                val tracks = all
                    .filter { it.id in ids || (local && -it.id in ids) }
                    .sortedWith(compareBy({ it.discNumber ?: 0 }, { it.trackNumber ?: 0 }))
                if (tracks.isEmpty()) return@onSuccess
                val newName = tracks.first().displayAlbum
                applyTracks(newName, tracks)
                if (newName != oldName) {
                    // Remember the rename so stale back-stack entries (the album
                    // page below this editor) forward to the new name.
                    AlbumRenames.record(oldName, newName)
                    _renamed.tryEmit(newName)
                }
            }
        }
    }

    fun onAlbumChange(value: String) = _uiState.update { it.copy(album = value) }
    fun onAlbumArtistChange(value: String) = _uiState.update { it.copy(albumArtist = value) }
    fun onYearChange(value: String) = _uiState.update { it.copy(year = value) }
    fun onGenreChange(value: String) = _uiState.update { it.copy(genre = value) }

    /** Writes only the fields that differ from the loaded values to every
     *  track of the album — the batch endpoint on the server, one local
     *  update per track in local mode. */
    /**
     * Auto-search Wikipedia for this album's cover candidates (port of the
     * legacy desktop app's auto-search cover feature). Nothing is written yet:
     * the candidates open in a picker and only the one the user taps is
     * written, as a perfect square, to every track of the album.
     */
    fun searchCoverOnline() {
        val state = _uiState.value
        if (state.coverSearching || state.tracks.isEmpty()) return
        viewModelScope.launch {
            if (preferences.musicMode.first() == "local") {
                uiMessenger.show("Cover search is only available in server mode", OrcMood.Worried)
                return@launch
            }
            _uiState.update { it.copy(coverSearching = true) }
            val found = WikipediaCover.findAlbumCoverUrls(state.album, state.albumArtist, limit = 6)
            _uiState.update { it.copy(coverSearching = false) }
            if (found.isFailure) {
                uiMessenger.show("Cover search failed: ${found.exceptionOrNull()?.message}", OrcMood.Worried)
                return@launch
            }
            // The album's current cover is always the first candidate, tagged Original.
            val original = state.tracks.firstOrNull { it.hasCover }?.let { t ->
                CoverCandidate(
                    url = "${preferences.serverUrlState.value}/api/tracks/${t.id}/cover?size=1200",
                    isOriginal = true,
                )
            }
            val candidates = listOfNotNull(original) + found.getOrThrow().map { CoverCandidate(url = it) }
            _uiState.update { it.copy(coverCandidates = candidates, coverPickerVisible = true) }
        }
    }

    fun dismissCoverPicker() {
        _uiState.update { it.copy(coverPickerVisible = false, coverCandidates = emptyList()) }
    }

    /** Write the picked candidate as a perfect square to every track of the album. */
    fun applyCover(candidate: CoverCandidate) {
        val state = _uiState.value
        if (state.applyingCover || state.tracks.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(applyingCover = true) }
            val headers = if (candidate.isOriginal && preferences.apiKeyState.value.isNotBlank()) {
                mapOf("Authorization" to "Bearer ${preferences.apiKeyState.value}")
            } else {
                emptyMap()
            }
            val base64 = WikipediaCover.downloadSquareCoverBase64(candidate.url, headers).getOrNull()
            if (base64 == null) {
                _uiState.update { it.copy(applyingCover = false) }
                uiMessenger.show("Could not download the selected cover", OrcMood.Worried)
                return@launch
            }
            val items = state.tracks.map { t ->
                BatchMetadataItem(id = t.id.toLong(), pictureBase64 = base64)
            }
            repository.batchPatchMetadata(items).fold(
                onSuccess = { n ->
                    uiMessenger.show("Set cover on $n track${if (n == 1) "" else "s"}", OrcMood.Happy)
                    refreshAfterSave()
                },
                onFailure = { e -> uiMessenger.show("Failed to set cover: ${e.friendlyMessage()}", OrcMood.Worried) },
            )
            _uiState.update { it.copy(applyingCover = false, coverPickerVisible = false, coverCandidates = emptyList()) }
        }
    }

    fun save() {
        val state = _uiState.value
        val base = snapshot ?: return
        if (state.tracks.isEmpty() || state.saving) return
        fun changed(current: String, original: String): String? =
            if (current != original) current.trim().takeIf { it.isNotBlank() } else null
        val update = MetadataUpdateRequest(
            album = changed(state.album, base.album),
            albumArtist = changed(state.albumArtist, base.albumArtist),
            genre = changed(state.genre, base.genre),
            year = if (state.year != base.year) state.year.trim().toIntOrNull() else null,
        )
        if (!update.hasAnyField()) {
            uiMessenger.show("No changes to save")
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true) }
            val local = preferences.musicMode.first() == "local"
            if (local) {
                var failed = 0
                for (t in state.tracks) {
                    runCatching { localRepository.updateTrackMetadata(-t.id, update) }
                        .onFailure { failed++ }
                }
                if (failed == 0) uiMessenger.show("Edited ${state.tracks.size} tracks", OrcMood.Happy)
                else uiMessenger.show("Saved ${state.tracks.size - failed} of ${state.tracks.size} tracks", OrcMood.Worried)
                refreshAfterSave()
            } else {
                // The batch route never backs up — say so before writing.
                uiMessenger.show("Writing tags to ${state.tracks.size} tracks — no backup is made")
                val items = state.tracks.map { t ->
                    BatchMetadataItem(
                        id = t.id.toLong(),
                        album = update.album,
                        albumArtist = update.albumArtist,
                        genre = update.genre,
                        year = update.year,
                    )
                }
                repository.batchPatchMetadata(items).fold(
                    onSuccess = { n ->
                        uiMessenger.show("Edited $n track${if (n == 1) "" else "s"}", OrcMood.Happy)
                        refreshAfterSave()
                    },
                    onFailure = { e -> uiMessenger.show("Failed to save: ${e.friendlyMessage()}", OrcMood.Worried) },
                )
            }
            _uiState.update { it.copy(saving = false) }
        }
    }
}

data class TrackEditUiState(
    val track: CatalogTrack? = null,
    val isLoading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumArtist: String = "",
    val featuring: String = "",
    val genre: String = "",
    val year: String = "",
    val trackNumber: String = "",
    val discNumber: String = "",
)

@HiltViewModel
class TrackEditViewModel @Inject constructor(
    private val repository: LibraryRepository,
    private val localRepository: LocalLibraryRepository,
    private val preferences: AppPreferences,
    private val uiMessenger: UiMessenger,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrackEditUiState())
    val uiState: StateFlow<TrackEditUiState> = _uiState.asStateFlow()
    private var snapshot: TrackEditUiState? = null

    fun load(trackId: Int) {
        if (_uiState.value.track?.id == trackId && _uiState.value.track != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val local = preferences.musicMode.first() == "local"
            val result = if (local) runCatching { localRepository.getAllTracks() }
            else repository.getAllTracks()
            result.fold(
                onSuccess = { all ->
                    val track = all.firstOrNull { it.id == trackId || (local && -it.id == trackId) }
                    if (track == null) {
                        _uiState.update { it.copy(isLoading = false, error = "Track not found") }
                        return@fold
                    }
                    val state = TrackEditUiState(
                        track = track,
                        isLoading = false,
                        title = track.title ?: "",
                        artist = track.artist ?: "",
                        album = track.album ?: "",
                        albumArtist = track.albumArtist ?: "",
                        featuring = track.featuring ?: "",
                        genre = track.genre ?: "",
                        year = track.year?.toString() ?: "",
                        trackNumber = track.trackNumber?.toString() ?: "",
                        discNumber = track.discNumber?.toString() ?: "",
                    )
                    snapshot = state
                    _uiState.value = state
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.friendlyMessage()) }
                },
            )
        }
    }

    fun onTitleChange(v: String) = _uiState.update { it.copy(title = v) }
    fun onArtistChange(v: String) = _uiState.update { it.copy(artist = v) }
    fun onAlbumChange(v: String) = _uiState.update { it.copy(album = v) }
    fun onAlbumArtistChange(v: String) = _uiState.update { it.copy(albumArtist = v) }
    fun onFeaturingChange(v: String) = _uiState.update { it.copy(featuring = v) }
    fun onGenreChange(v: String) = _uiState.update { it.copy(genre = v) }
    fun onYearChange(v: String) = _uiState.update { it.copy(year = v) }
    fun onTrackNumberChange(v: String) = _uiState.update { it.copy(trackNumber = v) }
    fun onDiscNumberChange(v: String) = _uiState.update { it.copy(discNumber = v) }

    fun save() {
        val state = _uiState.value
        val track = state.track ?: return
        val base = snapshot ?: return
        if (state.saving) return
        fun changed(current: String, original: String): String? =
            if (current != original) current.trim().takeIf { it.isNotBlank() } else null
        fun changedInt(current: String, original: String): Int? =
            if (current != original) current.trim().toIntOrNull() else null
        val update = MetadataUpdateRequest(
            title = changed(state.title, base.title),
            artist = changed(state.artist, base.artist),
            album = changed(state.album, base.album),
            albumArtist = changed(state.albumArtist, base.albumArtist),
            featuring = changed(state.featuring, base.featuring),
            genre = changed(state.genre, base.genre),
            year = changedInt(state.year, base.year),
            trackNumber = changedInt(state.trackNumber, base.trackNumber),
            discNumber = changedInt(state.discNumber, base.discNumber),
        )
        if (!update.hasAnyField()) {
            uiMessenger.show("No changes to save")
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true) }
            val local = preferences.musicMode.first() == "local"
            val result = if (local) {
                runCatching { localRepository.updateTrackMetadata(-track.id, update) }
            } else {
                repository.patchTrackMetadata(
                    track.id,
                    update.copy(backupBeforeWrite = preferences.backupBeforeWrite.first()),
                )
            }
            result.fold(
                onSuccess = {
                    uiMessenger.show("Saved \"${track.displayTitle}\"", OrcMood.Happy)
                    load(track.id)
                },
                onFailure = { e -> uiMessenger.show("Failed to save: ${e.friendlyMessage()}", OrcMood.Worried) },
            )
            _uiState.update { it.copy(saving = false) }
        }
    }
}

/**
 * The album metadata editor: album-level fields on top (pre-filled with the
 * value shared by all tracks, saved to every track of the album) and the
 * album's track list underneath, each row opening the per-track editor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumEditScreen(
    albumName: String,
    onBack: () -> Unit,
    onEditTrack: (trackId: Int) -> Unit,
    onAlbumRenamed: (newAlbumName: String) -> Unit = {},
    viewModel: AlbumEditViewModel = hiltViewModel(),
) {
    LaunchedEffect(albumName) { viewModel.load(albumName) }
    // A save that renames the album re-opens this editor on the new name, so
    // the screen keeps showing the edited album instead of an empty list.
    LaunchedEffect(Unit) {
        viewModel.renamed.collect { newName -> onAlbumRenamed(newName) }
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = LocalBottomInset.current + 8.dp),
        ) {
            item {
                EditHeader(title = "Edit album metadata", subtitle = albumName, onBack = onBack)
            }
            if (uiState.isLoading) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) { OrcLoading() }
                }
            } else if (uiState.error != null || uiState.tracks.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        OrcIllustration(OrcVariant.NotFound, modifier = Modifier.size(160.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(
                            uiState.error ?: "Album not found",
                            color = if (uiState.error != null) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text(
                            "Shared values are pre-filled; only the fields you change are written to all " +
                                "${uiState.tracks.size} track${if (uiState.tracks.size == 1) "" else "s"} of this album.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        EditField("Album title", uiState.album, viewModel::onAlbumChange)
                        EditField("Album artist", uiState.albumArtist, viewModel::onAlbumArtistChange)
                        EditField("Genre", uiState.genre, viewModel::onGenreChange)
                        EditField(
                            "Release year", uiState.year, viewModel::onYearChange,
                            keyboardType = KeyboardType.Number,
                        )
                        Spacer(Modifier.height(8.dp))
                        // Auto-search the album cover on Wikipedia and show the
                        // candidates in a picker; only the tapped one is written
                        // to every track of the album.
                        OutlinedButton(
                            onClick = viewModel::searchCoverOnline,
                            enabled = !uiState.saving && !uiState.coverSearching,
                        ) {
                            if (uiState.coverSearching) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(if (uiState.coverSearching) "Searching cover…" else "Search cover online")
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(onClick = viewModel::save, enabled = !uiState.saving) {
                                Text(if (uiState.saving) "Saving…" else "Save")
                            }
                            Spacer(Modifier.width(12.dp))
                            TextButton(onClick = onBack) { Text("Cancel") }
                        }
                    }
                }
                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    Text(
                        "TRACKS — TAP A TRACK TO EDIT IT",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.8.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                }
                items(uiState.tracks, key = { it.id }) { track ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEditTrack(track.id) }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            track.trackNumber?.toString() ?: "·",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(28.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                track.displayTitle,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                            )
                            Text(
                                track.displayArtist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        Icon(
                            painter = painterResource(mageIconRes("chevron-right")),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
        if (uiState.coverPickerVisible) {
            ModalBottomSheet(
                onDismissRequest = viewModel::dismissCoverPicker,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        "Pick a cover",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "Tap a candidate to write it as a square cover to all " +
                            "${uiState.tracks.size} track${if (uiState.tracks.size == 1) "" else "s"} of this album.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        items(uiState.coverCandidates, key = { it.url }) { candidate ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box {
                                    AsyncImage(
                                        model = candidate.url,
                                        contentDescription = if (candidate.isOriginal) "Current cover" else "Wikipedia cover candidate",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f)
                                            .clickable(enabled = !uiState.applyingCover) {
                                                viewModel.applyCover(candidate)
                                            },
                                    )
                                    if (uiState.applyingCover) {
                                        CircularProgressIndicator(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .align(Alignment.Center),
                                            strokeWidth = 2.dp,
                                        )
                                    }
                                }
                                if (candidate.isOriginal) {
                                    Text(
                                        "Original",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The per-track metadata editor page. */
@Composable
fun TrackEditScreen(
    trackId: Int,
    onBack: () -> Unit,
    onEditAlbum: (albumName: String) -> Unit,
    viewModel: TrackEditViewModel = hiltViewModel(),
) {
    LaunchedEffect(trackId) { viewModel.load(trackId) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = LocalBottomInset.current + 8.dp),
        ) {
            item {
                EditHeader(
                    title = "Edit track",
                    subtitle = uiState.track?.displayTitle ?: "",
                    onBack = onBack,
                )
            }
            if (uiState.isLoading) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) { OrcLoading() }
                }
            } else if (uiState.error != null || uiState.track == null) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        OrcIllustration(OrcVariant.NotFound, modifier = Modifier.size(160.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(
                            uiState.error ?: "Track not found",
                            color = if (uiState.error != null) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        EditField("Title", uiState.title, viewModel::onTitleChange)
                        EditField("Artist", uiState.artist, viewModel::onArtistChange)
                        EditField("Album", uiState.album, viewModel::onAlbumChange)
                        EditField("Album artist", uiState.albumArtist, viewModel::onAlbumArtistChange)
                        EditField("Featuring", uiState.featuring, viewModel::onFeaturingChange)
                        EditField("Genre", uiState.genre, viewModel::onGenreChange)
                        EditField("Year", uiState.year, viewModel::onYearChange, keyboardType = KeyboardType.Number)
                        EditField("Track #", uiState.trackNumber, viewModel::onTrackNumberChange, keyboardType = KeyboardType.Number)
                        EditField("Disc #", uiState.discNumber, viewModel::onDiscNumberChange, keyboardType = KeyboardType.Number)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(onClick = viewModel::save, enabled = !uiState.saving) {
                                Text(if (uiState.saving) "Saving…" else "Save")
                            }
                            Spacer(Modifier.width(12.dp))
                            TextButton(onClick = onBack) { Text("Cancel") }
                        }
                        val album = uiState.track?.album
                        if (!album.isNullOrBlank()) {
                            Spacer(Modifier.height(4.dp))
                            TextButton(onClick = { onEditAlbum(album) }) {
                                MageIcon("edit", modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Edit the whole album instead")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                painter = painterResource(mageIconRes("chevron-left")),
                contentDescription = "Back",
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun EditField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    )
}
