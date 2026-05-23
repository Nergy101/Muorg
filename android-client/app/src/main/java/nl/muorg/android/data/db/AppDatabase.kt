package nl.muorg.android.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [LocalTrack::class, LocalPlaylist::class, LocalPlaylistEntry::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun localTrackDao(): LocalTrackDao
    abstract fun localPlaylistDao(): LocalPlaylistDao
}
