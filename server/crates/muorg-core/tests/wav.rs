//! WAV support: tags live in an `id3 ` chunk (the de-facto standard DJ software
//! and taggers write) with RIFF `LIST/INFO` as the older fallback.
//!
//! The cases lean on what a real DJ library carries alongside the audio — cue
//! and sampler chunks, catalogue numbers in TXXX frames — because a tag edit
//! that silently drops those is worse than not supporting WAV at all.

use std::path::{Path, PathBuf};

use id3::TagLike;
use muorg_core::metadata::{
    format_from_ext, needs_transcode, read_metadata, write_metadata, AudioFormat, MetadataUpdate,
};

fn fixture(name: &str) -> PathBuf {
    Path::new(env!("CARGO_MANIFEST_DIR")).join("tests/fixtures").join(name)
}

/// The fixture's chunks as `(id, body)` pairs, `RIFF`/`WAVE` header stripped.
fn chunks(bytes: &[u8]) -> Vec<([u8; 4], Vec<u8>)> {
    assert_eq!(&bytes[0..4], b"RIFF");
    assert_eq!(&bytes[8..12], b"WAVE");
    let mut out = Vec::new();
    let mut pos = 12;
    while pos + 8 <= bytes.len() {
        let id: [u8; 4] = bytes[pos..pos + 4].try_into().unwrap();
        let size = u32::from_le_bytes(bytes[pos + 4..pos + 8].try_into().unwrap()) as usize;
        out.push((id, bytes[pos + 8..pos + 8 + size].to_vec()));
        pos += 8 + size + (size & 1);
    }
    out
}

/// Re-assembles a WAV from chunks, fixing up the RIFF size.
fn assemble(chunks: &[([u8; 4], Vec<u8>)]) -> Vec<u8> {
    let mut body = b"WAVE".to_vec();
    for (id, data) in chunks {
        body.extend_from_slice(id);
        body.extend_from_slice(&(data.len() as u32).to_le_bytes());
        body.extend_from_slice(data);
        if data.len() & 1 == 1 {
            body.push(0);
        }
    }
    let mut out = b"RIFF".to_vec();
    out.extend_from_slice(&(body.len() as u32).to_le_bytes());
    out.extend_from_slice(&body);
    out
}

/// Chunk IDs compare case-insensitively: taggers write both `id3 ` and `ID3 `.
fn chunk<'a>(bytes: &'a [([u8; 4], Vec<u8>)], id: &[u8; 4]) -> Option<&'a Vec<u8>> {
    bytes.iter().find(|(i, _)| i.eq_ignore_ascii_case(id)).map(|(_, d)| d)
}

/// One cue point — the shape rekordbox/Serato leave behind.
fn cue_chunk() -> Vec<u8> {
    let mut cue = 1u32.to_le_bytes().to_vec();
    cue.extend_from_slice(&1u32.to_le_bytes()); // id
    cue.extend_from_slice(&0u32.to_le_bytes()); // position
    cue.extend_from_slice(b"data");
    cue.extend_from_slice(&0u32.to_le_bytes());
    cue.extend_from_slice(&0u32.to_le_bytes());
    cue.extend_from_slice(&4000u32.to_le_bytes()); // sample offset
    cue
}

/// A `LIST/INFO` chunk with the given `(field, value)` pairs.
fn info_chunk(fields: &[(&[u8; 4], &str)]) -> Vec<u8> {
    let mut list = b"INFO".to_vec();
    for (id, value) in fields {
        let mut v = value.as_bytes().to_vec();
        v.push(0);
        list.extend_from_slice(*id);
        list.extend_from_slice(&(v.len() as u32).to_le_bytes());
        list.extend_from_slice(&v);
        if v.len() & 1 == 1 {
            list.push(0);
        }
    }
    list
}

/// A copy of the silent fixture with `extra` chunks appended after `data`.
fn wav_with(dir: &Path, extra: Vec<([u8; 4], Vec<u8>)>) -> PathBuf {
    let mut parts = chunks(&std::fs::read(fixture("silence.wav")).unwrap());
    parts.extend(extra);
    let path = dir.join("track.wav");
    std::fs::write(&path, assemble(&parts)).unwrap();
    path
}

fn set(v: &str) -> Option<Option<String>> {
    Some(Some(v.to_string()))
}

#[test]
fn wav_is_a_recognised_format_that_streams_transcoded() {
    assert_eq!(format_from_ext("wav"), Some(AudioFormat::Wav));
    assert_eq!(format_from_ext("WAV"), Some(AudioFormat::Wav));
    assert_eq!(format_from_ext("wave"), Some(AudioFormat::Wav));
    assert_eq!(AudioFormat::Wav.as_str(), "wav");

    assert!(needs_transcode("/music/a.wav"));
    assert!(needs_transcode("/music/a.flac"));
    assert!(!needs_transcode("/music/a.mp3"));
    assert!(!needs_transcode("/music/a.txt"), "unknown formats are served as-is");
}

