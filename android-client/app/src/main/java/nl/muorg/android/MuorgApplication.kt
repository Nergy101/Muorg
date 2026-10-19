package nl.muorg.android

import android.app.Application
import com.google.android.gms.cast.framework.CastContext
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import nl.muorg.android.data.repository.OfflineSyncScheduler
import javax.inject.Inject

@HiltAndroidApp
class MuorgApplication : Application() {
    @Inject lateinit var offlineSyncScheduler: OfflineSyncScheduler

    override fun onCreate() {
        super.onCreate()
        try { CastContext.getSharedInstance(this) } catch (_: Exception) {}
        // Re-arms the downloaded-playlist sync (also covers playlists
        // downloaded before it existed) and catches up on anything added since.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { offlineSyncScheduler.onAppStart() }
    }
}
