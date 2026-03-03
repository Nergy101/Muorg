use crate::metadata;
use serde::Serialize;
use std::path::Path;
use walkdir::WalkDir;

#[derive(Debug, Clone, Serialize)]
pub struct CatalogTrack {
    pub id: i64,
    pub path: String,
    pub root_id: i64,
    pub title: Option<String>,
    pub artist: Option<String>,
    pub album: Option<String>,
    pub album_artist: Option<String>,
    pub year: Option<i64>,
    pub genre: Option<String>,
    pub track_number: Option<i64>,
    pub disc_number: Option<i64>,
    pub duration_secs: Option<i64>,
    pub format: String,
    pub mtime_secs: i64,
}

const SCHEMA: &str = "
CREATE TABLE IF NOT EXISTS roots (
    id INTEGER PRIMARY KEY,
    path TEXT NOT NULL UNIQUE,
    added_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS tracks (
    id INTEGER PRIMARY KEY,
    root_id INTEGER NOT NULL REFERENCES roots(id),
    path TEXT NOT NULL,
    title TEXT,
    artist TEXT,
    album TEXT,
    album_artist TEXT,
    year INTEGER,
    genre TEXT,
    track_number INTEGER,
    disc_number INTEGER,
    duration_secs INTEGER,
    format TEXT NOT NULL,
    mtime_secs INTEGER NOT NULL,
    UNIQUE(root_id, path)
);
CREATE INDEX IF NOT EXISTS idx_tracks_root ON tracks(root_id);
CREATE INDEX IF NOT EXISTS idx_tracks_artist ON tracks(artist);
CREATE INDEX IF NOT EXISTS idx_tracks_album ON tracks(album);
";

pub fn init_schema(conn: &rusqlite::Connection) -> Result<(), String> {
    conn.execute_batch(SCHEMA).map_err(|e| e.to_string())?;
    Ok(())
}

pub fn save_roots(conn: &rusqlite::Connection, paths: &[String]) -> Result<(), String> {
    let now = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map_err(|_| "time error")?
        .as_secs() as i64;
    let mut stmt = conn
        .prepare("INSERT OR IGNORE INTO roots (path, added_at) VALUES (?1, ?2)")
        .map_err(|e| e.to_string())?;
    for p in paths {
        stmt.execute(rusqlite::params![p, now])
            .map_err(|e| e.to_string())?;
    }
    Ok(())
}

pub fn load_roots(conn: &rusqlite::Connection) -> Result<Vec<String>, String> {
    let mut stmt = conn
        .prepare("SELECT path FROM roots ORDER BY added_at")
        .map_err(|e| e.to_string())?;
    let rows = stmt
        .query_map([], |r| r.get::<_, String>(0))
        .map_err(|e| e.to_string())?;
    let mut out = Vec::new();
    for row in rows {
        out.push(row.map_err(|e| e.to_string())?);
    }
    Ok(out)
}

pub fn load_tracks(conn: &rusqlite::Connection) -> Result<Vec<CatalogTrack>, String> {
    let mut stmt = conn
        .prepare(
            "SELECT id, path, root_id, title, artist, album, album_artist, year, genre, track_number, disc_number, duration_secs, format, mtime_secs FROM tracks ORDER BY artist, album, track_number, title",
        )
        .map_err(|e| e.to_string())?;
    let rows = stmt
        .query_map([], |r| {
            Ok(CatalogTrack {
                id: r.get(0)?,
                path: r.get(1)?,
                root_id: r.get(2)?,
                title: r.get(3)?,
                artist: r.get(4)?,
                album: r.get(5)?,
                album_artist: r.get(6)?,
                year: r.get(7)?,
                genre: r.get(8)?,
                track_number: r.get(9)?,
                disc_number: r.get(10)?,
                duration_secs: r.get(11)?,
                format: r.get(12)?,
                mtime_secs: r.get(13)?,
            })
        })
        .map_err(|e| e.to_string())?;
    let mut out = Vec::new();
    for row in rows {
        out.push(row.map_err(|e| e.to_string())?);
    }
    Ok(out)
}

fn format_from_path(path: &Path) -> Option<&'static str> {
    path.extension()
        .and_then(|e| e.to_str())
        .and_then(|s| match s.to_lowercase().as_str() {
            "mp3" => Some("mp3"),
            "flac" => Some("flac"),
            _ => None,
        })
}