#[test]
fn reads_an_untagged_wav() {
    let meta = read_metadata(&fixture("silence.wav")).expect("read wav");
    assert_eq!(meta.title, None);
    assert_eq!(meta.duration_secs, Some(1));
}

#[test]
fn a_tag_edit_round_trips_and_leaves_the_audio_and_cue_points_alone() {
    let dir = tempfile::tempdir().unwrap();
    let path = wav_with(dir.path(), vec![(*b"cue ", cue_chunk())]);
    let before = chunks(&std::fs::read(&path).unwrap());

    write_metadata(
        &path,
        &MetadataUpdate {
            title: set("Sinkhole"),
            artist: set("Mefjus"),
            album: set("Manifest"),
            album_artist: set("Mefjus"),
            featuring: set("Kije"),
            year: Some(Some(2020)),
            genre: set("Drum & Bass"),
            track_number: Some(Some(6)),
            ..Default::default()
        },
    )
    .expect("write wav tags");

    let meta = read_metadata(&path).expect("re-read wav");
    assert_eq!(meta.title.as_deref(), Some("Sinkhole"));
    assert_eq!(meta.artist.as_deref(), Some("Mefjus"));
    assert_eq!(meta.album.as_deref(), Some("Manifest"));
    assert_eq!(meta.album_artist.as_deref(), Some("Mefjus"));
    assert_eq!(meta.year, Some(2020));
    assert_eq!(meta.genre.as_deref(), Some("Drum & Bass"));
    assert_eq!(meta.track_number, Some(6));
    assert_eq!(meta.duration_secs, Some(1));

    let after = chunks(&std::fs::read(&path).unwrap());
    assert_eq!(chunk(&after, b"data"), chunk(&before, b"data"), "audio must be untouched");
    assert_eq!(chunk(&after, b"fmt "), chunk(&before, b"fmt "));
    assert_eq!(chunk(&after, b"cue "), chunk(&before, b"cue "), "cue points must survive");
    assert!(chunk(&after, b"id3 ").is_some(), "tags go in an id3 chunk");
}

#[test]
fn editing_featuring_keeps_the_other_txxx_frames() {
    let dir = tempfile::tempdir().unwrap();
    let path = wav_with(dir.path(), vec![]);
    let mut tag = id3::Tag::new();
    tag.set_title("These Eyes");
    tag.add_frame(id3::frame::ExtendedText {
        description: "CATALOGNUMBER".to_string(),
        value: "DIVIDID001".to_string(),
    });
    tag.write_to_path(&path, id3::Version::Id3v23).unwrap();

    write_metadata(&path, &MetadataUpdate { featuring: set("Abis"), ..Default::default() })
        .expect("write featuring");

    let tag = id3::Tag::read_from_path(&path).unwrap();
    let txxx: Vec<(String, String)> = tag
        .extended_texts()
        .map(|t| (t.description.clone(), t.value.clone()))
        .collect();
    assert!(txxx.contains(&("CATALOGNUMBER".to_string(), "DIVIDID001".to_string())));
    assert!(txxx.contains(&("FEATURING".to_string(), "Abis".to_string())));
    assert_eq!(tag.title(), Some("These Eyes"));
    assert_eq!(tag.version(), id3::Version::Id3v23, "the existing ID3 version is kept");
}

#[test]
fn reads_riff_info_and_keeps_it_visible_after_the_first_edit() {
    let dir = tempfile::tempdir().unwrap();
    let path = wav_with(
        dir.path(),
        vec![(*b"LIST", info_chunk(&[(b"INAM", "Puma Rhythm"), (b"IART", "Thys & Two Fingers")]))],
    );

    let meta = read_metadata(&path).expect("read INFO-tagged wav");
    assert_eq!(meta.title.as_deref(), Some("Puma Rhythm"));
    assert_eq!(meta.artist.as_deref(), Some("Thys & Two Fingers"));

    write_metadata(&path, &MetadataUpdate { genre: set("Techno"), ..Default::default() })
        .expect("write genre");

    let meta = read_metadata(&path).expect("re-read");
    assert_eq!(meta.genre.as_deref(), Some("Techno"));
    assert_eq!(meta.title.as_deref(), Some("Puma Rhythm"), "INFO title must carry over");
    assert_eq!(meta.artist.as_deref(), Some("Thys & Two Fingers"));
}
