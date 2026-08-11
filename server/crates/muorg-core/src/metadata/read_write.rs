use lofty::config::WriteOptions;
use id3::TagLike;
use lofty::file::{AudioFile, FileType, TaggedFileExt};
use lofty::picture::PictureType;
use lofty::probe::Probe;
use lofty::tag::{Accessor, Tag, TagType};
use serde::Serialize;
use std::io::BufReader;
use std::path::Path;

mod double_option {
    use serde::{Deserialize, Deserializer};
    pub fn deserialize<'de, T, D>(deserializer: D) -> Result<Option<Option<T>>, D::Error>
    where
        T: Deserialize<'de>,
        D: Deserializer<'de>,
    {
        Deserialize::deserialize(deserializer).map(Some)
    }
}

#[derive(Debug, Clone, Default, Serialize)]
pub struct TrackMetadata {
    pub title: Option<String>,
    pub artist: Option<String>,
    pub album: Option<String>,
    pub album_artist: Option<String>,
    pub featuring: Option<String>,
    pub year: Option<u32>,
    pub genre: Option<String>,
    pub track_number: Option<u32>,
    pub disc_number: Option<u32>,
    pub duration_secs: Option<u64>,
    pub picture_base64: Option<String>,
    pub picture_mime: Option<String>,
    pub picture_size_bytes: Option<u32>,
    pub replaygain_track_gain_db: Option<f32>,
    pub replaygain_track_peak: Option<f32>,
    pub replaygain_album_gain_db: Option<f32>,
    pub replaygain_album_peak: Option<f32>,
    /// Embedded lyrics text (USLT / UNSYNCEDLYRICS, or synced when available).
    pub lyrics: Option<String>,
    /// `"lrc"` when the lyrics text carries `[mm:ss.xx]` timing lines, else
    /// `"plain"`. Absent when there are no embedded lyrics.
    pub lyrics_format: Option<String>,
}

/// Audio container formats muorg can read tags from.
///
/// Exists so callers outside this crate can pick a format without depending on
/// `lofty`.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum AudioFormat {
    Mp3,
    Flac,
}

impl AudioFormat {
    pub fn as_str(self) -> &'static str {
        match self {
            Self::Mp3 => "mp3",
            Self::Flac => "flac",
        }
    }

    fn file_type(self) -> FileType {
        match self {
            Self::Mp3 => FileType::Mpeg,
            Self::Flac => FileType::Flac,
        }
    }
}

/// Maps a file extension to a supported format. `ext` is matched
/// case-insensitively and must not include the leading dot.
pub fn format_from_ext(ext: &str) -> Option<AudioFormat> {
    match ext.to_lowercase().as_str() {
        "mp3" => Some(AudioFormat::Mp3),
        "flac" => Some(AudioFormat::Flac),
        _ => None,
    }
}

pub fn read_metadata(path: &Path) -> Result<TrackMetadata, String> {
    let format = path
        .extension()
        .and_then(|e| e.to_str())
        .and_then(format_from_ext)
        .ok_or_else(|| "Unsupported format".to_string())?;
    let file = std::fs::File::open(path).map_err(|e| e.to_string())?;
    read_metadata_from_reader(BufReader::new(file), format)
}

