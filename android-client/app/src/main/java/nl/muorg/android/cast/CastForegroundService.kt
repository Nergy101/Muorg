package nl.muorg.android.cast

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import nl.muorg.android.MainActivity
import nl.muorg.android.R

/**
 * Keeps the app in the foreground for the whole Chromecast session.
 *
 * When casting, the local ExoPlayer is paused, so Media3's MediaSessionService
 * drops its foreground state once its user-engaged timeout expires. With the
 * screen off the app then falls under Doze: network access is suspended, the
 * CastManager poll loop stops receiving status updates, and the auto-advance
 * chain can no longer load the next track onto the Chromecast — playback stops
 * at the end of the current song. This service keeps the process foreground
 * (and Doze-exempt) for as long as the cast session is alive.
 */
class CastForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val deviceName = intent?.getStringExtra(EXTRA_DEVICE_NAME)
            ?: getString(R.string.cast_default_device)
        showForeground(deviceName)
        return START_STICKY
    }

    override fun onDestroy() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun showForeground(deviceName: String) {
        val channelId = ensureChannel()
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_screencast)
            .setContentTitle(getString(R.string.cast_notification_title, deviceName))
            .setContentText(getString(R.string.cast_notification_text))
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openApp)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else {
            0
        }
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        } catch (_: Exception) {
            // Foreground-service type rejected (e.g. no active MediaSession yet
            // after a process restart) — stop rather than crash.
            stopSelf()
        }
    }

    private fun ensureChannel(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.cast_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                )
            )
        }
        return CHANNEL_ID
    }

    companion object {
        private const val CHANNEL_ID = "muorg_cast"
        const val NOTIFICATION_ID = 4242
        private const val EXTRA_DEVICE_NAME = "device_name"

        fun start(context: Context, deviceName: String?) {
            val intent = Intent(context, CastForegroundService::class.java)
                .putExtra(EXTRA_DEVICE_NAME, deviceName)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CastForegroundService::class.java))
        }
    }
}
