package nl.muorg.android.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        LocalTrack::class,
        LocalPlaylist::class,
        LocalPlaylistEntry::class,
        OfflineTrack::class,
        OfflinePlaylist::class,
    ],
    version = 5,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun localTrackDao(): LocalTrackDao
    abstract fun localPlaylistDao(): LocalPlaylistDao
    abstract fun offlineTrackDao(): OfflineTrackDao
    abstract fun offlinePlaylistDao(): OfflinePlaylistDao

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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `offline_tracks` (
                        `trackId` INTEGER NOT NULL PRIMARY KEY,
                        `filePath` TEXT NOT NULL,
                        `playlistId` INTEGER,
                        `status` TEXT NOT NULL DEFAULT 'downloading',
                        `progress` INTEGER NOT NULL DEFAULT 0,
                        `totalBytes` INTEGER NOT NULL DEFAULT 0,
                        `downloadedAt` INTEGER NOT NULL DEFAULT 0
                    )"""
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Playlist metadata, so a downloaded playlist renders with no
                // server to ask for its name and icon.
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `offline_playlists` (
                        `playlistId` INTEGER NOT NULL PRIMARY KEY,
                        `name` TEXT NOT NULL,
                        `icon` TEXT,
                        `downloadedAt` INTEGER NOT NULL DEFAULT 0
                    )"""
                )
                // Track metadata, so downloaded tracks render and group into
                // albums offline. Nullable: rows from before this column fall
                // back to a filename-only track.
                database.execSQL("ALTER TABLE `offline_tracks` ADD COLUMN `trackJson` TEXT")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // The server's track count at the last sync. Rows from before
                // this column read 0, which never matches a real playlist, so
                // the first background sync reconciles them once.
                database.execSQL(
                    "ALTER TABLE `offline_playlists` ADD COLUMN `trackCount` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }
    }
}
