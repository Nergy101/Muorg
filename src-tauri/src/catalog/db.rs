use crate::metadata;
use serde::Serialize;
use sha2::{Digest, Sha256};
use std::io::{Read as IoRead, Seek, SeekFrom};
use std::path::Path;
use walkdir::WalkDir;

#[derive(Debug, Clone, Serialize)]
pub struct Playlist {
    pub id: i64,
    pub name: String,
    pub track_count: i64,
    pub icon: Option<String>,
    /// JSON rule array for smart playlists; None for regular playlists.
    pub smart_rules: Option<String>,
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
    /// User rating 1–5, or None if unrated.
    pub rating: Option<i64>,
    /// Number of times this track has been played past 30 s.
    pub play_count: i64,
    /// Unix timestamp of the most recent play, or None if never played.
    pub last_played_at: Option<i64>,
}

#[derive(Debug, Clone, Serialize)]
pub struct TrackBackupRecord {
    pub id: i64,
    pub track_path: String,
    pub backup_path: String,
    pub created_at: i64,
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
CREATE TABLE IF NOT EXISTS track_backups (
    id INTEGER PRIMARY KEY,
    track_path TEXT NOT NULL,
    backup_path TEXT NOT NULL,
    created_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_track_backups_track_path ON track_backups(track_path);
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
    if !schema_has_column(conn, "tracks", "content_hash")? {
        conn.execute("ALTER TABLE tracks ADD COLUMN content_hash TEXT", [])
            .map_err(|e| e.to_string())?;
        conn.execute_batch(
            "CREATE INDEX IF NOT EXISTS idx_tracks_content_hash ON tracks(content_hash);",
        )
        .map_err(|e| e.to_string())?;
    }
    if !schema_has_column(conn, "tracks", "deleted_at")? {
        conn.execute("ALTER TABLE tracks ADD COLUMN deleted_at INTEGER", [])
            .map_err(|e| e.to_string())?;
    }
    if !schema_has_column(conn, "roots", "deleted_at")? {
        conn.execute("ALTER TABLE roots ADD COLUMN deleted_at INTEGER", [])
            .map_err(|e| e.to_string())?;
    }
    if !schema_has_column(conn, "tracks", "rating")? {
        conn.execute("ALTER TABLE tracks ADD COLUMN rating INTEGER", [])
            .map_err(|e| e.to_string())?;
    }
    if !schema_has_column(conn, "playlists", "icon")? {
        conn.execute("ALTER TABLE playlists ADD COLUMN icon TEXT", [])
            .map_err(|e| e.to_string())?;
    }
    if !schema_has_column(conn, "tracks", "play_count")? {
        conn.execute(
            "ALTER TABLE tracks ADD COLUMN play_count INTEGER NOT NULL DEFAULT 0",
            [],
        )
        .map_err(|e| e.to_string())?;
    }
    if !schema_has_column(conn, "tracks", "last_played_at")? {
        conn.execute("ALTER TABLE tracks ADD COLUMN last_played_at INTEGER", [])
            .map_err(|e| e.to_string())?;
    }
    if !schema_has_column(conn, "playlists", "smart_rules")? {
        conn.execute("ALTER TABLE playlists ADD COLUMN smart_rules TEXT", [])
            .map_err(|e| e.to_string())?;
    }
    if !schema_has_column(conn, "playlists", "sort_order")? {
        conn.execute(
            "ALTER TABLE playlists ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0",
            [],
        )
        .map_err(|e| e.to_string())?;
        // Seed sort_order from existing creation order so the list doesn't shuffle.
        conn.execute_batch(
            "UPDATE playlists SET sort_order = (
                SELECT COUNT(*) FROM playlists p2 WHERE p2.created_at < playlists.created_at
             )",
        )
        .map_err(|e| e.to_string())?;
    }
    // FTS5 full-text search — regular (non-content) table, kept in sync via triggers.
    //
    // Detect whether the existing fts_tracks table is the old content-table style.
    // A content FTS5 table has 'content' = 'tracks' in fts_tracks_config; a regular
    // table has 'content' = ''. Missing config table means the table doesn't exist yet.
    let fts_needs_setup: bool = conn
        .query_row(
            "SELECT v FROM fts_tracks_config WHERE k = 'content'",
            [],
            |r| r.get::<_, String>(0),
        )
        .map(|v| v == "tracks") // true  → old content-table, must migrate
        .unwrap_or(true);       // error → table absent or unreadable, also set up fresh

    if fts_needs_setup {
        // Drop the old content-table FTS and its triggers (ignore errors — the table
        // might not exist or might itself be in a malformed state).
        let _ = conn.execute_batch(
            "DROP TABLE IF EXISTS fts_tracks;
             DROP TRIGGER IF EXISTS tracks_ai;
             DROP TRIGGER IF EXISTS tracks_au;
             DROP TRIGGER IF EXISTS tracks_ad;",
        );

        conn.execute_batch(
            // Regular FTS5 table — stores its own index data, no external-content sync needed.
            "CREATE VIRTUAL TABLE IF NOT EXISTS fts_tracks USING fts5(
                 title, artist, album, album_artist, genre,
                 tokenize='unicode61 remove_diacritics 1'
             );
             -- INSERT: index the new row.
             CREATE TRIGGER IF NOT EXISTS tracks_ai AFTER INSERT ON tracks BEGIN
                 INSERT INTO fts_tracks(rowid, title, artist, album, album_artist, genre)
                 VALUES(new.id, new.title, new.artist, new.album, new.album_artist, new.genre);
             END;
             -- UPDATE of indexed columns only (not play_count, rating, deleted_at, etc.).
             CREATE TRIGGER IF NOT EXISTS tracks_au
                 AFTER UPDATE OF title, artist, album, album_artist, genre ON tracks BEGIN
                 DELETE FROM fts_tracks WHERE rowid = old.id;
                 INSERT INTO fts_tracks(rowid, title, artist, album, album_artist, genre)
                 VALUES(new.id, new.title, new.artist, new.album, new.album_artist, new.genre);
             END;
             -- DELETE: remove index entry (safe even if rowid not present).
             CREATE TRIGGER IF NOT EXISTS tracks_ad AFTER DELETE ON tracks BEGIN
                 DELETE FROM fts_tracks WHERE rowid = old.id;
             END;",
        )
        .map_err(|e| e.to_string())?;

