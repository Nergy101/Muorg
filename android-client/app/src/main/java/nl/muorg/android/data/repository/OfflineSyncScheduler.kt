package nl.muorg.android.data.repository

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import nl.muorg.android.data.db.OfflinePlaylistDao
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val SYNC_NOW = "offline-playlist-sync-now"
private const val SYNC_PERIODIC = "offline-playlist-sync-periodic"

/** WorkManager's minimum period — a playlist edited elsewhere is picked up within this. */
private const val PERIODIC_MINUTES = 15L

/**
 * Keeps downloaded playlists in step with the server without the app open.
 *
 * Two triggers: a one-off run right after the user adds songs here, and a
 * periodic run that catches everything else — additions made on the web app or
 * another device, and smart playlists that grew with the library. Both do the
 * same thing, [OfflineDownloadManager.syncDownloadedPlaylists], which is a
 * single request when nothing changed.
 */
@Singleton
class OfflineSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val offlinePlaylistDao: OfflinePlaylistDao,
) {
    private val workManager get() = WorkManager.getInstance(context)

    private val needsNetwork = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** Sync once, as soon as there is a connection. Queued behind a run already going. */
    fun syncNow() {
        workManager.enqueueUniqueWork(
            SYNC_NOW,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<OfflineSyncWorker>().setConstraints(needsNetwork).build(),
        )
    }

    /**
     * The periodic run exists exactly while something is downloaded, so a user
     * who never downloads a playlist never has the app waking up for nothing.
     * Returns whether anything is downloaded.
     */
    suspend fun reconcile(): Boolean {
        val anyDownloaded = offlinePlaylistDao.getAll().isNotEmpty()
        if (anyDownloaded) {
            workManager.enqueueUniquePeriodicWork(
                SYNC_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<OfflineSyncWorker>(PERIODIC_MINUTES, TimeUnit.MINUTES)
                    .setConstraints(needsNetwork)
                    .build(),
            )
        } else {
            workManager.cancelUniqueWork(SYNC_PERIODIC)
        }
        return anyDownloaded
    }

    /** Opening the app is itself a reason to look: catches up and re-arms the periodic run. */
    suspend fun onAppStart() {
        if (reconcile()) syncNow()
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface OfflineSyncEntryPoint {
    fun offlineDownloadManager(): OfflineDownloadManager
}

/**
 * Runs [OfflineDownloadManager.syncDownloadedPlaylists] from WorkManager.
 *
 * Reaches the manager through a Hilt entry point rather than hilt-work: it
 * needs one singleton and nothing else, which is not worth a second Hilt
 * artifact and a custom worker factory.
 */
class OfflineSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val manager = EntryPointAccessors
            .fromApplication(applicationContext, OfflineSyncEntryPoint::class.java)
            .offlineDownloadManager()
        return try {
            manager.syncDownloadedPlaylists()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
