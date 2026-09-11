//! Tests for the catalog layer — the SQLite surface every client reads through.
//!
//! `catalog/db.rs` is the widest single file in the repo and the one place a
//! regression breaks the desktop app, the web app and Android at once, since
//! all three go through it (the desktop app links `muorg-core` directly, the
//! other two over HTTP). The cases here lean towards the behaviours that have
//! actually broken: pagination bounds, soft deletes leaking into reads,
//! move detection losing a rating, and the smart-playlist rule compiler.
//!
//! Fixtures are built by inserting rows through `RootUpsert` rather than by
//! scanning a directory, so a test says what state it needs without needing
//! audio files on disk.

use muorg_core::catalog::{self as cat, CatalogTrack, RootUpsert, ScannedTrack};
use muorg_core::metadata::{MetadataUpdate, TrackMetadata};
use rusqlite::Connection;

// ---------------------------------------------------------------------------
// Fixture helpers
// ---------------------------------------------------------------------------

/// A catalog on a temp-file database.
///
/// Not `:memory:` — `Catalog::new` is the only way in from outside the crate
/// (`init_schema` is private), and it takes a path. The file goes with the
/// `TempDir`.
struct TestCatalog {
    catalog: muorg_core::catalog::Catalog,
    _dir: tempfile::TempDir,
}

impl TestCatalog {
    fn new() -> Self {
        let dir = tempfile::tempdir().expect("temp dir");
        let catalog = muorg_core::catalog::Catalog::new(&dir.path().join("test.db"))
            .expect("open catalog");
        Self { catalog, _dir: dir }
    }

    fn conn(&self) -> std::sync::MutexGuard<'_, Connection> {
        self.catalog.db.lock().unwrap()
    }
}

/// Metadata with the fields the tests actually assert on set, the rest default.
fn meta(title: &str, artist: &str, album: &str) -> TrackMetadata {
    TrackMetadata {
        title: Some(title.to_string()),
        artist: Some(artist.to_string()),
        album: Some(album.to_string()),
        album_artist: Some(artist.to_string()),
        duration_secs: Some(180),
        ..TrackMetadata::default()
    }
}

/// Insert one track under `root`, returning its id.
fn insert_track(
    conn: &Connection,
    root: &str,
    path: &str,
    m: &TrackMetadata,
    content_hash: Option<&str>,
) -> i64 {
    let upsert = RootUpsert::new(conn, root).expect("root must exist");
    upsert
        .upsert(&ScannedTrack {
            path,
            format: "mp3",
            mtime_secs: 1_700_000_000,
            content_hash,
            has_cover: false,
            meta: m,
        })
        .expect("upsert");
    conn.query_row("SELECT id FROM tracks WHERE path = ?1", [path], |r| r.get(0))
        .expect("inserted row")
}

/// A root with `n` tracks named `Track {i}`, ids returned in insertion order.
fn seed(conn: &Connection, root: &str, n: usize) -> Vec<i64> {
    cat::save_roots(conn, &[root.to_string()]).unwrap();
    (0..n)
        .map(|i| {
            let m = meta(&format!("Track {i}"), "Artist", "Album");
            insert_track(conn, root, &format!("{root}/track{i}.mp3"), &m, None)
        })
        .collect()
}

fn titles(tracks: &[CatalogTrack]) -> Vec<String> {
    tracks
        .iter()
        .map(|t| t.title.clone().unwrap_or_default())
        .collect()
}

// ---------------------------------------------------------------------------
// Roots
// ---------------------------------------------------------------------------

#[test]
fn saving_a_root_twice_keeps_one_row() {
    let tc = TestCatalog::new();
    let conn = tc.conn();
    cat::save_roots(&conn, &["/music".to_string()]).unwrap();
    cat::save_roots(&conn, &["/music".to_string()]).unwrap();
    assert_eq!(cat::load_roots(&conn).unwrap(), vec!["/music".to_string()]);
}

#[test]
fn removing_a_root_hides_it_and_its_tracks_without_deleting_them() {
    let tc = TestCatalog::new();
    let conn = tc.conn();
    seed(&conn, "/music", 3);
    cat::remove_root(&conn, "/music").unwrap();

    assert!(cat::load_roots(&conn).unwrap().is_empty(), "root should be hidden");
    assert_eq!(cat::count_tracks(&conn).unwrap(), 0, "tracks should be hidden");
    // Still on disk in the DB sense: the rows are soft-deleted, which is what
    // lets re-adding the folder restore ratings and playlist membership.
    let raw: i64 = conn
        .query_row("SELECT COUNT(*) FROM tracks", [], |r| r.get(0))
        .unwrap();
    assert_eq!(raw, 3, "rows should be soft-deleted, not gone");
}

#[test]
fn re_adding_a_removed_root_resurrects_it() {
    let tc = TestCatalog::new();
    let conn = tc.conn();
    seed(&conn, "/music", 1);
    cat::remove_root(&conn, "/music").unwrap();
    cat::save_roots(&conn, &["/music".to_string()]).unwrap();
    assert_eq!(cat::load_roots(&conn).unwrap(), vec!["/music".to_string()]);
}

// ---------------------------------------------------------------------------
// Pagination — the desktop app loads the library in pages, and got this wrong
// ---------------------------------------------------------------------------

#[test]
fn pagination_walks_the_whole_library_without_gaps_or_repeats() {
    let tc = TestCatalog::new();
    let conn = tc.conn();
    let expected = seed(&conn, "/music", 25).len();

    let mut seen: Vec<i64> = Vec::new();
    let mut offset = 0i64;
    loop {
        let (page, total) = cat::load_tracks_paginated(&conn, offset, 10).unwrap();
        assert_eq!(total, expected as i64, "total must not drift between pages");
        if page.is_empty() {
            break;
        }
        seen.extend(page.iter().map(|t| t.id));
        offset += 10;
    }

    assert_eq!(seen.len(), expected, "every track should appear exactly once");
    let mut deduped = seen.clone();
    deduped.sort_unstable();
    deduped.dedup();
    assert_eq!(deduped.len(), seen.len(), "no track should appear twice");
}