/// Scan directory for mp3/flac, read metadata, insert into DB. Returns number of tracks added.
pub fn scan_and_insert(conn: &rusqlite::Connection, root_path: &str) -> Result<u64, String> {
    let root_id: i64 = conn
        .query_row("SELECT id FROM roots WHERE path = ?1", [root_path], |r| {
            r.get(0)
        })
        .map_err(|e| e.to_string())?;

    let _root = Path::new(root_path);
    let mut count = 0u64;
    let mut insert = conn
        .prepare(
            "INSERT OR REPLACE INTO tracks (root_id, path, title, artist, album, album_artist, year, genre, track_number, disc_number, duration_secs, format, mtime_secs)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13)",
        )
        .map_err(|e| e.to_string())?;

    for entry in WalkDir::new(root_path)
        .follow_links(false)
        .into_iter()
        .filter_map(|e| e.ok())
    {
        let path = entry.path();
        if !path.is_file() {
            continue;
        }
        let format = match format_from_path(path) {
            Some(f) => f,
            None => continue,
        };
        let path_str = path.to_str().ok_or("Invalid path encoding")?.to_string();
        let mtime_secs = std::fs::metadata(path)
            .ok()
            .and_then(|m| m.modified().ok())
            .and_then(|t| t.duration_since(std::time::UNIX_EPOCH).ok())
            .map(|d| d.as_secs() as i64)
            .unwrap_or(0);

        let meta = match metadata::read_metadata(path) {
            Ok(m) => m,
            Err(_) => continue,
        };

        insert
            .execute(rusqlite::params![
                root_id,
                path_str,
                meta.title,
                meta.artist,
                meta.album,
                meta.album_artist,
                meta.year.map(|y| y as i64),
                meta.genre,
                meta.track_number.map(|n| n as i64),
                meta.disc_number.map(|n| n as i64),
                meta.duration_secs.map(|d| d as i64),
                format,
                mtime_secs,
            ])
            .map_err(|e| e.to_string())?;
        count += 1;
    }
    Ok(count)
}

/// Rescan a root: re-read files from disk and update DB (add new, update changed, optionally remove missing).
pub fn rescan_root(conn: &rusqlite::Connection, root_path: &str) -> Result<u64, String> {
    scan_and_insert(conn, root_path)
}

/// Remove a root and its tracks from the catalog. Does not delete anything on disk.
pub fn remove_root(conn: &rusqlite::Connection, root_path: &str) -> Result<(), String> {
    let root_id: i64 = conn
        .query_row("SELECT id FROM roots WHERE path = ?1", [root_path], |r| {
            r.get(0)
        })
        .map_err(|e| e.to_string())?;
    conn.execute("DELETE FROM tracks WHERE root_id = ?1", [root_id])
        .map_err(|e| e.to_string())?;
    conn.execute("DELETE FROM roots WHERE id = ?1", [root_id])
        .map_err(|e| e.to_string())?;
    Ok(())
}

/// Update a track's metadata in the catalog after writing to file.
/// Only touches columns for which the update has a value (Some); others are left unchanged.
pub fn update_track_metadata(
    conn: &rusqlite::Connection,
    path: &str,
    update: &crate::metadata::MetadataUpdate,
) -> Result<(), String> {
    let mut sets = Vec::new();
    let mut params: Vec<rusqlite::types::Value> = Vec::new();

    if let Some(ref t) = update.title {
        sets.push("title = ?");
        params.push(rusqlite::types::Value::Text(t.clone()));
    }
    if let Some(ref a) = update.artist {
        sets.push("artist = ?");
        params.push(rusqlite::types::Value::Text(a.clone()));
    }
    if let Some(ref a) = update.album {
        sets.push("album = ?");
        params.push(rusqlite::types::Value::Text(a.clone()));
    }
    if let Some(ref a) = update.album_artist {
        sets.push("album_artist = ?");
        params.push(rusqlite::types::Value::Text(a.clone()));
    }
    if let Some(y) = update.year {
        sets.push("year = ?");
        params.push(rusqlite::types::Value::Integer(y as i64));
    }
    if let Some(ref g) = update.genre {
        sets.push("genre = ?");
        params.push(rusqlite::types::Value::Text(g.clone()));
    }
    if let Some(n) = update.track_number {
        sets.push("track_number = ?");
        params.push(rusqlite::types::Value::Integer(n as i64));
    }
    if let Some(n) = update.disc_number {
        sets.push("disc_number = ?");
        params.push(rusqlite::types::Value::Integer(n as i64));
    }

    if sets.is_empty() {
        return Ok(());
    }

    let sql = format!("UPDATE tracks SET {} WHERE path = ?", sets.join(", "));
    params.push(rusqlite::types::Value::Text(path.to_string()));
    let mut stmt = conn.prepare(&sql).map_err(|e| e.to_string())?;
    stmt.execute(rusqlite::params_from_iter(params.iter()))
        .map_err(|e| e.to_string())?;
    Ok(())
}