/// Reads tags from any seekable source. Used for object-storage tracks, where
/// there is no local file to open.
pub fn read_metadata_from_reader<R: std::io::Read + std::io::Seek>(
    mut reader: R,
    format: AudioFormat,
) -> Result<TrackMetadata, String> {
    let tagged_file = Probe::with_file_type(&mut reader, format.file_type())
        .read()
        .map_err(|e| e.to_string())?;

    let tag = tagged_file
        .primary_tag()
        .or_else(|| tagged_file.first_tag());
    let mut meta = TrackMetadata::default();

    if let Some(tag) = tag {
        meta.title = tag.title().map(|s| s.to_string());
        meta.artist = tag.artist().map(|s| s.to_string());
        meta.album = tag.album().map(|s| s.to_string());
        meta.album_artist = tag
            .get_string(lofty::tag::ItemKey::AlbumArtist)
            .map(|s| s.to_string());
        meta.featuring = lofty::tag::ItemKey::from_key(tag.tag_type(), "FEATURING")
            .and_then(|k| tag.get_string(k))
            .map(|s| s.to_string());
        meta.year = tag.date().map(|d| d.year as u32);
        if meta.year.is_none() && format == AudioFormat::Mp3 {
            // lofty does not surface a bare ID3v2 TYER/TDRC year on every file;
            // fall back to id3's own parse before giving up.
            if reader.seek(std::io::SeekFrom::Start(0)).is_ok() {
                if let Ok(id3_tag) = id3::Tag::read_from2(&mut reader) {
                    if let Some(y) = id3_tag.year() {
                        if y > 0 {
                            meta.year = Some(y as u32);
                        }
                    }
                }
            }
        }
        meta.genre = tag.genre().map(|s| s.to_string());
        meta.track_number = tag.track();
        meta.disc_number = tag.disk();
        // Prefer an explicit front cover, but fall back to any embedded picture.
        // Many files (especially FLAC, and MP3s tagged by various tools) store
        // artwork as PictureType::Other or with no front-cover designation, so
        // matching CoverFront alone would drop their cover art entirely.
        let pic = tag
            .get_picture_type(PictureType::CoverFront)
            .or_else(|| tag.pictures().first());
        if let Some(pic) = pic {
            meta.picture_base64 = Some(base64::Engine::encode(
                &base64::engine::general_purpose::STANDARD,
                pic.data(),
            ));
            meta.picture_mime = pic.mime_type().map(|m| m.as_str().to_string());
            meta.picture_size_bytes = Some(pic.data().len() as u32);
        }
        meta.replaygain_track_gain_db = parse_replaygain_db(
            tag.get_string(lofty::tag::ItemKey::ReplayGainTrackGain),
        );
        meta.replaygain_track_peak = parse_replaygain_plain(
            tag.get_string(lofty::tag::ItemKey::ReplayGainTrackPeak),
        );
        meta.replaygain_album_gain_db = parse_replaygain_db(
            tag.get_string(lofty::tag::ItemKey::ReplayGainAlbumGain),
        );
        meta.replaygain_album_peak = parse_replaygain_plain(
            tag.get_string(lofty::tag::ItemKey::ReplayGainAlbumPeak),
        );
        // Embedded lyrics: USLT / UNSYNCEDLYRICS surface under ItemKey::Lyrics
        // (a synced LRC-style track is flagged by its `[mm:ss]` timestamp lines).
        let lyrics = tag
            .get_string(lofty::tag::ItemKey::Lyrics)
            .or_else(|| tag.get_string(lofty::tag::ItemKey::UnsyncLyrics))
            .map(|s| s.trim().to_string())
            .filter(|s| !s.is_empty());
        meta.lyrics = lyrics;
        meta.lyrics_format = meta
            .lyrics
            .as_ref()
            .map(|l| detect_lyrics_format(l).to_string());
    }

    meta.duration_secs = Some(tagged_file.properties().duration().as_secs());

    Ok(meta)
}

fn parse_replaygain_db(v: Option<&str>) -> Option<f32> {
    let s = v?.trim();
    let trimmed = s.trim_end_matches("dB").trim_end_matches("DB").trim();
    trimmed.parse::<f32>().ok()
}

fn parse_replaygain_plain(v: Option<&str>) -> Option<f32> {
    v?.trim().parse::<f32>().ok()
}

/// A lyrics blob is treated as synchronised ("lrc") when it carries at least
/// one `[mm:ss]` / `[mm:ss.xx]` timestamp line; otherwise it is plain text.
fn detect_lyrics_format(text: &str) -> &'static str {
    let has_timestamp = text.lines().any(|line| {
        let t = line.trim_start();
        t.starts_with('[')
            && t[1..].split_once(']').is_some_and(|(stamp, _)| {
                let digits = stamp.chars().filter(|c| *c == ':').count() == 1
                    && stamp
                        .split(':')
                        .all(|part| !part.is_empty() && part.chars().all(|c| c.is_ascii_digit() || c == '.'));
                digits
            })
    });
    if has_timestamp { "lrc" } else { "plain" }
}

