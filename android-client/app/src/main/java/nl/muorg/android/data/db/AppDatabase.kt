package nl.muorg.android.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [LocalTrack::class, LocalPlaylist::class, LocalPlaylistEntry::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun localTrackDao(): LocalTrackDao
    abstract fun localPlaylistDao(): LocalPlaylistDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DROP TABLE IF EXISTS `local_playlist_entries`")
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `local_playlist_entries` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `playlistId` INTEGER NOT NULL,
                        `filePath` TEXT NOT NULL,
                        `trackTitle` TEXT,
                        `trackArtist` TEXT,
                        `position` INTEGER NOT NULL,
                        FOREIGN KEY(`playlistId`) REFERENCES `local_playlists`(`id`) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_local_playlist_entries_playlistId` ON `local_playlist_entries` (`playlistId`)"
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_local_playlist_entries_playlistId_filePath` ON `local_playlist_entries` (`playlistId`, `filePath`)"
                )
            }
        }
    }
}