#[test]
fn pagination_past_the_end_returns_an_empty_page_not_an_error() {
    let tc = TestCatalog::new();
    let conn = tc.conn();
    seed(&conn, "/music", 3);
    let (page, total) = cat::load_tracks_paginated(&conn, 500, 10).unwrap();
    assert!(page.is_empty());
    assert_eq!(total, 3, "the total is still the library size");
}

#[test]
fn pagination_clamps_a_nonsense_offset_and_limit() {
    let tc = TestCatalog::new();
    let conn = tc.conn();
    seed(&conn, "/music", 3);
    // A negative offset means the start, and a zero limit means at least one
    // row — otherwise a client bug turns into an endless empty-page loop.
    let (page, _) = cat::load_tracks_paginated(&conn, -5, 0).unwrap();
    assert_eq!(page.len(), 1);
}

#[test]
fn a_soft_deleted_track_disappears_from_reads() {
    let tc = TestCatalog::new();
    let conn = tc.conn();
    let ids = seed(&conn, "/music", 3);
    let present: std::collections::HashSet<String> = ["/music/track0.mp3".to_string()]
        .into_iter()
        .collect();
    let swept = cat::sweep_missing_tracks(&conn, "/music", &present).unwrap();
    assert_eq!(swept, 2);

    assert_eq!(cat::count_tracks(&conn).unwrap(), 1);
    assert_eq!(cat::load_tracks(&conn).unwrap().len(), 1);
    assert!(cat::get_track_by_id(&conn, ids[1]).unwrap().is_none());
    assert!(cat::get_track_path_by_id(&conn, ids[1]).unwrap().is_none());
    assert!(cat::get_library_stats(&conn).unwrap().track_count == 1);
}

#[test]
fn sweeping_twice_reports_nothing_the_second_time() {
    let tc = TestCatalog::new();
    let conn = tc.conn();
    seed(&conn, "/music", 2);
    let none = std::collections::HashSet::new();
    assert_eq!(cat::sweep_missing_tracks(&conn, "/music", &none).unwrap(), 2);
    assert_eq!(
        cat::sweep_missing_tracks(&conn, "/music", &none).unwrap(),
        0,
        "already-deleted tracks must not be counted again"
    );
}

// ---------------------------------------------------------------------------
// Move and rename detection
// ---------------------------------------------------------------------------

#[test]
fn a_moved_file_keeps_its_rating_play_count_and_playlists() {
    let tc = TestCatalog::new();
    let conn = tc.conn();
    cat::save_roots(&conn, &["/music".to_string()]).unwrap();
    let m = meta("Song", "Artist", "Album");
    let old_path = "/music/old/song.mp3";
    let id = insert_track(&conn, "/music", old_path, &m, Some("deadbeefcafe"));

    cat::set_track_rating(&conn, old_path, Some(4)).unwrap();
    cat::record_play(&conn, old_path).unwrap();
    let pl = cat::create_playlist(&conn, "Favourites").unwrap();
    cat::add_tracks_to_playlist(&conn, pl.id, &[id]).unwrap();

    // The file moves: the old path is swept, then the scanner finds it at a new
    // path with the same content hash.
    let none = std::collections::HashSet::new();
    cat::sweep_missing_tracks(&conn, "/music", &none).unwrap();
    let new_path = "/music/new/song.mp3";
    let new_id = insert_track(&conn, "/music", new_path, &m, Some("deadbeefcafe"));

    assert_eq!(new_id, id, "the same row should be reused, not a new one");
    let track = cat::get_track_by_id(&conn, id).unwrap().expect("track is live again");
    assert_eq!(track.path, new_path);
    assert_eq!(track.rating, Some(4), "rating should survive the move");
    assert_eq!(track.play_count, 1, "play count should survive the move");
    assert_eq!(
        cat::get_playlist_tracks(&conn, pl.id).unwrap(),
        vec![id],
        "playlist membership should survive the move"
    );
}

#[test]
fn a_different_file_at_a_new_path_gets_its_own_row() {
    let tc = TestCatalog::new();
    let conn = tc.conn();
    cat::save_roots(&conn, &["/music".to_string()]).unwrap();
    let first = insert_track(
        &conn,
        "/music",
        "/music/a.mp3",
        &meta("A", "Artist", "Album"),
        Some("1111aaaa2222"),
    );
    let second = insert_track(
        &conn,
        "/music",
        "/music/b.mp3",
        &meta("B", "Artist", "Album"),
        Some("3333bbbb4444"),
    );
    assert_ne!(first, second);
    assert_eq!(cat::count_tracks(&conn).unwrap(), 2);
}

#[test]
fn rescanning_the_same_path_updates_the_row_in_place() {
    let tc = TestCatalog::new();
    let conn = tc.conn();
    cat::save_roots(&conn, &["/music".to_string()]).unwrap();
    let path = "/music/song.mp3";
    let id = insert_track(&conn, "/music", path, &meta("Old", "Artist", "Album"), None);
    let again = insert_track(&conn, "/music", path, &meta("New", "Artist", "Album"), None);

    assert_eq!(id, again);
    assert_eq!(cat::count_tracks(&conn).unwrap(), 1);
    let track = cat::get_track_by_id(&conn, id).unwrap().unwrap();
    assert_eq!(track.title.as_deref(), Some("New"), "tags should be refreshed");
}

#[test]
fn content_hash_changes_with_size_and_with_bytes() {
    // The hash exists to recognise a moved file, so it has to be stable for the
    // same content and different for different content. Both halves of the
    // input matter: two files of the same length with different tails, and two
    // with the same tail at different lengths, must not collide.
    let a = cat::content_hash_from_parts(1024, b"tail-bytes");
    assert_eq!(a, cat::content_hash_from_parts(1024, b"tail-bytes"));
    assert_ne!(a, cat::content_hash_from_parts(1024, b"other-tail"));
    assert_ne!(a, cat::content_hash_from_parts(2048, b"tail-bytes"));
}

// ---------------------------------------------------------------------------
// Garbage collection
// ---------------------------------------------------------------------------