        // Populate FTS from all current non-deleted tracks.
        conn.execute_batch(
            "INSERT INTO fts_tracks(rowid, title, artist, album, album_artist, genre)
             SELECT id, title, artist, album, album_artist, genre
             FROM tracks WHERE deleted_at IS NULL",
        )
        .map_err(|e| e.to_string())?;
    }
    Ok(())
}

/// Compute a content fingerprint for a file: SHA-256 of the 8-byte little-endian file size
/// followed by up to 64 KB read from the tail of the file. Reading from the tail reliably
/// lands in audio data for both MP3 (ID3 tags are at the front) and FLAC (metadata blocks
/// are at the front), making the hash stable across tag-only metadata edits.
pub fn compute_content_hash(path: &Path) -> Result<String, String> {
    let mut file = std::fs::File::open(path).map_err(|e| e.to_string())?;
    let file_size = file.metadata().map_err(|e| e.to_string())?.len();
    let mut hasher = Sha256::new();
    hasher.update(file_size.to_le_bytes());
    const TAIL_SIZE: u64 = 65_536;
    let offset = file_size.saturating_sub(TAIL_SIZE);
    file.seek(SeekFrom::Start(offset)).map_err(|e| e.to_string())?;
    let mut buf = Vec::with_capacity(TAIL_SIZE as usize);
    file.read_to_end(&mut buf).map_err(|e| e.to_string())?;
    hasher.update(&buf);
    Ok(format!("{:x}", hasher.finalize()))
}

/// Update only the content_hash column for an existing track (looked up by path).
pub fn update_track_hash(
    conn: &rusqlite::Connection,
    path: &str,
    hash: &str,
) -> Result<(), String> {
    conn.execute(
        "UPDATE tracks SET content_hash = ?1 WHERE path = ?2",
        rusqlite::params![hash, path],
    )
    .map_err(|e| e.to_string())?;
    Ok(())
}

/// Hard-delete soft-deleted tracks whose `deleted_at` timestamp is older than `older_than_secs`
/// seconds ago, and hard-delete their now-childless soft-deleted root rows.
/// Call on startup for gradual cleanup; safe to skip entirely.
pub fn gc_deleted_tracks(
    conn: &rusqlite::Connection,
    older_than_secs: i64,
) -> Result<(), String> {
    let cutoff = now_secs()? - older_than_secs;
    conn.execute(
        "DELETE FROM tracks WHERE deleted_at IS NOT NULL AND deleted_at < ?1",
        [cutoff],
    )
    .map_err(|e| e.to_string())?;
    // Remove soft-deleted roots that no longer have any track rows referencing them.
    conn.execute(
        "DELETE FROM roots \
         WHERE deleted_at IS NOT NULL AND deleted_at < ?1 \
         AND id NOT IN (SELECT DISTINCT root_id FROM tracks)",
        [cutoff],
    )
    .map_err(|e| e.to_string())?;
    Ok(())
}

fn now_secs() -> Result<i64, String> {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_secs() as i64)
        .map_err(|_| "time error".to_string())
}

pub fn save_roots(conn: &rusqlite::Connection, paths: &[String]) -> Result<(), String> {
    let now = now_secs()?;
    let mut stmt = conn
        .prepare(
            "INSERT INTO roots (path, added_at) VALUES (?1, ?2) \
             ON CONFLICT(path) DO UPDATE SET deleted_at = NULL, added_at = excluded.added_at",
        )
        .map_err(|e| e.to_string())?;
    for p in paths {
        stmt.execute(rusqlite::params![p, now])
            .map_err(|e| e.to_string())?;
    }
    Ok(())
}

pub fn load_roots(conn: &rusqlite::Connection) -> Result<Vec<String>, String> {
    let mut stmt = conn
        .prepare("SELECT path FROM roots WHERE deleted_at IS NULL ORDER BY added_at")
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
    load_tracks_filtered(conn, None)
}

