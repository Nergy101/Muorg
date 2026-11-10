package nl.muorg.android.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import nl.muorg.android.R
import nl.muorg.android.data.api.CatalogTrack

/** Serves the row factories for [RecentTracksWidget]. */
class RecentTracksWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        RecentTracksFactory(
            applicationContext,
            intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID),
        )
}

private const val RECENT_LIMIT = 12

/**
 * Loads the recent play history (and warms the cover cache) in
 * [onDataSetChanged], which the system calls on the binder thread whenever the
 * widget binds or [AppWidgetManager.notifyAppWidgetViewDataChanged] fires, so
 * [getViewAt] only ever reads from memory/disk.
 */
class RecentTracksFactory(
    private val context: Context,
    private val appWidgetId: Int,
) : RemoteViewsService.RemoteViewsFactory {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tracks: List<CatalogTrack> = emptyList()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        runBlocking(Dispatchers.IO) {
            val repository = EntryPointAccessors
                .fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
                .libraryRepository()
            tracks = repository.getRecentPlayHistory(RECENT_LIMIT).getOrElse { emptyList() }
            // Stashed so a row tap can resolve the track id back to a full track.
            WidgetStateStore.saveRecentTracks(context, tracks)
            // Warm the disk cover cache so getViewAt never does network work.
            for (track in tracks) {
                WidgetStateStore.ensureCoverCached(context, track)
            }
        }
    }

    override fun getViewAt(position: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.recent_tracks_widget_item)
        val track = tracks.getOrNull(position) ?: return views
        views.setTextViewText(R.id.widget_item_title, track.displayTitle)
        views.setTextViewText(R.id.widget_item_artist, track.displayArtist)
        // Placeholder disc tint, applied in code for the same reason as the
        // now-playing widget (host process, no appcompat tint).
        views.setInt(R.id.widget_item_cover_placeholder, "setColorFilter", 0x66FFFFFF.toInt())
        val cover = WidgetStateStore.peekCover(context, track.id)
        if (cover != null) {
            views.setImageViewBitmap(R.id.widget_item_cover, cover)
            views.setViewVisibility(R.id.widget_item_cover, View.VISIBLE)
            views.setViewVisibility(R.id.widget_item_cover_placeholder, View.GONE)
        } else {
            views.setViewVisibility(R.id.widget_item_cover, View.GONE)
            views.setViewVisibility(R.id.widget_item_cover_placeholder, View.VISIBLE)
        }
        views.setOnClickFillInIntent(
            R.id.widget_item_root,
            Intent().putExtra(RecentTracksWidget.EXTRA_TRACK_ID, track.id),
        )
        return views
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getCount(): Int = tracks.size
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = tracks.getOrNull(position)?.id?.toLong() ?: -1L
    override fun hasStableIds(): Boolean = true
    override fun onDestroy() {
        scope.cancel()
    }
}