#[test]
fn gc_removes_old_soft_deletes_and_spares_recent_ones() {
    let tc = TestCatalog::new();
    let conn = tc.conn();
    seed(&conn, "/music", 2);
    let none = std::collections::HashSet::new();
    cat::sweep_missing_tracks(&conn, "/music", &none).unwrap();

    // Just-deleted rows are inside any sane retention window.
    cat::gc_deleted_tracks(&conn, 30 * 24 * 60 * 60).unwrap();
    let kept: i64 = conn
        .query_row("SELECT COUNT(*) FROM tracks", [], |r| r.get(0))
        .unwrap();
    assert_eq!(kept, 2, "recent soft deletes must be recoverable");

    // A zero-second retention means "collect everything already deleted".
    cat::gc_deleted_tracks(&conn, -1).unwrap();
    let gone: i64 = conn
        .query_row("SELECT COUNT(*) FROM tracks", [], |r| r.get(0))
        .unwrap();
    assert_eq!(gone, 0);
}

#[test]
fn gc_never_touches_live_tracks() {
    let tc = TestCatalog::new();
    let conn = tc.conn();
    seed(&conn, "/music", 3);
    cat::gc_deleted_tracks(&conn, -1).unwrap();
    assert_eq!(cat::count_tracks(&conn).unwrap(), 3);
}

// ---------------------------------------------------------------------------
// Search
// ---------------------------------------------------------------------------

#[test]
fn search_matches_a_title_prefix() {
    let tc = TestCatalog::new();
    let conn = tc.conn();
    cat::save_roots(&conn, &["/music".to_string()]).unwrap();
    insert_track(&conn, "/music", "/music/a.mp3", &meta("Paranoid Android", "Radiohead", "OK Computer"), None);
    insert_track(&conn, "/music", "/music/b.mp3", &meta("Karma Police", "Radiohead", "OK Computer"), None);

    let hits = cat::search_tracks(&conn, "parano").unwrap();
    assert_eq!(titles(&hits), vec!["Paranoid Android".to_string()]);
}

#[test]
fn search_matches_on_artist_and_album_too() {
    let tc = TestCatalog::new();
    let conn = tc.conn();
    cat::save_roots(&conn, &["/music".to_string()]).unwrap();
    insert_track(&conn, "/music", "/music/a.mp3", &meta("Paranoid Android", "Radiohead", "OK Computer"), None);
    insert_track(&conn, "/music", "/music/b.mp3", &meta("Teardrop", "Massive Attack", "Mezzanine"), None);

    assert_eq!(cat::search_tracks(&conn, "radiohead").unwrap().len(), 1);
    assert_eq!(cat::search_tracks(&conn, "mezzanine").unwrap().len(), 1);
}

#[test]
fn search_requires_every_word_to_match() {
    let tc = TestCatalog::new();
    let conn = tc.conn();
    cat::save_roots(&conn, &["/music".to_string()]).unwrap();
    insert_track(&conn, "/music", "/music/a.mp3", &meta("Paranoid Android", "Radiohead", "OK Computer"), None);
    insert_track(&conn, "/music", "/music/b.mp3", &meta("Teardrop", "Massive Attack", "Mezzanine"), None);

    assert_eq!(cat::search_tracks(&conn, "radiohead paranoid").unwrap().len(), 1);
    assert!(
        cat::search_tracks(&conn, "radiohead teardrop").unwrap().is_empty(),
        "terms are ANDed, so a cross-track combination matches nothing"
    );
}

#[test]
fn search_survives_quotes_and_apostrophes_in_the_query() {
    // These are FTS5 syntax and SQL syntax respectively; unescaped, either one
    // turns a search box into an error or an injection.
    let tc = TestCatalog::new();
    let conn = tc.conn();
    cat::save_roots(&conn, &["/music".to_string()]).unwrap();
    insert_track(&conn, "/music", "/music/a.mp3", &meta("Dont Stop", "Fleetwood Mac", "Rumours"), None);

    for query in ["\"dont", "don't", "dont\"\" OR 1=1 --", "'; DROP TABLE tracks; --"] {
        let hits = cat::search_tracks(&conn, query);
        assert!(hits.is_ok(), "query {query:?} should not error: {hits:?}");
    }
    // The table is still there.
    assert_eq!(cat::count_tracks(&conn).unwrap(), 1);
}

#[test]
fn an_empty_or_blank_search_returns_nothing() {
    let tc = TestCatalog::new();
    let conn = tc.conn();
    seed(&conn, "/music", 3);
    assert!(cat::search_tracks(&conn, "").unwrap().is_empty());
    assert!(cat::search_tracks(&conn, "   ").unwrap().is_empty());
}

#[test]
fn search_does_not_return_soft_deleted_tracks() {
    let tc = TestCatalog::new();
    let conn = tc.conn();
    cat::save_roots(&conn, &["/music".to_string()]).unwrap();
    insert_track(&conn, "/music", "/music/a.mp3", &meta("Findable", "Artist", "Album"), None);
    assert_eq!(cat::search_tracks(&conn, "findable").unwrap().len(), 1);

    let none = std::collections::HashSet::new();
    cat::sweep_missing_tracks(&conn, "/music", &none).unwrap();
    assert!(
        cat::search_tracks(&conn, "findable").unwrap().is_empty(),
        "the FTS index must not outlive the soft delete"
    );
}

// ---------------------------------------------------------------------------
// Library stats
// ---------------------------------------------------------------------------

#[test]
fn stats_count_artists_and_albums_case_insensitively() {
    let tc = TestCatalog::new();
    let conn = tc.conn();
    cat::save_roots(&conn, &["/music".to_string()]).unwrap();
    insert_track(&conn, "/music", "/music/a.mp3", &meta("A", "Radiohead", "OK Computer"), None);
    insert_track(&conn, "/music", "/music/b.mp3", &meta("B", "radiohead", "ok computer"), None);
    insert_track(&conn, "/music", "/music/c.mp3", &meta("C", "  Radiohead  ", "OK Computer"), None);

    let stats = cat::get_library_stats(&conn).unwrap();
    assert_eq!(stats.track_count, 3);
    assert_eq!(stats.artist_count, 1, "casing and padding are the same artist");
    assert_eq!(stats.album_count, 1, "casing and padding are the same album");
    assert_eq!(stats.total_duration_secs, 540, "3 × 180s");
}

