package nl.muorg.android.ui.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dagger.hilt.EntryPointAccessors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import nl.muorg.android.R
import nl.muorg.android.player.PlaybackService

/**
 * The resizable Now Playing widget.
 *
 * One provider, one widget id, two layouts: the compact row for short sizes
 * and the expanded panel (album line, previous + favorite) for anything
 * taller than [EXPANDED_MIN_HEIGHT_DP] — chosen from the widget's current
 * options in [onAppWidgetOptionsChanged]/[onUpdate], never by applying two
 * RemoteViews over one id (the last one would simply win).
 *
 * All state comes from [WidgetStateStore], which the app keeps up to date via
 * [ACTION_UPDATE_WIDGET]; that is also what lets the widget render correctly
 * when the system cold-starts this receiver after a reboot ([BOOT_COMPLETED])
 * or while the app process was dead. Transport and favorite buttons are
 * handled here too: they connect a short-lived [MediaController] to
 * [PlaybackService], so the buttons work even when the app itself was not
 * running — and when there is nothing queued at all, play opens the app.
 */
class NowPlayingWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        updateAll(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        render(context, appWidgetManager, appWidgetId, WidgetStateStore.loadNowPlaying(context))
        maybeFetchCover(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_UPDATE_WIDGET -> handleUpdate(context, intent)
            ACTION_PLAY_PAUSE -> handleTransport(context, Transport.PLAY_PAUSE)
            ACTION_NEXT -> handleTransport(context, Transport.NEXT)
            ACTION_PREVIOUS -> handleTransport(context, Transport.PREVIOUS)
            ACTION_TOGGLE_FAVORITE -> handleFavorite(context)
            Intent.ACTION_BOOT_COMPLETED -> updateAll(context)
            else -> super.onReceive(context, intent)
        }
    }

    /** Re-render every instance of this widget from the persisted state. */
    private fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, NowPlayingWidget::class.java))
        val state = WidgetStateStore.loadNowPlaying(context)
        for (appWidgetId in ids) {
            render(context, manager, appWidgetId, state)
        }
        maybeFetchCover(context)
    }

    /**
     * The app's playback state changed. The broadcast always carries
     * [EXTRA_TRACK_ID], which marks the extras as authoritative — a null title
     * then means "nothing playing", not "keep the old title".
     */
    private fun handleUpdate(context: Context, intent: Intent) {
        val prev = WidgetStateStore.loadNowPlaying(context)
        val state = if (intent.hasExtra(EXTRA_TRACK_ID)) {
            WidgetStateStore.NowPlayingState(
                title = intent.getStringExtra(EXTRA_TITLE),
                artist = intent.getStringExtra(EXTRA_ARTIST),
                album = intent.getStringExtra(EXTRA_ALBUM),
                isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, false),
                trackId = intent.getIntExtra(EXTRA_TRACK_ID, 0),
                hasCover = intent.getBooleanExtra(EXTRA_HAS_COVER, false),
                localCoverPath = intent.getStringExtra(EXTRA_LOCAL_COVER_PATH),
                isFavorite = intent.getBooleanExtra(EXTRA_IS_FAVORITE, false),
            )
        } else {
            prev
        }
        WidgetStateStore.saveNowPlaying(context, state)
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, NowPlayingWidget::class.java))
        for (appWidgetId in ids) {
            render(context, manager, appWidgetId, state)
        }
        maybeFetchCover(context)
    }

    private enum class Transport { PLAY_PAUSE, NEXT, PREVIOUS }

    /**
     * A transport button was tapped. Connects a [MediaController] to the
     * playback service — which starts the service if the app process was dead —
     * issues the command, and re-renders the widget from the controller's own
     * state, because in the cold-start case no [nl.muorg.android.player.PlayerController]
     * exists to send [ACTION_UPDATE_WIDGET].
     */
    private fun handleTransport(context: Context, transport: Transport) {
        val pendingResult = goAsync()
        ioScope.launch {
            try {
                val future = MediaController.Builder(
                    context,
                    SessionToken(context, ComponentName(context, PlaybackService::class.java)),
                ).buildAsync()
                try {
                    val controller = try {
                        future.get(5, TimeUnit.SECONDS)
                    } catch (e: Exception) {
                        null
                    }
                    if (controller == null) {
                        if (transport == Transport.PLAY_PAUSE) WidgetIntents.openApp(context)
                        return@launch
                    }
                    when (transport) {
                        Transport.PLAY_PAUSE -> {
                            if (controller.mediaItemCount == 0) {
                                // Nothing queued (fresh process): play opens the app.
                                WidgetIntents.openApp(context)
                            } else if (controller.isPlaying) {
                                controller.pause()
                            } else {
                                controller.play()
                            }
                        }
                        Transport.NEXT -> controller.seekToNextMediaItem()
                        Transport.PREVIOUS -> {
                            if (controller.currentPosition > 3000) controller.seekTo(0L)
                            else controller.seekToPreviousMediaItem()
                        }
                    }
                    val prev = WidgetStateStore.loadNowPlaying(context)
                    val item = controller.currentMediaItem
                    val metadata = item?.mediaMetadata
                    val trackId = item?.mediaId?.toIntOrNull() ?: 0
                    val favorites = EntryPointAccessors
                        .fromApplication(context, WidgetEntryPoint::class.java)
                        .preferences()
                        .favorites
                        .first()
                    val state = WidgetStateStore.NowPlayingState(
                        title = metadata?.title?.toString(),
                        artist = metadata?.artist?.toString(),
                        album = metadata?.albumTitle?.toString(),
                        isPlaying = controller.isPlaying,
                        trackId = trackId,
                        hasCover = if (trackId == prev.trackId) prev.hasCover else trackId > 0,
                        localCoverPath = if (trackId == prev.trackId) prev.localCoverPath else null,
                        isFavorite = trackId != 0 && trackId.toString() in favorites,
                    )
                    WidgetStateStore.saveNowPlaying(context, state)
                    val manager = AppWidgetManager.getInstance(context)
                    val ids = manager.getAppWidgetIds(ComponentName(context, NowPlayingWidget::class.java))
                    for (appWidgetId in ids) {
                        render(context, manager, appWidgetId, state)
                    }
                    maybeFetchCover(context)
                } finally {
                    MediaController.releaseFuture(future)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleFavorite(context: Context) {
        val pendingResult = goAsync()
        ioScope.launch {
            try {
                val prev = WidgetStateStore.loadNowPlaying(context)
                if (prev.trackId != 0) {
                    val preferences = EntryPointAccessors
                        .fromApplication(context, WidgetEntryPoint::class.java)
                        .preferences()
                    preferences.toggleFavorite(prev.trackId.toString())
                    val state = prev.copy(isFavorite = !prev.isFavorite)
                    WidgetStateStore.saveNowPlaying(context, state)
                    val manager = AppWidgetManager.getInstance(context)
                    val ids = manager.getAppWidgetIds(ComponentName(context, NowPlayingWidget::class.java))
                    for (appWidgetId in ids) {
                        render(context, manager, appWidgetId, state)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * Fetch the current track's cover off the main thread (network or disk),
     * cache it, and re-render once it is there — but only if the track is
     * still the persisted one.
     */
    private fun maybeFetchCover(context: Context) {
        val state = WidgetStateStore.loadNowPlaying(context)
        if (state.trackId == 0) return
        if (state.localCoverPath == null && !state.hasCover) return
        if (WidgetStateStore.hasCachedCover(context, state.trackId)) return
        if (WidgetStateStore.knowsNoCover(state.trackId)) return
        val pendingResult = goAsync()
        ioScope.launch {
            try {
                val bitmap = WidgetStateStore.fetchNowPlayingCover(context, state)
                if (bitmap != null && WidgetStateStore.loadNowPlaying(context).trackId == state.trackId) {
                    val manager = AppWidgetManager.getInstance(context)
                    val ids = manager.getAppWidgetIds(ComponentName(context, NowPlayingWidget::class.java))
                    val current = WidgetStateStore.loadNowPlaying(context)
                    for (appWidgetId in ids) {
                        render(context, manager, appWidgetId, current)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun render(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        state: WidgetStateStore.NowPlayingState,
    ) {
        val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
        val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        val expanded = heightDp >= EXPANDED_MIN_HEIGHT_DP
        val layoutId = if (expanded) R.layout.now_playing_widget_expanded
        else R.layout.now_playing_widget_compact

        val views = RemoteViews(context.packageName, layoutId)
        views.setOnClickPendingIntent(R.id.widget_root, WidgetIntents.openAppPendingIntent(context))

        val cover = WidgetStateStore.peekCover(context, state.trackId)
        if (cover != null) {
            views.setImageViewBitmap(R.id.widget_cover, cover)
            views.setViewVisibility(R.id.widget_cover, View.VISIBLE)
            views.setViewVisibility(R.id.widget_cover_placeholder, View.GONE)
        } else {
            views.setViewVisibility(R.id.widget_cover, View.GONE)
            views.setViewVisibility(R.id.widget_cover_placeholder, View.VISIBLE)
        }

        views.setTextViewText(R.id.widget_title, state.title ?: context.getString(R.string.widget_no_track))
        views.setTextViewText(R.id.widget_artist, state.artist.orEmpty())

        views.setImageViewResource(
            R.id.widget_play_pause,
            if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
        )
        views.setInt(R.id.widget_play_pause, "setColorFilter", ICON_TINT)
        views.setOnClickPendingIntent(
            R.id.widget_play_pause,
            WidgetIntents.actionPendingIntent(context, NowPlayingWidget::class.java, ACTION_PLAY_PAUSE, REQUEST_PLAY_PAUSE),
        )
        views.setInt(R.id.widget_next, "setColorFilter", ICON_TINT)
        views.setOnClickPendingIntent(
            R.id.widget_next,
            WidgetIntents.actionPendingIntent(context, NowPlayingWidget::class.java, ACTION_NEXT, REQUEST_NEXT),
        )

        if (expanded) {
            views.setTextViewText(R.id.widget_album, state.album.orEmpty())
            views.setInt(R.id.widget_previous, "setColorFilter", ICON_TINT)
            views.setOnClickPendingIntent(
                R.id.widget_previous,
                WidgetIntents.actionPendingIntent(context, NowPlayingWidget::class.java, ACTION_PREVIOUS, REQUEST_PREVIOUS),
            )
            views.setImageViewResource(
                R.id.widget_favorite,
                if (state.isFavorite) R.drawable.ic_heart_fill else R.drawable.ic_heart,
            )
            views.setInt(
                R.id.widget_favorite,
                "setColorFilter",
                if (state.isFavorite) FAVORITE_TINT else ICON_TINT,
            )
            views.setOnClickPendingIntent(
                R.id.widget_favorite,
                WidgetIntents.actionPendingIntent(context, NowPlayingWidget::class.java, ACTION_TOGGLE_FAVORITE, REQUEST_FAVORITE),
            )
        }

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    companion object {
        const val ACTION_UPDATE_WIDGET = "nl.muorg.android.UPDATE_WIDGET"
        const val ACTION_PLAY_PAUSE = "nl.muorg.android.WIDGET_PLAY_PAUSE"
        const val ACTION_NEXT = "nl.muorg.android.WIDGET_NEXT"
        const val ACTION_PREVIOUS = "nl.muorg.android.WIDGET_PREVIOUS"
        const val ACTION_TOGGLE_FAVORITE = "nl.muorg.android.WIDGET_TOGGLE_FAVORITE"

        const val EXTRA_TITLE = "title"
        const val EXTRA_ARTIST = "artist"
        const val EXTRA_ALBUM = "album"
        const val EXTRA_IS_PLAYING = "is_playing"
        const val EXTRA_TRACK_ID = "track_id"
        const val EXTRA_HAS_COVER = "has_cover"
        const val EXTRA_LOCAL_COVER_PATH = "local_cover_path"
        const val EXTRA_IS_FAVORITE = "is_favorite"

        /** Widgets at least this tall (dp) get the expanded layout; shorter ones the compact row. */
        private const val EXPANDED_MIN_HEIGHT_DP = 100
        private const val ICON_TINT = Color.WHITE
        private const val FAVORITE_TINT = 0xFFEF5350.toInt()
        private const val REQUEST_PLAY_PAUSE = 1
        private const val REQUEST_NEXT = 2
        private const val REQUEST_PREVIOUS = 3
        private const val REQUEST_FAVORITE = 4

        /** Worker scope for the goAsync() bodies — never the main thread. */
        internal val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
