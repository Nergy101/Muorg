mod db;

pub use db::{
    add_tracks_to_playlist, batch_update_track_metadata, compute_content_hash,
    content_hash_from_parts, count_tracks, create_playlist, create_smart_playlist,
    delete_playlist, gc_deleted_tracks, get_latest_track_backup, get_library_stats,
    get_playlist_entries, get_playlist_tracks, get_playlists_for_track, get_track_by_id,
    get_track_mtime_by_path, get_track_path_and_mtime_by_id, get_track_path_by_id,
    load_most_played, load_playlists, load_recently_added, load_recently_played,
    load_roots, load_tracks, load_tracks_paginated, prune_play_history, record_play,
    record_track_backup, remove_playlist_entry_by_id, remove_root, remove_tracks_from_playlist,
    rename_playlist, reorder_playlist_tracks, reorder_playlists, rescan_root, resolve_smart_playlist_track_ids, save_roots,
    scan_and_insert, search_tracks, set_playlist_icon, set_smart_playlist_rules, set_track_rating,
    sweep_missing_tracks, update_track_hash, update_track_metadata, update_track_mtime,
    update_track_path, CatalogTrack, LibraryStats, Playlist, PlaylistTrackEntry, RootUpsert,
    ScannedTrack, TrackBackupRecord, CONTENT_HASH_TAIL_BYTES,
};
use std::path::Path;
use std::sync::Mutex;

pub struct Catalog {
    pub db: Mutex<rusqlite::Connection>,
}

impl Catalog {
    pub fn new(db_path: &Path) -> Result<Self, String> {
        let conn = rusqlite::Connection::open(db_path).map_err(|e| e.to_string())?;
        db::init_schema(&conn)?;
        Ok(Catalog {
            db: Mutex::new(conn),
        })
    }
}