#[test]
fn stats_separate_same_named_albums_by_album_artist() {
    // "Greatest Hits" by two artists is two albums, which is why the album key
    // is album + album_artist rather than album alone.
    let tc = TestCatalog::new();
    let conn = tc.conn();
    cat::save_roots(&conn, &["/music".to_string()]).unwrap();
    insert_track(&conn, "/music", "/music/a.mp3", &meta("A", "Queen", "Greatest Hits"), None);
    insert_track(&conn, "/music", "/music/b.mp3", &meta("B", "Abba", "Greatest Hits"), None);

    let stats = cat::get_library_stats(&conn).unwrap();
    assert_eq!(stats.album_count, 2);
    assert_eq!(stats.artist_count, 2);
}

#[test]
fn stats_on_an_empty_library_are_zero_not_an_error() {
    let tc = TestCatalog::new();
    let conn = tc.conn();
    let stats = cat::get_library_stats(&conn).unwrap();
    assert_eq!(stats.track_count, 0);
    assert_eq!(stats.total_duration_secs, 0, "SUM over no rows must be 0, not NULL");
}

// ---------------------------------------------------------------------------
// Play history
// ---------------------------------------------------------------------------

#[test]
fn recording_a_play_bumps_the_count_and_the_history() {
    let tc = TestCatalog::new();
    let conn = tc.conn();
    let ids = seed(&conn, "/music", 1);
    cat::record_play(&conn, "/music/track0.mp3").unwrap();
    cat::record_play(&conn, "/music/track0.mp3").unwrap();

    let track = cat::get_track_by_id(&conn, ids[0]).unwrap().unwrap();
    assert_eq!(track.play_count, 2);
    assert!(track.last_played_at.is_some());

    let recent = cat::load_recently_played(&conn, 10).unwrap();
    assert_eq!(recent.len(), 1, "the same track twice is one entry, not two");
}

#[test]
fn most_played_ranks_by_count_inside_the_window() {
    let tc = TestCatalog::new();
    let conn = tc.conn();
    seed(&conn, "/music", 2);
    cat::record_play(&conn, "/music/track1.mp3").unwrap();
    cat::record_play(&conn, "/music/track1.mp3").unwrap();
    cat::record_play(&conn, "/music/track0.mp3").unwrap();

    let top = cat::load_most_played(&conn, 10, 30).unwrap();
    assert_eq!(titles(&top).first().map(String::as_str), Some("Track 1"));
    assert_eq!(top.len(), 2);
}

#[test]
fn most_played_over_a_zero_day_window_still_sees_todays_plays() {
    // `days = 0` means "since midnight-ish", not "nothing" — the cutoff is
    // `now - 0`, so a play recorded a moment ago has to be at or after it.
    let tc = TestCatalog::new();
    let conn = tc.conn();
    seed(&conn, "/music", 1);
    cat::record_play(&conn, "/music/track0.mp3").unwrap();
    assert_eq!(cat::load_most_played(&conn, 10, 0).unwrap().len(), 1);
}

#[test]
fn pruning_history_drops_old_entries_and_keeps_the_play_count() {
    let tc = TestCatalog::new();
    let conn = tc.conn();
    let ids = seed(&conn, "/music", 1);
    cat::record_play(&conn, "/music/track0.mp3").unwrap();

    cat::prune_play_history(&conn, -1).unwrap();
    assert!(
        cat::load_recently_played(&conn, 10).unwrap().is_empty(),
        "history rows should be gone"
    );
    let track = cat::get_track_by_id(&conn, ids[0]).unwrap().unwrap();
    assert_eq!(
        track.play_count, 1,
        "the lifetime count lives on the track and must outlive the history"
    );
}

#[test]
fn recording_a_play_for_an_unknown_path_is_a_no_op() {
    let tc = TestCatalog::new();
    let conn = tc.conn();
    seed(&conn, "/music", 1);
    cat::record_play(&conn, "/music/does-not-exist.mp3").unwrap();
    assert!(cat::load_recently_played(&conn, 10).unwrap().is_empty());
}

#[test]
fn recently_added_is_newest_first() {
    let tc = TestCatalog::new();
    let conn = tc.conn();
    seed(&conn, "/music", 3);
    let added = cat::load_recently_added(&conn, 10).unwrap();
    assert_eq!(added.len(), 3);
    // Same created_at for all three, so the tiebreak is id DESC.
    assert_eq!(titles(&added).first().map(String::as_str), Some("Track 2"));
}

// ---------------------------------------------------------------------------
// Playlists
// ---------------------------------------------------------------------------

#[test]
fn a_playlist_keeps_insertion_order_and_allows_the_same_track_twice() {
    let tc = TestCatalog::new();
    let conn = tc.conn();
    let ids = seed(&conn, "/music", 3);
    let pl = cat::create_playlist(&conn, "Mix").unwrap();

    cat::add_tracks_to_playlist(&conn, pl.id, &[ids[2], ids[0]]).unwrap();
    cat::add_tracks_to_playlist(&conn, pl.id, &[ids[2]]).unwrap();

    assert_eq!(
        cat::get_playlist_tracks(&conn, pl.id).unwrap(),
        vec![ids[2], ids[0], ids[2]],
        "a manual playlist is a sequence, so a repeat is legitimate"
    );
    let entries = cat::get_playlist_entries(&conn, pl.id).unwrap();
    assert_eq!(entries.len(), 3);
    assert_eq!(
        entries.iter().map(|e| e.entry_id).collect::<std::collections::HashSet<_>>().len(),
        3,
        "each occurrence needs its own entry id so one copy can be removed"
    );
}

