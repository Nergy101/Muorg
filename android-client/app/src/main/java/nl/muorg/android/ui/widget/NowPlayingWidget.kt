package nl.muorg.android.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
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
                android.content.ComponentName(context, NowPlayingWidget::class.java)
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

            val playIntent = Intent(context, PlaybackService::class.java).apply {
                action = if (isPlaying == true) PlaybackService.ACTION_PAUSE
                else PlaybackService.ACTION_PLAY
            }
            val playPending = PendingIntent.getService(
                context, 1, playIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            setOnClickPendingIntent(R.id.widget_play_pause, playPending)

            val nextIntent = Intent(context, PlaybackService::class.java).apply {
                action = PlaybackService.ACTION_NEXT
            }
            val nextPending = PendingIntent.getService(
                context, 2, nextIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            setOnClickPendingIntent(R.id.widget_next, nextPending)
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

            val playIntent = Intent(context, PlaybackService::class.java).apply {
                action = if (isPlaying == true) PlaybackService.ACTION_PAUSE
                else PlaybackService.ACTION_PLAY
            }
            val playPending = PendingIntent.getService(
                context, 1, playIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            setOnClickPendingIntent(R.id.widget_play_pause, playPending)

            val nextIntent = Intent(context, PlaybackService::class.java).apply {
                action = PlaybackService.ACTION_NEXT
            }
            val nextPending = PendingIntent.getService(
                context, 2, nextIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            setOnClickPendingIntent(R.id.widget_next, nextPending)

            val prevIntent = Intent(context, PlaybackService::class.java).apply {
                action = PlaybackService.ACTION_PREVIOUS
            }
            val prevPending = PendingIntent.getService(
                context, 3, prevIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            setOnClickPendingIntent(R.id.widget_previous, prevPending)
        }

        // Apply both — the system picks based on widget size
        appWidgetManager.updateAppWidget(appWidgetId, compact)
        appWidgetManager.updateAppWidget(appWidgetId, expanded)
    }

    companion object {
        const val ACTION_UPDATE_WIDGET = "nl.muorg.android.UPDATE_WIDGET"
        const val EXTRA_TITLE = "title"
        const val EXTRA_ARTIST = "artist"
        const val EXTRA_ALBUM = "album"
        const val EXTRA_IS_PLAYING = "is_playing"
    }
}