/// Load tracks, optionally restricted to a specific set of IDs (returned in the order given).
fn load_tracks_filtered(
    conn: &rusqlite::Connection,
    id_order: Option<&[i64]>,
) -> Result<Vec<CatalogTrack>, String> {
    let has_cover_col = schema_has_column(conn, "tracks", "has_cover")?;
    let featuring_col = schema_has_column(conn, "tracks", "featuring")?;
    let deleted_at_col = schema_has_column(conn, "tracks", "deleted_at")?;
    let rating_col = schema_has_column(conn, "tracks", "rating")?;
    let play_count_col = schema_has_column(conn, "tracks", "play_count")?;
    let last_played_at_col = schema_has_column(conn, "tracks", "last_played_at")?;

    // Build column list, recording the index of each optional column.
    let mut cols: Vec<&str> = Vec::with_capacity(20);
    cols.extend_from_slice(&["id", "path", "root_id", "title", "artist", "album", "album_artist"]);
    let feat_idx: Option<usize> = if featuring_col {
        let i = cols.len(); cols.push("featuring"); Some(i)
    } else { None };
    cols.extend_from_slice(&["year", "genre", "track_number", "disc_number", "duration_secs", "format", "mtime_secs"]);
    let cover_idx: Option<usize> = if has_cover_col {
        let i = cols.len(); cols.push("has_cover"); Some(i)
    } else { None };
    let rating_idx: Option<usize> = if rating_col {
        let i = cols.len(); cols.push("rating"); Some(i)
    } else { None };
    let play_count_idx: Option<usize> = if play_count_col {
        let i = cols.len(); cols.push("play_count"); Some(i)
    } else { None };
    let last_played_idx: Option<usize> = if last_played_at_col {
        let i = cols.len(); cols.push("last_played_at"); Some(i)
    } else { None };

    // Base index of the year column — shifts by 1 if featuring is present.
    let year_base: usize = if featuring_col { 8 } else { 7 };

    let sql = match id_order {
        None => {
            let filter = if deleted_at_col { " WHERE deleted_at IS NULL" } else { "" };
            format!(
                "SELECT {} FROM tracks{} ORDER BY artist, album, track_number, title",
                cols.join(", "), filter
            )
        }
        Some(ids) => {
            let placeholders = ids.iter().map(|_| "?").collect::<Vec<_>>().join(",");
            let del = if deleted_at_col { " AND deleted_at IS NULL" } else { "" };
            format!(
                "SELECT {} FROM tracks WHERE id IN ({}){}",
                cols.join(", "), placeholders, del
            )
        }
    };

    let map_row = move |r: &rusqlite::Row| {
        let featuring = feat_idx.and_then(|i| r.get::<_, Option<String>>(i).ok().flatten());
        let has_cover = cover_idx
            .and_then(|i| r.get::<_, i64>(i).ok())
            .map(|n| n != 0)
            .unwrap_or(false);
        let rating = rating_idx.and_then(|i| r.get::<_, Option<i64>>(i).ok().flatten());
        let play_count = play_count_idx
            .and_then(|i| r.get::<_, i64>(i).ok())
            .unwrap_or(0);
        let last_played_at = last_played_idx.and_then(|i| r.get::<_, Option<i64>>(i).ok().flatten());
        Ok(CatalogTrack {
            id: r.get(0)?,
            path: r.get(1)?,
            root_id: r.get(2)?,
            title: r.get(3)?,
            artist: r.get(4)?,
            album: r.get(5)?,
            album_artist: r.get(6)?,
            featuring,
            year: r.get(year_base)?,
            genre: r.get(year_base + 1)?,
            track_number: r.get(year_base + 2)?,
            disc_number: r.get(year_base + 3)?,
            duration_secs: r.get(year_base + 4)?,
            format: r.get(year_base + 5)?,
            mtime_secs: r.get(year_base + 6)?,
            has_cover,
            rating,
            play_count,
            last_played_at,
        })
    };

    let mut stmt = conn.prepare(&sql).map_err(|e| e.to_string())?;
    let rows = match id_order {
        None => stmt.query_map([], map_row).map_err(|e| e.to_string())?,
        Some(ids) => {
            let params: Vec<rusqlite::types::Value> =
                ids.iter().map(|&id| rusqlite::types::Value::Integer(id)).collect();
            stmt.query_map(rusqlite::params_from_iter(params.iter()), map_row)
                .map_err(|e| e.to_string())?
        }
    };

    let mut out: Vec<CatalogTrack> = rows
        .filter_map(|r| r.ok())
        .collect();

    // When loading by ID list, re-sort to match the requested order.
    if let Some(ids) = id_order {
        let pos: std::collections::HashMap<i64, usize> =
            ids.iter().enumerate().map(|(i, &id)| (id, i)).collect();
        out.sort_by_key(|t| pos.get(&t.id).copied().unwrap_or(usize::MAX));
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

/// Scan directory for mp3/flac, read metadata, insert into DB. Returns number of tracks processed.
///
/// Uses hash-aware upsert logic when the schema supports it:
///
/// 1. If an existing row (deleted or not) has the same (root_id, path), update it in-place.
/// 2. Else if a soft-deleted row has a matching content_hash, resurrect it at the new location.
/// 3. Otherwise insert a new row.
///
/// Updating in-place preserves the track's `id` and keeps all `playlist_tracks` references intact.
pub fn scan_and_insert(conn: &rusqlite::Connection, root_path: &str) -> Result<u64, String> {
    let root_id: i64 = conn
        .query_row("SELECT id FROM roots WHERE path = ?1", [root_path], |r| {
            r.get(0)
        })
        .map_err(|e| e.to_string())?;

    let mut count = 0u64;
    let featuring_col = schema_has_column(conn, "tracks", "featuring")?;
    let deleted_at_col = schema_has_column(conn, "tracks", "deleted_at")?;
    let content_hash_col = schema_has_column(conn, "tracks", "content_hash")?;
    let use_smart_upsert = deleted_at_col && content_hash_col;

    // Fallback INSERT OR REPLACE used only on old DBs that predate our migrations.
    let insert_sql = if featuring_col {
        "INSERT OR REPLACE INTO tracks (root_id, path, title, artist, album, album_artist, featuring, year, genre, track_number, disc_number, duration_secs, format, mtime_secs, has_cover) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14, ?15)"
    } else {
        "INSERT OR REPLACE INTO tracks (root_id, path, title, artist, album, album_artist, year, genre, track_number, disc_number, duration_secs, format, mtime_secs, has_cover) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14)"
    };
    let mut legacy_insert = conn.prepare(insert_sql).map_err(|e| e.to_string())?;

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

        if use_smart_upsert {
            let hash = compute_content_hash(path).ok();

            // Step 1: Look for any existing row with this (root_id, path) — deleted or not.
            let by_path: Option<i64> = conn
                .query_row(
                    "SELECT id FROM tracks WHERE root_id = ?1 AND path = ?2",
                    rusqlite::params![root_id, &path_str],
                    |r| r.get(0),
                )
                .ok();

            if let Some(track_id) = by_path {
                update_track_row(
                    conn,
                    track_id,
                    root_id,
                    &path_str,
                    &meta,
                    format,
                    mtime_secs,
                    has_cover,
                    hash.as_deref(),
                    featuring_col,
                )?;
                count += 1;
                continue;
            }

            // Step 1b: Same absolute path, any root — covers the common case of a parent folder
            // being (re-)added. The file hasn't moved; only the root that claims it changes.
            let by_path_any_root: Option<i64> = conn
                .query_row(
                    "SELECT id FROM tracks WHERE path = ?1 AND deleted_at IS NOT NULL LIMIT 1",
                    [&path_str],
                    |r| r.get(0),
                )
                .ok();

            if let Some(track_id) = by_path_any_root {
                update_track_row(
                    conn,
                    track_id,
                    root_id,
                    &path_str,
                    &meta,
                    format,
                    mtime_secs,
                    has_cover,
                    hash.as_deref(),
                    featuring_col,
                )?;
                count += 1;
                continue;
            }

            // Step 2: Look for a soft-deleted row whose content_hash matches (cross-location).
            if let Some(ref h) = hash {
                let by_hash: Option<i64> = conn
                    .query_row(
                        "SELECT id FROM tracks WHERE content_hash = ?1 AND deleted_at IS NOT NULL LIMIT 1",
                        [h],
                        |r| r.get(0),
                    )
                    .ok();

                if let Some(track_id) = by_hash {
                    update_track_row(
                        conn,
                        track_id,
                        root_id,
                        &path_str,
                        &meta,
                        format,
                        mtime_secs,
                        has_cover,
                        hash.as_deref(),
                        featuring_col,
                    )?;
                    count += 1;
                    continue;
                }
            }

            // Step 2b: Path-suffix fallback for pre-migration tracks whose content_hash is NULL.
            // Matches a soft-deleted track whose relative path (path minus its root prefix)
            // is a suffix of the new absolute path. This handles the case where a parent
            // folder is re-added after individual sub-folders were removed: e.g. a track that
            // lived at /old/artistA/album/t.flac (root=/old/artistA) will have relative path
            // "album/t.flac", which is a suffix of the new /music/artistA/album/t.flac.
            // Only applied to tracks with no stored hash; if a hash exists but didn't match
            // in step 2 the file content has changed and we should not resurrect it.
            {
                let by_suffix: Option<i64> = conn
                    .query_row(
                        "SELECT t.id FROM tracks t \
                         JOIN roots r ON r.id = t.root_id \
                         WHERE t.deleted_at IS NOT NULL \
                         AND t.content_hash IS NULL \
                         AND ?1 LIKE '%' || SUBSTR(t.path, LENGTH(r.path) + 2) \
                         ORDER BY LENGTH(t.path) DESC \
                         LIMIT 1",
                        [&path_str],
                        |r| r.get(0),
                    )
                    .ok();

                if let Some(track_id) = by_suffix {
                    update_track_row(
                        conn,
                        track_id,
                        root_id,
                        &path_str,
                        &meta,
                        format,
                        mtime_secs,
                        has_cover,
                        hash.as_deref(),
                        featuring_col,
                    )?;
                    count += 1;
                    continue;
                }
            }

            // Step 3: Genuinely new file — insert.
            insert_track_row(
                conn,
                root_id,
                &path_str,
                &meta,
                format,
                mtime_secs,
                has_cover,
                hash.as_deref(),
                featuring_col,
            )?;
            count += 1;
            continue;
        }

        // Legacy path: INSERT OR REPLACE (old DB schema without soft-delete columns).
        if featuring_col {
            legacy_insert
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
            legacy_insert
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

/// UPDATE an existing track row in-place by its `id`, refreshing all metadata and clearing
/// `deleted_at` (resurrection). The row's primary key is unchanged, so all `playlist_tracks`
/// FK references survive.
#[allow(clippy::too_many_arguments)]
fn update_track_row(
    conn: &rusqlite::Connection,
    track_id: i64,
    root_id: i64,
    path_str: &str,
    meta: &metadata::TrackMetadata,
    format: &str,
    mtime_secs: i64,
    has_cover: bool,
    content_hash: Option<&str>,
    featuring_col: bool,
) -> Result<(), String> {
    let mut sets: Vec<&str> = vec![
        "deleted_at = NULL",
        "root_id = ?",
        "path = ?",
        "title = ?",
        "artist = ?",
        "album = ?",
        "album_artist = ?",
        "year = ?",
        "genre = ?",
        "track_number = ?",
        "disc_number = ?",
        "duration_secs = ?",
        "format = ?",
        "mtime_secs = ?",
        "has_cover = ?",
    ];
    if featuring_col {
        sets.push("featuring = ?");
    }
    if content_hash.is_some() {
        sets.push("content_hash = ?");
    }
    let sql = format!("UPDATE tracks SET {} WHERE id = ?", sets.join(", "));

    let mut params: Vec<rusqlite::types::Value> = vec![
        rusqlite::types::Value::Integer(root_id),
        rusqlite::types::Value::Text(path_str.to_string()),
        meta.title.clone().map(rusqlite::types::Value::Text).unwrap_or(rusqlite::types::Value::Null),
        meta.artist.clone().map(rusqlite::types::Value::Text).unwrap_or(rusqlite::types::Value::Null),
        meta.album.clone().map(rusqlite::types::Value::Text).unwrap_or(rusqlite::types::Value::Null),
        meta.album_artist.clone().map(rusqlite::types::Value::Text).unwrap_or(rusqlite::types::Value::Null),
        meta.year.map(|y| rusqlite::types::Value::Integer(y as i64)).unwrap_or(rusqlite::types::Value::Null),
        meta.genre.clone().map(rusqlite::types::Value::Text).unwrap_or(rusqlite::types::Value::Null),
        meta.track_number.map(|n| rusqlite::types::Value::Integer(n as i64)).unwrap_or(rusqlite::types::Value::Null),
        meta.disc_number.map(|n| rusqlite::types::Value::Integer(n as i64)).unwrap_or(rusqlite::types::Value::Null),
        meta.duration_secs.map(|d| rusqlite::types::Value::Integer(d as i64)).unwrap_or(rusqlite::types::Value::Null),
        rusqlite::types::Value::Text(format.to_string()),
        rusqlite::types::Value::Integer(mtime_secs),
        rusqlite::types::Value::Integer(if has_cover { 1 } else { 0 }),
    ];
    if featuring_col {
        params.push(
            meta.featuring.clone().map(rusqlite::types::Value::Text).unwrap_or(rusqlite::types::Value::Null),
        );
    }
    if let Some(h) = content_hash {
        params.push(rusqlite::types::Value::Text(h.to_string()));
    }
    params.push(rusqlite::types::Value::Integer(track_id));

    let mut stmt = conn.prepare(&sql).map_err(|e| e.to_string())?;
    stmt.execute(rusqlite::params_from_iter(params.iter()))
        .map_err(|e| e.to_string())?;
    Ok(())
}

/// INSERT a brand-new track row.
#[allow(clippy::too_many_arguments)]
fn insert_track_row(
    conn: &rusqlite::Connection,
    root_id: i64,
    path_str: &str,
    meta: &metadata::TrackMetadata,
    format: &str,
    mtime_secs: i64,
    has_cover: bool,
    content_hash: Option<&str>,
    featuring_col: bool,
) -> Result<(), String> {
    let mut cols = vec![
        "root_id", "path", "title", "artist", "album", "album_artist",
        "year", "genre", "track_number", "disc_number", "duration_secs",
        "format", "mtime_secs", "has_cover",
    ];
    if featuring_col {
        cols.push("featuring");
    }
    if content_hash.is_some() {
        cols.push("content_hash");
    }
    let placeholders: Vec<&str> = (0..cols.len()).map(|_| "?").collect();
    let sql = format!(
        "INSERT INTO tracks ({}) VALUES ({})",
        cols.join(", "),
        placeholders.join(", ")
    );

    let mut params: Vec<rusqlite::types::Value> = vec![
        rusqlite::types::Value::Integer(root_id),
        rusqlite::types::Value::Text(path_str.to_string()),
        meta.title.clone().map(rusqlite::types::Value::Text).unwrap_or(rusqlite::types::Value::Null),
        meta.artist.clone().map(rusqlite::types::Value::Text).unwrap_or(rusqlite::types::Value::Null),
        meta.album.clone().map(rusqlite::types::Value::Text).unwrap_or(rusqlite::types::Value::Null),
        meta.album_artist.clone().map(rusqlite::types::Value::Text).unwrap_or(rusqlite::types::Value::Null),
        meta.year.map(|y| rusqlite::types::Value::Integer(y as i64)).unwrap_or(rusqlite::types::Value::Null),
        meta.genre.clone().map(rusqlite::types::Value::Text).unwrap_or(rusqlite::types::Value::Null),
        meta.track_number.map(|n| rusqlite::types::Value::Integer(n as i64)).unwrap_or(rusqlite::types::Value::Null),
        meta.disc_number.map(|n| rusqlite::types::Value::Integer(n as i64)).unwrap_or(rusqlite::types::Value::Null),
        meta.duration_secs.map(|d| rusqlite::types::Value::Integer(d as i64)).unwrap_or(rusqlite::types::Value::Null),
        rusqlite::types::Value::Text(format.to_string()),
        rusqlite::types::Value::Integer(mtime_secs),
        rusqlite::types::Value::Integer(if has_cover { 1 } else { 0 }),
    ];
    if featuring_col {
        params.push(
            meta.featuring.clone().map(rusqlite::types::Value::Text).unwrap_or(rusqlite::types::Value::Null),
        );
    }
    if let Some(h) = content_hash {
        params.push(rusqlite::types::Value::Text(h.to_string()));
    }

    let mut stmt = conn.prepare(&sql).map_err(|e| e.to_string())?;
    stmt.execute(rusqlite::params_from_iter(params.iter()))
        .map_err(|e| e.to_string())?;
    Ok(())
}

/// Rescan a root: re-read files from disk, update DB (add new, update changed), and
/// soft-delete tracks whose files no longer exist on disk.
pub fn rescan_root(conn: &rusqlite::Connection, root_path: &str) -> Result<u64, String> {
    let count = scan_and_insert(conn, root_path)?;
    let root_id: i64 = conn
        .query_row("SELECT id FROM roots WHERE path = ?1", [root_path], |r| r.get(0))
        .map_err(|e| e.to_string())?;
    let deleted_at_col = schema_has_column(conn, "tracks", "deleted_at")?;
    let mut stmt = conn
        .prepare("SELECT id, path FROM tracks WHERE root_id = ?1 AND deleted_at IS NULL")
        .map_err(|e| e.to_string())?;
    let rows: Vec<(i64, String)> = stmt
        .query_map([root_id], |r| Ok((r.get(0)?, r.get(1)?)))
        .map_err(|e| e.to_string())?
        .collect::<Result<Vec<_>, _>>()
        .map_err(|e| e.to_string())?;
    if deleted_at_col {
        let now = now_secs()?;
        let mut soft_del = conn
            .prepare("UPDATE tracks SET deleted_at = ?1 WHERE id = ?2")
            .map_err(|e| e.to_string())?;
        for (id, path) in rows {
            if !Path::new(&path).exists() {
                soft_del.execute(rusqlite::params![now, id]).map_err(|e| e.to_string())?;
            }
        }
    } else {
        let mut del = conn
            .prepare("DELETE FROM tracks WHERE id = ?1")
            .map_err(|e| e.to_string())?;
        for (id, path) in rows {
            if !Path::new(&path).exists() {
                del.execute([id]).map_err(|e| e.to_string())?;
            }
        }
    }
    Ok(count)
}

/// Remove a root from the active catalog.
///
/// Both the root row and its track rows are **soft-deleted** (their `deleted_at` timestamp is
/// set to now). This preserves the `tracks.id` primary keys so that `playlist_tracks` FK
/// references remain intact. When the same folder is re-added later, `scan_and_insert` will
/// match files by (root_id, path) or content_hash and resurrect the rows in-place, restoring
/// all playlist memberships automatically.
pub fn remove_root(conn: &rusqlite::Connection, root_path: &str) -> Result<(), String> {
    let root_id: i64 = conn
        .query_row("SELECT id FROM roots WHERE path = ?1", [root_path], |r| {
            r.get(0)
        })
        .map_err(|e| e.to_string())?;
    let deleted_at_col = schema_has_column(conn, "tracks", "deleted_at")?;
    let roots_deleted_at_col = schema_has_column(conn, "roots", "deleted_at")?;
    if deleted_at_col && roots_deleted_at_col {
        let now = now_secs()?;
        conn.execute(
            "UPDATE tracks SET deleted_at = ?1 WHERE root_id = ?2",
            rusqlite::params![now, root_id],
        )
        .map_err(|e| e.to_string())?;
        // Soft-delete the root itself to keep its row (and satisfy FK constraints on
        // tracks.root_id) while hiding it from load_roots / the active folder list.
        conn.execute(
            "UPDATE roots SET deleted_at = ?1 WHERE id = ?2",
            rusqlite::params![now, root_id],
        )
        .map_err(|e| e.to_string())?;
    } else {
        // Legacy hard-delete path for old DBs without soft-delete columns.
        conn.execute("DELETE FROM tracks WHERE root_id = ?1", [root_id])
            .map_err(|e| e.to_string())?;
        conn.execute("DELETE FROM roots WHERE id = ?1", [root_id])
            .map_err(|e| e.to_string())?;
    }
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
    let now = now_secs()?;
    let next_order: i64 = conn
        .query_row("SELECT COALESCE(MAX(sort_order) + 1, 0) FROM playlists", [], |r| r.get(0))
        .unwrap_or(0);
    conn.execute(
        "INSERT INTO playlists (name, created_at, sort_order) VALUES (?1, ?2, ?3)",
        rusqlite::params![name, now, next_order],
    )
    .map_err(|e| e.to_string())?;
    let id = conn.last_insert_rowid();
    Ok(Playlist {
        id,
        name: name.to_string(),
        track_count: 0,
        icon: None,
        smart_rules: None,
    })
}

pub fn load_playlists(conn: &rusqlite::Connection) -> Result<Vec<Playlist>, String> {
    let deleted_at_col = schema_has_column(conn, "tracks", "deleted_at")?;
    let sql = if deleted_at_col {
        // Only count playlist entries whose track is not soft-deleted.
        "SELECT p.id, p.name, COUNT(t.id) as track_count, p.icon, p.smart_rules \
         FROM playlists p \
         LEFT JOIN playlist_tracks pt ON pt.playlist_id = p.id \
         LEFT JOIN tracks t ON t.id = pt.track_id AND t.deleted_at IS NULL \
         GROUP BY p.id, p.name, p.icon, p.smart_rules \
         ORDER BY p.sort_order, p.created_at"
    } else {
        "SELECT p.id, p.name, COUNT(pt.id) as track_count, p.icon, p.smart_rules \
         FROM playlists p \
         LEFT JOIN playlist_tracks pt ON pt.playlist_id = p.id \
         GROUP BY p.id, p.name, p.icon, p.smart_rules \
         ORDER BY p.sort_order, p.created_at"
    };
    let mut stmt = conn.prepare(sql).map_err(|e| e.to_string())?;
    let rows = stmt
        .query_map([], |r| {
            Ok(Playlist {
                id: r.get(0)?,
                name: r.get(1)?,
                track_count: r.get(2)?,
                icon: r.get(3)?,
                smart_rules: r.get(4)?,
            })
        })
        .map_err(|e| e.to_string())?;
    let mut out = Vec::new();
    for row in rows {
        out.push(row.map_err(|e| e.to_string())?);
    }
    // The JOIN-based count is always 0 for smart playlists (no playlist_tracks rows).
    // Resolve the real count by running each smart playlist's rules.
    for playlist in &mut out {
        if let Some(rules_json) = &playlist.smart_rules.clone() {
            playlist.track_count =
                count_smart_playlist_tracks(conn, rules_json).unwrap_or(0);
        }
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

pub fn set_playlist_icon(conn: &rusqlite::Connection, id: i64, icon: Option<&str>) -> Result<(), String> {
    conn.execute(
        "UPDATE playlists SET icon = ?1 WHERE id = ?2",
        rusqlite::params![icon, id],
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

/// Returns the IDs of all playlists that contain a given track.
pub fn get_playlists_for_track(
    conn: &rusqlite::Connection,
    track_id: i64,
) -> Result<Vec<i64>, String> {
    let mut stmt = conn
        .prepare(
            "SELECT DISTINCT playlist_id FROM playlist_tracks WHERE track_id = ?1",
        )
        .map_err(|e| e.to_string())?;
    let rows = stmt
        .query_map([track_id], |r| r.get::<_, i64>(0))
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

    if let Some(v) = &update.title {
        sets.push("title = ?");
        params.push(match v {
            Some(s) => rusqlite::types::Value::Text(s.clone()),
            None => rusqlite::types::Value::Null,
        });
    }
    if let Some(v) = &update.artist {
        sets.push("artist = ?");
        params.push(match v {
            Some(s) => rusqlite::types::Value::Text(s.clone()),
            None => rusqlite::types::Value::Null,
        });
    }
    if let Some(v) = &update.album {
        sets.push("album = ?");
        params.push(match v {
            Some(s) => rusqlite::types::Value::Text(s.clone()),
            None => rusqlite::types::Value::Null,
        });
    }
    if let Some(v) = &update.album_artist {
        sets.push("album_artist = ?");
        params.push(match v {
            Some(s) => rusqlite::types::Value::Text(s.clone()),
            None => rusqlite::types::Value::Null,
        });
    }
    if let Some(v) = &update.featuring {
        if schema_has_column(conn, "tracks", "featuring")? {
            sets.push("featuring = ?");
            params.push(match v {
                Some(s) => rusqlite::types::Value::Text(s.clone()),
                None => rusqlite::types::Value::Null,
            });
        }
    }
    if let Some(v) = update.year {
        sets.push("year = ?");
        params.push(match v {
            Some(y) => rusqlite::types::Value::Integer(y as i64),
            None => rusqlite::types::Value::Null,
        });
    }
    if let Some(v) = &update.genre {
        sets.push("genre = ?");
        params.push(match v {
            Some(s) => rusqlite::types::Value::Text(s.clone()),
            None => rusqlite::types::Value::Null,
        });
    }
    if let Some(v) = update.track_number {
        sets.push("track_number = ?");
        params.push(match v {
            Some(n) => rusqlite::types::Value::Integer(n as i64),
            None => rusqlite::types::Value::Null,
        });
    }
    if let Some(v) = update.disc_number {
        sets.push("disc_number = ?");
        params.push(match v {
            Some(n) => rusqlite::types::Value::Integer(n as i64),
            None => rusqlite::types::Value::Null,
        });
    }
    if let Some(v) = &update.picture_base64 {
        // Clearing picture should also clear has_cover.
        let has = match v {
            Some(b64) => if b64.is_empty() { 0 } else { 1 },
            None => 0,
        };
        sets.push("has_cover = ?");
        params.push(rusqlite::types::Value::Integer(has));
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

/// Set (or clear) the star rating for a track. `rating` must be 1–5, or None to clear.
pub fn set_track_rating(
    conn: &rusqlite::Connection,
    path: &str,
    rating: Option<i64>,
) -> Result<(), String> {
    conn.execute(
        "UPDATE tracks SET rating = ?1 WHERE path = ?2",
        rusqlite::params![rating, path],
    )
    .map_err(|e| e.to_string())?;
    Ok(())
}

pub fn update_track_path(
    conn: &rusqlite::Connection,
    old_path: &str,
    new_path: &str,
) -> Result<(), String> {
    conn.execute(
        "UPDATE tracks SET path = ?1 WHERE path = ?2",
        rusqlite::params![new_path, old_path],
    )
    .map_err(|e| e.to_string())?;
    Ok(())
}

pub fn record_track_backup(
    conn: &rusqlite::Connection,
    track_path: &str,
    backup_path: &str,
) -> Result<(), String> {
    let created_at = now_secs()?;
    conn.execute(
        "INSERT INTO track_backups (track_path, backup_path, created_at) VALUES (?1, ?2, ?3)",
        rusqlite::params![track_path, backup_path, created_at],
    )
    .map_err(|e| e.to_string())?;
    Ok(())
}

pub fn get_latest_track_backup(
    conn: &rusqlite::Connection,
    track_path: &str,
) -> Result<Option<TrackBackupRecord>, String> {
    let mut stmt = conn
        .prepare(
            "SELECT id, track_path, backup_path, created_at FROM track_backups \
             WHERE track_path = ?1 ORDER BY created_at DESC, id DESC LIMIT 1",
        )
        .map_err(|e| e.to_string())?;
    let mut rows = stmt
        .query(rusqlite::params![track_path])
        .map_err(|e| e.to_string())?;
    if let Some(r) = rows.next().map_err(|e| e.to_string())? {
        return Ok(Some(TrackBackupRecord {
            id: r.get(0).map_err(|e| e.to_string())?,
            track_path: r.get(1).map_err(|e| e.to_string())?,
            backup_path: r.get(2).map_err(|e| e.to_string())?,
            created_at: r.get(3).map_err(|e| e.to_string())?,
        }));
    }
    Ok(None)
}

// ── Play count ─────────────────────────────────────────────────────────────

/// Increment play_count and update last_played_at for the given track path.
pub fn record_play(conn: &rusqlite::Connection, path: &str) -> Result<(), String> {
    let now = now_secs()?;
    conn.execute(
        "UPDATE tracks SET play_count = COALESCE(play_count, 0) + 1, last_played_at = ?1 WHERE path = ?2",
        rusqlite::params![now, path],
    )
    .map_err(|e| e.to_string())?;
    Ok(())
}

// ── Full-text search ───────────────────────────────────────────────────────

/// Full-text search over title, artist, album, album_artist, genre using FTS5.
/// Returns matching tracks in relevance order (best match first).
pub fn search_tracks(conn: &rusqlite::Connection, query: &str) -> Result<Vec<CatalogTrack>, String> {
    let q = query.trim();
    if q.is_empty() {
        return Ok(Vec::new());
    }
    // Build an FTS5 prefix query: each whitespace-delimited token becomes "token"*
    let fts_query: String = q
        .split_whitespace()
        .map(|w| format!("\"{}\"*", w.replace(['"', '\''], "")))
        .collect::<Vec<_>>()
        .join(" ");
    if fts_query.is_empty() {
        return Ok(Vec::new());
    }
    // Get IDs in rank order from FTS
    let mut stmt = conn
        .prepare("SELECT rowid FROM fts_tracks WHERE fts_tracks MATCH ? ORDER BY rank LIMIT 500")
        .map_err(|e| e.to_string())?;
    let ids: Vec<i64> = stmt
        .query_map([&fts_query], |r| r.get::<_, i64>(0))
        .map_err(|e| e.to_string())?
        .collect::<Result<Vec<_>, _>>()
        .map_err(|e| e.to_string())?;
    if ids.is_empty() {
        return Ok(Vec::new());
    }
    load_tracks_filtered(conn, Some(&ids))
}

// ── Library stats ──────────────────────────────────────────────────────────

#[derive(Debug, serde::Serialize)]
pub struct LibraryStats {
    pub track_count: i64,
    pub artist_count: i64,
    pub album_count: i64,
    pub total_duration_secs: i64,
}

pub fn get_library_stats(conn: &rusqlite::Connection) -> Result<LibraryStats, String> {
    let deleted_at_col = schema_has_column(conn, "tracks", "deleted_at")?;
    let filter = if deleted_at_col { "WHERE deleted_at IS NULL" } else { "" };
    let sql = format!(
        "SELECT \
            COUNT(*), \
            COUNT(DISTINCT LOWER(TRIM(COALESCE(artist, '')))), \
            COUNT(DISTINCT LOWER(TRIM(COALESCE(album, ''))) || '|||' || LOWER(TRIM(COALESCE(album_artist, '')))), \
            COALESCE(SUM(duration_secs), 0) \
         FROM tracks {}",
        filter
    );
    conn.query_row(&sql, [], |r| {
        Ok(LibraryStats {
            track_count: r.get(0)?,
            artist_count: r.get(1)?,
            album_count: r.get(2)?,
            total_duration_secs: r.get(3)?,
        })
    })
    .map_err(|e| e.to_string())
}

// ── Smart playlists ────────────────────────────────────────────────────────

/// Rule schema: `{"field": "<field>", "op": "<op>", "value": <json_value>}`.
#[derive(serde::Deserialize)]
struct SmartRule {
    field: String,
    op: String,
    value: Option<serde_json::Value>,
}

const ALLOWED_SMART_FIELDS: &[&str] = &[
    "title", "artist", "album", "album_artist", "genre", "year",
    "rating", "play_count", "last_played_at", "has_cover",
];

fn smart_value_to_sqlite(v: &Option<serde_json::Value>) -> Result<rusqlite::types::Value, String> {
    match v {
        None => Err("Missing value".to_string()),
        Some(serde_json::Value::String(s)) => Ok(rusqlite::types::Value::Text(s.clone())),
        Some(serde_json::Value::Number(n)) => {
            if let Some(i) = n.as_i64() {
                Ok(rusqlite::types::Value::Integer(i))
            } else if let Some(f) = n.as_f64() {
                Ok(rusqlite::types::Value::Real(f))
            } else {
                Err("Invalid number".to_string())
            }
        }
        Some(serde_json::Value::Bool(b)) => {
            Ok(rusqlite::types::Value::Integer(if *b { 1 } else { 0 }))
        }
        _ => Err("Unsupported value type".to_string()),
    }
}

/// Build a SQL WHERE clause and parameter list from smart playlist rules JSON.
///
/// Rules for the **same field** are combined with OR (e.g. two Genre rules match
/// tracks in either genre).  Rules for **different fields** are combined with AND.
fn build_smart_where(
    conn: &rusqlite::Connection,
    rules_json: &str,
) -> Result<(String, Vec<rusqlite::types::Value>), String> {
    let rules: Vec<SmartRule> =
        serde_json::from_str(rules_json).map_err(|e| format!("Invalid rules: {e}"))?;

    let deleted_at_col = schema_has_column(conn, "tracks", "deleted_at")?;

    // Collect (sql_fragment, optional_param) per field, preserving first-seen order.
    let mut field_order: Vec<String> = Vec::new();
    let mut field_groups: std::collections::HashMap<String, Vec<(String, Option<rusqlite::types::Value>)>> =
        std::collections::HashMap::new();

    for rule in &rules {
        if !ALLOWED_SMART_FIELDS.contains(&rule.field.as_str()) {
            return Err(format!("Unknown field: {}", rule.field));
        }
        let (sql, param) = match rule.op.as_str() {
            "eq" => (
                format!("{} = ?", rule.field),
                Some(smart_value_to_sqlite(&rule.value)?),
            ),
            "neq" => (
                format!("{} != ?", rule.field),
                Some(smart_value_to_sqlite(&rule.value)?),
            ),
            "gt" => (
                format!("{} > ?", rule.field),
                Some(smart_value_to_sqlite(&rule.value)?),
            ),
            "gte" => (
                format!("{} >= ?", rule.field),
                Some(smart_value_to_sqlite(&rule.value)?),
            ),
            "lt" => (
                format!("{} < ?", rule.field),
                Some(smart_value_to_sqlite(&rule.value)?),
            ),
            "lte" => (
                format!("{} <= ?", rule.field),
                Some(smart_value_to_sqlite(&rule.value)?),
            ),
            "is_null" => (format!("{} IS NULL", rule.field), None),
            "is_not_null" => (format!("{} IS NOT NULL", rule.field), None),
            "contains" => {
                let s = rule.value.as_ref().and_then(|v| v.as_str()).unwrap_or("");
                let escaped = s.replace('%', "\\%").replace('_', "\\_");
                (
                    format!("{} LIKE ? ESCAPE '\\'", rule.field),
                    Some(rusqlite::types::Value::Text(format!("%{escaped}%"))),
                )
            }
            other => return Err(format!("Unknown operator: {other}")),
        };
        if !field_order.contains(&rule.field) {
            field_order.push(rule.field.clone());
        }
        field_groups.entry(rule.field.clone()).or_default().push((sql, param));
    }

    // Build the final AND-of-OR-groups clause.
    let mut and_parts: Vec<String> = Vec::new();
    if deleted_at_col {
        and_parts.push("deleted_at IS NULL".to_string());
    }
    let mut params: Vec<rusqlite::types::Value> = Vec::new();

    for field in &field_order {
        let group = &field_groups[field];
        let sqls: Vec<&str> = group.iter().map(|(s, _)| s.as_str()).collect();
        if sqls.len() == 1 {
            and_parts.push(sqls[0].to_string());
        } else {
            and_parts.push(format!("({})", sqls.join(" OR ")));
        }
        for (_, param) in group {
            if let Some(p) = param {
                params.push(p.clone());
            }
        }
    }

    let where_sql = if and_parts.is_empty() {
        String::new()
    } else {
        format!("WHERE {}", and_parts.join(" AND "))
    };
    Ok((where_sql, params))
}

/// Count tracks matching smart playlist rules JSON.
fn count_smart_playlist_tracks(
    conn: &rusqlite::Connection,
    rules_json: &str,
) -> Result<i64, String> {
    let (where_sql, params) = build_smart_where(conn, rules_json)?;
    let sql = format!("SELECT COUNT(*) FROM tracks {where_sql}");
    conn.query_row(&sql, rusqlite::params_from_iter(params.iter()), |r| r.get::<_, i64>(0))
        .map_err(|e| e.to_string())
}

/// Resolve smart playlist rules JSON to a list of matching track IDs.
pub fn resolve_smart_playlist_track_ids(
    conn: &rusqlite::Connection,
    rules_json: &str,
) -> Result<Vec<i64>, String> {
    let (where_sql, params) = build_smart_where(conn, rules_json)?;
    let sql = format!(
        "SELECT id FROM tracks {where_sql} ORDER BY artist, album, track_number, title"
    );
    let mut stmt = conn.prepare(&sql).map_err(|e| e.to_string())?;
    let ids: Vec<i64> = stmt
        .query_map(rusqlite::params_from_iter(params.iter()), |r| r.get::<_, i64>(0))
        .map_err(|e| e.to_string())?
        .collect::<Result<Vec<_>, _>>()
        .map_err(|e| e.to_string())?;
    Ok(ids)
}

pub fn create_smart_playlist(
    conn: &rusqlite::Connection,
    name: &str,
    rules_json: &str,
) -> Result<Playlist, String> {
    let now = now_secs()?;
    let next_order: i64 = conn
        .query_row("SELECT COALESCE(MAX(sort_order) + 1, 0) FROM playlists", [], |r| r.get(0))
        .unwrap_or(0);
    conn.execute(
        "INSERT INTO playlists (name, created_at, smart_rules, sort_order) VALUES (?1, ?2, ?3, ?4)",
        rusqlite::params![name, now, rules_json, next_order],
    )
    .map_err(|e| e.to_string())?;
    let id = conn.last_insert_rowid();
    let track_count = count_smart_playlist_tracks(conn, rules_json).unwrap_or(0);
    Ok(Playlist {
        id,
        name: name.to_string(),
        track_count,
        icon: None,
        smart_rules: Some(rules_json.to_string()),
    })
}

pub fn set_smart_playlist_rules(
    conn: &rusqlite::Connection,
    id: i64,
    rules_json: Option<&str>,
) -> Result<(), String> {
    conn.execute(
        "UPDATE playlists SET smart_rules = ?1 WHERE id = ?2",
        rusqlite::params![rules_json, id],
    )
    .map_err(|e| e.to_string())?;
    Ok(())
}

/// Persist a new playlist order. `ids` is the full ordered list of playlist IDs;
/// each playlist's `sort_order` is set to its index in that list.
pub fn reorder_playlists(conn: &rusqlite::Connection, ids: &[i64]) -> Result<(), String> {
    for (i, id) in ids.iter().enumerate() {
        conn.execute(
            "UPDATE playlists SET sort_order = ?1 WHERE id = ?2",
            rusqlite::params![i as i64, id],
        )
        .map_err(|e| e.to_string())?;
    }
    Ok(())
}