#[derive(serde::Deserialize, Clone)]
pub struct MetadataUpdate {
    #[serde(default, deserialize_with = "double_option::deserialize")]
    pub title: Option<Option<String>>,
    #[serde(default, deserialize_with = "double_option::deserialize")]
    pub artist: Option<Option<String>>,
    #[serde(default, deserialize_with = "double_option::deserialize")]
    pub album: Option<Option<String>>,
    #[serde(default, deserialize_with = "double_option::deserialize")]
    pub album_artist: Option<Option<String>>,
    #[serde(default, deserialize_with = "double_option::deserialize")]
    pub featuring: Option<Option<String>>,
    #[serde(default, deserialize_with = "double_option::deserialize")]
    pub year: Option<Option<u32>>,
    #[serde(default, deserialize_with = "double_option::deserialize")]
    pub genre: Option<Option<String>>,
    #[serde(default, deserialize_with = "double_option::deserialize")]
    pub track_number: Option<Option<u32>>,
    #[serde(default, deserialize_with = "double_option::deserialize")]
    pub disc_number: Option<Option<u32>>,
    #[serde(default, deserialize_with = "double_option::deserialize")]
    pub picture_base64: Option<Option<String>>,
}

pub fn write_metadata(path: &Path, update: &MetadataUpdate) -> Result<(), String> {
    let ext = path
        .extension()
        .and_then(|e| e.to_str())
        .map(|s| s.to_lowercase());
    match ext.as_deref() {
        Some("mp3") => write_metadata_mp3(path, update),
        Some("flac") => write_metadata_flac(path, update),
        _ => Err("Unsupported format".to_string()),
    }
}

fn write_metadata_mp3(path: &Path, update: &MetadataUpdate) -> Result<(), String> {
    use id3::frame::{Content, ExtendedText, Frame, Picture, PictureType};
    use id3::{TagLike, Version};

    let mut tag = match id3::Tag::read_from_path(path) {
        Ok(t) => t,
        Err(id3::Error {
            kind: id3::ErrorKind::NoTag,
            ..
        }) => id3::Tag::new(),
        Err(e) => return Err(e.to_string()),
    };

    if let Some(v) = &update.title {
        match v {
            Some(t) => tag.set_title(t.clone()),
            None => { tag.remove("TIT2"); }
        }
    }
    if let Some(v) = &update.artist {
        match v {
            Some(a) => tag.set_artist(a.clone()),
            None => { tag.remove("TPE1"); }
        }
    }
    if let Some(v) = &update.album {
        match v {
            Some(a) => tag.set_album(a.clone()),
            None => { tag.remove("TALB"); }
        }
    }
    if let Some(v) = &update.album_artist {
        match v {
            Some(a) => tag.set_album_artist(a.clone()),
            None => { tag.remove("TPE2"); }
        }
    }
    if let Some(v) = &update.featuring {
        tag.remove("TXXX");
        if let Some(f) = v {
            tag.add_frame(Frame::with_content(
                "TXXX",
                Content::ExtendedText(ExtendedText {
                    description: "FEATURING".to_string(),
                    value: f.clone(),
                }),
            ));
        }
    }
    if let Some(v) = update.year {
        match v {
            Some(y) => tag.set_year(y as i32),
            None => {
                tag.remove("TYER");
                tag.remove("TDRC");
            }
        }
    }
    if let Some(v) = &update.genre {
        match v {
            Some(g) => tag.set_genre(g.clone()),
            None => { tag.remove("TCON"); }
        }
    }
    if let Some(v) = update.track_number {
        match v {
            Some(t) => tag.set_track(t),
            None => { tag.remove("TRCK"); }
        }
    }
    if let Some(v) = update.disc_number {
        match v {
            Some(d) => tag.set_disc(d),
            None => { tag.remove("TPOS"); }
        }
    }
    if let Some(v) = &update.picture_base64 {
        tag.remove_picture_by_type(PictureType::CoverFront);
        if let Some(b64) = v {
            if !b64.is_empty() {
                match base64::Engine::decode(&base64::engine::general_purpose::STANDARD, b64) {
                    Ok(data) => {
                        tag.add_frame(Picture {
                            mime_type: "image/jpeg".to_string(),
                            picture_type: PictureType::CoverFront,
                            description: String::new(),
                            data,
                        });
                    }
                    Err(_) => return Err("Invalid base64 for picture".to_string()),
                }
            }
        }
    }

    tag.write_to_path(path, Version::Id3v24).map_err(|e| e.to_string())?;
    Ok(())
}

