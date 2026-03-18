mod db;

pub use db::{
    add_tracks_to_playlist, compute_content_hash, create_playlist, delete_playlist,
    gc_deleted_tracks, get_playlist_entries, get_playlist_tracks, load_playlists, load_roots,
    load_tracks, remove_playlist_entry_by_id, remove_root, remove_tracks_from_playlist,
    rename_playlist, rescan_root, save_roots, scan_and_insert, update_track_hash,
    set_track_rating, update_track_metadata, CatalogTrack, Playlist, PlaylistTrackEntry,
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
