package nl.muorg.android.ui.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.widget.RemoteViews
import dagger.hilt.EntryPointAccessors
import kotlinx.coroutines.launch
import nl.muorg.android.R

/**
 * The Recently Played list widget.
 *
 * Rows are rendered by [RecentTracksWidgetService]; each row carries a
 * fill-in intent with its track id, delivered through the list's PendingIntent
 * template to [ACTION_PLAY_TRACK], which resolves the id against the stashed
 * recent list and starts playback through the app's [nl.muorg.android.player.PlayerController].
 */
class RecentTracksWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        setup(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_PLAY_TRACK -> handlePlayTrack(context, intent)
            ACTION_REFRESH -> {
                val manager = AppWidgetManager.getInstance(context)
                manager.notifyAppWidgetViewDataChanged(
                    manager.getAppWidgetIds(ComponentName(context, RecentTracksWidget::class.java)),
                    R.id.widget_recent_list,
                )
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                val manager = AppWidgetManager.getInstance(context)
                setup(context, manager, manager.getAppWidgetIds(ComponentName(context, RecentTracksWidget::class.java)))
            }
            else -> super.onReceive(context, intent)
        }
    }

    private fun setup(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            val serviceIntent = Intent(context, RecentTracksWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                // Unique data so each widget instance gets its own factory.
                data = Uri.parse("muorg://widget/recent/$appWidgetId")
            }
            val views = RemoteViews(context.packageName, R.layout.recent_tracks_widget).apply {
                setRemoteAdapter(R.id.widget_recent_list, serviceIntent)
                setEmptyView(R.id.widget_recent_list, R.id.widget_recent_empty)
                setOnClickPendingIntent(R.id.widget_root, WidgetIntents.openAppPendingIntent(context))
                setInt(R.id.widget_refresh, "setColorFilter", Color.WHITE)
                setOnClickPendingIntent(
                    R.id.widget_refresh,
                    WidgetIntents.actionPendingIntent(context, RecentTracksWidget::class.java, ACTION_REFRESH, REQUEST_REFRESH),
                )
                // Mutable so each row's fill-in intent can add its track id.
                setPendingIntentTemplate(
                    R.id.widget_recent_list,
                    WidgetIntents.actionPendingIntent(
                        context,
                        RecentTracksWidget::class.java,
                        ACTION_PLAY_TRACK,
                        REQUEST_TEMPLATE,
                        mutable = true,
                    ),
                )
            }
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_recent_list)
    }

    private fun handlePlayTrack(context: Context, intent: Intent) {
        val trackId = intent.getIntExtra(EXTRA_TRACK_ID, 0)
        if (trackId == 0) return
        val pendingResult = goAsync()
        NowPlayingWidget.ioScope.launch {
            try {
                val track = WidgetStateStore.loadRecentTracks(context).firstOrNull { it.id == trackId }
                if (track != null) {
                    EntryPointAccessors
                        .fromApplication(context, WidgetEntryPoint::class.java)
                        .playerController()
                        .playTrack(track, listOf(track))
                } else {
                    // The stash is stale (track no longer in recent history): just open the app.
                    WidgetIntents.openApp(context)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_PLAY_TRACK = "nl.muorg.android.WIDGET_PLAY_TRACK"
        const val ACTION_REFRESH = "nl.muorg.android.WIDGET_REFRESH_RECENT"
        const val EXTRA_TRACK_ID = NowPlayingWidget.EXTRA_TRACK_ID
        private const val REQUEST_REFRESH = 20
        private const val REQUEST_TEMPLATE = 21
    }
}