#[test]
fn removing_one_entry_leaves_the_other_copy() {
    let tc = TestCatalog::new();
    let conn = tc.conn();
    let ids = seed(&conn, "/music", 1);
    let pl = cat::create_playlist(&conn, "Mix").unwrap();
    cat::add_tracks_to_playlist(&conn, pl.id, &[ids[0], ids[0]]).unwrap();

    let first = cat::get_playlist_entries(&conn, pl.id).unwrap()[0].entry_id;
    cat::remove_playlist_entry_by_id(&conn, first).unwrap();
    assert_eq!(cat::get_playlist_tracks(&conn, pl.id).unwrap(), vec![ids[0]]);
}

#[test]
fn removing_a_track_by_id_removes_every_copy() {
    let tc = TestCatalog::new();
    let conn = tc.conn();
    let ids = seed(&conn, "/music", 2);
    let pl = cat::create_playlist(&conn, "Mix").unwrap();
    cat::add_tracks_to_playlist(&conn, pl.id, &[ids[0], ids[1], ids[0]]).unwrap();

    cat::remove_tracks_from_playlist(&conn, pl.id, &[ids[0]]).unwrap();
    assert_eq!(cat::get_playlist_tracks(&conn, pl.id).unwrap(), vec![ids[1]]);
}

#[test]
fn playlist_track_count_ignores_soft_deleted_tracks() {
    let tc = TestCatalog::new();
    let conn = tc.conn();
    let ids = seed(&conn, "/music", 2);
    let pl = cat::create_playlist(&conn, "Mix").unwrap();
    cat::add_tracks_to_playlist(&conn, pl.id, &ids).unwrap();
    assert_eq!(cat::load_playlists(&conn).unwrap()[0].track_count, 2);

    let present: std::collections::HashSet<String> =
        ["/music/track0.mp3".to_string()].into_iter().collect();
    cat::sweep_missing_tracks(&conn, "/music", &present).unwrap();
    assert_eq!(
        cat::load_playlists(&conn).unwrap()[0].track_count,
        1,
        "a playlist should not advertise tracks the library no longer has"
    );
}

#[test]
fn reordering_a_playlist_rewrites_positions() {
    let tc = TestCatalog::new();
    let conn = tc.conn();
    let ids = seed(&conn, "/music", 3);
    let pl = cat::create_playlist(&conn, "Mix").unwrap();
    cat::add_tracks_to_playlist(&conn, pl.id, &ids).unwrap();

    let reversed: Vec<i64> = ids.iter().rev().copied().collect();
    cat::reorder_playlist_tracks(&conn, pl.id, &reversed).unwrap();
    assert_eq!(cat::get_playlist_tracks(&conn, pl.id).unwrap(), reversed);
}

#[test]
fn playlists_come_back_in_their_sort_order() {
    let tc = TestCatalog::new();
    let conn = tc.conn();
    let a = cat::create_playlist(&conn, "A").unwrap();
    let b = cat::create_playlist(&conn, "B").unwrap();
    let c = cat::create_playlist(&conn, "C").unwrap();

    cat::reorder_playlists(&conn, &[c.id, a.id, b.id]).unwrap();
    let names: Vec<String> = cat::load_playlists(&conn).unwrap().into_iter().map(|p| p.name).collect();
    assert_eq!(names, vec!["C", "A", "B"]);
}

#[test]
fn deleting_a_playlist_takes_its_entries_but_not_the_tracks() {
    let tc = TestCatalog::new();
    let conn = tc.conn();
    let ids = seed(&conn, "/music", 2);
    let pl = cat::create_playlist(&conn, "Mix").unwrap();
    cat::add_tracks_to_playlist(&conn, pl.id, &ids).unwrap();

    cat::delete_playlist(&conn, pl.id).unwrap();
    assert!(cat::load_playlists(&conn).unwrap().is_empty());
    assert_eq!(cat::count_tracks(&conn).unwrap(), 2);
    let orphans: i64 = conn
        .query_row("SELECT COUNT(*) FROM playlist_tracks", [], |r| r.get(0))
        .unwrap();
    assert_eq!(orphans, 0, "entries should not outlive their playlist");
}

#[test]
fn renaming_and_icons_round_trip() {
    let tc = TestCatalog::new();
    let conn = tc.conn();
    let pl = cat::create_playlist(&conn, "Old").unwrap();
    cat::rename_playlist(&conn, pl.id, "New").unwrap();
    cat::set_playlist_icon(&conn, pl.id, Some("skull")).unwrap();

    let loaded = &cat::load_playlists(&conn).unwrap()[0];
    assert_eq!(loaded.name, "New");
    assert_eq!(loaded.icon.as_deref(), Some("skull"));

    cat::set_playlist_icon(&conn, pl.id, None).unwrap();
    assert!(cat::load_playlists(&conn).unwrap()[0].icon.is_none());
}

#[test]
fn a_tracks_playlist_memberships_are_reported_once_each() {
    let tc = TestCatalog::new();
    let conn = tc.conn();
    let ids = seed(&conn, "/music", 1);
    let a = cat::create_playlist(&conn, "A").unwrap();
    let b = cat::create_playlist(&conn, "B").unwrap();
    cat::add_tracks_to_playlist(&conn, a.id, &[ids[0], ids[0]]).unwrap();
    cat::add_tracks_to_playlist(&conn, b.id, &[ids[0]]).unwrap();

    let mut memberships = cat::get_playlists_for_track(&conn, ids[0]).unwrap();
    memberships.sort_unstable();
    assert_eq!(memberships, vec![a.id, b.id], "twice in A is still one membership");
}

// ---------------------------------------------------------------------------
// Smart playlists — the rule compiler builds SQL, so its edges matter
// ---------------------------------------------------------------------------

/// Three tracks with distinct genres, years and ratings for rule tests.
fn seed_for_rules(conn: &Connection) -> Vec<i64> {
    cat::save_roots(conn, &["/music".to_string()]).unwrap();
    let specs = [
        ("Alpha", "Artist A", "Rock", 1995u32, Some(5i64)),
        ("Beta", "Artist B", "Jazz", 2005, Some(3)),
        ("Gamma", "Artist C", "Rock", 2015, None),
    ];
    specs
        .iter()
        .enumerate()
        .map(|(i, (title, artist, genre, year, rating))| {
            let m = TrackMetadata {
                title: Some(title.to_string()),
                artist: Some(artist.to_string()),
                album: Some("Album".to_string()),
                album_artist: Some(artist.to_string()),
                genre: Some(genre.to_string()),
                year: Some(*year),
                duration_secs: Some(180),
                ..TrackMetadata::default()
            };
            let path = format!("/music/track{i}.mp3");
            let id = insert_track(conn, "/music", &path, &m, None);
            cat::set_track_rating(conn, &path, *rating).unwrap();
            id
        })
        .collect()
}

