//! Regression tests for embedded cover-art extraction.
//!
//! Covers the bug where only `PictureType::CoverFront` was extracted, so files
//! whose artwork is tagged `Other` (the common case for FLAC and many MP3s) had
//! no cover — `has_cover` stayed false at scan time and `/cover` returned 404.

use std::path::Path;

use muorg_core::metadata::read_metadata;

fn fixture(name: &str) -> std::path::PathBuf {
    Path::new(env!("CARGO_MANIFEST_DIR")).join("tests/fixtures").join(name)
}

#[test]
fn extracts_front_cover_from_mp3() {
    let meta = read_metadata(&fixture("cover_front.mp3")).expect("read mp3");
    let b64 = meta.picture_base64.expect("mp3 should have a picture");
    assert!(!b64.is_empty(), "picture data must be non-empty");
    assert_eq!(meta.picture_mime.as_deref(), Some("image/jpeg"));
    assert!(meta.picture_size_bytes.is_some_and(|n| n > 0));
}

#[test]
fn extracts_non_front_cover_from_flac() {
    // The picture in this fixture is tagged PictureType::Other, not CoverFront.
    let meta = read_metadata(&fixture("cover_other.flac")).expect("read flac");
    let b64 = meta.picture_base64.expect("flac 'Other' picture should be extracted");
    assert!(!b64.is_empty(), "picture data must be non-empty");
    assert_eq!(meta.picture_mime.as_deref(), Some("image/jpeg"));
    assert!(meta.picture_size_bytes.is_some_and(|n| n > 0));
}

#[test]
fn reports_no_cover_when_absent() {
    let meta = read_metadata(&fixture("no_cover.flac")).expect("read flac");
    assert!(
        meta.picture_base64.is_none(),
        "file without artwork must not report a picture"
    );
}