fn write_metadata_flac(path: &Path, update: &MetadataUpdate) -> Result<(), String> {
    let file = std::fs::File::open(path).map_err(|e| e.to_string())?;
    let reader = BufReader::new(file);
    let mut tagged_file = Probe::with_file_type(reader, FileType::Flac)
        .read()
        .map_err(|e| e.to_string())?;

    let tag = if let Some(t) = tagged_file.primary_tag_mut() {
        t
    } else if let Some(t) = tagged_file.first_tag_mut() {
        t
    } else {
        tagged_file.insert_tag(Tag::new(TagType::VorbisComments));
        tagged_file.primary_tag_mut().unwrap()
    };

    if let Some(v) = &update.title {
        match v {
            Some(t) => tag.set_title(t.clone()),
            None => tag.remove_key(lofty::tag::ItemKey::TrackTitle),
        }
    }
    if let Some(v) = &update.artist {
        match v {
            Some(a) => tag.set_artist(a.clone()),
            None => tag.remove_key(lofty::tag::ItemKey::TrackArtist),
        }
    }
    if let Some(v) = &update.album {
        match v {
            Some(a) => tag.set_album(a.clone()),
            None => tag.remove_key(lofty::tag::ItemKey::AlbumTitle),
        }
    }
    if let Some(v) = &update.featuring {
        let key = lofty::tag::ItemKey::from_key(tag.tag_type(), "FEATURING");
        match v {
            Some(f) => {
                if let Some(k) = key {
                    tag.insert_text(k, f.clone());
                }
            }
            None => {
                if let Some(k) = key {
                    tag.remove_key(k);
                }
            }
        }
    }
    if let Some(v) = &update.album_artist {
        match v {
            Some(a) => { tag.insert_text(lofty::tag::ItemKey::AlbumArtist, a.clone()); }
            None => tag.remove_key(lofty::tag::ItemKey::AlbumArtist),
        }
    }
    if let Some(v) = update.year {
        match v {
            Some(y) => tag.set_date(lofty::tag::items::Timestamp { year: y as u16, month: None, day: None, hour: None, minute: None, second: None }),
            None => tag.remove_key(lofty::tag::ItemKey::Year),
        }
    }
    if let Some(v) = &update.genre {
        match v {
            Some(g) => tag.set_genre(g.clone()),
            None => tag.remove_key(lofty::tag::ItemKey::Genre),
        }
    }
    if let Some(v) = update.track_number {
        match v {
            Some(t) => tag.set_track(t),
            None => tag.remove_key(lofty::tag::ItemKey::TrackNumber),
        }
    }
    if let Some(v) = update.disc_number {
        match v {
            Some(d) => tag.set_disk(d),
            None => tag.remove_key(lofty::tag::ItemKey::DiscNumber),
        }
    }
    if let Some(v) = &update.picture_base64 {
        tag.remove_picture_type(PictureType::CoverFront);
        if let Some(b64) = v {
            if !b64.is_empty() {
                match base64::Engine::decode(&base64::engine::general_purpose::STANDARD, b64) {
                    Ok(data) => {
                        let picture = lofty::picture::Picture::unchecked(data)
                            .pic_type(PictureType::CoverFront)
                            .mime_type(lofty::picture::MimeType::Jpeg)
                            .build();
                        tag.push_picture(picture);
                    }
                    Err(_) => return Err("Invalid base64 for picture".to_string()),
                }
            }
        }
    }

    tagged_file
        .save_to_path(path, WriteOptions::default())
        .map_err(|e| e.to_string())?;

    Ok(())
}

#[cfg(test)]
mod tests {
    use super::detect_lyrics_format;

    #[test]
    fn detects_lrc_vs_plain() {
        assert_eq!(detect_lyrics_format("[00:01.00]line one\n[00:02.00]line two"), "lrc");
        assert_eq!(detect_lyrics_format("[00:01]line one"), "lrc");
        assert_eq!(detect_lyrics_format("line one\nline two"), "plain");
        assert_eq!(detect_lyrics_format(""), "plain");
        // Bracketed but not a timestamp → not LRC.
        assert_eq!(detect_lyrics_format("[verse 1] chorus"), "plain");
    }
}
