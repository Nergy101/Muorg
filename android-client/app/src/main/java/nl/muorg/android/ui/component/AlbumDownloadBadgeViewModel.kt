package nl.muorg.android.ui.component

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.muorg.android.data.api.CatalogTrack
import nl.muorg.android.data.repository.LibraryRepository
import nl.muorg.android.data.repository.OfflineDownloadManager
import javax.inject.Inject

/**
 * Which albums are fully on disk, for the downloaded badge on album cards.
 * Cards in the same screen share one instance, so the track list is loaded
 * once per screen, not once per card.
 */
@HiltViewModel
class AlbumDownloadBadgeViewModel @Inject constructor(
    private val offline: OfflineDownloadManager,
    private val library: LibraryRepository,
) : ViewModel() {

    private val allTracks = MutableStateFlow<List<CatalogTrack>>(emptyList())

    /** Album names whose every track is downloaded. */
    val downloadedAlbums: StateFlow<Set<String>> = combine(
        offline.observeDownloadedTrackIds(),
        allTracks,
    ) { ids, tracks ->
        if (tracks.isEmpty()) emptySet()
        else tracks
            .groupBy { it.displayAlbum }
            .filter { (_, albumTracks) -> albumTracks.isNotEmpty() && albumTracks.all { it.id in ids } }
            .keys
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    init {
        viewModelScope.launch {
            library.getAllTracks().onSuccess { allTracks.value = it }
        }
    }
}
