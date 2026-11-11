package nl.muorg.android.ui.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import nl.muorg.android.MainActivity

/** PendingIntent helpers shared by the widget providers. */
internal object WidgetIntents {

    fun openAppPendingIntent(context: Context, requestCode: Int = 0): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /**
     * Template for the Recently Played rows: opens [MainActivity] with
     * [RecentTracksWidget.ACTION_PLAY_TRACK]; each row's fill-in adds its track
     * id. SINGLE_TOP + CLEAR_TOP deliver it to a running activity through
     * onNewIntent instead of recreating (and so resetting) the UI.
     */
    fun playTrackTemplate(context: Context, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, MainActivity::class.java).apply {
                action = RecentTracksWidget.ACTION_PLAY_TRACK
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )

    /**
     * A transport button on the Now Playing widget: opens the app and hands
     * [nl.muorg.android.ui.widget.NowPlayingWidget.EXTRA_TRANSPORT] to
     * MainActivity, which performs the command through PlayerController. A
     * receiver cannot bind to the playback service itself
     * (ReceiverCallNotAllowedException), so the action always goes via the app.
     */
    fun transportPendingIntent(context: Context, transport: String, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, MainActivity::class.java).apply {
                action = nl.muorg.android.ui.widget.NowPlayingWidget.ACTION_TRANSPORT
                putExtra(nl.muorg.android.ui.widget.NowPlayingWidget.EXTRA_TRANSPORT, transport)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /**
     * A broadcast PendingIntent aimed at one of the widget receivers. The
     * component is explicit and the package pinned, so the receivers can stay
     * non-exported — a PendingIntent fires under the creating app's identity,
     * which is the only sender a non-exported receiver accepts anyway.
     */
    fun actionPendingIntent(
        context: Context,
        target: Class<*>,
        action: String,
        requestCode: Int,
        mutable: Boolean = false,
    ): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, target).apply {
                this.action = action
                setPackage(context.packageName)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or
                if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE,
        )
}
