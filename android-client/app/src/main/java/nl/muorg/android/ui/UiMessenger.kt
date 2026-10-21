package nl.muorg.android.ui

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import nl.muorg.android.data.api.OfflineException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

data class UiMessage(
    val text: String,
    val actionLabel: String? = null,
    val onAction: (suspend () -> Unit)? = null,
)

/**
 * The one Snackbar channel for the whole app, rendered by the host in
 * [nl.muorg.android.ui.navigation.NavGraph]. Replaces the four dead
 * per-ViewModel toast fields (`addToastMsg` × 3, `PlaylistsUiState.error`)
 * that nothing ever rendered, plus `PlayerViewModel`'s own `toastEvent`.
 */
@Singleton
class UiMessenger @Inject constructor() {
    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    fun show(text: String) {
        _messages.tryEmit(UiMessage(text))
    }

    fun showWithAction(text: String, actionLabel: String, onAction: suspend () -> Unit) {
        _messages.tryEmit(UiMessage(text, actionLabel, onAction))
    }
}

/**
 * Short, user-facing text for a failure, instead of raw Retrofit/exception
 * text reaching the Snackbar.
 */
fun Throwable.friendlyMessage(): String = when (this) {
    is OfflineException -> "You're offline"
    is IOException -> "Can't reach the server"
    else -> message ?: "Something went wrong"
}
