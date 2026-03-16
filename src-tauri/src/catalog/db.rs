use crate::metadata;
use serde::Serialize;
use std::path::Path;
use walkdir::WalkDir;

#[derive(Debug, Clone, Serialize)]
pub struct Playlist {
    pub id: i64,
    pub name: String,
    pub track_count: i64,
}

#[derive(Debug, Clone, Serialize)]
pub struct CatalogTrack {
    pub id: i64,
    pub path: String,
    pub root_id: i64,
    pub title: Option<String>,
    pub artist: Option<String>,
    pub album: Option<String>,
    pub album_artist: Option<String>,
    pub featuring: Option<String>,
    pub year: Option<i64>,
    pub genre: Option<String>,
    pub track_number: Option<i64>,
    pub disc_number: Option<i64>,
    pub duration_secs: Option<i64>,
    pub format: String,
    pub mtime_secs: i64,
    /// True if the track has embedded album cover art (CoverFront).
    pub has_cover: bool,
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
CREATE TABLE IF NOT EXISTS playlists (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL,
    created_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS playlist_tracks (
    id INTEGER PRIMARY KEY,
    playlist_id INTEGER NOT NULL REFERENCES playlists(id) ON DELETE CASCADE,
    track_id INTEGER NOT NULL REFERENCES tracks(id) ON DELETE CASCADE,
    position INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_playlist_tracks_playlist ON playlist_tracks(playlist_id);
";

fn schema_has_column(
    conn: &rusqlite::Connection,
    table: &str,
    column: &str,
) -> Result<bool, String> {
    let mut stmt = conn
        .prepare(&format!("PRAGMA table_info({})", table))
        .map_err(|e| e.to_string())?;
    let rows = stmt
        .query_map([], |r| r.get::<_, String>(1))
        .map_err(|e| e.to_string())?;
    for row in rows {
        if row.map_err(|e| e.to_string())? == column {
            return Ok(true);
        }
    }
    Ok(false)
}

pub fn init_schema(conn: &rusqlite::Connection) -> Result<(), String> {
    conn.execute_batch("PRAGMA foreign_keys = ON;").map_err(|e| e.to_string())?;
    conn.execute_batch(SCHEMA).map_err(|e| e.to_string())?;
    if !schema_has_column(conn, "tracks", "has_cover")? {
        conn.execute(
            "ALTER TABLE tracks ADD COLUMN has_cover INTEGER NOT NULL DEFAULT 0",
            [],
        )
        .map_err(|e| e.to_string())?;
    }
    if !schema_has_column(conn, "tracks", "featuring")? {
        conn.execute("ALTER TABLE tracks ADD COLUMN featuring TEXT", [])
            .map_err(|e| e.to_string())?;
    }
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
    let has_cover_col = schema_has_column(conn, "tracks", "has_cover")?;
    let featuring_col = schema_has_column(conn, "tracks", "featuring")?;
    let sql = match (has_cover_col, featuring_col) {
        (true, true) => "SELECT id, path, root_id, title, artist, album, album_artist, featuring, year, genre, track_number, disc_number, duration_secs, format, mtime_secs, has_cover FROM tracks ORDER BY artist, album, track_number, title",
        (true, false) => "SELECT id, path, root_id, title, artist, album, album_artist, year, genre, track_number, disc_number, duration_secs, format, mtime_secs, has_cover FROM tracks ORDER BY artist, album, track_number, title",
        (false, true) => "SELECT id, path, root_id, title, artist, album, album_artist, featuring, year, genre, track_number, disc_number, duration_secs, format, mtime_secs FROM tracks ORDER BY artist, album, track_number, title",
        (false, false) => "SELECT id, path, root_id, title, artist, album, album_artist, year, genre, track_number, disc_number, duration_secs, format, mtime_secs FROM tracks ORDER BY artist, album, track_number, title",
    };
    let mut stmt = conn.prepare(sql).map_err(|e| e.to_string())?;
    let rows = stmt
        .query_map([], |r| {
            let (has_cover, featuring) = match (has_cover_col, featuring_col) {
                (true, true) => (
                    r.get::<_, i64>(15).map(|n| n != 0).unwrap_or(false),
                    r.get::<_, Option<String>>(7).ok().flatten(),
                ),
                (true, false) => (
                    r.get::<_, i64>(14).map(|n| n != 0).unwrap_or(false),
                    None,
                ),
                (false, true) => (false, r.get::<_, Option<String>>(7).ok().flatten()),
                (false, false) => (false, None),
            };
            let (year, genre, track_number, disc_number, duration_secs, format, mtime_secs) = if featuring_col {
                (
                    r.get::<_, Option<i64>>(8)?,
                    r.get::<_, Option<String>>(9)?,
                    r.get::<_, Option<i64>>(10)?,
                    r.get::<_, Option<i64>>(11)?,
                    r.get::<_, Option<i64>>(12)?,
                    r.get::<_, String>(13)?,
                    r.get::<_, i64>(14)?,
                )
            } else {
                (
                    r.get::<_, Option<i64>>(7)?,
                    r.get::<_, Option<String>>(8)?,
                    r.get::<_, Option<i64>>(9)?,
                    r.get::<_, Option<i64>>(10)?,
                    r.get::<_, Option<i64>>(11)?,
                    r.get::<_, String>(12)?,
                    r.get::<_, i64>(13)?,
                )
            };
            Ok(CatalogTrack {
                id: r.get(0)?,
                path: r.get(1)?,
                root_id: r.get(2)?,
                title: r.get(3)?,
                artist: r.get(4)?,
                album: r.get(5)?,
                album_artist: r.get(6)?,
                featuring,
                year,
                genre,
                track_number,
                disc_number,
                duration_secs,
                format,
                mtime_secs,
                has_cover,
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
    let featuring_col = schema_has_column(conn, "tracks", "featuring")?;
    let insert_sql = if featuring_col {
        "INSERT OR REPLACE INTO tracks (root_id, path, title, artist, album, album_artist, featuring, year, genre, track_number, disc_number, duration_secs, format, mtime_secs, has_cover) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14, ?15)"
    } else {
        "INSERT OR REPLACE INTO tracks (root_id, path, title, artist, album, album_artist, year, genre, track_number, disc_number, duration_secs, format, mtime_secs, has_cover) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14)"
    };
    let mut insert = conn.prepare(insert_sql).map_err(|e| e.to_string())?;

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

        let has_cover = meta.picture_base64.as_ref().is_some_and(|s| !s.is_empty());
        if featuring_col {
            insert
                .execute(rusqlite::params![
                    root_id,
                    path_str,
                    meta.title,
                    meta.artist,
                    meta.album,
                    meta.album_artist,
                    meta.featuring,
                    meta.year.map(|y| y as i64),
                    meta.genre,
                    meta.track_number.map(|n| n as i64),
                    meta.disc_number.map(|n| n as i64),
                    meta.duration_secs.map(|d| d as i64),
                    format,
                    mtime_secs,
                    if has_cover { 1i64 } else { 0i64 },
                ])
                .map_err(|e| e.to_string())?;
        } else {
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
                    if has_cover { 1i64 } else { 0i64 },
                ])
                .map_err(|e| e.to_string())?;
        }
        count += 1;
    }
    Ok(count)
}

/// Rescan a root: re-read files from disk, update DB (add new, update changed), and remove tracks whose files no longer exist.
pub fn rescan_root(conn: &rusqlite::Connection, root_path: &str) -> Result<u64, String> {
    let count = scan_and_insert(conn, root_path)?;
    let root_id: i64 = conn
        .query_row("SELECT id FROM roots WHERE path = ?1", [root_path], |r| r.get(0))
        .map_err(|e| e.to_string())?;
    let mut stmt = conn
        .prepare("SELECT id, path FROM tracks WHERE root_id = ?1")
        .map_err(|e| e.to_string())?;
    let rows: Vec<(i64, String)> = stmt
        .query_map([root_id], |r| Ok((r.get(0)?, r.get(1)?)))
        .map_err(|e| e.to_string())?
        .collect::<Result<Vec<_>, _>>()
        .map_err(|e| e.to_string())?;
    let mut del = conn
        .prepare("DELETE FROM tracks WHERE id = ?1")
        .map_err(|e| e.to_string())?;
    for (id, path) in rows {
        if !Path::new(&path).exists() {
            del.execute([id]).map_err(|e| e.to_string())?;
        }
    }
    Ok(count)
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

// ── Playlist functions ─────────────────────────────────────────────────────

/// A single row in the `playlist_tracks` join table, returned so the frontend can
/// target a specific entry (by `entry_id`) when removing one of several duplicates.
#[derive(Debug, Clone, Serialize)]
pub struct PlaylistTrackEntry {
    pub entry_id: i64,
    pub track_id: i64,
}

pub fn create_playlist(conn: &rusqlite::Connection, name: &str) -> Result<Playlist, String> {
    let now = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map_err(|_| "time error")?
        .as_secs() as i64;
    conn.execute(
        "INSERT INTO playlists (name, created_at) VALUES (?1, ?2)",
        rusqlite::params![name, now],
    )
    .map_err(|e| e.to_string())?;
    let id = conn.last_insert_rowid();
    Ok(Playlist {
        id,
        name: name.to_string(),
        track_count: 0,
    })
}

pub fn load_playlists(conn: &rusqlite::Connection) -> Result<Vec<Playlist>, String> {
    let mut stmt = conn
        .prepare(
            "SELECT p.id, p.name, COUNT(pt.id) as track_count \
             FROM playlists p \
             LEFT JOIN playlist_tracks pt ON pt.playlist_id = p.id \
             GROUP BY p.id, p.name \
             ORDER BY p.created_at",
        )
        .map_err(|e| e.to_string())?;
    let rows = stmt
        .query_map([], |r| {
            Ok(Playlist {
                id: r.get(0)?,
                name: r.get(1)?,
                track_count: r.get(2)?,
            })
        })
        .map_err(|e| e.to_string())?;
    let mut out = Vec::new();
    for row in rows {
        out.push(row.map_err(|e| e.to_string())?);
    }
    Ok(out)
}

pub fn rename_playlist(conn: &rusqlite::Connection, id: i64, name: &str) -> Result<(), String> {
    conn.execute(
        "UPDATE playlists SET name = ?1 WHERE id = ?2",
        rusqlite::params![name, id],
    )
    .map_err(|e| e.to_string())?;
    Ok(())
}

pub fn delete_playlist(conn: &rusqlite::Connection, id: i64) -> Result<(), String> {
    conn.execute("DELETE FROM playlists WHERE id = ?1", [id])
        .map_err(|e| e.to_string())?;
    Ok(())
}

pub fn get_playlist_tracks(
    conn: &rusqlite::Connection,
    playlist_id: i64,
) -> Result<Vec<i64>, String> {
    let mut stmt = conn
        .prepare(
            "SELECT track_id FROM playlist_tracks \
             WHERE playlist_id = ?1 ORDER BY position",
        )
        .map_err(|e| e.to_string())?;
    let rows = stmt
        .query_map([playlist_id], |r| r.get::<_, i64>(0))
        .map_err(|e| e.to_string())?;
    let mut out = Vec::new();
    for row in rows {
        out.push(row.map_err(|e| e.to_string())?);
    }
    Ok(out)
}

/// Returns every entry for a playlist in position order, including the `playlist_tracks.id`
/// primary key so the frontend can target a specific row when removing one of several duplicates.
pub fn get_playlist_entries(
    conn: &rusqlite::Connection,
    playlist_id: i64,
) -> Result<Vec<PlaylistTrackEntry>, String> {
    let mut stmt = conn
        .prepare(
            "SELECT id, track_id FROM playlist_tracks \
             WHERE playlist_id = ?1 ORDER BY position",
        )
        .map_err(|e| e.to_string())?;
    let rows = stmt
        .query_map([playlist_id], |r| {
            Ok(PlaylistTrackEntry {
                entry_id: r.get(0)?,
                track_id: r.get(1)?,
            })
        })
        .map_err(|e| e.to_string())?;
    let mut out = Vec::new();
    for row in rows {
        out.push(row.map_err(|e| e.to_string())?);
    }
    Ok(out)
}

/// Remove exactly one entry from a playlist by its `playlist_tracks.id` primary key.
/// Safe to call even if the track appears multiple times — only the targeted row is deleted.
pub fn remove_playlist_entry_by_id(
    conn: &rusqlite::Connection,
    entry_id: i64,
) -> Result<(), String> {
    conn.execute("DELETE FROM playlist_tracks WHERE id = ?1", [entry_id])
        .map_err(|e| e.to_string())?;
    Ok(())
}

pub fn add_tracks_to_playlist(
    conn: &rusqlite::Connection,
    playlist_id: i64,
    track_ids: &[i64],
) -> Result<(), String> {
    if track_ids.is_empty() {
        return Ok(());
    }
    let max_pos: i64 = conn
        .query_row(
            "SELECT COALESCE(MAX(position), -1) FROM playlist_tracks WHERE playlist_id = ?1",
            [playlist_id],
            |r| r.get(0),
        )
        .map_err(|e| e.to_string())?;
    let mut stmt = conn
        .prepare(
            "INSERT INTO playlist_tracks (playlist_id, track_id, position) VALUES (?1, ?2, ?3)",
        )
        .map_err(|e| e.to_string())?;
    for (i, &track_id) in track_ids.iter().enumerate() {
        let pos = max_pos + 1 + i as i64;
        stmt.execute(rusqlite::params![playlist_id, track_id, pos])
            .map_err(|e| e.to_string())?;
    }
    Ok(())
}

pub fn remove_tracks_from_playlist(
    conn: &rusqlite::Connection,
    playlist_id: i64,
    track_ids: &[i64],
) -> Result<(), String> {
    for &track_id in track_ids {
        conn.execute(
            "DELETE FROM playlist_tracks WHERE playlist_id = ?1 AND track_id = ?2",
            rusqlite::params![playlist_id, track_id],
        )
        .map_err(|e| e.to_string())?;
    }
    Ok(())
}

// ── Track metadata ─────────────────────────────────────────────────────────

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
    if let Some(ref f) = update.featuring {
        if schema_has_column(conn, "tracks", "featuring")? {
            sets.push("featuring = ?");
            params.push(rusqlite::types::Value::Text(f.clone()));
        }
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
    if let Some(ref b64) = update.picture_base64 {
        sets.push("has_cover = ?");
        params.push(rusqlite::types::Value::Integer(if b64.is_empty() {
            0
        } else {
            1
        }));
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
