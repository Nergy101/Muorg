package nl.muorg.android.ui.screen.scanning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.muorg.android.data.api.schema.MuorgApi
import nl.muorg.android.data.preferences.AppPreferences
import nl.muorg.android.data.repository.LocalLibraryRepository
import javax.inject.Inject

enum class ScanKind { LOCAL, REMOTE }

enum class ScanPhase {
    /** Scanning the device's folders, or waiting for the server's startup scan. */
    WORKING,

    /** Everything is scanned / the server is ready. */
    DONE,

    /** The local scan threw. The library may be empty; Settings can rescan. */
    FAILED,
}

data class ScanUiState(
    /** Null until the saved music source has been read. */
    val kind: ScanKind? = null,
    val phase: ScanPhase = ScanPhase.WORKING,
    /** Local: files read so far. [total] is 0 while the files are still being counted. */
    val scanned: Int = 0,
    val total: Int = 0,
    /** Local: tracks in the library once the scan finished. */
    val trackCount: Int = 0,
    /** Remote: the server answered, and reports that its own library scan is still running. */
    val serverBusy: Boolean = false,
    /** Remote: the last poll got no usable answer. */
    val serverUnreachable: Boolean = false,
    /** Set once the screen has done its job (or was skipped): time to move on. */
    val finished: Boolean = false,
)

/**
 * The "scanning" screen shown once, right after the user finishes setting up
 * a music source. Local: scans the chosen folders. Remote: waits until the
 * server's startup scan is over (`GET /api/health` answers `Scanning` until
 * then). Either way it stays up for at least [MIN_VISIBLE_MS] so a source that
 * is already ready does not flash past, then lingers [DONE_LINGER_MS] on its
 * result before [ScanUiState.finished] flips.
 */
@HiltViewModel
class ScanningViewModel @Inject constructor(
    private val preferences: AppPreferences,
    private val api: MuorgApi,
    private val localLibraryRepository: LocalLibraryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    private val work: Job = viewModelScope.launch {
        val kind = if (preferences.musicMode.first() == "local") ScanKind.LOCAL else ScanKind.REMOTE
        _uiState.update { it.copy(kind = kind) }

        val minimumVisible = launch { delay(MIN_VISIBLE_MS) }
        val succeeded = when (kind) {
            ScanKind.LOCAL -> scanLocalFolders()
            ScanKind.REMOTE -> waitForServer()
        }
        minimumVisible.join()

        _uiState.update { it.copy(phase = if (succeeded) ScanPhase.DONE else ScanPhase.FAILED) }
        delay(DONE_LINGER_MS)
        _uiState.update { it.copy(finished = true) }
    }

    /**
     * Leaves without waiting. Cancels the scan: a half-finished local scan
     * writes nothing, and Home starts the library's automatic first scan
     * itself when it finds the library empty.
     */
    fun skip() {
        work.cancel()
        _uiState.update { it.copy(finished = true) }
    }

    /**
     * A full scan rather than `scanIfEmpty`: the user has just confirmed which
     * folders to use, so the library has to match them even if a previous
     * source left tracks behind.
     */
    private suspend fun scanLocalFolders(): Boolean {
        val folders = preferences.localFolderUris.first()
        return try {
            val count = localLibraryRepository.scanAndSave(folders) { done, total ->
                _uiState.update { it.copy(scanned = done, total = total) }
            }
            _uiState.update { it.copy(trackCount = count) }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun waitForServer(): Boolean {
        while (true) {
            // Retrofit buffers a non-@Streaming body inside the call, so
            // reading it here does no I/O.
            val reply = try {
                api.health().takeIf { it.isSuccessful }?.body()?.string()?.trim()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            when (reply) {
                null -> _uiState.update { it.copy(serverUnreachable = true) }
                SERVER_SCANNING -> _uiState.update { it.copy(serverBusy = true, serverUnreachable = false) }
                // "Healthy", or any other 2xx body: the server answered and
                // does not claim to be busy.
                else -> return true
            }
            delay(POLL_MS)
        }
    }

    companion object {
        const val MIN_VISIBLE_MS = 2_000L
        const val DONE_LINGER_MS = 1_200L
        const val POLL_MS = 1_500L
        private const val SERVER_SCANNING = "Scanning"
    }
}
