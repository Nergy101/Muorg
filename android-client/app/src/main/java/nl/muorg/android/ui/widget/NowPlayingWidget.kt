package nl.muorg.android.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import android.widget.RemoteViews
import nl.muorg.android.MainActivity
import nl.muorg.android.R
import nl.muorg.android.player.PlaybackService

class NowPlayingWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId, null, null, null, false)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (ACTION_UPDATE_WIDGET == intent.action) {
            val title = intent.getStringExtra(EXTRA_TITLE)
            val artist = intent.getStringExtra(EXTRA_ARTIST)
            val album = intent.getStringExtra(EXTRA_ALBUM)
            val isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, false)

            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, NowPlayingWidget::class.java)
            )
            for (appWidgetId in appWidgetIds) {
                updateWidget(context, appWidgetManager, appWidgetId, title, artist, album, isPlaying)
            }
        }
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        title: String?,
        artist: String?,
        album: String?,
        isPlaying: Boolean?,
    ) {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val noTrack = context.getString(R.string.widget_no_track)
        val displayTitle = title ?: noTrack
        val displayArtist = artist ?: ""
        val displayAlbum = album ?: ""

        // ── Compact (4x1) ──────────────────────────────────────────────────
        val compact = RemoteViews(context.packageName, R.layout.now_playing_widget_compact).apply {
            setOnClickPendingIntent(R.id.widget_cover, openPending)
            setTextViewText(R.id.widget_title, displayTitle)
            setTextViewText(R.id.widget_artist, displayArtist)

            val playIcon = if (isPlaying == true) android.R.drawable.ic_media_pause
            else android.R.drawable.ic_media_play
            setImageViewResource(R.id.widget_play_pause, playIcon)

            setOnClickPendingIntent(
                R.id.widget_play_pause,
                mediaButton(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, requestCode = 1),
            )
            setOnClickPendingIntent(
                R.id.widget_next,
                mediaButton(context, KeyEvent.KEYCODE_MEDIA_NEXT, requestCode = 2),
            )
        }

        // ── Expanded (4x2) ─────────────────────────────────────────────────
        val expanded = RemoteViews(context.packageName, R.layout.now_playing_widget_expanded).apply {
            setOnClickPendingIntent(R.id.widget_cover, openPending)
            setTextViewText(R.id.widget_title, displayTitle)
            setTextViewText(R.id.widget_artist, displayArtist)
            setTextViewText(R.id.widget_album, displayAlbum)

            val playIcon = if (isPlaying == true) android.R.drawable.ic_media_pause
            else android.R.drawable.ic_media_play
            setImageViewResource(R.id.widget_play_pause, playIcon)

            setOnClickPendingIntent(
                R.id.widget_play_pause,
                mediaButton(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, requestCode = 1),
            )
            setOnClickPendingIntent(
                R.id.widget_next,
                mediaButton(context, KeyEvent.KEYCODE_MEDIA_NEXT, requestCode = 2),
            )
            setOnClickPendingIntent(
                R.id.widget_previous,
                mediaButton(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS, requestCode = 3),
            )
        }

        // Apply both — the system picks based on widget size
        appWidgetManager.updateAppWidget(appWidgetId, compact)
        appWidgetManager.updateAppWidget(appWidgetId, expanded)
    }

    /** A [PendingIntent] that Media3's `MediaSessionService.onStartCommand` dispatches to the session. */
    private fun mediaButton(context: Context, keyCode: Int, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            context,
            requestCode,
            Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                component = ComponentName(context, PlaybackService::class.java)
                putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        const val ACTION_UPDATE_WIDGET = "nl.muorg.android.UPDATE_WIDGET"
        const val EXTRA_TITLE = "title"
        const val EXTRA_ARTIST = "artist"
        const val EXTRA_ALBUM = "album"
        const val EXTRA_IS_PLAYING = "is_playing"
    }
}
