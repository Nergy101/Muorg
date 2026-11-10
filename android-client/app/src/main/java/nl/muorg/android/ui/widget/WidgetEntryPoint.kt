package nl.muorg.android.ui.widget

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import nl.muorg.android.data.preferences.AppPreferences
import nl.muorg.android.data.repository.LibraryRepository
import nl.muorg.android.player.PlayerController

/**
 * Lets the widget receivers and the RemoteViews factory reach the app's
 * singletons without being Hilt components themselves — a BroadcastReceiver
 * and a RemoteViewsService factory are instantiated by the system, not by
 * Dagger, so they go through [dagger.hilt.EntryPointAccessors].
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun preferences(): AppPreferences
    fun libraryRepository(): LibraryRepository
    fun playerController(): PlayerController
}
