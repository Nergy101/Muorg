package nl.muorg.android.ui.component

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.muorg.android.data.api.CatalogTrack
import nl.muorg.android.data.preferences.AppPreferences
import nl.muorg.android.data.repository.DownloadProgress
import nl.muorg.android.data.repository.LibraryRepository
import nl.muorg.android.data.repository.OfflineDownloadManager
import javax.inject.Inject

/** Offline state for the album action sheet: which of its tracks are on disk, and a download button. */
@HiltViewModel
class AlbumActionsViewModel @Inject constructor(
    private val offline: OfflineDownloadManager,
    private val library: LibraryRepository,
    preferences: AppPreferences,
) : ViewModel() {

    val downloadedTrackIds: StateFlow<Set<Int>> = offline.observeDownloadedTrackIds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val progress: StateFlow<DownloadProgress> = offline.downloadProgress

    /** Local libraries already live on the device and offline mode has no server to ask. */
    val canDownload: StateFlow<Boolean> =
        combine(preferences.musicMode, preferences.offlineMode) { mode, offlineMode ->
            mode != "local" && !offlineMode
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _tracks = MutableStateFlow<List<CatalogTrack>>(emptyList())
    val tracks: StateFlow<List<CatalogTrack>> = _tracks.asStateFlow()

    fun load(albumName: String) {
        viewModelScope.launch {
            library.getAllTracks().onSuccess { all ->
                _tracks.value = all
                    .filter { it.displayAlbum == albumName }
                    .sortedWith(compareBy({ it.discNumber ?: 0 }, { it.trackNumber ?: 0 }))
            }
        }
    }

    fun download(albumKey: String) {
        val tracks = _tracks.value
        if (tracks.isNotEmpty()) offline.downloadAlbum(albumKey, tracks)
    }

    fun removeDownload() = offline.removeAlbum(_tracks.value.map { it.id })
}
