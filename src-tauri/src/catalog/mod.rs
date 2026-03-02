mod db;

pub use db::{
    load_roots, load_tracks, remove_root, rescan_root, save_roots, scan_and_insert,
    update_track_metadata, CatalogTrack,
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
