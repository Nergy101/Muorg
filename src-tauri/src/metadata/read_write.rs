use lofty::config::WriteOptions;
use id3::TagLike;
use lofty::file::{AudioFile, FileType, TaggedFileExt};
use lofty::picture::PictureType;
use lofty::probe::Probe;
use lofty::tag::{Accessor, Tag, TagType};
use serde::Serialize;
use std::io::BufReader;
use std::path::Path;

/// Deserializer helper for `Option<Option<T>>` fields:
/// - JSON key absent → `None` (leave field unchanged)
/// - JSON key = null  → `Some(None)` (clear field)
/// - JSON key = value → `Some(Some(value))` (set field)
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
    /// Featuring / guest artist. FLAC: FEATURING; MP3: TPE2.
    pub featuring: Option<String>,
    pub year: Option<u32>,
    pub genre: Option<String>,
    pub track_number: Option<u32>,
    pub disc_number: Option<u32>,
    pub duration_secs: Option<u64>,
    pub picture_base64: Option<String>,
    /// MIME type for the picture (e.g. "image/jpeg", "image/png") so the frontend can use the correct data URL.
    pub picture_mime: Option<String>,
    /// Size in bytes of the picture data.
    pub picture_size_bytes: Option<u32>,
    pub replaygain_track_gain_db: Option<f32>,
    pub replaygain_track_peak: Option<f32>,
    pub replaygain_album_gain_db: Option<f32>,
    pub replaygain_album_peak: Option<f32>,
}

