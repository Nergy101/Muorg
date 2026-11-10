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

    fun openApp(context: Context) {
        context.startActivity(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

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