fn resolve(conn: &Connection, rules: &str) -> Vec<i64> {
    cat::resolve_smart_playlist_track_ids(conn, rules).expect("rules should compile")
}

#[test]
fn smart_rules_on_different_fields_are_anded() {
    let tc = TestCatalog::new();
    let conn = tc.conn();
    let ids = seed_for_rules(&conn);
    let matched = resolve(
        &conn,
        r#"[{"field":"genre","op":"eq","value":"Rock"},{"field":"year","op":"gte","value":2000}]"#,
    );
    assert_eq!(matched, vec![ids[2]], "only Gamma is Rock and from 2000 on");
}

#[test]
fn smart_rules_on_the_same_field_are_ored() {
    // Two `genre = ?` rules mean "either genre", not the empty set — this is
    // the whole reason rules are grouped by field before being joined.
    let tc = TestCatalog::new();
    let conn = tc.conn();
    let ids = seed_for_rules(&conn);
    let mut matched = resolve(
        &conn,
        r#"[{"field":"genre","op":"eq","value":"Rock"},{"field":"genre","op":"eq","value":"Jazz"}]"#,
    );
    matched.sort_unstable();
    let mut all = ids.clone();
    all.sort_unstable();
    assert_eq!(matched, all);
}

#[test]
fn smart_contains_treats_percent_and_underscore_as_literal_text() {
    // Unescaped, `_` is LIKE's single-character wildcard, so a search for
    // "A_pha" would match "Alpha" and the rule would quietly over-select.
    let tc = TestCatalog::new();
    let conn = tc.conn();
    seed_for_rules(&conn);
    assert!(
        resolve(&conn, r#"[{"field":"title","op":"contains","value":"A_pha"}]"#).is_empty(),
        "`_` must not act as a wildcard"
    );
    assert!(
        resolve(&conn, r#"[{"field":"title","op":"contains","value":"%pha"}]"#).is_empty(),
        "`%` must not act as a wildcard"
    );
    assert_eq!(
        resolve(&conn, r#"[{"field":"title","op":"contains","value":"lph"}]"#).len(),
        1,
        "a genuine substring should still match"
    );
}

#[test]
fn smart_null_operators_find_unset_fields() {
    let tc = TestCatalog::new();
    let conn = tc.conn();
    let ids = seed_for_rules(&conn);
    assert_eq!(
        resolve(&conn, r#"[{"field":"rating","op":"is_null"}]"#),
        vec![ids[2]]
    );
    assert_eq!(
        resolve(&conn, r#"[{"field":"rating","op":"is_not_null"}]"#).len(),
        2
    );
}

#[test]
fn smart_rules_reject_an_unknown_field_or_operator() {
    // The field name is interpolated into SQL, so anything outside the
    // allowlist has to be refused rather than passed through.
    let tc = TestCatalog::new();
    let conn = tc.conn();
    seed_for_rules(&conn);
    for rules in [
        r#"[{"field":"path","op":"eq","value":"/etc/passwd"}]"#,
        r#"[{"field":"id) OR 1=1 --","op":"eq","value":1}]"#,
        r#"[{"field":"title","op":"regexp","value":"x"}]"#,
    ] {
        assert!(
            cat::resolve_smart_playlist_track_ids(&conn, rules).is_err(),
            "{rules} should be rejected"
        );
    }
}

#[test]
fn smart_rules_reject_malformed_json_and_missing_values() {
    let tc = TestCatalog::new();
    let conn = tc.conn();
    seed_for_rules(&conn);
    assert!(cat::resolve_smart_playlist_track_ids(&conn, "not json").is_err());
    assert!(
        cat::resolve_smart_playlist_track_ids(&conn, r#"[{"field":"year","op":"eq"}]"#).is_err(),
        "an operator that needs a value must not compile without one"
    );
}

#[test]
fn an_empty_rule_set_selects_the_whole_live_library() {
    let tc = TestCatalog::new();
    let conn = tc.conn();
    let ids = seed_for_rules(&conn);
    assert_eq!(resolve(&conn, "[]").len(), ids.len());
}

#[test]
fn smart_playlists_never_include_soft_deleted_tracks() {
    let tc = TestCatalog::new();
    let conn = tc.conn();
    seed_for_rules(&conn);
    let present: std::collections::HashSet<String> =
        ["/music/track0.mp3".to_string()].into_iter().collect();
    cat::sweep_missing_tracks(&conn, "/music", &present).unwrap();

    assert_eq!(resolve(&conn, r#"[{"field":"genre","op":"eq","value":"Rock"}]"#).len(), 1);
    assert_eq!(resolve(&conn, "[]").len(), 1);
}

#[test]
fn a_smart_playlists_count_follows_the_library() {
    let tc = TestCatalog::new();
    let conn = tc.conn();
    seed_for_rules(&conn);
    let rules = r#"[{"field":"genre","op":"eq","value":"Rock"}]"#;
    let pl = cat::create_smart_playlist(&conn, "Rock", rules).unwrap();
    assert_eq!(pl.track_count, 2);

    // A new Rock track should show up without touching the playlist.
    let m = TrackMetadata {
        title: Some("Delta".to_string()),
        genre: Some("Rock".to_string()),
        ..meta("Delta", "Artist D", "Album")
    };
    insert_track(&conn, "/music", "/music/track3.mp3", &m, None);
    let loaded = cat::load_playlists(&conn).unwrap();
    assert_eq!(loaded[0].track_count, 3);
    assert_eq!(loaded[0].smart_rules.as_deref(), Some(rules));
}

#[test]
fn clearing_the_rules_turns_a_smart_playlist_back_into_a_manual_one() {
    let tc = TestCatalog::new();
    let conn = tc.conn();
    seed_for_rules(&conn);
    let pl = cat::create_smart_playlist(&conn, "Rock", r#"[{"field":"genre","op":"eq","value":"Rock"}]"#).unwrap();
    cat::set_smart_playlist_rules(&conn, pl.id, None).unwrap();

    let loaded = &cat::load_playlists(&conn).unwrap()[0];
    assert!(loaded.smart_rules.is_none());
    assert_eq!(loaded.track_count, 0, "it has no manual entries");
}

// ---------------------------------------------------------------------------
// Metadata writes
// ---------------------------------------------------------------------------

#[test]
fn a_metadata_update_touches_only_the_fields_it_names() {
    let tc = TestCatalog::new();
    let conn = tc.conn();
    let ids = seed(&conn, "/music", 1);
    let path = "/music/track0.mp3";

    cat::update_track_metadata(
        &conn,
        path,
        &MetadataUpdate { title: Some(Some("Renamed".into())), ..Default::default() },
    )
    .unwrap();

    let track = cat::get_track_by_id(&conn, ids[0]).unwrap().unwrap();
    assert_eq!(track.title.as_deref(), Some("Renamed"));
    assert_eq!(track.artist.as_deref(), Some("Artist"), "artist was not in the update");
    assert_eq!(track.album.as_deref(), Some("Album"));
}

#[test]
fn an_explicit_null_clears_a_field_and_an_absent_key_does_not() {
    // The double `Option` is what separates "set to nothing" from "don't
    // touch", and it is the difference between clearing a year and losing one.
    let tc = TestCatalog::new();
    let conn = tc.conn();
    let ids = seed(&conn, "/music", 1);
    let path = "/music/track0.mp3";
    cat::update_track_metadata(
        &conn,
        path,
        &MetadataUpdate { year: Some(Some(1999)), genre: Some(Some("Rock".into())), ..Default::default() },
    )
    .unwrap();

    cat::update_track_metadata(
        &conn,
        path,
        &MetadataUpdate { year: Some(None), ..Default::default() },
    )
    .unwrap();

    let track = cat::get_track_by_id(&conn, ids[0]).unwrap().unwrap();
    assert!(track.year.is_none(), "an explicit null should clear the year");
    assert_eq!(track.genre.as_deref(), Some("Rock"), "genre was untouched");
}

#[test]
fn an_empty_update_is_a_no_op_rather_than_invalid_sql() {
    let tc = TestCatalog::new();
    let conn = tc.conn();
    let ids = seed(&conn, "/music", 1);
    cat::update_track_metadata(&conn, "/music/track0.mp3", &MetadataUpdate::default()).unwrap();
    let track = cat::get_track_by_id(&conn, ids[0]).unwrap().unwrap();
    assert_eq!(track.title.as_deref(), Some("Track 0"));
}

#[test]
fn a_batch_update_applies_every_row() {
    let tc = TestCatalog::new();
    let conn = tc.conn();
    let ids = seed(&conn, "/music", 3);
    let update = MetadataUpdate { album: Some(Some("Compilation".into())), ..Default::default() };
    let paths: Vec<String> = (0..3).map(|i| format!("/music/track{i}.mp3")).collect();
    let batch: Vec<(&str, &MetadataUpdate)> =
        paths.iter().map(|p| (p.as_str(), &update)).collect();

    cat::batch_update_track_metadata(&conn, &batch).unwrap();
    for id in ids {
        let track = cat::get_track_by_id(&conn, id).unwrap().unwrap();
        assert_eq!(track.album.as_deref(), Some("Compilation"));
    }
}

#[test]
fn an_empty_batch_does_not_open_a_transaction() {
    // It used to be possible to leave a `BEGIN DEFERRED` open on an empty
    // batch, which wedges the next writer.
    let tc = TestCatalog::new();
    let conn = tc.conn();
    seed(&conn, "/music", 1);
    cat::batch_update_track_metadata(&conn, &[]).unwrap();
    cat::set_track_rating(&conn, "/music/track0.mp3", Some(1)).unwrap();
}

#[test]
fn setting_a_cover_flips_has_cover_and_clearing_it_flips_back() {
    let tc = TestCatalog::new();
    let conn = tc.conn();
    let ids = seed(&conn, "/music", 1);
    let path = "/music/track0.mp3";

    cat::update_track_metadata(
        &conn,
        path,
        &MetadataUpdate { picture_base64: Some(Some("AAAA".into())), ..Default::default() },
    )
    .unwrap();
    assert!(cat::get_track_by_id(&conn, ids[0]).unwrap().unwrap().has_cover);

    cat::update_track_metadata(
        &conn,
        path,
        &MetadataUpdate { picture_base64: Some(None), ..Default::default() },
    )
    .unwrap();
    assert!(!cat::get_track_by_id(&conn, ids[0]).unwrap().unwrap().has_cover);
}

#[test]
fn an_empty_picture_string_counts_as_no_cover() {
    let tc = TestCatalog::new();
    let conn = tc.conn();
    let ids = seed(&conn, "/music", 1);
    cat::update_track_metadata(
        &conn,
        "/music/track0.mp3",
        &MetadataUpdate { picture_base64: Some(Some(String::new())), ..Default::default() },
    )
    .unwrap();
    assert!(!cat::get_track_by_id(&conn, ids[0]).unwrap().unwrap().has_cover);
}

#[test]
fn a_rating_can_be_set_and_cleared() {
    let tc = TestCatalog::new();
    let conn = tc.conn();
    let ids = seed(&conn, "/music", 1);
    cat::set_track_rating(&conn, "/music/track0.mp3", Some(5)).unwrap();
    assert_eq!(cat::get_track_by_id(&conn, ids[0]).unwrap().unwrap().rating, Some(5));
    cat::set_track_rating(&conn, "/music/track0.mp3", None).unwrap();
    assert!(cat::get_track_by_id(&conn, ids[0]).unwrap().unwrap().rating.is_none());
}

#[test]
fn renaming_a_file_moves_the_row_to_the_new_path() {
    let tc = TestCatalog::new();
    let conn = tc.conn();
    let ids = seed(&conn, "/music", 1);
    cat::update_track_path(&conn, "/music/track0.mp3", "/music/renamed.mp3").unwrap();

    let track = cat::get_track_by_id(&conn, ids[0]).unwrap().unwrap();
    assert_eq!(track.path, "/music/renamed.mp3");
    assert_eq!(
        cat::get_track_path_by_id(&conn, ids[0]).unwrap().as_deref(),
        Some("/music/renamed.mp3")
    );
}

#[test]
fn mtime_round_trips_by_path_and_by_id() {
    let tc = TestCatalog::new();
    let conn = tc.conn();
    let ids = seed(&conn, "/music", 1);
    let path = "/music/track0.mp3";
    cat::update_track_mtime(&conn, path, 1_800_000_000).unwrap();

    assert_eq!(cat::get_track_mtime_by_path(&conn, path).unwrap(), Some(1_800_000_000));
    assert_eq!(
        cat::get_track_path_and_mtime_by_id(&conn, ids[0]).unwrap(),
        Some((path.to_string(), 1_800_000_000))
    );
}

// ---------------------------------------------------------------------------
// Backups
// ---------------------------------------------------------------------------

#[test]
fn the_latest_backup_for_a_track_is_the_most_recent_one() {
    let tc = TestCatalog::new();
    let conn = tc.conn();
    seed(&conn, "/music", 1);
    let path = "/music/track0.mp3";

    assert!(cat::get_latest_track_backup(&conn, path).unwrap().is_none());
    cat::record_track_backup(&conn, path, "/backups/first.mp3").unwrap();
    cat::record_track_backup(&conn, path, "/backups/second.mp3").unwrap();

    let latest = cat::get_latest_track_backup(&conn, path).unwrap().unwrap();
    assert_eq!(latest.backup_path, "/backups/second.mp3");
    assert_eq!(latest.track_path, path);
}

#[test]
fn backups_are_kept_per_track() {
    let tc = TestCatalog::new();
    let conn = tc.conn();
    seed(&conn, "/music", 2);
    cat::record_track_backup(&conn, "/music/track0.mp3", "/backups/a.mp3").unwrap();
    cat::record_track_backup(&conn, "/music/track1.mp3", "/backups/b.mp3").unwrap();

    assert_eq!(
        cat::get_latest_track_backup(&conn, "/music/track0.mp3").unwrap().unwrap().backup_path,
        "/backups/a.mp3"
    );
    assert_eq!(
        cat::get_latest_track_backup(&conn, "/music/track1.mp3").unwrap().unwrap().backup_path,
        "/backups/b.mp3"
    );
}

// ---------------------------------------------------------------------------
// Scanning a real directory
// ---------------------------------------------------------------------------

#[test]
fn scanning_a_directory_indexes_audio_and_ignores_everything_else() {
    let dir = tempfile::tempdir().unwrap();
    let root = dir.path().to_str().unwrap().to_string();
    let fixtures = std::path::Path::new(env!("CARGO_MANIFEST_DIR")).join("tests/fixtures");
    std::fs::copy(fixtures.join("cover_front.mp3"), dir.path().join("song.mp3")).unwrap();
    std::fs::copy(fixtures.join("no_cover.flac"), dir.path().join("song.flac")).unwrap();
    std::fs::write(dir.path().join("cover.jpg"), b"not audio").unwrap();
    std::fs::write(dir.path().join("notes.txt"), b"not audio").unwrap();

    let tc = TestCatalog::new();
    let conn = tc.conn();
    cat::save_roots(&conn, &[root.clone()]).unwrap();
    let added = cat::scan_and_insert(&conn, &root).unwrap();

    assert_eq!(added, 2, "only the mp3 and the flac are audio");
    let formats: std::collections::HashSet<String> = cat::load_tracks(&conn)
        .unwrap()
        .into_iter()
        .map(|t| t.format)
        .collect();
    assert_eq!(
        formats,
        ["mp3".to_string(), "flac".to_string()].into_iter().collect()
    );
}

#[test]
fn a_rescan_after_a_deletion_soft_deletes_the_missing_track() {
    let dir = tempfile::tempdir().unwrap();
    let root = dir.path().to_str().unwrap().to_string();
    let fixtures = std::path::Path::new(env!("CARGO_MANIFEST_DIR")).join("tests/fixtures");
    let song = dir.path().join("song.mp3");
    std::fs::copy(fixtures.join("cover_front.mp3"), &song).unwrap();

    let tc = TestCatalog::new();
    let conn = tc.conn();
    cat::save_roots(&conn, &[root.clone()]).unwrap();
    cat::rescan_root(&conn, &root).unwrap();
    assert_eq!(cat::count_tracks(&conn).unwrap(), 1);

    std::fs::remove_file(&song).unwrap();
    cat::rescan_root(&conn, &root).unwrap();
    assert_eq!(cat::count_tracks(&conn).unwrap(), 0);
}

#[test]
fn scanning_sets_has_cover_from_the_embedded_picture() {
    let dir = tempfile::tempdir().unwrap();
    let root = dir.path().to_str().unwrap().to_string();
    let fixtures = std::path::Path::new(env!("CARGO_MANIFEST_DIR")).join("tests/fixtures");
    std::fs::copy(fixtures.join("cover_front.mp3"), dir.path().join("with.mp3")).unwrap();
    std::fs::copy(fixtures.join("no_cover.flac"), dir.path().join("without.flac")).unwrap();

    let tc = TestCatalog::new();
    let conn = tc.conn();
    cat::save_roots(&conn, &[root.clone()]).unwrap();
    cat::scan_and_insert(&conn, &root).unwrap();

    let tracks = cat::load_tracks(&conn).unwrap();
    let with = tracks.iter().find(|t| t.path.ends_with("with.mp3")).unwrap();
    let without = tracks.iter().find(|t| t.path.ends_with("without.flac")).unwrap();
    assert!(with.has_cover, "the mp3 fixture has embedded art");
    assert!(!without.has_cover, "the flac fixture has none");
}