/// Detect format from path (extension) and read metadata. Returns None if unsupported or error.
pub fn read_metadata(path: &Path) -> Result<TrackMetadata, String> {
    let ext = path
        .extension()
        .and_then(|e| e.to_str())
        .map(|s| s.to_lowercase());
    if ext.as_deref() != Some("mp3") && ext.as_deref() != Some("flac") {
        return Err("Unsupported format".to_string());
    }

    // Explicitly set format from extension to avoid "no format could be determined" when
    // content-based detection fails (e.g. non-standard MP3 headers).
    let file_type = match ext.as_deref() {
        Some("mp3") => FileType::Mpeg,
        Some("flac") => FileType::Flac,
        _ => return Err("Unsupported format".to_string()),
    };
    let file = std::fs::File::open(path).map_err(|e| e.to_string())?;
    let reader = BufReader::new(file);
    let tagged_file = Probe::with_file_type(reader, file_type)
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
            .get_string(&lofty::tag::ItemKey::AlbumArtist)
            .map(|s| s.to_string());
        // FEATURING is stored as a custom tag key ("FEATURING") across formats.
        if ext.as_deref() == Some("flac") {
            meta.featuring = tag
                .get_string(&lofty::tag::ItemKey::Unknown("FEATURING".to_string()))
                .map(|s| s.to_string());
        } else if ext.as_deref() == Some("mp3") {
            meta.featuring = tag
                .get_string(&lofty::tag::ItemKey::Unknown("FEATURING".to_string()))
                .map(|s| s.to_string());
        }
        meta.year = tag.year();
        if meta.year.is_none() && ext.as_deref() == Some("mp3") {
            // Fallback for MP3 files where Lofty doesn't surface year from legacy ID3 frames.
            if let Ok(id3_tag) = id3::Tag::read_from_path(path) {
                if let Some(y) = id3_tag.year() {
                    if y > 0 {
                        meta.year = Some(y as u32);
                    }
                }
            }
        }
        meta.genre = tag.genre().map(|s| s.to_string());
        meta.track_number = tag.track();
        meta.disc_number = tag.disk();
        if let Some(pic) = tag.get_picture_type(PictureType::CoverFront) {
            meta.picture_base64 = Some(base64::Engine::encode(
                &base64::engine::general_purpose::STANDARD,
                pic.data(),
            ));
            meta.picture_mime = pic.mime_type().map(|m| m.as_str().to_string());
            meta.picture_size_bytes = Some(pic.data().len() as u32);
        }
        meta.replaygain_track_gain_db = parse_replaygain_db(
            tag.get_string(&lofty::tag::ItemKey::Unknown("REPLAYGAIN_TRACK_GAIN".to_string())),
        );
        meta.replaygain_track_peak = parse_replaygain_plain(
            tag.get_string(&lofty::tag::ItemKey::Unknown("REPLAYGAIN_TRACK_PEAK".to_string())),
        );
        meta.replaygain_album_gain_db = parse_replaygain_db(
            tag.get_string(&lofty::tag::ItemKey::Unknown("REPLAYGAIN_ALBUM_GAIN".to_string())),
        );
        meta.replaygain_album_peak = parse_replaygain_plain(
            tag.get_string(&lofty::tag::ItemKey::Unknown("REPLAYGAIN_ALBUM_PEAK".to_string())),
        );
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

/// Absent = leave unchanged; null = clear; value = set.
#[derive(serde::Deserialize)]
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

/// Write metadata to file. Creates tag if missing. Supports MP3 and FLAC.
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

/// MP3 write via id3 crate – avoids lofty's guess_file_type which fails on non-standard headers.
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
            None => {
                tag.remove("TIT2");
            }
        }
    }
    if let Some(v) = &update.artist {
        match v {
            Some(a) => tag.set_artist(a.clone()),
            None => {
                tag.remove("TPE1");
            }
        }
    }
    if let Some(v) = &update.album {
        match v {
            Some(a) => tag.set_album(a.clone()),
            None => {
                tag.remove("TALB");
            }
        }
    }
    if let Some(v) = &update.album_artist {
        match v {
            Some(a) => tag.set_album_artist(a.clone()),
            None => {
                tag.remove("TPE2");
            }
        }
    }

    // Featuring in MP3: store in TXXX:FEATURING (do NOT overload AlbumArtist).
    if let Some(v) = &update.featuring {
        // Remove any existing FEATURING frames (best-effort: remove all TXXX frames).
        // This app only writes FEATURING as TXXX currently, so this is safe/simpler.
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
            None => {
                tag.remove("TCON");
            }
        }
    }
    if let Some(v) = update.track_number {
        match v {
            Some(t) => tag.set_track(t),
            None => {
                tag.remove("TRCK");
            }
        }
    }
    if let Some(v) = update.disc_number {
        match v {
            Some(d) => tag.set_disc(d),
            None => {
                tag.remove("TPOS");
            }
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

/// FLAC write via lofty.
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
            None => tag.remove_key(&lofty::tag::ItemKey::TrackTitle),
        }
    }
    if let Some(v) = &update.artist {
        match v {
            Some(a) => tag.set_artist(a.clone()),
            None => tag.remove_key(&lofty::tag::ItemKey::TrackArtist),
        }
    }
    if let Some(v) = &update.album {
        match v {
            Some(a) => tag.set_album(a.clone()),
            None => tag.remove_key(&lofty::tag::ItemKey::AlbumTitle),
        }
    }
    if let Some(v) = &update.featuring {
        let key = lofty::tag::ItemKey::Unknown("FEATURING".to_string());
        match v {
            Some(f) => {
                tag.insert_text(key, f.clone());
            }
            None => tag.remove_key(&key),
        }
    }
    if let Some(v) = &update.album_artist {
        match v {
            Some(a) => {
                tag.insert_text(lofty::tag::ItemKey::AlbumArtist, a.clone());
            }
            None => tag.remove_key(&lofty::tag::ItemKey::AlbumArtist),
        }
    }
    if let Some(v) = update.year {
        match v {
            Some(y) => tag.set_year(y),
            None => tag.remove_key(&lofty::tag::ItemKey::Year),
        }
    }
    if let Some(v) = &update.genre {
        match v {
            Some(g) => tag.set_genre(g.clone()),
            None => tag.remove_key(&lofty::tag::ItemKey::Genre),
        }
    }
    if let Some(v) = update.track_number {
        match v {
            Some(t) => tag.set_track(t),
            None => tag.remove_key(&lofty::tag::ItemKey::TrackNumber),
        }
    }
    if let Some(v) = update.disc_number {
        match v {
            Some(d) => tag.set_disk(d),
            None => tag.remove_key(&lofty::tag::ItemKey::DiscNumber),
        }
    }
    if let Some(v) = &update.picture_base64 {
        tag.remove_picture_type(PictureType::CoverFront);
        if let Some(b64) = v {
            if !b64.is_empty() {
                match base64::Engine::decode(&base64::engine::general_purpose::STANDARD, b64) {
                    Ok(data) => {
                        let picture = lofty::picture::Picture::new_unchecked(
                            PictureType::CoverFront,
                            Some(lofty::picture::MimeType::Jpeg),
                            None,
                            data,
                        );
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
